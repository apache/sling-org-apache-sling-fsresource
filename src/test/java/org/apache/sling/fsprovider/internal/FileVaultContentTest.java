/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.fsprovider.internal;

import static org.apache.sling.fsprovider.internal.TestUtils.assertFile;
import static org.apache.sling.fsprovider.internal.TestUtils.assertFolder;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import com.google.common.collect.Iterables;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.fsprovider.internal.TestUtils.RegisterFsResourcePlugin;
import org.apache.sling.hamcrest.ResourceMatchers;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.junit.SlingContextBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

/**
 * Test access FileFault XML files, folders and content.
 */
public class FileVaultContentTest {

    private Resource damAsset;
    private Resource sampleContent;

    @Rule
    public SlingContext context = new SlingContextBuilder(ResourceResolverType.JCR_MOCK)
        .plugin(new RegisterFsResourcePlugin(
                "provider.fs.mode", FsMode.FILEVAULT_XML.name(),
                "provider.file", "src/test/resources/vaultfs-test/jcr_root",
                "provider.filevault.filterxml.path", "src/test/resources/vaultfs-test/META-INF/vault/filter.xml",
                "provider.root", "/content/dam/talk.png"
                ))
        .plugin(new RegisterFsResourcePlugin(
                "provider.fs.mode", FsMode.FILEVAULT_XML.name(),
                "provider.file", "src/test/resources/vaultfs-test/jcr_root",
                "provider.filevault.filterxml.path", "src/test/resources/vaultfs-test/META-INF/vault/filter.xml",
                "provider.root", "/content/samples"
                ))
        .build();

    @Before
    public void setUp() {
        damAsset = context.resourceResolver().getResource("/content/dam/talk.png");
        sampleContent = context.resourceResolver().getResource("/content/samples");
    }

    @Test
    public void testDamAsset() {
        assertNotNull(damAsset);
        assertEquals("app:Asset", damAsset.getResourceType());
        
        Resource content = damAsset.getChild("jcr:content");
        assertNotNull(content);
        assertEquals("app:AssetContent", content.getResourceType());
        
        Resource metadata = content.getChild("metadata");
        assertNotNull(metadata);
        ValueMap props = metadata.getValueMap();
        assertEquals((Integer)4, props.get("app:Bitsperpixel", Integer.class));
        
        assertFolder(content, "renditions");
        assertFile(content, "renditions/original", null);
        assertFile(content, "renditions/web.1280.1280.png", null);
    }

    @Test
    public void testSampleContent() {
        assertNotNull(sampleContent);
        assertEquals("sling:OrderedFolder", sampleContent.getResourceType());

        Resource enContent = sampleContent.getChild("en/jcr:content");
        assertArrayEquals(new String[] { "/etc/mobile/groups/responsive" }, enContent.getValueMap().get("app:deviceGroups", String[].class));
    }

    @Test
    public void testListChildren() {
        Resource en = sampleContent.getChild("en");
        // about_us and about_them are not defined for ordering in en/.content.xml and thus sorted last
        assertThat(en, ResourceMatchers.containsChildren("jcr:content", "tools", "extra", "about_them", "about_us"));
        assertEquals("samples/sample-app/components/content/page/homepage", en.getChild("jcr:content").getResourceType());
        assertEquals("app:Page", en.getChild("tools").getResourceType());

        // another child (conference) is hidden because of filter
    }

    @Test
    public void testJcrMixedContent() throws RepositoryException {
        // prepare mixed JCR content
        Node root = context.resourceResolver().adaptTo(Session.class).getNode("/");
        Node content = root.addNode("content", "nt:folder");
        Node samples = content.addNode("samples", "nt:folder");
        Node en = samples.addNode("en", "nt:folder");
        Node conference = en.addNode("conference", "nt:folder");
        conference.addNode("page2", "nt:folder");
        samples.addNode("it", "nt:folder");
        
        // pass-through because of filter
        assertNotNull(context.resourceResolver().getResource("/content/samples/en/conference"));
        assertNotNull(sampleContent.getChild("en/conference"));
        assertNotNull(context.resourceResolver().getResource("/content/samples/en/conference/page2"));
        assertNotNull(sampleContent.getChild("en/conference/page2"));
        
        // hidden because overlayed by resource provider
        assertNull(context.resourceResolver().getResource("/content/samples/it"));
        assertNull(sampleContent.getChild("it"));

        // list children with mixed content
        Resource enResource = sampleContent.getChild("en");
        assertThat(enResource, ResourceMatchers.containsChildren("jcr:content", "tools", "extra", "about_them", "about_us", "conference"));
    }

    @Test
    public void testExtraContent() throws RepositoryException {
        Resource extraContent = sampleContent.getChild("en/extra/extracontent");
        assertNotNull(extraContent);
        assertEquals("apps/app1/components/comp1", extraContent.getResourceType());
        
        Resource layout = extraContent.getChild("layout");
        assertNotNull(layout);
        assertEquals("apps/app1/components/comp2", layout.getResourceType());

        Resource binaryFile = sampleContent.getChild("en/extra/binaryfile.xml");
        assertNotNull(binaryFile);
        assertEquals("nt:file", binaryFile.getResourceType());
    }

    @Test
    public void testAggregateFilesDirectAccess() throws Exception {
        Resource aggregate = sampleContent.getChild("aggregates/sling:aggregate");
        assertNotNull("aggregate is null", aggregate);
        assertEquals("Aggregate Test", aggregate.getValueMap().get("jcr:title", String.class));
        assertTrue("sling:aggregate has no children", aggregate.hasChildren());
        Resource child = aggregate.getChild("child");
        assertNotNull("sling:aggregate has no child called 'child'", child);
        assertEquals("Child of Aggregate", child.getValueMap().get("jcr:title", String.class));
    }

    @Test
    public void testAggregateFilesAccessByChildIteration() throws Exception {
        Resource aggregates = sampleContent.getChild("aggregates");
        assertNotNull("aggregates folder is null", aggregates);
        Map<String, Resource> childrenByName = new HashMap<>();
        for (final Resource child : aggregates.getChildren()) {
            childrenByName.put(child.getName(), child);
        }

        assertEquals("Wrong child count for 'aggregates'",1, childrenByName.size());
        assertTrue("Child named 'sling:aggregate' does not exist", childrenByName.containsKey("sling:aggregate"));
    }
}
