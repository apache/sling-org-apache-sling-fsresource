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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.fsprovider.internal.parser.ContentFileTypes;
import org.apache.sling.fsprovider.internal.parser.ContentParserHolder;
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
import static org.apache.jackrabbit.vault.util.Constants.DOT_CONTENT_XML;

@Component(name="org.apache.sling.fsprovider.FileVaultResourceProvider",
           service=ResourceProvider.class,
           configurationPolicy=ConfigurationPolicy.REQUIRE)
@ServiceVendor("The Apache Software Foundation")
@Designate(ocd=FileVaultResourceProvider.Config.class, factory=true)
public class FileVaultResourceProvider extends FsResourceProvider {

    @ObjectClassDefinition(name = "Apache Sling File System File Vault Resource Provider",
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
        String webconsole_configurationFactory_nameHint() default "{" + ResourceProvider.PROPERTY_ROOT + "}";
    }

    @Reference
    private ContentParserHolder holder;

    @Activate
    protected void activate(BundleContext bundleContext, final Config config) {
        final List<String> contentFileSuffixes = new ArrayList<>();
        contentFileSuffixes.add("/" + DOT_CONTENT_XML);
        contentFileSuffixes.add(ContentFileTypes.XML_SUFFIX);

        File filterXmlFile = null;
        if (StringUtils.isNotBlank(config.provider_filevault_filterxml_path())) {
            filterXmlFile = new File(config.provider_filevault_filterxml_path());
        }
        super.activate(bundleContext,
            config.provider_root(),
            config.provider_file(),
            config.provider_checkinterval(),
            false,
            config.provider_cache_size(),
            contentFileSuffixes,
            true,
            filterXmlFile);
    }

    @Deactivate
    protected void deactivate() {
        super.deactivate();
    }
}
