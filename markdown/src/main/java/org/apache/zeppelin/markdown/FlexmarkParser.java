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
        GitLabExtension.create(),
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
    LOGGER.debug("markdownText: {}", markdownText);
    Node document = parser.parse(markdownText);
    String html = renderer.render(document);
    LOGGER.info("Renderer HTML: {} ", html);
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
