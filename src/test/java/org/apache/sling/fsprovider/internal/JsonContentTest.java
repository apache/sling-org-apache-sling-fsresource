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

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.nodetype.NodeType;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.contentparser.api.ParserOptions;
import org.apache.sling.fsprovider.internal.TestUtils.RegisterFsResourcePlugin;
import org.apache.sling.hamcrest.ResourceMatchers;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.junit.SlingContextBuilder;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import static org.apache.sling.fsprovider.internal.TestUtils.assertFile;
import static org.apache.sling.fsprovider.internal.TestUtils.assertFolder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Test access to files and folders and JSON content from file system.
 */
@SuppressWarnings("null")
public class JsonContentTest {

    private Resource root;
    private Resource fsroot;

    @Rule
    public SlingContext context = new SlingContextBuilder(ResourceResolverType.JCR_MOCK)
            .plugin(new RegisterFsResourcePlugin(
                    "provider.fs.mode",
                    FsMode.INITIAL_CONTENT.name(),
                    "provider.initial.content.import.options",
                    "overwrite:=true;ignoreImportProviders:=jcr.xml"))
            .build();

    @Before
    public void setUp() {
        root = context.resourceResolver().getResource("/");
        fsroot = context.resourceResolver().getResource("/fs-test");
    }

    @Test
    public void testFolders() {
        assertFolder(fsroot, "folder1");
        assertFolder(fsroot, "folder1/folder11");
        assertFolder(fsroot, "folder2");
    }

    @Test
    public void testFiles() {
        assertFile(fsroot, "folder1/file1a.txt", "file1a");
        assertFile(fsroot, "folder1/sling:file1b.txt", "file1b");
        assertFile(fsroot, "folder1/folder11/file11a.txt", "file11a");
        assertNull(fsroot.getChild("folder2/content.json"));
        assertFile(fsroot, "folder2/content/file2content.txt", "file2content");
        assertFile(fsroot, "folder3/content.jcr.xml", null);
    }

    @Test
    public void testListChildren() {
        assertThat(root, ResourceMatchers.containsChildren("fs-test"));
        assertThat(fsroot, ResourceMatchers.hasChildren("folder1", "folder2"));
        assertThat(
                fsroot.getChild("folder1"),
                ResourceMatchers.containsChildren("file1a.txt", "folder11", "sling:file1b.txt"));
        assertThat(fsroot.getChild("folder2"), ResourceMatchers.containsChildren("content", "folder21"));
        assertThat(
                fsroot.getChild("folder2/content"),
                ResourceMatchers.containsChildren(
                        "jcr:content",
                        "toolbar",
                        "child",
                        "file2content.txt",
                        "fileWithOverwrittenMimeType.scss",
                        "sling:content2"));
        assertThat(
                fsroot.getChild("folder2/content/child"),
                ResourceMatchers.containsChildren("jcr:content", "grandchild"));
    }

    @Test
    public void testContent_Root() {
        Resource underTest = fsroot.getChild("folder2/content");
        assertNotNull(underTest);
        assertEquals("app:Page", underTest.getValueMap().get("jcr:primaryType", String.class));
        assertEquals("app:Page", underTest.getResourceType());
        assertThat(underTest, ResourceMatchers.hasChildren("jcr:content"));
    }

    @Test
    public void testContent_Level1() {
        Resource underTest = fsroot.getChild("folder2/content/jcr:content");
        assertNotNull(underTest);
        assertEquals("app:PageContent", underTest.getValueMap().get("jcr:primaryType", String.class));
        assertEquals("sample/components/homepage", underTest.getResourceType());
        assertEquals("sample/components/supertype", underTest.getResourceSuperType());
        assertThat(
                underTest,
                ResourceMatchers.hasChildren("par", "header", "newslist", "lead", "image", "carousel", "rightpar"));
    }

    @Test
    public void testContent_Level5() {
        Resource underTest = fsroot.getChild("folder2/content/jcr:content/par/image/file/jcr:content");
        assertNotNull(underTest);
        assertEquals("nt:resource", underTest.getValueMap().get("jcr:primaryType", String.class));
        assertFalse(underTest.listChildren().hasNext());
    }

    @Test
    public void testContent_Datatypes() {
        Resource underTest = fsroot.getChild("folder2/content/toolbar/profiles/jcr:content");
        ValueMap props = underTest.getValueMap();

        assertEquals("Profiles", props.get("jcr:title", String.class));
        assertEquals(true, props.get("booleanProp", false));
        assertEquals((Long) 1234567890123L, props.get("longProp", Long.class));
        assertEquals(1.2345d, props.get("decimalProp", Double.class), 0.00001d);
        assertEquals(new BigDecimal("1.2345"), props.get("decimalProp", BigDecimal.class));

        assertArrayEquals(new String[] {"aa", "bb", "cc"}, props.get("stringPropMulti", String[].class));
        assertArrayEquals(new Long[] {1234567890123L, 55L}, props.get("longPropMulti", Long[].class));
    }

    @Test
    public void testContent_Datatypes_JCR() throws RepositoryException {
        Resource underTest = fsroot.getChild("folder2/content/toolbar/profiles/jcr:content");
        ValueMap props = underTest.getValueMap();
        Node node = underTest.adaptTo(Node.class);

        assertEquals("/fs-test/folder2/content/toolbar/profiles/jcr:content", node.getPath());
        assertEquals(6, node.getDepth());

        assertTrue(node.hasProperty(JcrConstants.JCR_PRIMARYTYPE));
        assertTrue(
                node.getProperty(JcrConstants.JCR_PRIMARYTYPE).getDefinition().isProtected());

        assertTrue(node.hasProperty("jcr:title"));
        assertEquals(PropertyType.STRING, node.getProperty("jcr:title").getType());
        assertFalse(node.getProperty("jcr:title").isMultiple());
        assertEquals("jcr:title", node.getProperty("jcr:title").getDefinition().getName());
        assertFalse(node.getProperty("jcr:title").getDefinition().isProtected());
        assertEquals(
                "/fs-test/folder2/content/toolbar/profiles/jcr:content/jcr:title",
                node.getProperty("jcr:title").getPath());
        assertEquals("Profiles", node.getProperty("jcr:title").getString());
        assertEquals(PropertyType.BOOLEAN, node.getProperty("booleanProp").getType());
        assertEquals(true, node.getProperty("booleanProp").getBoolean());
        assertEquals(PropertyType.LONG, node.getProperty("longProp").getType());
        assertEquals(1234567890123L, node.getProperty("longProp").getLong());
        assertEquals(PropertyType.DECIMAL, node.getProperty("decimalProp").getType());
        assertEquals(1.2345d, node.getProperty("decimalProp").getDouble(), 0.00001d);
        assertEquals(new BigDecimal("1.2345"), node.getProperty("decimalProp").getDecimal());

        assertEquals(PropertyType.STRING, node.getProperty("stringPropMulti").getType());
        assertTrue(node.getProperty("stringPropMulti").isMultiple());
        Value[] stringPropMultiValues = node.getProperty("stringPropMulti").getValues();
        assertEquals(3, stringPropMultiValues.length);
        assertEquals("aa", stringPropMultiValues[0].getString());
        assertEquals("bb", stringPropMultiValues[1].getString());
        assertEquals("cc", stringPropMultiValues[2].getString());

        assertEquals(PropertyType.LONG, node.getProperty("longPropMulti").getType());
        assertTrue(node.getProperty("longPropMulti").isMultiple());
        Value[] longPropMultiValues = node.getProperty("longPropMulti").getValues();
        assertEquals(2, longPropMultiValues.length);
        assertEquals(1234567890123L, longPropMultiValues[0].getLong());
        assertEquals(55L, longPropMultiValues[1].getLong());

        // assert property iterator
        Set<String> propertyNames = new HashSet<>();
        PropertyIterator propertyIterator = node.getProperties();
        while (propertyIterator.hasNext()) {
            propertyNames.add(propertyIterator.nextProperty().getName());
        }
        assertTrue(props.keySet().containsAll(propertyNames));

        // assert node iterator
        Set<String> nodeNames = new HashSet<>();
        NodeIterator nodeIterator = node.getNodes();
        while (nodeIterator.hasNext()) {
            nodeNames.add(nodeIterator.nextNode().getName());
        }
        assertEquals(ImmutableSet.of("par", "rightpar"), nodeNames);

        // node hierarchy
        assertTrue(node.hasNode("rightpar"));
        Node rightpar = node.getNode("rightpar");
        assertEquals(7, rightpar.getDepth());
        Node parent = rightpar.getParent();
        assertTrue(node.isSame(parent));
        Node ancestor = (Node) rightpar.getAncestor(5);
        assertEquals(underTest.getParent().getPath(), ancestor.getPath());
        Node root = (Node) rightpar.getAncestor(0);
        assertEquals("/", root.getPath());

        // node types
        assertTrue(node.isNodeType("app:PageContent"));
        assertEquals("app:PageContent", node.getPrimaryNodeType().getName());
        assertFalse(node.getPrimaryNodeType().isMixin());
        NodeType[] mixinTypes = node.getMixinNodeTypes();
        assertEquals(2, mixinTypes.length);
        assertEquals("type1", mixinTypes[0].getName());
        assertEquals("type2", mixinTypes[1].getName());
        assertTrue(mixinTypes[0].isMixin());
        assertTrue(mixinTypes[1].isMixin());
    }

    @Test
    public void testFallbackNodeType() throws RepositoryException {
        Resource underTest = fsroot.getChild("folder2/content/jcr:content/par/title_2");
        assertEquals(
                ParserOptions.DEFAULT_PRIMARY_TYPE,
                underTest.adaptTo(Node.class).getPrimaryNodeType().getName());
    }

    @Test
    public void testContent_InvalidPath() {
        Resource underTest = fsroot.getChild("folder2/content/jcr:content/xyz");
        assertNull(underTest);
    }

    @Test
    public void testJcrMixedContent() throws RepositoryException {
        // prepare mixed JCR content
        Node node = root.adaptTo(Node.class);
        Node fstest = node.addNode("fs-test", "nt:folder");
        fstest.addNode("folder99", "nt:folder");

        assertNull(fsroot.getChild("folder99"));
    }

    @Test
    public void testFolder2ChildNodes() throws RepositoryException {
        Resource folder2 = fsroot.getChild("folder2");
        List<Resource> children = Lists.newArrayList(folder2.listChildren());
        Collections.sort(children, new ResourcePathComparator());

        assertEquals(2, children.size());
        Resource child1 = children.get(0);
        assertEquals("content", child1.getName());
        assertEquals("app:Page", child1.getResourceType());
        assertEquals("app:Page", child1.getValueMap().get("jcr:primaryType", String.class));

        Resource child2 = children.get(1);
        assertEquals("folder21", child2.getName());
        assertEquals("sling:OrderedFolder", child2.getValueMap().get("jcr:primaryType", String.class));
    }

    @Test
    public void testFile21aNodeDescriptor() throws RepositoryException {
        Resource file21a = fsroot.getChild("folder2/folder21/file21a.txt");
        assertEquals("nt:file", file21a.getResourceType());
        assertEquals("/my/super/type", file21a.getResourceSuperType());

        ValueMap props = file21a.getValueMap();
        assertEquals("nt:file", props.get("jcr:primaryType", String.class));
        assertEquals("/my/super/type", props.get("sling:resourceSuperType", String.class));
        assertEquals("en", props.get("jcr:language", String.class));
        assertArrayEquals(new String[] {"mix:language"}, props.get("jcr:mixinTypes", String[].class));

        assertNull(fsroot.getChild("folder2/folder21/file21a.txt.xml"));

        Node node = file21a.adaptTo(Node.class);
        assertNotNull(node);
        assertEquals(
                "/my/super/type", node.getProperty("sling:resourceSuperType").getString());
        assertEquals("en", node.getProperty("jcr:language").getString());
    }

    @Test
    public void testContent2() throws RepositoryException {
        Resource content2 = fsroot.getChild("folder2/content/sling:content2");
        assertNotNull(content2);
        assertEquals("app:Page", content2.getResourceType());

        Resource content = fsroot.getChild("folder2/content");
        assertThat(content, ResourceMatchers.hasChildren("sling:content2"));
    }

    @Test
    public void testDeepValueMapAccess() throws Exception {
        Resource underTest = fsroot.getChild("folder2/content/toolbar");
        ValueMap properties = underTest.getValueMap();
        String toolbarTitle = properties.get("jcr:content/jcr:title", String.class);
        assertEquals("Toolbar", toolbarTitle);

        String profilesTitle = properties.get("profiles/jcr:content/jcr:title", String.class);
        assertEquals("Profiles", profilesTitle);
    }

    @Test
    @Ignore
    public void testFileHasJcrContentAndData() {
        Resource underTest = fsroot.getChild("folder2/content/file2content.txt");
        assertNotNull("failed adapting file2content.txt to InputStream", underTest.adaptTo(InputStream.class));
        // assertThat(underTest, ResourceMatchers.hasChildren("jcr:content"));
        Resource content = underTest.getChild("jcr:content");
        ValueMap props = content.getValueMap();
        assertNotNull("jcr:data is missing", props.get("jcr:data", InputStream.class));
    }

    @Test
    @Ignore
    public void testFileWithOverwrittenMimeType() {
        Resource underTest = fsroot.getChild("folder2/content/fileWithOverwrittenMimeType.scss");
        assertNotNull(
                "failed adapting fileWithOverwrittenMimeType.scss to InputStream",
                underTest.adaptTo(InputStream.class));
        Resource content = underTest.getChild("jcr:content");
        ValueMap props = content.getValueMap();
        assertEquals("nt:unstructured", props.get("jcr:primaryType", "[missing]"));
        assertEquals("text/css", props.get("jcr:mimeType", "[missing]"));
        assertNotNull("jcr:data is missing", props.get("jcr:data", InputStream.class));
    }
}
