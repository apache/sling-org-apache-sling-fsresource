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
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.collections4.Predicate;
import org.apache.commons.collections4.Transformer;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.fsprovider.internal.ContentFileExtensions;
import org.apache.sling.fsprovider.internal.FileStatCache;
import org.apache.sling.fsprovider.internal.FsResourceProvider;
import org.apache.sling.fsprovider.internal.parser.ContentFileCache;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.apache.sling.spi.resource.provider.ResourceContext;
import org.apache.sling.spi.resource.provider.ResourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FileResourceMapper {

    // The location in the resource tree where the resources are mapped
    private final String providerRoot;

    // providerRoot + "/" to be used for prefix matching of paths
    private final String providerRootPrefix;

    // The "root" file or folder in the file system
    protected final File providerFile;

    private final ContentFileExtensions contentFileExtensions;
    protected final ContentFileCache contentFileCache;
    protected final FileStatCache fileStatCache;

    private final ContentFileResourceMapper contentFileMapper;

    // if true resources from file system are only "overlayed" to JCR resources, serving JCR as fallback within the same
    // path
    protected final boolean overlayParentResourceProvider;

    public FileResourceMapper(
            String providerRoot,
            File providerFile,
            ContentFileExtensions contentFileExtensions,
            ContentFileCache contentFileCache,
            FileStatCache fileStatCache,
            final boolean overlayParentResourceProvider,
            final boolean createContentFileMapper) {
        this.providerRoot = providerRoot;
        this.providerRootPrefix = providerRoot.concat("/");
        this.providerFile = providerFile;
        this.contentFileExtensions = contentFileExtensions;
        this.contentFileCache = contentFileCache;
        this.fileStatCache = fileStatCache;
        this.overlayParentResourceProvider = overlayParentResourceProvider;
        if (contentFileExtensions != null && createContentFileMapper) {
            this.contentFileMapper = new ContentFileResourceMapper(
                    this.providerRoot,
                    this.providerFile,
                    contentFileExtensions,
                    this.contentFileCache,
                    this.fileStatCache);
        } else {
            this.contentFileMapper = null;
        }
    }

    protected Map.Entry<Resource, Boolean> resolveResource(final ResourceResolver resolver, final String path) {
        // Sling-Initial-Content: mount folder/files an content files
        final boolean askParentResourceProvider = this.overlayParentResourceProvider;
        Resource rsrc = null;
        final File file = getFile(path);
        if (file != null) {
            rsrc = new FileResource(resolver, path, file, contentFileExtensions, contentFileCache, fileStatCache);
        } else if (contentFileMapper != null) {
            rsrc = contentFileMapper.getResource(resolver, path);
        }
        return new SimpleEntry<>(rsrc, askParentResourceProvider);
    }

    protected boolean resolveChildren(
            final ResourceResolver resolver,
            final Resource parent,
            final List<Iterator<? extends Resource>> allChildren) {
        // Sling-Initial-Content: get all matching folders/files and content files
        final boolean askParentResourceProvider = this.overlayParentResourceProvider;
        Iterator<Resource> children =
                contentFileMapper == null ? null : contentFileMapper.getChildren(resolver, parent);
        if (children != null) {
            allChildren.add(children);
        }
        children = this.getChildren(resolver, parent);
        if (children != null) {
            allChildren.add(children);
        }
        return askParentResourceProvider;
    }

    /**
     * Returns a resource wrapping a file system file or folder for the given
     * path. If the <code>path</code> is equal to the configured resource tree
     * location of this provider, the configured file system file or folder is
     * used for the resource. Otherwise the configured resource tree location
     * prefix is removed from the path and the remaining relative path is used
     * to access the file or folder. If no such file or folder exists, this
     * method returns <code>null</code>.
     */
    @SuppressWarnings("unchecked")
    public Resource getResource(
            final @NotNull ResolveContext<Object> ctx,
            final @NotNull String path,
            final @NotNull ResourceContext resourceContext,
            final @Nullable Resource parent) {

        final ResourceResolver resolver = ctx.getResourceResolver();

        final Map.Entry<Resource, Boolean> resolved = resolveResource(resolver, path);
        final boolean askParentResourceProvider = resolved.getValue();
        Resource rsrc = resolved.getKey();

        if (askParentResourceProvider) {
            // make sure directory resources from parent resource provider have higher precedence than from this
            // provider
            // this allows properties like sling:resourceSuperType to take effect
            if (rsrc == null
                    || rsrc.getResourceMetadata().containsKey(FsResourceProvider.RESOURCE_METADATA_FILE_DIRECTORY)) {
                // get resource from shadowed provider
                @SuppressWarnings("rawtypes")
                final ResourceProvider rp = ctx.getParentResourceProvider();
                @SuppressWarnings("rawtypes")
                final ResolveContext resolveContext = ctx.getParentResolveContext();
                if (rp != null && resolveContext != null) {
                    Resource resourceFromParentResourceProvider =
                            rp.getResource(resolveContext, path, resourceContext, parent);
                    if (resourceFromParentResourceProvider != null) {
                        rsrc = resourceFromParentResourceProvider;
                    }
                }
            }
        }

        return rsrc;
    }

    protected void addChildren(
            final List<Iterator<? extends Resource>> allChildren, final Iterator<Resource> children) {
        allChildren.add(children);
    }

    /**
     * Returns an iterator of resources.
     */
    @SuppressWarnings("unchecked")
    public Iterator<Resource> listChildren(final @NotNull ResolveContext<Object> ctx, final @NotNull Resource parent) {
        final ResourceResolver resolver = ctx.getResourceResolver();

        final List<Iterator<? extends Resource>> allChildren = new ArrayList<>();
        final boolean askParentResourceProvider = this.resolveChildren(resolver, parent, allChildren);

        // get children from from shadowed provider
        if (askParentResourceProvider) {
            @SuppressWarnings("rawtypes")
            final ResourceProvider parentResourceProvider = ctx.getParentResourceProvider();
            @SuppressWarnings("rawtypes")
            final ResolveContext resolveContext = ctx.getParentResolveContext();
            if (parentResourceProvider != null && resolveContext != null) {
                Iterator<Resource> children = parentResourceProvider.listChildren(resolveContext, parent);
                if (children != null) {
                    this.addChildren(allChildren, children);
                }
            }
        }

        if (allChildren.isEmpty()) {
            return null;
        } else if (allChildren.size() == 1) {
            return (Iterator<Resource>) allChildren.get(0);
        } else {
            // merge all children from the different iterators, but filter out potential duplicates with same resource
            // name
            return IteratorUtils.filteredIterator(
                    IteratorUtils.chainedIterator(allChildren), new Predicate<Resource>() {
                        private Set<String> names = new HashSet<>();

                        @Override
                        public boolean evaluate(final Resource resource) {
                            return names.add(resource.getName());
                        }
                    });
        }
    }

    private Iterator<Resource> getChildren(final ResourceResolver resolver, final Resource parent) {
        final String parentPath = parent.getPath();
        File parentFile = parent.adaptTo(File.class);

        // not a FsResource, try to create one from the resource
        if (parentFile == null) {
            // if the parent path is at or below the provider root, get
            // the respective file
            parentFile = getFile(parentPath);

            // if the parent path is actually the parent of the provider
            // root, return a single element iterator just containing the
            // provider file, unless the provider file is a directory and
            // a repository item with the same path actually exists
            if (parentFile == null) {

                if (!StringUtils.startsWith(parentPath, providerRoot)) {
                    String parentPathPrefix = parentPath.concat("/");
                    if (providerRoot.startsWith(parentPathPrefix)) {
                        String relPath = providerRoot.substring(parentPathPrefix.length());
                        if (relPath.indexOf('/') < 0) {
                            Resource res = new FileResource(
                                    resolver,
                                    providerRoot,
                                    providerFile,
                                    contentFileExtensions,
                                    contentFileCache,
                                    fileStatCache);
                            return IteratorUtils.singletonIterator(res);
                        }
                    }
                }

                // no children here
                return null;
            }
        }

        File[] files = parentFile.listFiles();
        if (files == null) {
            return null;
        }

        Arrays.sort(files, FileNameComparator.INSTANCE);
        Iterator<File> children =
                IteratorUtils.filteredIterator(IteratorUtils.arrayIterator(files), new Predicate<File>() {
                    @Override
                    public boolean evaluate(final File file) {
                        return contentFileExtensions == null || !contentFileExtensions.matchesSuffix(file);
                    }
                });
        if (!children.hasNext()) {
            return null;
        }
        return IteratorUtils.transformedIterator(children, new Transformer<File, Resource>() {
            @Override
            public Resource transform(final File file) {
                String path = parentPath + "/" + Escape.fileToResourceName(file.getName());
                return new FileResource(resolver, path, file, contentFileExtensions, contentFileCache, fileStatCache);
            }
        });
    }

    /**
     * Returns a file corresponding to the given absolute resource tree path. If
     * the path equals the configured provider root, the provider root file is
     * returned. If the path starts with the configured provider root, a file is
     * returned relative to the provider root file whose relative path is the
     * remains of the resource tree path without the provider root path.
     * Otherwise <code>null</code> is returned.
     */
    private File getFile(final String path) {
        if (path.equals(providerRoot)) {
            return providerFile;
        }
        if (path.startsWith(providerRootPrefix)) {
            String relPath = Escape.resourceToFileName(path.substring(providerRootPrefix.length()));
            File file = new File(providerFile, relPath);
            if ((contentFileExtensions == null || !contentFileExtensions.matchesSuffix(file))
                    && fileStatCache.exists(file)) {
                return file;
            }
        }
        return null;
    }

    /**
     * Transforms the path based on the mode. By default this method does nothing
     * @param path
     * @return The transformed path
     */
    public String transformPath(final String path) {
        return path;
    }
}
