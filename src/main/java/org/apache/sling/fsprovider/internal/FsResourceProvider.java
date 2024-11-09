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

import static org.apache.jackrabbit.vault.util.Constants.DOT_CONTENT_XML;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.fsprovider.internal.mapper.FileResourceMapper;
import org.apache.sling.fsprovider.internal.mapper.FileVaultResourceMapper;
import org.apache.sling.fsprovider.internal.parser.ContentFileCache;
import org.apache.sling.fsprovider.internal.parser.ContentFileTypes;
import org.apache.sling.fsprovider.internal.parser.ContentType;
import org.apache.sling.spi.resource.provider.ObservationReporter;
import org.apache.sling.spi.resource.provider.ProviderContext;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.apache.sling.spi.resource.provider.ResourceContext;
import org.apache.sling.spi.resource.provider.ResourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.metatype.annotations.Option;

/**
 * The <code>FsResourceProvider</code> is a resource provider which maps
 * file system files and folders into the virtual resource tree. The provider is
 * implemented in terms of a component factory, that is multiple instances of
 * this provider may be created by creating respective configuration.
 * <p>
 * Each provider instance is configured with two properties: The location in the
 * resource tree where resources are provided (provider.root)
 * and the file system path from where files and folders are mapped into the
 * resource (provider.file).
 */
@Component(name="org.apache.sling.fsprovider.internal.FsResourceProvider",
           service=ResourceProvider.class,
           configurationPolicy=ConfigurationPolicy.REQUIRE,
           property={
                   Constants.SERVICE_DESCRIPTION + "=Sling File System Resource Provider",
                   Constants.SERVICE_VENDOR + "=The Apache Software Foundation"
           })
@Designate(ocd=FsResourceProvider.Config.class, factory=true)
public final class FsResourceProvider extends ResourceProvider<Object> {

    /**
     * Resource metadata property set by {@link org.apache.sling.fsprovider.internal.mapper.FileResource}
     * if the underlying file reference is a directory.
     */
    public static final String RESOURCE_METADATA_FILE_DIRECTORY = ":org.apache.sling.fsprovider.file.directory";

    @ObjectClassDefinition(name = "Apache Sling File System Resource Provider",
            description = "Configure an instance of the file system " +
                          "resource provider in terms of provider root and file system location")
    public @interface Config {

        @AttributeDefinition(name = "File System Root",
                description = "File system directory mapped to the virtual " +
                        "resource tree. This property must not be an empty string. If the path is " +
                        "relative it is resolved against sling.home or the current working directory. " +
                        "The path may be a file or folder. If the path does not address an existing " +
                        "file or folder, an empty folder is created.")
        String provider_file();

        @AttributeDefinition(name = "Provider Root",
                description = "Location in the virtual resource tree where the " +
                "file system resources are mapped in. This property must not be an empty string.")
        String provider_root();

        @AttributeDefinition(name = "File system layout",
                description = "File system layout mode for files, folders and content.",
                options={
                        @Option(value="FILES_FOLDERS", label="FILES_FOLDERS - "
                                + "Support only files and folders (classic mode)"),
                        @Option(value="INITIAL_CONTENT", label="INITIAL_CONTENT - "
                                + "Sling-Initial-Content file system layout, supports file and folders ant content files in JSON, xml and jcr.xml format"),
                        @Option(value="FILEVAULT_XML", label="FILEVAULT_XML - "
                                + "FileVault XML format (expanded content package)"),
                })
        FsMode provider_fs_mode() default FsMode.FILES_FOLDERS;

        @AttributeDefinition(name = "Init. Content Options",
                description = "Import options for Sling-Initial-Content file system layout. Supported options: overwrite, ignoreImportProviders.")
        String provider_initial_content_import_options();

        @AttributeDefinition(name = "FileVault Filter",
                description = "Path to META-INF/vault/filter.xml when using FileVault XML file system layout.")
        String provider_filevault_filterxml_path();


        @AttributeDefinition(name = "Check Interval",
                             description = "If the interval has a value higher than 100, the provider will " +
             "check the file system for changes periodically. This interval defines the period in milliseconds " +
             "(the default is 1000). If a change is detected, resource events are sent through the event admin.")
        long provider_checkinterval() default 1000;

        @AttributeDefinition(name = "Cache Size",
                description = "Max. number of content files cached in memory.")
        int provider_cache_size() default 10000;

        // Internal Name hint for web console.
        String webconsole_configurationFactory_nameHint() default "{provider.fs.mode}: {" + ResourceProvider.PROPERTY_ROOT + "}";
    }

    // The location in the resource tree where the resources are mapped
    private String providerRoot;

    // The "root" file or folder in the file system
    private File providerFile;

    // The monitor to detect file changes.
    private FileMonitor monitor;

    // maps file system to resources
    private FileResourceMapper fileMapper;

    // cache for parsed content files
    private ContentFileCache contentFileCache;

    /**
     * Returns a resource wrapping a file system file or folder for the given
     * path. If the <code>path</code> is equal to the configured resource tree
     * location of this provider, the configured file system file or folder is
     * used for the resource. Otherwise the configured resource tree location
     * prefix is removed from the path and the remaining relative path is used
     * to access the file or folder. If no such file or folder exists, this
     * method returns <code>null</code>.
     */
	@Override
    public Resource getResource(final @NotNull ResolveContext<Object> ctx,
            final @NotNull String path,
            final @NotNull ResourceContext resourceContext,
            final @Nullable Resource parent) {
        return this.fileMapper.getResource(ctx, path, resourceContext, parent);
    }

    /**
     * Returns an iterator of resources.
     */
    @Override
    public Iterator<Resource> listChildren(final @NotNull ResolveContext<Object> ctx, final @NotNull Resource parent) {
        return this.fileMapper.listChildren(ctx, parent);
    }

    // ---------- SCR Integration
    @Activate
    protected void activate(BundleContext bundleContext, final Config config) {
        final FsMode fsMode = config.provider_fs_mode();
        String providerRoot = config.provider_root();
        if (StringUtils.isBlank(providerRoot)) {
            throw new IllegalArgumentException("provider.root property must be set");
        }

        String providerFileName = config.provider_file();
        if (StringUtils.isBlank(providerFileName)) {
            throw new IllegalArgumentException("provider.file property must be set");
        }

        this.providerRoot = providerRoot;
        this.providerFile = getProviderFile(providerFileName, bundleContext);
        boolean overlayParentResourceProvider = false;

        List<String> contentFileSuffixes = new ArrayList<>();
        if (fsMode == FsMode.FILEVAULT_XML) {
            contentFileSuffixes.add("/" + DOT_CONTENT_XML);
            contentFileSuffixes.add(ContentFileTypes.XML_SUFFIX);
        } else if (fsMode == FsMode.FILES_FOLDERS) {
            overlayParentResourceProvider = true;
        } else if (fsMode == FsMode.INITIAL_CONTENT) {
            InitialContentImportOptions options = new InitialContentImportOptions(config.provider_initial_content_import_options());
            overlayParentResourceProvider = !options.isOverwrite();
            if (!options.getIgnoreImportProviders().contains(ContentType.JSON.getExtension())) {
                contentFileSuffixes.add(ContentFileTypes.JSON_SUFFIX);
            }
            if (!options.getIgnoreImportProviders().contains(ContentType.JCR_XML.getExtension())) {
                contentFileSuffixes.add(ContentFileTypes.JCR_XML_SUFFIX);
            }
            if (!options.getIgnoreImportProviders().contains(ContentType.XML.getExtension())) {
                contentFileSuffixes.add(ContentFileTypes.XML_SUFFIX);
            }
        }
        final ContentFileExtensions contentFileExtensions = contentFileSuffixes.isEmpty() ? null : new ContentFileExtensions(contentFileSuffixes);

        this.contentFileCache = fsMode != FsMode.FILES_FOLDERS ? new ContentFileCache(config.provider_cache_size()) : null;
        // cache for files that were requested but don't exist
        final FileStatCache fileStatCache = new FileStatCache(this.providerFile);

        if (fsMode == FsMode.FILEVAULT_XML) {
            File filterXmlFile = null;
            if (StringUtils.isNotBlank(config.provider_filevault_filterxml_path())) {
                filterXmlFile = new File(config.provider_filevault_filterxml_path());
            }
            this.fileMapper = new FileVaultResourceMapper(this.providerRoot, this.providerFile, contentFileExtensions, this.contentFileCache, fileStatCache, overlayParentResourceProvider, filterXmlFile);
        } else {
            this.fileMapper = new FileResourceMapper(this.providerRoot, this.providerFile, contentFileExtensions, this.contentFileCache, fileStatCache, overlayParentResourceProvider, true);
        }

        // start background monitor if check interval is higher than 100
        if (config.provider_checkinterval() > 100) {
            File rootFile = this.providerFile;
            if (fsMode == FsMode.FILEVAULT_XML) {
                rootFile = new File(this.providerFile, ".".concat(fileMapper.transformPath(this.getProviderRoot())));
            }
            this.monitor = new FileMonitor(this,
                    rootFile,
                    config.provider_checkinterval(),
                    contentFileExtensions,
                    this.contentFileCache,
                    fileStatCache);
        }
    }

    @Deactivate
    protected void deactivate() {
        if ( this.monitor != null ) {
            this.monitor.stop();
            this.monitor = null;
        }
        this.providerRoot = null;
        this.providerFile = null;
        this.fileMapper = null;
        if (this.contentFileCache != null) {
            this.contentFileCache.clear();
            this.contentFileCache = null;
        }
    }

    String getProviderRoot() {
        return this.providerRoot;
    }

    String transformPath(final String path) {
        return this.fileMapper.transformPath(path);
    }

    // ---------- internal

    private File getProviderFile(String providerFileName,
            BundleContext bundleContext) {

        // the file object from the plain name
        File providerFile = new File(providerFileName);

        // resolve relative file name against sling.home or current
        // working directory
        if (!providerFile.isAbsolute()) {
            String home = bundleContext.getProperty("sling.home");
            if (home != null && home.length() > 0) {
                providerFile = new File(home, providerFileName);
            }
        }

        // resolve the path
        providerFile = providerFile.getAbsoluteFile();

        // if the provider file does not exist, create an empty new folder
        if (!providerFile.exists() && !providerFile.mkdirs()) {
            throw new IllegalArgumentException(
                    "Cannot create provider file root " + providerFile);
        }

        return providerFile;
    }

    public ObservationReporter getObservationReporter() {
        final ProviderContext ctx = this.getProviderContext();
        if (ctx != null) {
            return ctx.getObservationReporter();
        }
        return null;
    }
}
