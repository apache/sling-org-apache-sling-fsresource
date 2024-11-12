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

import java.util.ArrayList;
import java.util.List;

import org.apache.sling.fsprovider.internal.parser.ContentFileTypes;
import org.apache.sling.fsprovider.internal.parser.ContentParserHolder;
import org.apache.sling.fsprovider.internal.parser.ContentType;
import org.apache.sling.spi.resource.provider.ResourceProvider;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceVendor;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@Component(name="org.apache.sling.fsprovider.InitialContentResourceProvider",
           service=ResourceProvider.class,
           configurationPolicy=ConfigurationPolicy.REQUIRE)
@ServiceVendor("The Apache Software Foundation")
@Designate(ocd=InitialContentResourceProvider.Config.class, factory=true)
public class InitialContentResourceProvider extends FsResourceProvider {

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

        @AttributeDefinition(name = "Init. Content Options",
                description = "Import options for Sling-Initial-Content file system layout. Supported options: overwrite, ignoreImportProviders.")
        String provider_initial_content_import_options();

        @AttributeDefinition(name = "Check Interval",
                             description = "If the interval has a value higher than 100, the provider will " +
             "check the file system for changes periodically. This interval defines the period in milliseconds " +
             "(the default is 1000). If a change is detected, resource events are sent through the event admin.")
        long provider_checkinterval() default 1000;

        @AttributeDefinition(name = "Cache Size",
                description = "Max. number of content files cached in memory.")
        int provider_cache_size() default 10000;

        // Internal Name hint for web console.
        String webconsole_configurationFactory_nameHint() default "{" + ResourceProvider.PROPERTY_ROOT + "}";
    }

    @Reference
    private ContentParserHolder holder;

    @Activate
    protected void activate(BundleContext bundleContext, final Config config) {
        final List<String> contentFileSuffixes = new ArrayList<>();
        final InitialContentImportOptions options = new InitialContentImportOptions(config.provider_initial_content_import_options());
        if (!options.getIgnoreImportProviders().contains(ContentType.JSON.getExtension())) {
            contentFileSuffixes.add(ContentFileTypes.JSON_SUFFIX);
        }
        if (!options.getIgnoreImportProviders().contains(ContentType.JCR_XML.getExtension())) {
            contentFileSuffixes.add(ContentFileTypes.JCR_XML_SUFFIX);
        }
        if (!options.getIgnoreImportProviders().contains(ContentType.XML.getExtension())) {
            contentFileSuffixes.add(ContentFileTypes.XML_SUFFIX);
        }
        final boolean overlayParentResourceProvider = !options.isOverwrite();
        super.activate(bundleContext,
            config.provider_root(),
            config.provider_file(),
            config.provider_checkinterval(),
            overlayParentResourceProvider,
            config.provider_cache_size(),
            contentFileSuffixes.isEmpty() ? null : contentFileSuffixes,
            false,
            null);

    }

    @Deactivate
    protected void deactivate() {
        super.deactivate();
    }
}
