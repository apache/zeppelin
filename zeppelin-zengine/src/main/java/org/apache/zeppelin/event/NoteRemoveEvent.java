package org.apache.zeppelin.event;

import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.user.AuthenticationInfo;

public class NoteRemoveEvent implements NoteEvent {

  private Note note;

  private AuthenticationInfo subject;

  public NoteRemoveEvent(Note note, AuthenticationInfo subject) {
    this.note = note;
    this.subject = subject;
  }

  @Override
  public Note getNote() {
    return this.note;
  }

  @Override
  public AuthenticationInfo getSubject() {
    return subject;
  }
}