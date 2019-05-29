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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.collections4.Transformer;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.fsprovider.internal.ContentFileExtensions;
import org.apache.sling.fsprovider.internal.FileStatCache;
import org.apache.sling.fsprovider.internal.FsResourceMapper;
import org.apache.sling.fsprovider.internal.parser.ContentFileCache;

public final class ContentFileResourceMapper implements FsResourceMapper {
    
    // providerRoot + "/" to be used for prefix matching of paths
    private final String providerRootPrefix;

    // The "root" file or folder in the file system
    private final File providerFile;
    
    private final ContentFileExtensions contentFileExtensions;
    private final ContentFileCache contentFileCache;
    private FileStatCache fileStatCache;

    public ContentFileResourceMapper(String providerRoot, File providerFile,
                                     ContentFileExtensions contentFileExtensions, ContentFileCache contentFileCache,
                                     FileStatCache fileStatCache) {
        this.providerRootPrefix = providerRoot.concat("/");
        this.providerFile = providerFile;
        this.contentFileExtensions = contentFileExtensions;
        this.contentFileCache = contentFileCache;
        this.fileStatCache = fileStatCache;
    }
    
    @Override
    public Resource getResource(final ResourceResolver resolver, final String resourcePath) {
        if (contentFileExtensions.isEmpty()) {
            return null;
        }
        ContentFile contentFile = getFile(resourcePath, null);
        if (contentFile != null && contentFile.hasContent()) {
            return new ContentFileResource(resolver, contentFile);
        }
        else {
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public Iterator<Resource> getChildren(final ResourceResolver resolver, final Resource parent) {
        if (contentFileExtensions.isEmpty()) {
            return null;
        }

        final String parentPath = parent.getPath();
        final ContentFile parentContentFile = getFile(parentPath, null);;


        final List<Iterator<? extends Resource>> childIterators = new ArrayList<>(2);

        // add children from parsed content
        if (parentContentFile != null && parentContentFile.hasContent()) {
            childIterators.add(IteratorUtils.transformedIterator(parentContentFile.getContent().getChildren().keySet().iterator(), new Transformer() {
                @Override
                public Object transform(final Object input) {
                    String name = (String)input;
                    return new ContentFileResource(resolver, parentContentFile.navigateToRelative(name));
                }
            }));
        }

        // add children from filesystem folder
        File parentDir = new File(providerFile, StringUtils.removeStart(parentPath, providerRootPrefix));
        if (fileStatCache.isDirectory(parentDir)) {
            File[] files = parentDir.listFiles();
            if (files != null) {
                Arrays.sort(files, FileNameComparator.INSTANCE);
                childIterators.add(IteratorUtils.transformedIterator(IteratorUtils.arrayIterator(files), new Transformer() {
                    @Override
                    public Object transform(final Object input) {
                        File file = (File)input;
                        String path = parentPath + "/" + Escape.fileToResourceName(file.getName());
                        String filenameSuffix = contentFileExtensions.getSuffix(file);
                        if (filenameSuffix != null) {
                            path = StringUtils.substringBeforeLast(path, filenameSuffix);
                            ContentFile contentFile = new ContentFile(file, path, null, contentFileCache);
                            return new ContentFileResource(resolver, contentFile);
                        } else {
                            return new FileResource(resolver, path, file, contentFileExtensions, contentFileCache, fileStatCache);
                        }
                    }
                }));
            }
        }

        Iterator<Resource> children = IteratorUtils.chainedIterator(childIterators);
        if (!children.hasNext()) {
            return null;
        }
        return children;
    }
    
    private ContentFile getFile(String path, String subPath) {
        if (!StringUtils.startsWith(path, providerRootPrefix)) {
            return null;
        }
        String relPath = Escape.resourceToFileName(path.substring(providerRootPrefix.length()));
        for (String filenameSuffix : contentFileExtensions.getSuffixes()) {
            File file = new File(providerFile, relPath + filenameSuffix);
            if (fileStatCache.exists(file)) {
                return new ContentFile(file, path, subPath, contentFileCache);
            }
        }
        // try to find in parent path which contains content fragment
        String parentPath = ResourceUtil.getParent(path);
        if (parentPath == null) {
            return null;
        }
        String nextSubPath = path.substring(parentPath.length() + 1)
                + (subPath != null ? "/" + subPath : "");
        return getFile(parentPath, nextSubPath);
    }
    
}
