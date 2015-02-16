package com.nflabs.zeppelin.interpreter;

import java.util.HashMap;
import java.util.Map;

/**
 * Interpreter context
 */
public class InterpreterContext {
  private final String paragraphTitle;
  private final String paragraphId;
  private final String paragraphText;
  private final Map<String, Object> config;


  public InterpreterContext(String paragraphId,
                            String paragraphTitle,
                            String paragraphText,
                            Map<String, Object> config
                            ) {
    this.paragraphId = paragraphId;
    this.paragraphTitle = paragraphTitle;
    this.paragraphText = paragraphText;
    this.config = config;
  }

  public String getParagraphId() {
    return paragraphId;
  }

  public String getParagraphText() {
    return paragraphText;
  }

  public String getParagraphTitle() {
    return paragraphTitle;
  }

  public Map<String, Object> getConfig() {
    return config;
  }
}
