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


import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gitlab.GitLabExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.typographic.TypographicExtension;
import com.vladsch.flexmark.ext.wikilink.WikiLinkExtension;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Flexmark Parser
 */
public class FlexmarkParser implements MarkdownParser {
  private static final Logger LOGGER = LoggerFactory.getLogger(FlexmarkParser.class);
  Parser parser;
  HtmlRenderer renderer;

  public FlexmarkParser() {
    MutableDataSet options = new MutableDataSet();
    options.set(Parser.EXTENSIONS, Arrays.asList(StrikethroughExtension.create(),
        TablesExtension.create(),
        UMLExtension.create(),
        AutolinkExtension.create(),
        WikiLinkExtension.create(),
        TypographicExtension.create()));
    options.set(HtmlRenderer.SOFT_BREAK, "<br />\n");
    parser = Parser.builder(options).build();
    renderer = HtmlRenderer.builder(options).build();
  }

  @Override
  public String render(String markdownText) {
    Node document = parser.parse(markdownText);
    String html = renderer.render(document);
    return wrapWithMarkdownClassDiv(html);
  }

  /**
   * wrap with markdown class div to styling DOM using css.
   */
  public static String wrapWithMarkdownClassDiv(String html) {
    return new StringBuilder()
        .append("<div class=\"markdown-body\">\n")
        .append(html)
        .append("\n</div>")
        .toString();
  }
}
