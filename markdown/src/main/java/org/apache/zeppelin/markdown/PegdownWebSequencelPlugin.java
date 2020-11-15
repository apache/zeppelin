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

import org.parboiled.BaseParser;
import org.parboiled.Rule;
import org.parboiled.support.StringBuilderVar;
import org.pegdown.Parser;
import org.pegdown.ast.ExpImageNode;
import org.pegdown.ast.TextNode;
import org.pegdown.plugins.BlockPluginParser;
import org.pegdown.plugins.PegDownPlugins;

/**
 * Pegdown plugin for Websequence diagram.
 */
public class PegdownWebSequencelPlugin extends Parser implements BlockPluginParser {

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

  Rule startMarker() {
    return Sequence(Spn1(), TAG, Sp(), "sequence", Sp());
  }

  String endMarker() {
    return TAG;
  }

  Rule body() {
    return OneOrMore(TestNot(TAG), BaseParser.ANY);
  }

  Rule blockRule() {
    StringBuilderVar style = new StringBuilderVar();
    StringBuilderVar body = new StringBuilderVar();

    return NodeSequence(
        startMarker(),
        Optional(
            String("style="),
            Sequence(OneOrMore(Letter()), style.append(match()), Spn1())),
        Sequence(body(), body.append(match())),
        endMarker(),
        push(
            new ExpImageNode("title",
                MarkdownUtils.createWebsequenceUrl(style.getString(), body.getString()),
                new TextNode("")))
    );
  }

  @Override
  public Rule[] blockPluginRules() {
    return new Rule[]{blockRule()};
  }
}
