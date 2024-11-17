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

import static org.apache.jackrabbit.vault.util.Constants.DOT_CONTENT_XML;
import static org.apache.sling.fsprovider.internal.parser.ContentFileTypes.XML_SUFFIX;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.collections4.Predicate;
import org.apache.commons.collections4.Transformer;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.fsprovider.internal.ContentFileExtensions;
import org.apache.sling.fsprovider.internal.FileStatCache;
import org.apache.sling.fsprovider.internal.parser.ContentElement;
import org.apache.sling.fsprovider.internal.parser.ContentFileCache;
import org.apache.sling.fsprovider.internal.parser.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FileVaultResourceMapper extends FileResourceMapper {

    private static final String DOT_CONTENT_XML_SUFFIX = "/" + DOT_CONTENT_XML;
    private static final String DOT_DIR = ".dir";
    private static final String DOT_DIR_SUFFIX = "/" + DOT_DIR;

    private final File filterXmlFile;
    private final WorkspaceFilter workspaceFilter;

    private static final Logger log = LoggerFactory.getLogger(FileVaultResourceMapper.class);

    public FileVaultResourceMapper(String providerRoot, File providerFile,
                              ContentFileExtensions contentFileExtensions, ContentFileCache contentFileCache,
                              FileStatCache fileStatCache,
                              final boolean overlayParentResourceProvider,
                              final File filterXmlFile) {
        super(providerRoot, providerFile, contentFileExtensions, contentFileCache, fileStatCache, overlayParentResourceProvider, false);
        this.filterXmlFile = filterXmlFile;
        this.workspaceFilter = getWorkspaceFilter();
    }

    @Override
    public String transformPath(final String path) {
        return PlatformNameFormat.getRepositoryPath(path);
    }

    @Override
    protected Map.Entry<Resource, Boolean> resolveResource(final ResourceResolver resolver, final String path) {
        final boolean askParentResourceProvider;
        Resource rsrc = null;
        // filevault: check if path matches, if not fallback to parent resource provider
        if (this.pathMatches(path)) {
            askParentResourceProvider = false;
            rsrc = this.findResource(resolver, path);
        } else {
            askParentResourceProvider = true;
        }
        return new SimpleEntry<>(rsrc, askParentResourceProvider);
    }

    @Override
    protected boolean resolveChildren(final ResourceResolver resolver, final Resource parent, final List<Iterator<? extends Resource>> allChildren) {
        // filevault: always ask provider, it checks itself if children matches the filters
        final boolean askParentResourceProvider = true;
        final Iterator<Resource> children = this.findChildren(resolver, parent);
        if (children != null) {
            allChildren.add(children);
        }
        return askParentResourceProvider;
    }

    @Override
    protected void addChildren(final List<Iterator<? extends Resource>> allChildren, final Iterator<Resource> children) {
        // filevault: include all children from parent resource provider that do not match the filters
        allChildren.add(IteratorUtils.filteredIterator(children, new Predicate<Resource>() {
            @Override
            public boolean evaluate(final Resource child) {
                return !pathMatches(child.getPath());
            }
        }));
    }

    private Resource findResource(final ResourceResolver resolver, final String resourcePath) {

        // direct file
        File file = resolveFile(resourcePath);
        if (file != null && fileStatCache.isFile(file)) {
            return new FileResource(resolver, resourcePath, file, fileStatCache);
        }

        // content file
        ContentFile contentFile = getContentFile(resourcePath, null);
        if (contentFile != null) {
            return new ContentFileResource(resolver, contentFile);
        }

        // fallback to directory resource if folder was found but nothing else
        if (file != null && fileStatCache.isDirectory(file)) {
            return new FileResource(resolver, resourcePath, file, fileStatCache);
        }

        return null;
    }

    private Iterator<Resource> findChildren(final ResourceResolver resolver, final Resource parent) {
        String parentPath = parent.getPath();

        Set<String> childPaths = new LinkedHashSet<>();

        // get children from content resource of parent
        ContentFile parentContentFile = getContentFile(parentPath, null);
        if (parentContentFile != null) {
            Iterator<Map.Entry<String,ContentElement>> childMaps = parentContentFile.getChildren();
            while (childMaps.hasNext()) {
                Map.Entry<String,ContentElement> entry = childMaps.next();
                String childPath = parentPath + "/" + entry.getKey();
                if (pathMatches(childPath)) {
                    childPaths.add(childPath);
                }
            }
        }

        // additional check for children in file system
        File parentFile = resolveFile(parentPath);
        if (parentFile != null && fileStatCache.isDirectory(parentFile)) {
            File[] files = parentFile.listFiles();
            Arrays.sort(files, FileNameComparator.INSTANCE);
            for (File childFile : files) {
                String childPath = parentPath + "/" + PlatformNameFormat.getRepositoryName(childFile.getName());
                File file = resolveFile(childPath);
                if (file != null && pathMatches(childPath) && !childPaths.contains(childPath)) {
                    childPaths.add(childPath);
                    continue;
                }

                // strip xml extension unless it's .content.xml - the xml extension is re-added inside getContentFile
                if (!childPath.endsWith('/' + DOT_CONTENT_XML)) {
                    childPath = StringUtils.removeEnd(childPath, XML_SUFFIX);
                }
                ContentFile contentFile = getContentFile(childPath, null);
                if (contentFile != null && pathMatches(childPath) && !childPaths.contains(childPath)) {
                    childPaths.add(childPath);
                }
            }
        }

        if (childPaths.isEmpty()) {
            return null;
        } else {
            return IteratorUtils.transformedIterator(childPaths.iterator(), new Transformer<String, Resource>() {
                @Override
                public Resource transform(final String path) {
                    return findResource(resolver, path);
                }
            });
        }
    }

    /**
     * @return Workspace filter or null if none found.
     */
    private WorkspaceFilter getWorkspaceFilter() {
        if (filterXmlFile != null) {
            if (filterXmlFile.exists()) {
                try {
                    DefaultWorkspaceFilter workspaceFilter = new DefaultWorkspaceFilter();
                    workspaceFilter.load(filterXmlFile);
                    return workspaceFilter;
                } catch (IOException | ConfigurationException ex) {
                    log.error("Unable to parse workspace filter: " + filterXmlFile.getPath(), ex);
                }
            }
            else {
                log.debug("Workspace filter not found: {}", filterXmlFile.getPath());
            }
        }
        return null;
    }

    /**
     * Checks if the given path matches the workspace filter.
     * @param path Path
     * @return true if path matches
     */
    private boolean pathMatches(String path) {
        // ignore .dir folder
        if (StringUtils.endsWith(path, DOT_DIR_SUFFIX) || StringUtils.endsWith(path, DOT_CONTENT_XML_SUFFIX)) {
            return false;
        }
        if (workspaceFilter == null) {
            return true;
        }
        else {
            return workspaceFilter.contains(path);
        }
    }

    private File resolveFile(String path) {
        if (StringUtils.endsWith(path, DOT_CONTENT_XML_SUFFIX)) {
            return null;
        }
        File file = new File(providerFile, "." + PlatformNameFormat.getPlatformPath(path));
        if (fileStatCache.exists(file)) {
            if (StringUtils.endsWith(path, XML_SUFFIX) && !hasDotDirFile(file)) {
                return null;
            }
            return file;
        }
        return null;
    }

    private ContentFile getContentFile(String path, String subPath) {
        File file = new File(providerFile, "." + PlatformNameFormat.getPlatformPath(path) + DOT_CONTENT_XML_SUFFIX);
        if (fileStatCache.exists(file)) {
            ContentFile contentFile = new ContentFile(file, path, subPath, contentFileCache, ContentType.JCR_XML);
            if (contentFile.hasContent()) {
                return contentFile;
            }
        }

        file = new File(providerFile, "." + PlatformNameFormat.getPlatformPath(path) + XML_SUFFIX);
        if (fileStatCache.exists(file) && !hasDotDirFile(file)) {
            ContentFile contentFile = new ContentFile(file, path, subPath, contentFileCache, ContentType.JCR_XML);
            if (contentFile.hasContent()) {
                return contentFile;
            }
        }

        // try to find in parent path which contains content fragment
        String parentPath = ResourceUtil.getParent(path);
        if (parentPath == null) {
            return null;
        }
        String nextSubPath = path.substring(parentPath.length() + 1)
                + (subPath != null ? "/" + subPath : "");
        return getContentFile(parentPath, nextSubPath);
    }

    private boolean hasDotDirFile(File file) {
        File dotDir = new File(file.getPath() + DOT_DIR);
        if (fileStatCache.isDirectory(dotDir)) {
            return true;
        }
        return false;
    }
}
