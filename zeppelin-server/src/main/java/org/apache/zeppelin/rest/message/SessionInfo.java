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

package org.apache.zeppelin.rest.message;

/**
 * (TODO) Move it to zeppelin-common, so that we don't need to have 2 copies.
 */
public class SessionInfo {

  private String sessionId;
  private String noteId;
  private String interpreter;
  private String state;
  private String weburl;
  private String startTime;

  public SessionInfo(String sessionId) {
    this.sessionId = sessionId;
  }

  public SessionInfo(String sessionId, String noteId, String interpreter) {
    this.sessionId = sessionId;
    this.noteId = noteId;
    this.interpreter = interpreter;
  }

  public SessionInfo(String sessionId, String noteId, String interpreter, String state, String weburl, String startTime) {
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

  public void setState(String state) {
    this.state = state;
  }

  public void setStartTime(String startTime) {
    this.startTime = startTime;
  }

  public void setWeburl(String weburl) {
    this.weburl = weburl;
  }
}
