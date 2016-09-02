package org.apache.zeppelin.markdown;

import org.markdown4j.Markdown4jProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/** Markdown Parser using markdown4j processor . */
public class Markdown4jParser implements MarkdownParser {
  private final Logger logger = LoggerFactory.getLogger(Markdown4jParser.class);

  private Markdown4jProcessor processor;

  public Markdown4jParser() {
    processor = new Markdown4jProcessor();
  }

  @Override
  public String render(String markdownText) {
    String html = "";

    try {
      html = processor.process(markdownText);
    } catch (IOException e) {
      logger.error("Cannot parse markdown text to HTML using markdown4j", e);
    }

    return html;
  }
}
