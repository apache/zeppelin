package org.apache.zeppelin.rest.message;

public class Session {

  private String sessionId;
  private String noteId;
  private String interpreter;
  private String state;
  private String weburl;
  private String startTime;

  public Session(String sessionId, String noteId, String interpreter, String state, String weburl, String startTime) {
    this.sessionId = sessionId;
    this.noteId = noteId;
    this.interpreter = interpreter;
    this.state = state;
    this.weburl = weburl;
    this.startTime = startTime;
  }

  public String getSessionId() {
    return sessionId;
  }

  public String getNoteId() {
    return noteId;
  }

  public String getState() {
    return state;
  }

  public String getInterpreter() {
    return interpreter;
  }

  public String getWeburl() {
    return weburl;
  }

  public String getStartTime() {
    return startTime;
  }
}
