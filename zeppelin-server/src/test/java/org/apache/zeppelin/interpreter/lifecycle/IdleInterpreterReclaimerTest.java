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

package org.apache.zeppelin.interpreter.lifecycle;

import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.interpreter.AbstractInterpreterTest;
import org.apache.zeppelin.interpreter.ExecutionContext;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.scheduler.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests server driven idle reclaim, above all that an interpreter setting can override the global
 * threshold in either direction. That override is what the interpreter process side
 * {@link TimeoutLifecycleManager} cannot offer, because its threshold only reaches the process
 * through the global configuration map.
 */
class IdleInterpreterReclaimerTest extends AbstractInterpreterTest {

  private static final String THRESHOLD_PROPERTY =
      IdleInterpreterReclaimer.IDLE_TIMEOUT_THRESHOLD_PROPERTY;

  @Override
  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    zConf.setProperty(ConfVars.ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_CLASS.getVarName(),
        TimeoutLifecycleManager.class.getName());
    zConf.setProperty(
        ConfVars.ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_TIMEOUT_CHECK_INTERVAL.getVarName(),
        "1000");
    // The reclaimer picks these up when it starts, and that already happened while
    // super.setUp() built the InterpreterSettingManager, so restart it.
    interpreterSettingManager.getIdleInterpreterReclaimer().stop();
    interpreterSettingManager.getIdleInterpreterReclaimer().start();
  }

  /**
   * A setting may ask to be reclaimed sooner than the global threshold allows. The global
   * threshold stays at its 1h default here, so only the per setting value of 10s can close it.
   */
  @Test
  void perSettingThresholdReclaimsEarlierThanTheGlobalOne() throws Exception {
    InterpreterSetting interpreterSetting =
        interpreterSettingManager.getInterpreterSettingByName("test");
    interpreterSetting.setProperty(THRESHOLD_PROPERTY, "10s");

    startEchoInterpreter();
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());

    waitForInterpreterGroups(interpreterSetting, 0, 40);
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size(),
        "the group should be reclaimed after the per setting threshold of 10s");
  }

  /**
   * The other direction: a non positive per setting threshold means keep it, whatever the short
   * global threshold says.
   */
  @Test
  void perSettingThresholdCanOptOutOfAShortGlobalThreshold() throws Exception {
    zConf.setProperty(
        ConfVars.ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_TIMEOUT_THRESHOLD.getVarName(), "5s");

    InterpreterSetting interpreterSetting =
        interpreterSettingManager.getInterpreterSettingByName("test");
    interpreterSetting.setProperty(THRESHOLD_PROPERTY, "0");

    startEchoInterpreter();
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());

    Thread.sleep(20 * 1000);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size(),
        "the setting opted out of reclaim, so the short global threshold must not apply");
  }

  @Test
  void globalThresholdAppliesWhenTheSettingDoesNotOverrideIt() throws Exception {
    zConf.setProperty(
        ConfVars.ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_TIMEOUT_THRESHOLD.getVarName(), "10s");

    InterpreterSetting interpreterSetting =
        interpreterSettingManager.getInterpreterSettingByName("test");

    startEchoInterpreter();
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());

    waitForInterpreterGroups(interpreterSetting, 0, 40);
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }

  /**
   * A paragraph running for longer than the threshold must not have its interpreter pulled out
   * from under it. While a job runs the server polls its status, which counts as use.
   */
  @Test
  void aRunningParagraphKeepsItsInterpreterAlive() throws Exception {
    zConf.setProperty(
        ConfVars.ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_TIMEOUT_THRESHOLD.getVarName(), "5s");

    InterpreterSetting interpreterSetting =
        interpreterSettingManager.getInterpreterSettingByName("test");
    final RemoteInterpreter sleepInterpreter =
        (RemoteInterpreter) interpreterFactory.getInterpreter("test.sleep",
            new ExecutionContext("user1", "note1", "test"));

    // Submit through the scheduler the way Zeppelin submits a paragraph, so that the job status
    // poller runs.
    sleepInterpreter.getScheduler().submit(new Job<Object>("test-job", null) {
      @Override
      public Object getReturn() {
        return null;
      }

      @Override
      public int progress() {
        return 0;
      }

      @Override
      public Map<String, Object> info() {
        return null;
      }

      @Override
      protected Object jobRun() throws Throwable {
        return sleepInterpreter.interpret("30000", createDummyInterpreterContext());
      }

      @Override
      protected boolean jobAbort() {
        return false;
      }

      @Override
      public void setResult(Object results) {
      }
    });

    long deadline = System.currentTimeMillis() + 30 * 1000;
    while (!sleepInterpreter.isOpened() && System.currentTimeMillis() < deadline) {
      Thread.sleep(500);
    }
    assertTrue(sleepInterpreter.isOpened(), "interpreter did not start");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());

    Thread.sleep(20 * 1000);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size(),
        "a running paragraph must keep its interpreter group alive");
  }

  /**
   * A probe is cheap for the local launcher but not for docker or k8s, and this scan walks every
   * group on a timer.
   */
  @Test
  void scanNeverProbesTheInterpreterProcess() {
    RemoteInterpreterProcess process = mock(RemoteInterpreterProcess.class);

    InterpreterSetting interpreterSetting = mock(InterpreterSetting.class);
    when(interpreterSetting.getName()).thenReturn("probe-guard");
    when(interpreterSetting.getJavaProperties()).thenReturn(new Properties());

    ManagedInterpreterGroup interpreterGroup = mock(ManagedInterpreterGroup.class);
    when(interpreterGroup.getId()).thenReturn("probe-guard-shared_process");
    when(interpreterGroup.getInterpreterProcess()).thenReturn(process);
    when(interpreterGroup.getInterpreterSetting()).thenReturn(interpreterSetting);
    when(interpreterGroup.isEmpty()).thenReturn(false);
    // Idle since the epoch, so it is well past any threshold and does get closed.
    when(interpreterGroup.getLastUsedTimeInMillis()).thenReturn(0L);

    InterpreterSettingManager settingManager = mock(InterpreterSettingManager.class);
    when(settingManager.getAllInterpreterGroup())
        .thenReturn(Collections.singletonList(interpreterGroup));

    new IdleInterpreterReclaimer(zConf, settingManager).reclaimIdleInterpreterGroups();

    verify(interpreterGroup).close();
    verify(process, never()).isAlive();
    verify(process, never()).isRunning();
  }

  /**
   * The handle is published before the process is ready and the group has been idle since it was
   * created, so without the launching check the scan closes a process that is starting up.
   */
  @Test
  void aGroupBeingLaunchedIsNotReclaimed() {
    ManagedInterpreterGroup interpreterGroup = mock(ManagedInterpreterGroup.class);
    when(interpreterGroup.getId()).thenReturn("launching-shared_process");
    when(interpreterGroup.isLaunchingInterpreterProcess()).thenReturn(true);
    when(interpreterGroup.getInterpreterProcess())
        .thenReturn(mock(RemoteInterpreterProcess.class));
    when(interpreterGroup.isEmpty()).thenReturn(false);
    when(interpreterGroup.getLastUsedTimeInMillis()).thenReturn(0L);

    InterpreterSettingManager settingManager = mock(InterpreterSettingManager.class);
    when(settingManager.getAllInterpreterGroup())
        .thenReturn(Collections.singletonList(interpreterGroup));

    new IdleInterpreterReclaimer(zConf, settingManager).reclaimIdleInterpreterGroups();

    verify(interpreterGroup, never()).close();
  }

  @Test
  void thresholdResolutionPrefersTheSettingAndFallsBackOnGarbage() {
    zConf.setProperty(
        ConfVars.ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_TIMEOUT_THRESHOLD.getVarName(), "1h");

    assertEquals(3600000L, IdleInterpreterReclaimer.getIdleTimeoutThreshold(zConf, null),
        "no setting at all means the global threshold");

    InterpreterSetting interpreterSetting = mock(InterpreterSetting.class);
    when(interpreterSetting.getName()).thenReturn("threshold-resolution");

    when(interpreterSetting.getJavaProperties()).thenReturn(thresholdProperties(null));
    assertEquals(3600000L,
        IdleInterpreterReclaimer.getIdleTimeoutThreshold(zConf, interpreterSetting),
        "no override means the global threshold");

    when(interpreterSetting.getJavaProperties()).thenReturn(thresholdProperties("10s"));
    assertEquals(10000L,
        IdleInterpreterReclaimer.getIdleTimeoutThreshold(zConf, interpreterSetting));

    when(interpreterSetting.getJavaProperties()).thenReturn(thresholdProperties("600000"));
    assertEquals(600000L,
        IdleInterpreterReclaimer.getIdleTimeoutThreshold(zConf, interpreterSetting),
        "a plain number is milliseconds");

    when(interpreterSetting.getJavaProperties()).thenReturn(thresholdProperties("0"));
    assertEquals(0L,
        IdleInterpreterReclaimer.getIdleTimeoutThreshold(zConf, interpreterSetting),
        "zero opts the setting out of reclaim");

    when(interpreterSetting.getJavaProperties()).thenReturn(thresholdProperties("not-a-duration"));
    assertEquals(3600000L,
        IdleInterpreterReclaimer.getIdleTimeoutThreshold(zConf, interpreterSetting),
        "an unparsable override must fall back to the global threshold");
  }

  /**
   * A setting that opted out must not be shut down by the in-process fallback either. Its own
   * {@code 0} would mean "shut down at the next check" there, so it never reaches the process.
   */
  @Test
  void optingOutDisablesTheInProcessFallbackToo() {
    InterpreterSetting interpreterSetting = mock(InterpreterSetting.class);
    when(interpreterSetting.getName()).thenReturn("opt-out");
    when(interpreterSetting.getJavaProperties()).thenReturn(thresholdProperties("0"));

    Map<String, String> overrides =
        IdleInterpreterReclaimer.processConfigurationOverrides(zConf, interpreterSetting);
    assertEquals(String.valueOf(Long.MAX_VALUE), overrides.get(THRESHOLD_PROPERTY));
    assertNull(overrides.get(ConfVars.ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_CLASS.getVarName()),
        "the lifecycle manager the operator configured must be left alone");

    when(interpreterSetting.getJavaProperties()).thenReturn(thresholdProperties("10s"));
    overrides = IdleInterpreterReclaimer.processConfigurationOverrides(zConf, interpreterSetting);
    assertEquals("10000", overrides.get(THRESHOLD_PROPERTY),
        "the process gets the resolved threshold, not the global one");
  }

  /**
   * With the default lifecycle manager nothing is reclaimed and nothing is overridden, so an
   * existing deployment is untouched.
   */
  @Test
  void defaultLifecycleManagerLeavesEverythingAlone() {
    zConf.setProperty(ConfVars.ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_CLASS.getVarName(),
        NullLifecycleManager.class.getName());

    InterpreterSetting interpreterSetting = mock(InterpreterSetting.class);
    when(interpreterSetting.getJavaProperties()).thenReturn(thresholdProperties("10s"));

    assertTrue(IdleInterpreterReclaimer.processConfigurationOverrides(zConf, interpreterSetting)
        .isEmpty());
  }

  private Properties thresholdProperties(String threshold) {
    Properties properties = new Properties();
    if (threshold != null) {
      properties.setProperty(THRESHOLD_PROPERTY, threshold);
    }
    return properties;
  }

  private void startEchoInterpreter() throws Exception {
    RemoteInterpreter echoInterpreter =
        (RemoteInterpreter) interpreterFactory.getInterpreter("test.echo",
            new ExecutionContext("user1", "note1", "test"));
    echoInterpreter.interpret("hello", createDummyInterpreterContext());
    assertTrue(echoInterpreter.isOpened());
  }

  private void waitForInterpreterGroups(InterpreterSetting interpreterSetting,
                                        int expectedSize,
                                        int maxSeconds) throws Exception {
    long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
    while (interpreterSetting.getAllInterpreterGroups().size() != expectedSize
        && System.currentTimeMillis() < deadline) {
      Thread.sleep(1000);
    }
  }
}
