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

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.util.resource.Resource;
import org.junit.Ignore;
import org.junit.Test;

public class HtmlAddonResourceTest {

    private final static String TEST_BODY_ADDON = "<!-- foo -->";
    private final static String TEST_HEAD_ADDON = "<!-- bar -->";

    private final static String FILE_PATH_INDEX_HTML_ZEPPELIN_WEB = "../zeppelin-web/dist/index.html";
    private final static String FILE_PATH_INDEX_HTML_ZEPPELIN_WEB_ANGULAR = "../zeppelin-web-angular/dist/zeppelin/index.html";

    @Test
    public void testZeppelinWebHtmlAddon() throws IOException {
        final Resource addonResource = getHtmlAddonResource(FILE_PATH_INDEX_HTML_ZEPPELIN_WEB);

        final String content = IOUtils.toString(addonResource.getInputStream(), StandardCharsets.UTF_8);

        assertThat(content, containsString(TEST_BODY_ADDON));
        assertThat(content, containsString(TEST_HEAD_ADDON));

    }

    @Test
    @Ignore // ignored due to zeppelin-web-angular not build for core tests
    public void testZeppelinWebAngularHtmlAddon() throws IOException {
        final Resource addonResource = getHtmlAddonResource(FILE_PATH_INDEX_HTML_ZEPPELIN_WEB_ANGULAR);

        final String content = IOUtils.toString(addonResource.getInputStream(), StandardCharsets.UTF_8);

        assertThat(content, containsString(TEST_BODY_ADDON));
        assertThat(content, containsString(TEST_HEAD_ADDON));

    }

    private Resource getHtmlAddonResource(final String indexHtmlPath) {
        return getHtmlAddonResource(indexHtmlPath, TEST_BODY_ADDON, TEST_HEAD_ADDON);
    }

    private Resource getHtmlAddonResource(final String indexHtmlPath, final String bodyAddon, final String headAddon) {
        final Resource indexResource = Resource.newResource(new File(indexHtmlPath));
        return new HtmlAddonResource(indexResource, TEST_BODY_ADDON, TEST_HEAD_ADDON);
    }

}
