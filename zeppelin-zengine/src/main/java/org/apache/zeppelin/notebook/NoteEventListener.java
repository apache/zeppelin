package org.apache.zeppelin.notebook;

import org.apache.zeppelin.scheduler.Job;

/**
 * NoteEventListener
 */
public interface NoteEventListener {
  public void onParagraphRemove(Paragraph p);
  public void onParagraphCreate(Paragraph p);
  public void onParagraphStatusChange(Paragraph p, Job.Status status);
}
