package org.apache.zeppelin.notebook;

import org.apache.zeppelin.interpreter.InterpreterSetting;

/**
 * Notebook event
 */
public interface NotebookEventListener extends NoteEventListener {
  public void onNoteRemove(Note note);
  public void onNoteCreate(Note note);

  public void onUnbindInterpreter(Note note, InterpreterSetting setting);
}
