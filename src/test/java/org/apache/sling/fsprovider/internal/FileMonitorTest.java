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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChange.ChangeType;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.junit.SlingContextBuilder;
import org.apache.sling.testing.mock.sling.junit.SlingContextCallback;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test events when changing filesystem content.
 */
public class FileMonitorTest {

    private final File tempDir;
    private final ResourceListener resourceListener = new ResourceListener();
    
    public FileMonitorTest() throws Exception {
        tempDir = Files.createTempDirectory(getClass().getName()).toFile();
    }

    @Rule
    public SlingContext context = new SlingContextBuilder(ResourceResolverType.JCR_MOCK)
        .beforeSetUp(new SlingContextCallback() {
            @Override
            public void execute(SlingContext context) throws Exception {
                // copy test content to temp. directory
                tempDir.mkdirs();
                File sourceDir = new File("src/test/resources/fs-test");
                FileUtils.copyDirectory(sourceDir, tempDir);
                
                // mount temp. directory
                context.registerInjectActivateService(new FsResourceProvider(),
                        "provider.file", tempDir.getPath(),
                        "provider.root", "/fs-test",
                        "provider.checkinterval", 120,
                        "provider.json.content", true);
                
                // register resource change listener
                context.registerService(ResourceChangeListener.class, resourceListener,
                        ResourceChangeListener.PATHS, new String[] { "/fs-test" });
            }
        })
        .afterTearDown(new SlingContextCallback() {
            @Override
            public void execute(SlingContext context) throws Exception {
                // remove temp directory
                tempDir.delete();
            }
        })
        .build();

    @Test
    public void testUpdateFile() throws Exception {
        List<ResourceChange> changes = resourceListener.getChanges();
        assertTrue(changes.isEmpty());
        
        File file1a = new File(tempDir, "folder1/file1a.txt");
        FileUtils.touch(file1a);
        
        Thread.sleep(250);

        assertEquals(1, changes.size());
        assertChange(changes, "/fs-test/folder1/file1a.txt", ChangeType.CHANGED);
    }
    
    @Test
    public void testAddFile() throws Exception {
        List<ResourceChange> changes = resourceListener.getChanges();
        assertTrue(changes.isEmpty());
        
        File file1c = new File(tempDir, "folder1/file1c.txt");
        FileUtils.write(file1c, "newcontent");
        
        Thread.sleep(250);

        assertEquals(2, changes.size());
        assertChange(changes, "/fs-test/folder1", ChangeType.CHANGED);
        assertChange(changes, "/fs-test/folder1/file1c.txt", ChangeType.ADDED);
    }
    
    @Test
    public void testRemoveFile() throws Exception {
        List<ResourceChange> changes = resourceListener.getChanges();
        assertTrue(changes.isEmpty());
        
        File file1a = new File(tempDir, "folder1/file1a.txt");
        file1a.delete();
        
        Thread.sleep(250);

        assertEquals(2, changes.size());
        assertChange(changes, "/fs-test/folder1", ChangeType.CHANGED);
        assertChange(changes, "/fs-test/folder1/file1a.txt", ChangeType.REMOVED);
    }
    
    @Test
    public void testAddFolder() throws Exception {
        List<ResourceChange> changes = resourceListener.getChanges();
        assertTrue(changes.isEmpty());
        
        File folder99 = new File(tempDir, "folder99");
        folder99.mkdir();
        
        Thread.sleep(250);

        assertEquals(2, changes.size());
        assertChange(changes, "/fs-test", ChangeType.CHANGED);
        assertChange(changes, "/fs-test/folder99", ChangeType.ADDED);
    }
    
    @Test
    public void testRemoveFolder() throws Exception {
        List<ResourceChange> changes = resourceListener.getChanges();
        assertTrue(changes.isEmpty());
        
        File folder1 = new File(tempDir, "folder1");
        FileUtils.deleteDirectory(folder1);
        
        Thread.sleep(250);

        assertEquals(2, changes.size());
        assertChange(changes, "/fs-test", ChangeType.CHANGED);
        assertChange(changes, "/fs-test/folder1", ChangeType.REMOVED);
    }

    @Test
    public void testUpdateJsonContent() throws Exception {
        List<ResourceChange> changes = resourceListener.getChanges();
        assertTrue(changes.isEmpty());
        
        File file1a = new File(tempDir, "folder2/content.json");
        FileUtils.touch(file1a);
        
        Thread.sleep(250);

        assertTrue(changes.size() > 1);
        assertChange(changes, "/fs-test/folder2/content", ChangeType.REMOVED);
        assertChange(changes, "/fs-test/folder2/content", ChangeType.ADDED);
        assertChange(changes, "/fs-test/folder2/content/jcr:content", ChangeType.ADDED);
    }
    
    @Test
    public void testAddJsonContent() throws Exception {
        List<ResourceChange> changes = resourceListener.getChanges();
        assertTrue(changes.isEmpty());
        
        File file1c = new File(tempDir, "folder1/file1c.json");
        FileUtils.write(file1c, "{\"prop1\":\"value1\",\"child1\":{\"prop2\":\"value1\"}}");
        
        Thread.sleep(250);

        assertEquals(3, changes.size());
        assertChange(changes, "/fs-test/folder1", ChangeType.CHANGED);
        assertChange(changes, "/fs-test/folder1/file1c", ChangeType.ADDED);
        assertChange(changes, "/fs-test/folder1/file1c/child1", ChangeType.ADDED);
    }
    
    @Test
    public void testRemoveJsonContent() throws Exception {
        List<ResourceChange> changes = resourceListener.getChanges();
        assertTrue(changes.isEmpty());
        
        File file1a = new File(tempDir, "folder2/content.json");
        file1a.delete();
        
        Thread.sleep(250);

        assertEquals(2, changes.size());
        assertChange(changes, "/fs-test/folder2", ChangeType.CHANGED);
        assertChange(changes, "/fs-test/folder2/content", ChangeType.REMOVED);
    }
    
    
    private void assertChange(List<ResourceChange> changes, String path, ChangeType changeType) {
        boolean found = false;
        for (ResourceChange change : changes) {
            if (StringUtils.equals(change.getPath(), path) && change.getType() == changeType) {
                found = true;
                break;
            }
        }
        assertTrue("Change with path=" + path + ", changeType=" + changeType, found);
    }
    
    static class ResourceListener implements ResourceChangeListener {
        private final List<ResourceChange> allChanges = new ArrayList<>();
        @Override
        public void onChange(List<ResourceChange> changes) {
            allChanges.addAll(changes);
        }
        public List<ResourceChange> getChanges() {
            return allChanges;
        }
    }

}
