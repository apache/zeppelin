package org.apache.zeppelin.markdown;

import org.pegdown.Extensions;
import org.pegdown.PegDownProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Pegdown Markdown Parser. */
public class PegdownParser implements MarkdownParser {
  private PegDownProcessor processor;

  private final Logger logger = LoggerFactory.getLogger(PegdownParser.class);

  public PegdownParser() {
    int pegdownOptions = Extensions.ALL_WITH_OPTIONALS - Extensions.ANCHORLINKS;
    int parsingTimeoutAsMillis = 5000;
    processor = new PegDownProcessor(pegdownOptions, parsingTimeoutAsMillis);
  }

  @Override
  public String render(String markdownText) {
    String html = "";

    try {
      String parsed = processor.markdownToHtml(markdownText);
      if (null == parsed) throw new RuntimeException("Cannot parse markdown syntax string to HTML");

      html = wrapWithMarkdownClassDiv(parsed);

    } catch (RuntimeException e) {
      logger.error("Failed to parsed markdown text", e);
    }

    return html;
  }

  /** wrap with markdown class div to styling DOM using css */
  public static String wrapWithMarkdownClassDiv(String html) {
    return new StringBuilder()
            .append("<div class=\"markdown-body\">\n")
            .append(html)
            .append("\n</div>")
            .toString();
  }
}
