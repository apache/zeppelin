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

import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;

/**
 * Point-in-time status snapshot of a single interpreter process as seen by the Zeppelin server.
 * Built purely from in-memory server state without contacting the process, so {@code started}
 * reflects whether a process handle exists, not whether the process is currently reachable.
 * Reachability is intentionally out of scope here to keep the read path non-blocking.
 */
public class InterpreterProcessStatus {
  private final String settingId;
  private final String settingName;
  private final String groupId;
  private final int numSessions;
  private final boolean started;
  private String host;
  private int port = -1;
  private String startTime;
  private long uptimeSeconds;
  private String errorMessage;

  public InterpreterProcessStatus(ManagedInterpreterGroup group) {
    InterpreterSetting setting = group.getInterpreterSetting();
    this.settingId = setting.getId();
    this.settingName = setting.getName();
    this.groupId = group.getId();
    this.numSessions = group.getSessionNum();
    // Read the handle once: another thread may close the group concurrently.
    RemoteInterpreterProcess process = group.getInterpreterProcess();
    this.started = process != null;
    if (started) {
      this.host = process.getHost();
      this.port = process.getPort();
      this.startTime = process.getStartTime();
      this.uptimeSeconds = (System.currentTimeMillis() - process.getStartTimeMs()) / 1000;
      this.errorMessage = process.getErrorMessage();
    }
  }

  public String getSettingId() {
    return settingId;
  }

  public String getSettingName() {
    return settingName;
  }

  public String getGroupId() {
    return groupId;
  }

  public int getNumSessions() {
    return numSessions;
  }

  public boolean isStarted() {
    return started;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public String getStartTime() {
    return startTime;
  }

  public long getUptimeSeconds() {
    return uptimeSeconds;
  }

  public String getErrorMessage() {
    return errorMessage;
  }
}
