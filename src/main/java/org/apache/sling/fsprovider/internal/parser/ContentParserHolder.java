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
package org.apache.sling.fsprovider.internal.parser;

import org.apache.sling.contentparser.api.ContentParser;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

@Component(service=ContentParserHolder.class)
public class ContentParserHolder {

    @Reference(target = "(" + ContentParser.SERVICE_PROPERTY_CONTENT_TYPE + "=json)")
    private ContentParser jsonParser;

    @Reference(target = "(" + ContentParser.SERVICE_PROPERTY_CONTENT_TYPE + "=xml)")
    private ContentParser xmlParser;

    @Reference(target = "(" + ContentParser.SERVICE_PROPERTY_CONTENT_TYPE + "=jcr-xml)")
    private ContentParser jcrXmlParser;

    @Activate
    protected void activate() {
        ContentFileParserUtil.JSON_PARSER = jsonParser;
        ContentFileParserUtil.XML_PARSER = xmlParser;
        ContentFileParserUtil.JCR_XML_PARSER = jcrXmlParser;
    }

    @Deactivate
    protected void deactivate() {
        ContentFileParserUtil.JSON_PARSER = null;
        ContentFileParserUtil.XML_PARSER = null;
        ContentFileParserUtil.JCR_XML_PARSER = null;
    }
}
