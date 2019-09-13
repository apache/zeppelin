package org.apache.zeppelin.markdown;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataHolder;
import com.vladsch.flexmark.util.builder.Extension;


/**
 * Extension to support YUML and web sequnce diagram.
 */
public class UMLExtension implements Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension {

  private UMLExtension() {

  }

  public static Extension create() {
    return new UMLExtension();
  }

  @Override
  public void rendererOptions(MutableDataHolder options) {

  }

  @Override
  public void extend(HtmlRenderer.Builder rendererBuilder, String rendererType) {
    rendererBuilder.nodeRendererFactory(new UMLNodeRenderer.Factory());
  }

  @Override
  public void parserOptions(MutableDataHolder options) {
  }

  @Override
  public void extend(Parser.Builder parserBuilder) {
    parserBuilder.customBlockParserFactory(new UMLBlockQuoteParser.Factory());
  }
}
