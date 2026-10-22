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

import org.apache.zeppelin.interpreter.InterpreterHealthCheck.GroupHealth;
import org.apache.zeppelin.interpreter.InterpreterHealthCheck.Reason;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the on demand health check, above all that it stays within its deadline and that it never
 * starts an interpreter to answer.
 *
 * <p>The deadline is what makes probing acceptable here at all: {@code isRunning()} has no timeout
 * of its own, so without it a single unreachable interpreter would hold the request for as long as
 * the launcher underneath takes to give up.
 */
class InterpreterHealthCheckerTest {

  /** Short enough to keep the tests quick, long enough that a local probe finishes well inside. */
  private static final long PROBE_TIMEOUT_IN_MILLIS = 500;

  private InterpreterHealthChecker healthChecker;

  @AfterEach
  void tearDown() {
    if (healthChecker != null) {
      healthChecker.stop();
    }
  }

  /**
   * A setting whose interpreters are not running is the normal state right after a restart, so it
   * is reported as such - and answering must not be what starts them.
   */
  @Test
  void reportsNotRunningWithoutStartingAnInterpreter() throws Exception {
    ManagedInterpreterGroup interpreterGroup = interpreterGroup("group-1", null);
    InterpreterSetting interpreterSetting =
        interpreterSetting(Collections.singletonList(interpreterGroup));
    healthChecker = new InterpreterHealthChecker(PROBE_TIMEOUT_IN_MILLIS);

    InterpreterHealthCheck healthCheck = healthChecker.check(interpreterSetting);

    assertEquals(1, healthCheck.getGroups().size());
    GroupHealth groupHealth = healthCheck.getGroups().get(0);
    assertEquals(Reason.NOT_RUNNING, groupHealth.getReason());
    assertFalse(groupHealth.isHealthy(), "a group without a process cannot be healthy");
    assertNull(groupHealth.getAlive(), "nothing was probed, so nothing is known about the process");
    assertNull(groupHealth.getRunning());
    verify(interpreterGroup, never()).getOrCreateInterpreterProcess(anyString(), any());
  }

  /** A setting without any group at all reports on the setting rather than on groups. */
  @Test
  void reportsNotRunningWhenThereIsNoGroup() {
    InterpreterSetting interpreterSetting = interpreterSetting(Collections.emptyList());
    healthChecker = new InterpreterHealthChecker(PROBE_TIMEOUT_IN_MILLIS);

    InterpreterHealthCheck healthCheck = healthChecker.check(interpreterSetting);

    assertEquals(Reason.NOT_RUNNING, healthCheck.getReason());
    assertTrue(healthCheck.getGroups().isEmpty());
  }

  /** A reachable interpreter is healthy, and saying so takes a single remote call. */
  @Test
  void reportsHealthyOnASingleRemoteCall() {
    RemoteInterpreterProcess process = mock(RemoteInterpreterProcess.class);
    when(process.isRunning()).thenReturn(true);
    InterpreterSetting interpreterSetting = interpreterSetting(
        Collections.singletonList(interpreterGroup("group-1", process)));
    healthChecker = new InterpreterHealthChecker(PROBE_TIMEOUT_IN_MILLIS);

    GroupHealth groupHealth = healthChecker.check(interpreterSetting).getGroups().get(0);

    assertEquals(Reason.OK, groupHealth.getReason());
    assertTrue(groupHealth.isHealthy());
    assertEquals(Boolean.TRUE, groupHealth.getAlive());
    // Asking whether the process exists would be another remote call for no added answer.
    verify(process, never()).isAlive();
  }

  /**
   * The point of the whole exercise: a probe that does not come back must not hold the request. The
   * interpreter here never answers, which is what an unreachable host looks like.
   */
  @Test
  void stopsWaitingForAProbeThatDoesNotComeBack() {
    RemoteInterpreterProcess process = mock(RemoteInterpreterProcess.class);
    when(process.isRunning()).thenAnswer(invocation -> {
      Thread.sleep(60_000);
      return true;
    });
    InterpreterSetting interpreterSetting = interpreterSetting(
        Collections.singletonList(interpreterGroup("group-1", process)));
    healthChecker = new InterpreterHealthChecker(PROBE_TIMEOUT_IN_MILLIS);

    long startTimeInMillis = System.currentTimeMillis();
    GroupHealth groupHealth = healthChecker.check(interpreterSetting).getGroups().get(0);
    long tookInMillis = System.currentTimeMillis() - startTimeInMillis;

    assertEquals(Reason.PROBE_TIMEOUT, groupHealth.getReason());
    assertNull(groupHealth.getAlive(), "the probe never answered, so nothing is known");
    assertTrue(tookInMillis < PROBE_TIMEOUT_IN_MILLIS * 10,
        "the request should end on the deadline rather than with the probe, but took "
            + tookInMillis + "ms");
  }

  /**
   * The deadline belongs to the request, so the groups are probed together: one interpreter that
   * does not answer must not cost the others their result.
   */
  @Test
  void oneUnreachableGroupDoesNotHideTheOthers() {
    RemoteInterpreterProcess reachable = mock(RemoteInterpreterProcess.class);
    when(reachable.isRunning()).thenReturn(true);
    RemoteInterpreterProcess unreachable = mock(RemoteInterpreterProcess.class);
    when(unreachable.isRunning()).thenAnswer(invocation -> {
      Thread.sleep(60_000);
      return true;
    });
    InterpreterSetting interpreterSetting = interpreterSetting(Arrays.asList(
        interpreterGroup("group-unreachable", unreachable),
        interpreterGroup("group-reachable", reachable)));
    healthChecker = new InterpreterHealthChecker(PROBE_TIMEOUT_IN_MILLIS);

    List<GroupHealth> groupHealths = healthChecker.check(interpreterSetting).getGroups();

    assertEquals(2, groupHealths.size());
    // Reported in the order of the groups, whether an entry had to wait for the deadline or not.
    assertEquals("group-unreachable", groupHealths.get(0).getGroupId());
    assertEquals(Reason.PROBE_TIMEOUT, groupHealths.get(0).getReason());
    assertEquals("group-reachable", groupHealths.get(1).getGroupId());
    assertEquals(Reason.OK, groupHealths.get(1).getReason());
  }

  /** A group whose process is still being launched is not broken, and is not probed either. */
  @Test
  void reportsLaunchingWithoutProbing() {
    RemoteInterpreterProcess process = mock(RemoteInterpreterProcess.class);
    ManagedInterpreterGroup interpreterGroup = interpreterGroup("group-1", process);
    when(interpreterGroup.isLaunchingInterpreterProcess()).thenReturn(true);
    InterpreterSetting interpreterSetting =
        interpreterSetting(Collections.singletonList(interpreterGroup));
    healthChecker = new InterpreterHealthChecker(PROBE_TIMEOUT_IN_MILLIS);

    GroupHealth groupHealth = healthChecker.check(interpreterSetting).getGroups().get(0);

    assertEquals(Reason.LAUNCHING, groupHealth.getReason());
    verify(process, never()).isRunning();
  }

  /** A probe that throws says the interpreter could not be reached, not that the request failed. */
  @Test
  void reportsAFailingProbeAsUnreachable() {
    RemoteInterpreterProcess process = mock(RemoteInterpreterProcess.class);
    when(process.isRunning()).thenThrow(new RuntimeException("boom"));
    InterpreterSetting interpreterSetting = interpreterSetting(
        Collections.singletonList(interpreterGroup("group-1", process)));
    healthChecker = new InterpreterHealthChecker(PROBE_TIMEOUT_IN_MILLIS);

    GroupHealth groupHealth = healthChecker.check(interpreterSetting).getGroups().get(0);

    assertEquals(Reason.NOT_REACHABLE, groupHealth.getReason());
    assertNull(groupHealth.getRunning(), "the probe failed, so the process state is unknown");
  }

  private static InterpreterSetting interpreterSetting(List<ManagedInterpreterGroup> groups) {
    InterpreterSetting interpreterSetting = mock(InterpreterSetting.class);
    when(interpreterSetting.getId()).thenReturn("setting-1");
    when(interpreterSetting.getName()).thenReturn("test");
    when(interpreterSetting.getAllInterpreterGroups()).thenReturn(groups);
    return interpreterSetting;
  }

  private static ManagedInterpreterGroup interpreterGroup(String groupId,
                                                          RemoteInterpreterProcess process) {
    ManagedInterpreterGroup interpreterGroup = mock(ManagedInterpreterGroup.class);
    when(interpreterGroup.getId()).thenReturn(groupId);
    when(interpreterGroup.getInterpreterProcess()).thenReturn(process);
    return interpreterGroup;
  }
}
