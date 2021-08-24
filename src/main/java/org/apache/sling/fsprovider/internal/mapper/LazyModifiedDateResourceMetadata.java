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
package org.apache.sling.fsprovider.internal.mapper;

import java.io.File;

import org.apache.sling.api.resource.ResourceMetadata;

class LazyModifiedDateResourceMetadata extends ResourceMetadata {
    private static final long serialVersionUID = 1L;

    private volatile long lastModified = -1;
    private File file;

    public LazyModifiedDateResourceMetadata(final File file) {
        this.file = file;
    }

    @Override
    public long getModificationTime() {
        if (lastModified == -1) {
            // lazy init - fs access is quite slow
            lastModified = file.lastModified();
        }
        return lastModified;
    }
}
