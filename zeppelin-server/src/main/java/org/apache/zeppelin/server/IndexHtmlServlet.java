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

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexHtmlServlet extends HttpServlet {

  private static final Logger LOGGER = LoggerFactory.getLogger(IndexHtmlServlet.class);

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  private static final String TAG_BODY_OPENING = "<body"; // ignore bracket here to support potential html attributes
  private static final String TAG_BODY_CLOSING = "</body>";
  private static final String TAG_HEAD_CLOSING = "</head>";
  private static final String TAG_HTML_CLOSING = "</html>";

  final String bodyAddon;
  final String headAddon;

  public IndexHtmlServlet(ZeppelinConfiguration conf) {
    this.bodyAddon = conf.getHtmlBodyAddon();
    this.headAddon = conf.getHtmlHeadAddon();
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
    throws ServletException, IOException {
    try {
      URL indexResource = getServletContext().getResource("/index.html");
      String content = IOUtils.toString(indexResource, StandardCharsets.UTF_8);
      // read original content from resource
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
          LOGGER.error(
            "Unable to process Head html addon. Could not find proper anchor in index.html.");
        }
      }
      resp.setContentType("text/html");
      resp.setStatus(HttpServletResponse.SC_OK);
      resp.getWriter().append(content);
    } catch (IOException e) {
      LOGGER.error("Error rendering index.html.", e);
    }
  }
}
