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

import com.google.common.annotations.VisibleForTesting;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.scheduler.ExecutorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Closes interpreter groups that have been idle for longer than a threshold, driven by Zeppelin
 * server rather than by the interpreter process itself.
 *
 * <p>{@link TimeoutLifecycleManager} does the same thing from inside the interpreter process, where
 * the threshold can only arrive through the configuration map pushed over Thrift at startup. That
 * map holds {@link ConfVars} entries only, so an interpreter setting property never reaches it and
 * every process gets the same global threshold. Deciding here means the threshold of the owning
 * interpreter setting can just be read.
 *
 * <p>Follows {@code zeppelin.interpreter.lifecyclemanager.class}, which already says whether idle
 * reclaim is wanted: its {@link NullLifecycleManager} default leaves a deployment untouched, and
 * {@link TimeoutLifecycleManager} enables this. Any other implementation is left alone. The
 * in-process manager stays as a fallback for a server that went away and is given the same resolved
 * threshold by {@link #processConfigurationOverrides}.
 */
public class IdleInterpreterReclaimer {

  private static final Logger LOGGER = LoggerFactory.getLogger(IdleInterpreterReclaimer.class);

  private static final String SCHEDULER_NAME = "IdleInterpreterReclaimer";

  /**
   * Threshold property. On an interpreter setting, {@code 0} or below means never reclaimed.
   */
  public static final String IDLE_TIMEOUT_THRESHOLD_PROPERTY =
      ConfVars.ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_TIMEOUT_THRESHOLD.getVarName();

  private final ZeppelinConfiguration zConf;
  private final InterpreterSettingManager interpreterSettingManager;

  private ScheduledExecutorService checkScheduler;

  public IdleInterpreterReclaimer(ZeppelinConfiguration zConf,
                                  InterpreterSettingManager interpreterSettingManager) {
    this.zConf = zConf;
    this.interpreterSettingManager = interpreterSettingManager;
  }

  private static boolean isEnabled(ZeppelinConfiguration zConf) {
    return TimeoutLifecycleManager.class.getName().equals(zConf.getLifecycleManagerClass());
  }

  public void start() {
    if (!isEnabled(zConf)) {
      LOGGER.debug("Server driven idle interpreter reclaim is off, {} is {}",
          ConfVars.ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_CLASS.getVarName(),
          zConf.getLifecycleManagerClass());
      return;
    }
    long checkInterval = zConf.getInterpreterIdleCheckInterval();
    if (checkInterval <= 0) {
      LOGGER.warn("Not starting idle interpreter reclaim: {} must be positive but is {}",
          ConfVars.ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_TIMEOUT_CHECK_INTERVAL.getVarName(),
          checkInterval);
      return;
    }
    checkScheduler = ExecutorFactory.singleton().createOrGetScheduled(SCHEDULER_NAME, 1);
    // Fixed delay rather than fixed rate, so that a slow close does not queue up further checks.
    checkScheduler.scheduleWithFixedDelay(this::reclaimIdleInterpreterGroups,
        checkInterval, checkInterval, MILLISECONDS);
    LOGGER.info("Server driven idle interpreter reclaim started with checkInterval: {}ms, "
        + "default threshold: {}ms", checkInterval, zConf.getInterpreterIdleTimeoutThreshold());
  }

  public void stop() {
    if (checkScheduler != null) {
      ExecutorFactory.singleton().shutdown(SCHEDULER_NAME);
      checkScheduler = null;
      LOGGER.info("Server driven idle interpreter reclaim stopped");
    }
  }

  /**
   * Closes every interpreter group idle for longer than the threshold of its interpreter setting.
   * Uses in-memory state only: {@code isAlive()} and {@code isRunning()} cost a socket connect for
   * docker and a kube-apiserver round trip for k8s, and this walks every group on a timer.
   */
  @VisibleForTesting
  void reclaimIdleInterpreterGroups() {
    long now = System.currentTimeMillis();
    for (ManagedInterpreterGroup interpreterGroup :
        interpreterSettingManager.getAllInterpreterGroup()) {
      try {
        reclaimIfIdle(interpreterGroup, now);
      } catch (Exception e) {
        LOGGER.error("Fail to reclaim idle interpreter group: {}", interpreterGroup.getId(), e);
      }
    }
  }

  private void reclaimIfIdle(ManagedInterpreterGroup interpreterGroup, long now) {
    if (interpreterGroup.isLaunchingInterpreterProcess()) {
      // The handle is published before the process is ready, and a launch can outlast the
      // threshold, so this would close a process that is starting rather than an idle one.
      return;
    }
    if (interpreterGroup.getInterpreterProcess() == null) {
      // Like TimeoutLifecycleManager, only manage a group once its process has started.
      return;
    }
    if (interpreterGroup.isEmpty()) {
      // No session left: the group is already on its way out through close().
      return;
    }

    InterpreterSetting interpreterSetting = interpreterGroup.getInterpreterSetting();
    long threshold = getIdleTimeoutThreshold(zConf, interpreterSetting);
    if (threshold <= 0) {
      LOGGER.debug("Interpreter group {} is never reclaimed, its threshold is {}ms",
          interpreterGroup.getId(), threshold);
      return;
    }

    long idleTimeInMillis = now - interpreterGroup.getLastUsedTimeInMillis();
    if (idleTimeInMillis <= threshold) {
      return;
    }

    LOGGER.info("Reclaiming interpreter group {} of interpreter setting {}: idle for {}ms which "
            + "exceeds its threshold of {}ms", interpreterGroup.getId(),
        interpreterSetting == null ? "?" : interpreterSetting.getName(),
        idleTimeInMillis, threshold);
    interpreterGroup.close();
  }

  /**
   * @return idle threshold in milliseconds for the given interpreter setting, taking its own
   *         {@link #IDLE_TIMEOUT_THRESHOLD_PROPERTY} property over the global configuration
   */
  @VisibleForTesting
  static long getIdleTimeoutThreshold(ZeppelinConfiguration zConf,
                                      InterpreterSetting interpreterSetting) {
    if (interpreterSetting != null) {
      String override =
          interpreterSetting.getJavaProperties().getProperty(IDLE_TIMEOUT_THRESHOLD_PROPERTY);
      if (StringUtils.isNotBlank(override)) {
        try {
          return ZeppelinConfiguration.parseTimeMillis(override);
        } catch (RuntimeException e) {
          LOGGER.warn("Ignoring unparsable {} of interpreter setting {}: {}",
              IDLE_TIMEOUT_THRESHOLD_PROPERTY, interpreterSetting.getName(), override, e);
        }
      }
    }
    return zConf.getInterpreterIdleTimeoutThreshold();
  }

  /**
   * Gives the in-process {@link TimeoutLifecycleManager} fallback the threshold resolved here
   * instead of the global one. A setting that opted out gets {@link Long#MAX_VALUE} rather than its
   * own {@code 0}, which {@link TimeoutLifecycleManager} would read as "shut down at the next
   * check" since it has no way to express "never".
   *
   * @return entries to put on top of {@link ZeppelinConfiguration#getCompleteConfiguration()},
   *         empty when server driven reclaim is off
   */
  public static Map<String, String> processConfigurationOverrides(
      ZeppelinConfiguration zConf, InterpreterSetting interpreterSetting) {
    if (!isEnabled(zConf)) {
      return Collections.emptyMap();
    }
    long threshold = getIdleTimeoutThreshold(zConf, interpreterSetting);
    return Collections.singletonMap(IDLE_TIMEOUT_THRESHOLD_PROPERTY,
        String.valueOf(threshold <= 0 ? Long.MAX_VALUE : threshold));
  }
}
