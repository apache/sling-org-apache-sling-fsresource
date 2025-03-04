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
import java.util.Collection;
import java.util.List;

import static org.apache.sling.fsprovider.internal.parser.ContentFileTypes.JCR_XML_SUFFIX;
import static org.apache.sling.fsprovider.internal.parser.ContentFileTypes.XML_SUFFIX;

/**
 * Matches file names for content file extensions.
 */
public final class ContentFileExtensions {

    private final List<String> contentFileSuffixes;

    public ContentFileExtensions(final List<String> contentFileSuffixes) {
        this.contentFileSuffixes = contentFileSuffixes;
    }

    /**
     * Get suffix from file name.
     * @param file File
     * @return Content file name suffix or null if not a context file.
     */
    public String getSuffix(final File file) {
        String fileName = "/" + file.getName();
        for (String suffix : contentFileSuffixes) {
            if (XML_SUFFIX.equals(suffix)) {
                if (fileName.endsWith(XML_SUFFIX) && !fileName.endsWith(JCR_XML_SUFFIX)) {
                    return suffix;
                }
            } else {
                if (fileName.endsWith(suffix)) {
                    return suffix;
                }
            }
        }
        return null;
    }

    /**
     * Checks suffix from file name.
     * @param file File
     * @return true if content file
     */
    public boolean matchesSuffix(final File file) {
        return getSuffix(file) != null;
    }

    /**
     * @return Content file suffixes.
     */
    public Collection<String> getSuffixes() {
        return contentFileSuffixes;
    }
}
