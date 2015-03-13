package com.nflabs.zeppelin.interpreter;

import java.util.Map;

import com.nflabs.zeppelin.display.GUI;

/**
 * Interpreter context
 */
public class InterpreterContext {
  private final String paragraphTitle;
  private final String paragraphId;
  private final String paragraphText;
  private final Map<String, Object> config;
  private GUI gui;


  public InterpreterContext(String paragraphId,
                            String paragraphTitle,
                            String paragraphText,
                            Map<String, Object> config,
                            GUI gui
                            ) {
    this.paragraphId = paragraphId;
    this.paragraphTitle = paragraphTitle;
    this.paragraphText = paragraphText;
    this.config = config;
    this.gui = gui;
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

  public GUI getGui() {
    return gui;
  }

}
