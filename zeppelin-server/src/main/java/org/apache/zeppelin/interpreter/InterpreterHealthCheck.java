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

import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess.CheckMethod;

import java.util.Collections;
import java.util.List;

/**
 * Result of a user triggered health check of a single interpreter setting.
 *
 * <p>A setting owns zero or more {@link ManagedInterpreterGroup}s, one per interpreter process, so
 * the result reports an entry per group instead of one verdict for the setting.
 *
 * <p>{@code healthy} follows {@code InterpreterClient#isRunning()}. That call is documented as "the
 * interpreter can communicate with server", but no launcher asks the interpreter: each one looks at
 * its own state, from a process launcher flag to a Kubernetes Pod phase. {@code healthy} therefore
 * means the launcher reports the interpreter as running, not that the interpreter replied, and
 * {@code checkMethod} says what was looked at.
 *
 * <p>{@code alive}, {@code running} and {@code probeTookMs} are {@code null} whenever no probe was
 * performed, so that "probed and found dead" does not read the same as "never probed".
 */
public class InterpreterHealthCheck {

  /**
   * Why a group is, or is not, healthy. Only {@link #OK} means healthy; the remaining reasons are
   * not equally bad, and a client is expected to tell them apart rather than to treat every one of
   * them as a failure.
   */
  public enum Reason {
    /** The launcher reports the interpreter as running. */
    OK,
    /** A process handle exists, but the launcher reports it as not running or could not tell. */
    NOT_REACHABLE,
    /** The probe started but did not finish within the budget of this health check. */
    PROBE_TIMEOUT,
    /**
     * The probe never started, because every probe thread was busy and the queue was full or the
     * budget ran out first. Nothing was checked, so this says nothing about the interpreter.
     */
    PROBE_UNAVAILABLE,
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
    private final CheckMethod checkMethod;
    private final Boolean alive;
    private final Boolean running;
    private final Long probeTookMs;

    private GroupHealth(String groupId, Reason reason, CheckMethod checkMethod, Boolean alive,
                        Boolean running, Long probeTookMs) {
      this.groupId = groupId;
      this.reason = reason;
      this.checkMethod = checkMethod;
      this.alive = alive;
      this.running = running;
      this.probeTookMs = probeTookMs;
      this.healthy = Boolean.TRUE.equals(running);
    }

    /** The probe came back, and what the launcher reported decides the reason. */
    public static GroupHealth probed(String groupId, CheckMethod checkMethod, boolean alive,
                                     boolean running, long probeTookMs) {
      return new GroupHealth(groupId, running ? Reason.OK : Reason.NOT_REACHABLE, checkMethod,
          alive, running, probeTookMs);
    }

    /**
     * The probe did not come back within the budget. Nothing is known about the process, hence no
     * {@code alive} or {@code running}.
     */
    public static GroupHealth timedOut(String groupId, CheckMethod checkMethod,
                                       long probeTookMs) {
      return new GroupHealth(groupId, Reason.PROBE_TIMEOUT, checkMethod, null, null,
          probeTookMs);
    }

    /**
     * The probe itself failed, which says the interpreter could not be reached but leaves the state
     * of the process unknown, hence no {@code alive} or {@code running}.
     */
    public static GroupHealth probeFailed(String groupId, CheckMethod checkMethod,
                                          long probeTookMs) {
      return new GroupHealth(groupId, Reason.NOT_REACHABLE, checkMethod, null, null,
          probeTookMs);
    }

    /**
     * The probe never got a thread to run on, so nothing was checked and no time was spent on the
     * process; reporting a timeout here would blame an interpreter that was never asked.
     */
    public static GroupHealth unavailable(String groupId, CheckMethod checkMethod) {
      return new GroupHealth(groupId, Reason.PROBE_UNAVAILABLE, checkMethod, null, null, null);
    }

    /** A process is being launched for this group, so it is deliberately left unprobed. */
    public static GroupHealth launching(String groupId) {
      return new GroupHealth(groupId, Reason.LAUNCHING, null, null, null, null);
    }

    /** The group carries no process handle, so there is nothing to probe. */
    public static GroupHealth notRunning(String groupId) {
      return new GroupHealth(groupId, Reason.NOT_RUNNING, null, null, null, null);
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

    public CheckMethod getCheckMethod() {
      return checkMethod;
    }

    public Boolean getAlive() {
      return alive;
    }

    public Boolean getRunning() {
      return running;
    }

    public Long getProbeTookMs() {
      return probeTookMs;
    }
  }
}
