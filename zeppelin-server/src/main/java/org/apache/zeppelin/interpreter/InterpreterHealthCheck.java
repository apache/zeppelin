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

import java.util.Collections;
import java.util.List;

/**
 * Result of a user triggered health check of a single interpreter setting.
 *
 * <p>A setting owns zero or more {@link ManagedInterpreterGroup}s, one per interpreter process, so
 * the result reports an entry per group instead of one verdict for the setting.
 *
 * <p>{@code healthy} follows {@code InterpreterClient#isRunning()}, documented as "the interpreter
 * can communicate with server", while {@code isAlive()} only states that a process exists. Both are
 * reported so that a process which is up but no longer answering stays distinguishable from one
 * that is gone.
 *
 * <p>{@code alive} and {@code running} are {@code null} whenever no probe was performed, so that
 * "probed and found dead" does not read the same as "never probed".
 */
public class InterpreterHealthCheck {

  /**
   * Why a group is, or is not, healthy. Only {@link #OK} means healthy; the remaining reasons are
   * not equally bad, and a client is expected to tell them apart rather than to treat every one of
   * them as a failure.
   */
  public enum Reason {
    /** The interpreter answered the probe. */
    OK,
    /** A process handle exists, but the interpreter did not answer. */
    NOT_REACHABLE,
    /** The probe did not finish within the budget of this health check. */
    PROBE_TIMEOUT,
    /** A process is currently being launched for this group, which is not a failure. */
    LAUNCHING,
    /** No process is running, which is the normal state right after a restart. */
    NOT_RUNNING
  }

  private final String settingId;
  private final String settingName;
  private final Reason reason;
  private final List<GroupHealth> groups;

  private InterpreterHealthCheck(InterpreterSetting setting, Reason reason,
                                 List<GroupHealth> groups) {
    this.settingId = setting.getId();
    this.settingName = setting.getName();
    this.reason = reason;
    this.groups = groups;
  }

  /**
   * A setting that holds no interpreter group at all. Restarting a setting only closes its
   * processes and the next use lazily creates them again, so this is the expected state right after
   * a restart rather than something being wrong.
   */
  public static InterpreterHealthCheck notRunning(InterpreterSetting setting) {
    return new InterpreterHealthCheck(setting, Reason.NOT_RUNNING, Collections.emptyList());
  }

  /**
   * A setting with groups to report. The reason lives on each group, so no setting wide reason is
   * set here.
   */
  public static InterpreterHealthCheck of(InterpreterSetting setting, List<GroupHealth> groups) {
    return new InterpreterHealthCheck(setting, null, groups);
  }

  public String getSettingId() {
    return settingId;
  }

  public String getSettingName() {
    return settingName;
  }

  public Reason getReason() {
    return reason;
  }

  public List<GroupHealth> getGroups() {
    return groups;
  }

  /**
   * Health of one interpreter group, which is to say of one interpreter process.
   *
   * <p>Instances are created through the factory methods so that {@code healthy} is always derived
   * from the same probe result the reason was derived from, and the two can never disagree.
   */
  public static class GroupHealth {

    private final String groupId;
    private final boolean healthy;
    private final Reason reason;
    private final Boolean alive;
    private final Boolean running;
    private final long probeTookMs;

    private GroupHealth(String groupId, Reason reason, Boolean alive, Boolean running,
                        long probeTookMs) {
      this.groupId = groupId;
      this.reason = reason;
      this.alive = alive;
      this.running = running;
      this.probeTookMs = probeTookMs;
      this.healthy = Boolean.TRUE.equals(running);
    }

    /** The probe came back, and whether the interpreter answered decides the reason. */
    public static GroupHealth probed(String groupId, boolean alive, boolean running,
                                     long probeTookMs) {
      return new GroupHealth(groupId, running ? Reason.OK : Reason.NOT_REACHABLE,
          alive, running, probeTookMs);
    }

    /**
     * The probe did not come back within the budget. Nothing is known about the process, hence no
     * {@code alive} or {@code running}.
     */
    public static GroupHealth timedOut(String groupId, long probeTookMs) {
      return new GroupHealth(groupId, Reason.PROBE_TIMEOUT, null, null, probeTookMs);
    }

    /**
     * The probe itself failed, which says the interpreter could not be reached but leaves the state
     * of the process unknown, hence no {@code alive} or {@code running}.
     */
    public static GroupHealth probeFailed(String groupId, long probeTookMs) {
      return new GroupHealth(groupId, Reason.NOT_REACHABLE, null, null, probeTookMs);
    }

    /** A process is being launched for this group, so it is deliberately left unprobed. */
    public static GroupHealth launching(String groupId) {
      return new GroupHealth(groupId, Reason.LAUNCHING, null, null, 0);
    }

    /** The group carries no process handle, so there is nothing to probe. */
    public static GroupHealth notRunning(String groupId) {
      return new GroupHealth(groupId, Reason.NOT_RUNNING, null, null, 0);
    }

    public String getGroupId() {
      return groupId;
    }

    public boolean isHealthy() {
      return healthy;
    }

    public Reason getReason() {
      return reason;
    }

    public Boolean getAlive() {
      return alive;
    }

    public Boolean getRunning() {
      return running;
    }

    public long getProbeTookMs() {
      return probeTookMs;
    }
  }
}
