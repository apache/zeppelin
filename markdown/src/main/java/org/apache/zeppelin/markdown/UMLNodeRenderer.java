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

package org.apache.zeppelin.markdown;

import com.vladsch.flexmark.ext.gitlab.internal.GitLabOptions;
import com.vladsch.flexmark.html.CustomNodeRenderer;
import com.vladsch.flexmark.html.HtmlWriter;
import com.vladsch.flexmark.html.renderer.NodeRenderer;
import com.vladsch.flexmark.html.renderer.NodeRendererFactory;
import com.vladsch.flexmark.html.renderer.NodeRendererContext;
import com.vladsch.flexmark.html.renderer.NodeRenderingHandler;
import com.vladsch.flexmark.util.data.DataHolder;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.defaultString;

/**
 * Html Node renderer to render the image
 */
public class UMLNodeRenderer implements NodeRenderer {

  private static final Logger LOGGER = LoggerFactory.getLogger(UMLNodeRenderer.class);

  public static final String YUML = "yuml";
  public static final String SEQUENCE = "sequence";
  public static final String WEBSEQ_URL = "http://www.websequencediagrams.com";

  final GitLabOptions options;

  public UMLNodeRenderer(DataHolder options) {
    this.options = new GitLabOptions(options);
  }

  @Override
  public Set<NodeRenderingHandler<?>> getNodeRenderingHandlers() {
    Set<NodeRenderingHandler<?>> set = new HashSet<>();
    set.add(new NodeRenderingHandler<>(UMLBlockQuote.class,
        new CustomNodeRenderer<UMLBlockQuote>() {
          @Override
          public void render(UMLBlockQuote node, NodeRendererContext context,
                             HtmlWriter html) {
            UMLNodeRenderer.this.render(node, context, html);
          }
        }));
    return set;
  }


  private void render(final UMLBlockQuote node, final NodeRendererContext context,
                      HtmlWriter html) {
    LOGGER.debug("Rendering HTML");

    String firstLine = node.getOpeningTrailing().toString();
    String[] splitWithSpace = firstLine.split(" ");

    LOGGER.debug("Start of the node {} ", firstLine);
    LOGGER.debug("Content within block {} ", node.getFirstChild().getChars());

    Map<String, String> paramMap = new HashMap<>();
    for (int i = 1; i < splitWithSpace.length; i++) {
      String[] splitWithEqual = splitWithSpace[i].split("=");
      paramMap.put(splitWithEqual[0], splitWithEqual[1]);
    }

    String url = "";

    if (splitWithSpace[0].equals(YUML) && !Objects.isNull(node.getFirstChild())) {
      url = createYumlUrl(paramMap, node.getFirstChild().getChars().toString());
      LOGGER.debug("Encoded YUML URL {} ", url);
    } else if (splitWithSpace[0].equals(SEQUENCE) && !Objects.isNull(node.getFirstChild())) {
      url = createWebsequenceUrl(paramMap.get("style"), node.getFirstChild().getChars().toString());
      LOGGER.debug("Encoded web sequence diagram URL {} ", url);
    } else {
      html.withAttr().tagLineIndent("blockquote", new Runnable() {
        @Override
        public void run() {
          context.renderChildren(node);
        }
      });
      return;
    }

    html.attr("src", url);
    html.attr("alt", "");
    html.srcPos(node.getChars()).withAttr().tagVoid("img");
  }

  /**
   * Factory for node renderer
   */
  public static class Factory implements NodeRendererFactory {
    @Override
    public NodeRenderer apply(final DataHolder options) {
      return new UMLNodeRenderer(options);
    }
  }

  public static String createYumlUrl(Map<String, String> params, String body) {
    StringBuilder inlined = new StringBuilder();
    for (String line : body.split("\\r?\\n")) {
      line = line.trim();
      if (line.length() > 0) {
        if (inlined.length() > 0) {
          inlined.append(", ");
        }
        inlined.append(line);
      }
    }

    String encodedBody = null;
    try {
      encodedBody = URLEncoder.encode(inlined.toString(), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      new RuntimeException("Failed to encode YUML markdown body", e);
    }

    StringBuilder mergedStyle = new StringBuilder();
    String style = defaultString(params.get("style"), "scruffy");
    String type = defaultString(params.get("type"), "class");
    String format = defaultString(params.get("format"), "svg");

    mergedStyle.append(style);

    if (null != params.get("dir")) {
      mergedStyle.append(";dir:" + params.get("dir"));
    }

    if (null != params.get("scale")) {
      mergedStyle.append(";scale:" + params.get("scale"));
    }

    return new StringBuilder()
        .append("http://yuml.me/diagram/")
        .append(mergedStyle.toString() + "/")
        .append(type + "/")
        .append(encodedBody)
        .append("." + format)
        .toString();
  }

  public static String createWebsequenceUrl(String style,
                                            String content) {

    style = StringUtils.defaultString(style, "default");

    OutputStreamWriter writer = null;
    BufferedReader reader = null;

    String webSeqUrl = "";

    try {
      String query = new StringBuilder()
          .append("style=")
          .append(style)
          .append("&message=")
          .append(URLEncoder.encode(content, "UTF-8"))
          .append("&apiVersion=1")
          .toString();

      URL url = new URL(WEBSEQ_URL);
      URLConnection conn = url.openConnection();
      conn.setDoOutput(true);
      writer = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8);
      writer.write(query);
      writer.flush();

      StringBuilder response = new StringBuilder();
      reader = new BufferedReader(
          new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
      String line;
      while ((line = reader.readLine()) != null) {
        response.append(line);
      }

      writer.close();
      reader.close();

      String json = response.toString();

      int start = json.indexOf("?png=");
      int end = json.indexOf("\"", start);

      if (start != -1 && end != -1) {
        webSeqUrl = WEBSEQ_URL + "/" + json.substring(start, end);
        System.out.println("websequrl : " + webSeqUrl);
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to get proper response from websequencediagrams.com", e);
    } finally {
      IOUtils.closeQuietly(writer);
      IOUtils.closeQuietly(reader);
    }

    return webSeqUrl;
  }
}
