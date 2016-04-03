package org.apache.zeppelin.notebook;

/**
 * NoteEventListener
 */
public interface NoteEventListener {
  public void onParagraphRemove(Paragraph p);
  public void onParagraphCreate(Paragraph p);
}
