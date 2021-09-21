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
package org.apache.zeppelin.jupyter.parser;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.emoji.EmojiExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.typographic.TypographicExtension;
import com.vladsch.flexmark.ext.wikilink.WikiLinkExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import java.util.Arrays;

import static com.vladsch.flexmark.ext.emoji.EmojiImageType.UNICODE_ONLY;

public class MarkdownParser {
  private final Parser parser;
  private final HtmlRenderer renderer;

  public MarkdownParser() {
    MutableDataSet options = new MutableDataSet();
    options.set(Parser.EXTENSIONS, Arrays.asList(StrikethroughExtension.create(),
        TablesExtension.create(),
        AutolinkExtension.create(),
        WikiLinkExtension.create(),
        TypographicExtension.create(),
        EmojiExtension.create()));
    options.set(HtmlRenderer.SOFT_BREAK, "<br />\n");
    options.set(EmojiExtension.USE_IMAGE_TYPE, UNICODE_ONLY);
    parser = Parser.builder(options).build();
    renderer = HtmlRenderer.builder(options).build();
  }

  public String render(String markdownText) {
    Node document = parser.parse(markdownText);
    String html = renderer.render(document);
    return wrapWithMarkdownClassDiv(html);
  }

  public static String wrapWithMarkdownClassDiv(String html) {
    return "<div class=\"markdown-body\">\n" + html + "</div>";
  }
}
