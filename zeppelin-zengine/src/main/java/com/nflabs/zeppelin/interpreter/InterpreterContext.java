package com.nflabs.zeppelin.interpreter;

import com.nflabs.zeppelin.notebook.Paragraph;

/**
 * Interpreter context
 */
public class InterpreterContext {
  Paragraph paragraph;

  public InterpreterContext(Paragraph paragraph) {
    super();
    this.paragraph = paragraph;
  }

  public Paragraph getParagraph() {
    return paragraph;
  }
  public void setParagraph(Paragraph paragraph) {
    this.paragraph = paragraph;
  }
}
