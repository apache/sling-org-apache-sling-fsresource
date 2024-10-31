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
package org.apache.sling.simplefsprovider.internal;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.spi.resource.provider.ObservationReporter;
import org.apache.sling.spi.resource.provider.ProviderContext;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.apache.sling.spi.resource.provider.ResourceContext;
import org.apache.sling.spi.resource.provider.ResourceProvider;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

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
@Component(
        name = "org.apache.sling.simplefsprovider.FsResourceProvider",
        service = ResourceProvider.class,
        configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = FsResourceProvider.Config.class, factory = true)
public class FsResourceProvider extends ResourceProvider<Object> {

    @ObjectClassDefinition(
            name = "Apache Sling Simple Filesystem Resource Provider",
            description = "Configure an instance of the filesystem "
                    + "resource provider in terms of provider root and filesystem location")
    public @interface Config {
        /**
         * The name of the configuration property providing file system path of
         * files and folders mapped into the resource tree (value is
         * "provider.file").
         */
        @AttributeDefinition(
                name = "Filesystem Root",
                description = "Filesystem directory mapped to the virtual "
                        + "resource tree. This property must not be an empty string. If the path is "
                        + "relative it is resolved against sling.home or the current working directory. "
                        + "The path may be a file or folder. If the path does not address an existing "
                        + "file or folder, an empty folder is created.")
        String provider_file();

        /**
         * The name of the configuration property providing the check interval
         * for file changes (value is "provider.checkinterval").
         */
        @AttributeDefinition(
                name = "Check Interval",
                description =
                        "If the interval has a value higher than 100, the provider will "
                                + "check the file system for changes periodically. This interval defines the period in milliseconds "
                                + "(the default is 1000). If a change is detected, resource events are sent through the event admin.")
        long provider_checkinterval() default 1000;

        @AttributeDefinition(
                name = "Provider Root",
                description = "Location in the virtual resource tree where the "
                        + "filesystem resources are mapped in. This property must not be an empty string.")
        String provider_root();

        @AttributeDefinition(
                name = "Exclude Files",
                description = "Specify a list of simple patterns to exclude files.")
        String[] provider_exclude_files() default {
            "\\..*", ".*~", ".*\\.bak", ".*\\.swp", ".*\\.swx", ".*\\.swpx", ".*\\.tmp", ".*\\.log"
        };

        /**
         * Internal Name hint for web console.
         */
        String webconsole_configurationFactory_nameHint() default "Root path: {" + ResourceProvider.PROPERTY_ROOT + "}";
    }

    // Resource path prefix with trailing slash
    private final String resourcePathPrefix;

    // The "root" file or folder in the file system
    private final String home;

    /** The monitor to detect file changes. */
    private final FileMonitor monitor;

    private final List<Pattern> excludePatterns = new ArrayList<>();

    @Activate
    public FsResourceProvider(final Config config, final BundleContext bundleContext) {
        if (config.provider_root() == null || config.provider_root().length() == 0) {
            throw new IllegalArgumentException("provider.root property must be set");
        }
        if (config.provider_root().endsWith("/")) {
            this.resourcePathPrefix = config.provider_root();
        } else {
            this.resourcePathPrefix = config.provider_root().concat("/");
        }
        final String providerFileName = config.provider_file();
        if (providerFileName == null || providerFileName.length() == 0) {
            throw new IllegalArgumentException("provider.file property must be set");
        }
        final File homeDir = getProviderFile(providerFileName, bundleContext);
        this.home = homeDir.getAbsolutePath();
        // start background monitor if check interval is higher than 100
        if (config.provider_checkinterval() > 100) {
            this.monitor = new FileMonitor(
                    this,
                    this.resourcePathPrefix.substring(0, this.resourcePathPrefix.length() - 1),
                    homeDir,
                    config.provider_checkinterval());
        } else {
            this.monitor = null;
        }
        if (config.provider_exclude_files() != null) {
            for (final String pattern : config.provider_exclude_files()) {
                this.excludePatterns.add(Pattern.compile(pattern));
            }
        }
    }

    @Deactivate
    protected void deactivate() {
        if (this.monitor != null) {
            this.monitor.stop();
        }
    }

    private boolean include(final File file) {
        boolean include = false;
        if (file.exists() && (file.isDirectory() || file.canRead())) {
            include = true;
            for (final Pattern pattern : this.excludePatterns) {
                if (pattern.matcher(file.getName()).matches()) {
                    include = false;
                    break;
                }
            }
        }
        return include;
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
    @Override
    public Resource getResource(
            final ResolveContext<Object> ctx,
            final String resourcePath,
            final ResourceContext resourceContext,
            final Resource parent) {
        final String filePath = resourcePath.length() < this.resourcePathPrefix.length()
                ? ""
                : resourcePath.substring(this.resourcePathPrefix.length());

        // try one to one mapping
        final Path path = Paths.get(this.home, filePath.replace('/', File.separatorChar));
        final File file = path.toFile();

        if (include(file)) {
            return new FsResource(ctx.getResourceResolver(), resourcePath, file);
        }
        return null;
    }

    public Iterator<Resource> listChildren(final ResolveContext<Object> ctx, final Resource parent) {
        if (FsResource.RESOURCE_TYPE_FOLDER.equals(parent.getResourceType())) {
            final File file = parent.adaptTo(File.class);
            if (file != null && file.isDirectory()) {
                final List<File> children = new ArrayList<>();
                for (final File c : file.listFiles()) {
                    if (include(c)) {
                        children.add(c);
                    }
                }
                Collections.sort(children);
                final Iterator<File> i = children.iterator();
                return new Iterator<Resource>() {

                    @Override
                    public boolean hasNext() {
                        return i.hasNext();
                    }

                    @Override
                    public Resource next() {
                        final File file = i.next();
                        return new FsResource(
                                ctx.getResourceResolver(),
                                parent.getPath().concat("/").concat(file.getName()),
                                file);
                    }
                };
            }
        }
        return null;
    }

    private File getProviderFile(final String providerFileName, final BundleContext bundleContext) {
        // the file object from the plain name
        File providerFile = new File(providerFileName);

        // resolve relative file name against sling.home or current
        // working directory
        if (!providerFile.isAbsolute()) {
            final String home = bundleContext.getProperty("sling.home");
            if (home != null && home.length() > 0) {
                providerFile = new File(home, providerFileName);
            }
        }

        // resolve the path
        providerFile = providerFile.getAbsoluteFile();

        // if the provider file does not exist, create an empty new folder
        if (!providerFile.exists() && !providerFile.mkdirs()) {
            throw new IllegalArgumentException("Cannot create provider file root " + providerFile);
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
