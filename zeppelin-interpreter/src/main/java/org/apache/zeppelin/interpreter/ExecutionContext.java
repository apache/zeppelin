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

package org.apache.zeppelin.interpreter;

/**
 * Context info about running interpreter. This is used for deciding bind to
 * which interpreter progress(InterpreterGroup)
 */
public class ExecutionContext {

  private String user;
  private String noteId;
  private String interpreterGroupId;
  private String defaultInterpreterGroup;
  private boolean inIsolatedMode;
  // When is the execution triggered, e.g. when the cron job is triggered or when the rest api is triggered.
  private String startTime;

  public ExecutionContext(){

  }

  public ExecutionContext(String user, String noteId, String defaultInterpreterGroup) {
    this.user = user;
    this.noteId = noteId;
    this.defaultInterpreterGroup = defaultInterpreterGroup;
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public String getNoteId() {
    return noteId;
  }

  public void setNoteId(String noteId) {
    this.noteId = noteId;
  }

  public String getInterpreterGroupId() {
    return interpreterGroupId;
  }

  public void setInterpreterGroupId(String interpreterGroupId) {
    this.interpreterGroupId = interpreterGroupId;
  }

  public String getDefaultInterpreterGroup() {
    return defaultInterpreterGroup;
  }

  public void setDefaultInterpreterGroup(String defaultInterpreterGroup) {
    this.defaultInterpreterGroup = defaultInterpreterGroup;
  }

  public boolean isInIsolatedMode() {
    return inIsolatedMode;
  }

  public void setInIsolatedMode(boolean inIsolatedMode) {
    this.inIsolatedMode = inIsolatedMode;
  }

  public String getStartTime() {
    return startTime;
  }

  public void setStartTime(String startTime) {
    this.startTime = startTime;
  }

  @Override
  public String toString() {
    return "ExecutionContext{" +
            "user='" + user + '\'' +
            ", noteId='" + noteId + '\'' +
            ", interpreterGroupId='" + interpreterGroupId + '\'' +
            ", defaultInterpreterGroup='" + defaultInterpreterGroup + '\'' +
            ", inIsolatedMode=" + inIsolatedMode +
            ", startTime=" + startTime +
            '}';
  }
}
