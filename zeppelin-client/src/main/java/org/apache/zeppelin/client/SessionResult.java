package org.apache.zeppelin.client;

import kong.unirest.json.JSONObject;

public class SessionResult {

  private String sessionId;
  private String interpreter;
  private String state;
  private String weburl;
  private String startTime;

  public SessionResult(JSONObject sessionJson) {
    this.sessionId = sessionJson.getString("sessionId");
    this.interpreter = sessionJson.getString("interpreter");
    this.state = sessionJson.getString("state");
    if (sessionJson.has("weburl")) {
      this.weburl = sessionJson.getString("weburl");
    } else {
      this.weburl = "";
    }
    if (sessionJson.has("startTime")) {
      this.startTime = sessionJson.getString("startTime");
    } else {
      this.startTime = "";
    }
  }

  @Override
  public String toString() {
    return "SessionResult{" +
            "sessionId='" + sessionId + '\'' +
            ", interpreter='" + interpreter + '\'' +
            ", state='" + state + '\'' +
            ", weburl='" + weburl + '\'' +
            ", startTime='" + startTime + '\'' +
            '}';
  }
}
