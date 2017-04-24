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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.parboiled.BaseParser;
import org.parboiled.Rule;
import org.parboiled.support.StringBuilderVar;
import org.pegdown.Parser;
import org.pegdown.ast.ExpImageNode;
import org.pegdown.ast.TextNode;
import org.pegdown.plugins.BlockPluginParser;
import org.pegdown.plugins.PegDownPlugins;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Pegdown plugin for Websequence diagram
 */
public class PegdownWebSequencelPlugin extends Parser implements BlockPluginParser {

  private static final String WEBSEQ_URL = "http://www.websequencediagrams.com";

  public PegdownWebSequencelPlugin() {
    super(PegdownParser.OPTIONS,
        PegdownParser.PARSING_TIMEOUT_AS_MILLIS,
        DefaultParseRunnerProvider);
  }

  public PegdownWebSequencelPlugin(Integer opts, Long millis, ParseRunnerProvider provider,
                                   PegDownPlugins plugins) {
    super(opts, millis, provider, plugins);
  }

  public static final String TAG = "%%%";

  Rule StartMarker() {
    return Sequence(Spn1(), TAG, Sp(), "sequence", Sp());
  }

  String EndMarker() {
    return TAG;
  }

  Rule Body() {
    return OneOrMore(TestNot(TAG), BaseParser.ANY);
  }

  Rule BlockRule() {
    StringBuilderVar style = new StringBuilderVar();
    StringBuilderVar body = new StringBuilderVar();

    return NodeSequence(
        StartMarker(),
        Optional(
            String("style="),
            Sequence(OneOrMore(Letter()), style.append(match()), Spn1())),
        Sequence(Body(), body.append(match())),
        EndMarker(),
        push(
            new ExpImageNode("title",
                createWebsequenceUrl(style.getString(), body.getString()),
                new TextNode("")))
    );
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
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to get proper response from websequencediagrams.com", e);
    } finally {
      IOUtils.closeQuietly(writer);
      IOUtils.closeQuietly(reader);
    }

    return webSeqUrl;
  }

  @Override
  public Rule[] blockPluginRules() {
    return new Rule[]{BlockRule()};
  }
}
