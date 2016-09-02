package org.apache.zeppelin.markdown;

/** Abstract Markdown Parser. */
public interface MarkdownParser {
  String render(String markdownText);
}
