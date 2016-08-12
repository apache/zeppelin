package org.apache.zeppelin.notebook.repo.revision;

/**
 * Represents the 'Revision' a point in life of the notebook
 */

public class Revision {
  private RevisionId<?> id;
  private String message;
  private int time;

  public Revision(RevisionId<?> revId, String message, int time) {
    this.setRevisionId(revId);
    this.setMessage(message);
    this.setTime(time);
  }

  public RevisionId<?> getRevisionId() {
    return id;
  }

  public void setRevisionId(RevisionId<?> id) {
    this.id = id;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public int getTime() {
    return time;
  }

  public void setTime(int time) {
    this.time = time;
  }

}
