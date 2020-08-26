/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.zeppelin.client;

import kong.unirest.json.JSONObject;


/**
 * Represent each ZSession info.
 */
public class SessionInfo {

  private String sessionId;
  private String noteId;
  private String interpreter;
  private String state;
  private String weburl;
  private String startTime;

  public SessionInfo(JSONObject sessionJson) {
    this.sessionId = sessionJson.getString("sessionId");
    this.noteId = sessionJson.getString("noteId");
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

  public String getSessionId() {
    return sessionId;
  }

  public String getNoteId() {
    return noteId;
  }

  public String getInterpreter() {
    return interpreter;
  }

  public String getState() {
    return state;
  }

  public String getWeburl() {
    return weburl;
  }

  public String getStartTime() {
    return startTime;
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
