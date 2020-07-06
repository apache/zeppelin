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
 * Context info about running interpreter. This is used for deciding which interpreter binding
 * mode to use.
 */
public class ExecutionContext {

  private final String user;
  private final String noteId;
  private final String defaultInterpreterGroup;
  private final boolean inIsolatedMode;
  // When is the execution triggered, e.g. when the cron job is triggered or when the rest api is triggered.
  private final String startTime;

  public ExecutionContext(String user, String noteId, String defaultInterpreterGroup,
                          boolean inIsolatedMode, String startTime) {
    this.user = user;
    this.noteId = noteId;
    this.defaultInterpreterGroup = defaultInterpreterGroup;
    this.inIsolatedMode = inIsolatedMode;
    this.startTime = startTime;
  }

  public String getUser() {
    return user;
  }

  public String getNoteId() {
    return noteId;
  }

  public String getDefaultInterpreterGroup() {
    return defaultInterpreterGroup;
  }

  public boolean isInIsolatedMode() {
    return inIsolatedMode;
  }

  public String getStartTime() {
    return startTime;
  }

  @Override
  public String toString() {
    return "ExecutionContext{" +
            "user='" + user + '\'' +
            ", noteId='" + noteId + '\'' +
            ", defaultInterpreterGroup='" + defaultInterpreterGroup + '\'' +
            ", inIsolatedMode=" + inIsolatedMode +
            ", startTime=" + startTime +
            '}';
  }
}
