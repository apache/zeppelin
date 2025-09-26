package org.apache.zeppelin.event;

import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.user.AuthenticationInfo;

public interface NoteEvent {

  Note getNote();

  AuthenticationInfo getSubject();
}