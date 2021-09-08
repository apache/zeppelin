/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.server;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.util.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resource for enabling html addons in index.html.
 */
public class HtmlAddonResource extends Resource {

    private static final Logger LOGGER = LoggerFactory.getLogger(HtmlAddonResource.class);

    private static final String TAG_BODY_OPENING = "<body"; // ignore bracket here to support potential html attributes
    private static final String TAG_BODY_CLOSING = "</body>";
    private static final String TAG_HEAD_CLOSING = "</head>";
    private static final String TAG_HTML_CLOSING = "</html>";

    public static final String HTML_ADDON_IDENTIFIER = "zeppelin-index-with-addon";
    public static final String INDEX_HTML_PATH = "/index.html";

    private final Resource indexResource;
    private File alteredTempFile = null;
    private byte[] alteredContent;

    public HtmlAddonResource(final Resource indexResource, final String bodyAddon, final String headAddon) {
        LOGGER.info("Enabling html addons in {}: body='{}' head='{}'", indexResource, bodyAddon, headAddon);
        this.indexResource = indexResource;
        try {
            // read original content from resource
            String content = IOUtils.toString(indexResource.getInputStream(), StandardCharsets.UTF_8);

            // process body addon
            if (bodyAddon != null) {
                if (content.contains(TAG_BODY_CLOSING)) {
                    content = content.replace(TAG_BODY_CLOSING, bodyAddon + TAG_BODY_CLOSING);
                } else if (content.contains(TAG_HTML_CLOSING)) {
                    content = content.replace(TAG_HTML_CLOSING, bodyAddon + TAG_HTML_CLOSING);
                } else {
                    content = content + bodyAddon;
                }
            }

            // process head addon
            if (headAddon != null) {
                if (content.contains(TAG_HEAD_CLOSING)) {
                    content = content.replace(TAG_HEAD_CLOSING, headAddon + TAG_HEAD_CLOSING);
                } else if (content.contains(TAG_BODY_OPENING)) {
                    content = content.replace(TAG_BODY_OPENING, headAddon + TAG_BODY_OPENING);
                } else {
                    LOGGER.error("Unable to process Head html addon. Could not find proper anchor in index.html.");
                }
            }

            this.alteredContent = content.getBytes(StandardCharsets.UTF_8);

            // only relevant in development mode: create altered temp file (as zeppelin web archives are addressed via local
            // filesystem folders)
            if (indexResource.getFile() != null) {
                this.alteredTempFile = File.createTempFile(HTML_ADDON_IDENTIFIER, ".html");
                this.alteredTempFile.deleteOnExit();
                FileUtils.writeByteArrayToFile(this.alteredTempFile, this.alteredContent);
            }

        } catch (IOException e) {
            LOGGER.error("Error initializing html addons.", e);
        }

    }

    @Override
    public File getFile() throws IOException {
        return this.alteredTempFile;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(this.alteredContent);
    }

    @Override
    public String getName() {
        return indexResource.getName();
    }

    @Override
    public boolean isContainedIn(Resource r) throws MalformedURLException {
        return indexResource.isContainedIn(r);
    }

    @Override
    public boolean exists() {
        return indexResource.exists();
    }

    @Override
    public boolean isDirectory() {
        return indexResource.isDirectory();
    }

    @Override
    public long lastModified() {
        return indexResource.lastModified();
    }

    @Override
    public long length() {
        return alteredContent.length;
    }

    @Override
    public URL getURL() {
        return indexResource.getURL();
    }

    @Override
    public ReadableByteChannel getReadableByteChannel() throws IOException {
        return Channels.newChannel(new ByteArrayInputStream(this.alteredContent));
    }

    @Override
    public boolean delete() throws SecurityException {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public boolean renameTo(Resource dest) throws SecurityException {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public String[] list() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public Resource addPath(String path) throws IOException, MalformedURLException {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void close() {
    }

}
