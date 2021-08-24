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

import static org.apache.jackrabbit.vault.util.Constants.DOT_CONTENT_XML;
import static org.apache.sling.fsprovider.internal.parser.ContentFileTypes.JCR_XML_SUFFIX;
import static org.apache.sling.fsprovider.internal.parser.ContentFileTypes.JSON_SUFFIX;
import static org.apache.sling.fsprovider.internal.parser.ContentFileTypes.XML_SUFFIX;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.contentparser.api.ContentParser;
import org.apache.sling.contentparser.api.ParserOptions;
import org.apache.sling.contentparser.json.JSONParserFeature;
import org.apache.sling.contentparser.json.JSONParserOptions;
import org.apache.sling.contentparser.json.internal.JSONContentParser;
import org.apache.sling.contentparser.xml.internal.XMLContentParser;
import org.apache.sling.contentparser.xml.jcr.internal.JCRXMLContentParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses files that contains content fragments (e.g. JSON, JCR XML).
 */
class ContentFileParserUtil {

    private static final Logger log = LoggerFactory.getLogger(ContentFileParserUtil.class);

    private static final ParserOptions DEFAULT_PARSER_OPTIONS = new ParserOptions();
    private static final ParserOptions JSON_PARSER_OPTIONS = new JSONParserOptions()
            .withFeatures(JSONParserFeature.COMMENTS, JSONParserFeature.QUOTE_TICK);
    private static final ContentParser JSON_PARSER = new JSONContentParser();
    private static final ContentParser JCR_XML_PARSER = new JCRXMLContentParser();
    private static final ContentParser XML_PARSER = new XMLContentParser();

    private ContentFileParserUtil() {
        // static methods only
    }

    /**
     * Parse content from file.
     * @param file File. Type is detected automatically.
     * @return Content or null if content could not be parsed.
     */
    public static ContentElement parse(File file) {
        if (!file.exists()) {
            return null;
        }
        if (StringUtils.endsWith(file.getName(), JSON_SUFFIX)) {
            return parse(file, ContentType.JSON);
        }
        else if (StringUtils.equals(file.getName(), DOT_CONTENT_XML) || StringUtils.endsWith(file.getName(), JCR_XML_SUFFIX)) {
            return parse(file, ContentType.JCR_XML);
        }
        else if (StringUtils.endsWith(file.getName(), XML_SUFFIX) && !StringUtils.endsWith(file.getName(), JCR_XML_SUFFIX)) {
            return parse(file, ContentType.XML);
        }
        return null;
    }

    /**
     * Parse content from file.
     * @param file File. Type is detected automatically.
     * @param contentType Content type
     * @return Content or null if content could not be parsed.
     */
    public static ContentElement parse(File file, ContentType contentType) {
        if (!file.exists()) {
            return null;
        }
        try {
            switch (contentType) {
            case JSON:
                return parse(JSON_PARSER, file, JSON_PARSER_OPTIONS);
            case XML:
                return parse(XML_PARSER, file, DEFAULT_PARSER_OPTIONS);
            case JCR_XML:
                return parse(JCR_XML_PARSER, file, DEFAULT_PARSER_OPTIONS);
               default:
                    throw new IllegalArgumentException("Unexpected content type: " + contentType);
            }
        }
        catch (Throwable ex) {
            log.warn("Error parsing content from " + file.getPath(), ex);
        }
        return null;
    }

    private static ContentElement parse(ContentParser contentParser, File file, ParserOptions parserOptions) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
                BufferedInputStream bis = new BufferedInputStream(fis)) {
            ContentElementHandler handler = new ContentElementHandler();
            contentParser.parse(handler, bis, parserOptions);
            return handler.getRoot();
        }
    }

}
