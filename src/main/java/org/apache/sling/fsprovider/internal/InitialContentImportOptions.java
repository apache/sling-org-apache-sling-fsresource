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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.commons.osgi.ManifestHeader;
import org.apache.sling.commons.osgi.ManifestHeader.Entry;

class InitialContentImportOptions {

    /**
     * The overwrite directive specifying if content should be overwritten or
     * just initially added.
     */
    private static final String OVERWRITE_DIRECTIVE = "overwrite";

    /**
     * The ignore content readers directive specifying whether the available ContentReaders
     * should be used during content loading.
     */
    private static final String IGNORE_CONTENT_READERS_DIRECTIVE = "ignoreImportProviders";

    private final boolean overwrite;
    private final Set<String> ignoreImportProviders;

    public InitialContentImportOptions(String optionsString) {
        ManifestHeader header = ManifestHeader.parse("/dummy/path;" + optionsString);
        Entry[] entries = header.getEntries();
        if (entries.length > 0) {
            overwrite = BooleanUtils.toBoolean(entries[0].getDirectiveValue(OVERWRITE_DIRECTIVE));
            String ignoreImportProvidersString =
                    StringUtils.defaultString(entries[0].getDirectiveValue(IGNORE_CONTENT_READERS_DIRECTIVE));
            ignoreImportProviders = new HashSet<>(Arrays.asList(StringUtils.split(ignoreImportProvidersString, ",")));
        } else {
            overwrite = false;
            ignoreImportProviders = Collections.emptySet();
        }
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public Set<String> getIgnoreImportProviders() {
        return ignoreImportProviders;
    }
}
