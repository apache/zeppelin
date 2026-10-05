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
 *
 * <p>Every value below must be readable without leaving the JVM. In particular do not call
 * {@code isRunning()}, {@code isAlive()} or {@code getErrorMessage()} on the process from here:
 * those are failure-path diagnostics that contact the container runtime on some launchers, so
 * calling them would let a slow or unreachable runtime block this endpoint.
 *
 * <p>{@code started} and {@code launching} are read one after the other rather than under the
 * lock that guards a launch, so this is a best-effort view of a group that is starting up: the
 * pair can straddle the moment a launch finishes. What it does buy is that the window in which
 * a handle carries no {@code host} or {@code port} yet is reported as such instead of looking
 * like a fully started process.
 */
public class InterpreterProcessStatus {
  private final String settingId;
  private final String settingName;
  private final String groupId;
  private final int numSessions;
  private final boolean launching;
  private final boolean started;
  private String host;
  private int port = -1;
  private String startTime;
  private long attachedForSeconds;

  public InterpreterProcessStatus(ManagedInterpreterGroup group) {
    InterpreterSetting setting = group.getInterpreterSetting();
    this.settingId = setting.getId();
    this.settingName = setting.getName();
    this.groupId = group.getId();
    this.numSessions = group.getSessionNum();
    this.launching = group.isLaunchingInterpreterProcess();
    RemoteInterpreterProcess process = group.getInterpreterProcess();
    this.started = process != null;
    if (started) {
      this.host = process.getHost();
      this.port = process.getPort();
      this.startTime = process.getStartTime();
      this.attachedForSeconds = (System.currentTimeMillis() - process.getStartTimeMs()) / 1000;
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

  /**
   * @return whether a process is currently being launched for this group, in which case
   *         {@code host} and {@code port} may not be filled in yet even when {@code started}
   */
  public boolean isLaunching() {
    return launching;
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

  public long getAttachedForSeconds() {
    return attachedForSeconds;
  }
}
