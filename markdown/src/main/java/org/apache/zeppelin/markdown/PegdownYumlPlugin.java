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

import static org.apache.commons.lang3.StringUtils.defaultString;

import org.parboiled.BaseParser;
import org.parboiled.Rule;
import org.parboiled.support.StringBuilderVar;
import org.pegdown.Parser;
import org.pegdown.ast.ExpImageNode;
import org.pegdown.ast.TextNode;
import org.pegdown.plugins.BlockPluginParser;
import org.pegdown.plugins.PegDownPlugins;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

/**
 * Pegdown plugin for YUML
 */
public class PegdownYumlPlugin extends Parser implements BlockPluginParser {

  public PegdownYumlPlugin() {
    super(PegdownParser.OPTIONS,
        PegdownParser.PARSING_TIMEOUT_AS_MILLIS,
        DefaultParseRunnerProvider);
  }

  public PegdownYumlPlugin(Integer options,
                           Long maxParsingTimeInMillis,
                           ParseRunnerProvider parseRunnerProvider,
                           PegDownPlugins plugins) {
    super(options, maxParsingTimeInMillis, parseRunnerProvider, plugins);
  }

  public static final String TAG = "%%%";

  Rule StartMarker() {
    return Sequence(Spn1(), TAG, Sp(), "yuml", Sp());
  }

  String EndMarker() {
    return TAG;
  }

  Rule ParameterName() {
    return FirstOf("type", "style", "scale", "format", "dir");
  }

  Rule Body() {
    return OneOrMore(TestNot(TAG), BaseParser.ANY);
  }

  Rule BlockRule() {
    ParamVar<String, String> params = new ParamVar<String, String>();
    StringBuilderVar name = new StringBuilderVar();
    StringBuilderVar value = new StringBuilderVar();
    StringBuilderVar body = new StringBuilderVar();

    return NodeSequence(
        StartMarker(),
        ZeroOrMore(
            Sequence(
                ParameterName(), name.append(match()),
                String("="),
                OneOrMore(Alphanumeric()), value.append(match())),
            Sp(),
            params.put(name.getString(), value.getString()),
            name.clear(), value.clear()),
        Body(),
        body.append(match()),
        EndMarker(),
        push(
            new ExpImageNode(
                "title", createYumlUrl(params.get(), body.getString()), new TextNode("")))
    );
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

  @Override
  public Rule[] blockPluginRules() {
    return new Rule[]{BlockRule()};
  }
}
