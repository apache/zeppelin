package org.apache.zeppelin.interpreter.remote;

/**
 * This element stores the buffered
 * append-data of paragraph's output.
 */
public class AppendOutputBuffer {

  private String noteId;
  private String paragraphId;
  private String data;

  public AppendOutputBuffer(String noteId, String paragraphId, String data) {
    this.noteId = noteId;
    this.paragraphId = paragraphId;
    this.data = data;
  }

  public String getNoteId() {
    return noteId;
  }

  public String getParagraphId() {
    return paragraphId;
  }

  public String getData() {
    return data;
  }

}
