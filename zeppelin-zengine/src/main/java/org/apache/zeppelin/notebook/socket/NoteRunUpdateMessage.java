package org.apache.zeppelin.notebook.socket;

/**
 * Websocket message for updating note run status
 */
public class NoteRunUpdateMessage {
  String noteId;
  boolean runningSequentially;

  public NoteRunUpdateMessage(String noteId, boolean runningSequentially) {
    this.noteId = noteId;
    this.runningSequentially = runningSequentially;
  }
}
