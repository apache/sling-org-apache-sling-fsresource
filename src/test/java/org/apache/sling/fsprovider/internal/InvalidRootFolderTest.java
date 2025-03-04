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

import java.io.File;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.fsprovider.internal.TestUtils.RegisterFsResourcePlugin;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.junit.SlingContextBuilder;
import org.apache.sling.testing.mock.sling.junit.SlingContextCallback;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/**
 * Test with invalid fs folder.
 */
public class InvalidRootFolderTest {

    private Resource fsroot;

    @Rule
    public SlingContext context = new SlingContextBuilder(ResourceResolverType.JCR_MOCK)
            .plugin(new RegisterFsResourcePlugin("provider.file", "target/temp/invalid-folder"))
            .afterTearDown(new SlingContextCallback() {
                @Override
                public void execute(@NotNull SlingContext context) throws Exception {
                    File file = new File("target/temp/invalid-folder");
                    file.delete();
                }
            })
            .build();

    @Before
    public void setUp() {
        fsroot = context.resourceResolver().getResource("/fs-test");
    }

    @Test
    public void testFolders() {
        assertNull(fsroot.getChild("folder1"));
    }

    @Test
    public void testFiles() {
        assertNull(fsroot.getChild("folder1/file1a.txt"));
    }

    @Test
    public void testListChildren() {
        assertFalse(fsroot.listChildren().hasNext());
    }
}
