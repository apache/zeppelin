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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class IndexHtmlServletTest {

    private final static String TEST_BODY_ADDON = "<!-- foo -->";
    private final static String TEST_HEAD_ADDON = "<!-- bar -->";

    private final static String FILE_PATH_INDEX_HTML_ZEPPELIN_WEB = "../zeppelin-web/dist/index.html";
    private final static String FILE_PATH_INDEX_HTML_ZEPPELIN_WEB_ANGULAR = "../zeppelin-web-angular/dist/zeppelin/index.html";


    @Test
    void testZeppelinWebHtmlAddon() throws IOException, ServletException {
      ZeppelinConfiguration conf = mock(ZeppelinConfiguration.class);
      when(conf.getHtmlBodyAddon()).thenReturn(TEST_BODY_ADDON);
      when(conf.getHtmlHeadAddon()).thenReturn(TEST_HEAD_ADDON);

      ServletConfig sc = mock(ServletConfig.class);
      ServletContext ctx = mock(ServletContext.class);
      when(ctx.getResource("/index.html"))
        .thenReturn(new URL("file:" + FILE_PATH_INDEX_HTML_ZEPPELIN_WEB));
      when(sc.getServletContext()).thenReturn(ctx);

      IndexHtmlServlet servlet = new IndexHtmlServlet(conf);
      servlet.init(sc);

      HttpServletResponse mockResponse = mock(HttpServletResponse.class);
      HttpServletRequest mockRequest = mock(HttpServletRequest.class);

      // Catch content in ByteArrayOutputStream
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      PrintWriter writer = new PrintWriter(out);
      when(mockResponse.getWriter()).thenReturn(writer);

      servlet.doGet(mockRequest, mockResponse);
      writer.flush();
      // Get Content
      String content = new String(out.toString());

      assertThat(content, containsString(TEST_BODY_ADDON));
      assertThat(content, containsString(TEST_HEAD_ADDON));

    }

    @Test
    @Disabled("ignored due to zeppelin-web-angular not build for core tests")
    void testZeppelinWebAngularHtmlAddon() throws IOException, ServletException {
      ZeppelinConfiguration conf = mock(ZeppelinConfiguration.class);
      when(conf.getHtmlBodyAddon()).thenReturn(TEST_BODY_ADDON);
      when(conf.getHtmlHeadAddon()).thenReturn(TEST_HEAD_ADDON);

      ServletConfig sc = mock(ServletConfig.class);
      ServletContext ctx = mock(ServletContext.class);
      when(ctx.getResource("/index.html"))
        .thenReturn(new URL("file:" + FILE_PATH_INDEX_HTML_ZEPPELIN_WEB_ANGULAR));
      when(sc.getServletContext()).thenReturn(ctx);

      IndexHtmlServlet servlet = new IndexHtmlServlet(conf);
      servlet.init(sc);

      HttpServletResponse mockResponse = mock(HttpServletResponse.class);
      HttpServletRequest mockRequest = mock(HttpServletRequest.class);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      PrintWriter writer = new PrintWriter(out);
      when(mockResponse.getWriter()).thenReturn(writer);

      servlet.doGet(mockRequest, mockResponse);
      writer.flush();
      // Get Content
      String content = new String(out.toString());

      assertThat(content, containsString(TEST_BODY_ADDON));
      assertThat(content, containsString(TEST_HEAD_ADDON));

    }
}
