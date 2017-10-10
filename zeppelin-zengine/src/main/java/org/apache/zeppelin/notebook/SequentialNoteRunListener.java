package org.apache.zeppelin.notebook;

/**
 * Listener interface for sequential note run events
 */
public interface SequentialNoteRunListener {
  public void onSequentialRunFinished(Note note);
}
