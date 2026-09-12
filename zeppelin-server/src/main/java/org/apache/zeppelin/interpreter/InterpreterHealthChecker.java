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

import com.google.common.annotations.VisibleForTesting;

import org.apache.zeppelin.interpreter.InterpreterHealthCheck.GroupHealth;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Probes the interpreter processes of one interpreter setting and reports whether they answer,
 * within a deadline that holds for the whole request.
 *
 * <p>Unlike the interpreter status snapshot, which is built from server memory alone, this does
 * contact the interpreter. That is only acceptable because a probe happens when a user explicitly
 * asks for one, against a single setting, and under a deadline: {@code isRunning()} costs a socket
 * connect for docker and a kube-apiserver round trip for k8s, so the same call would be an accident
 * if a listing made it on a timer.
 *
 * <p>The deadline can only be imposed from the outside. {@code isRunning()} takes no timeout
 * argument, the socket connect behind the docker implementation hardcodes one second, and the
 * kubernetes client is created with the defaults of its library. So the probe runs on another
 * thread and this class stops waiting for it, which bounds the response but not the probe:
 * {@code Future#cancel(boolean)} interrupts, and neither a blocking socket connect nor an HTTP
 * client call ends on an interrupt. A probe thread can therefore stay busy after its result was
 * given up on, which is why the pool is small and its threads are daemons.
 */
public class InterpreterHealthChecker {

  private static final Logger LOGGER = LoggerFactory.getLogger(InterpreterHealthChecker.class);

  /**
   * Budget for one health check request, shared by all groups of the setting rather than granted to
   * each of them, so that an isolated setting cannot turn into a per group multiple of it.
   */
  @VisibleForTesting
  static final long PROBE_TIMEOUT_IN_MILLIS = 3_000;

  /**
   * A probe that was given up on keeps its thread, so this bounds how many can pile up while an
   * interpreter host is unreachable. Further probes wait for a thread instead of getting one, and
   * are reported as timed out by the deadline if they never start, which is the honest answer.
   */
  private static final int MAX_CONCURRENT_PROBES = 4;

  private final long probeTimeoutInMillis;
  private final ExecutorService probeExecutor;

  public InterpreterHealthChecker() {
    this(PROBE_TIMEOUT_IN_MILLIS);
  }

  @VisibleForTesting
  InterpreterHealthChecker(long probeTimeoutInMillis) {
    this.probeTimeoutInMillis = probeTimeoutInMillis;
    this.probeExecutor = newProbeExecutor();
  }

  private static ExecutorService newProbeExecutor() {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(
        MAX_CONCURRENT_PROBES, MAX_CONCURRENT_PROBES, 60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(),
        runnable -> {
          Thread thread = new Thread(runnable, "InterpreterHealthChecker-probe");
          thread.setDaemon(true);
          return thread;
        });
    executor.allowCoreThreadTimeOut(true);
    return executor;
  }

  /**
   * Probes every interpreter group of the given setting.
   *
   * <p>A setting owns one group per interpreter process, so an isolated setting is probed once per
   * user or note rather than once in total. The probes are submitted together and then collected
   * against one deadline, so a group that does not answer costs the request its budget once instead
   * of delaying the groups that would have answered right away.
   */
  public InterpreterHealthCheck check(InterpreterSetting interpreterSetting) {
    List<ManagedInterpreterGroup> interpreterGroups = interpreterSetting.getAllInterpreterGroups();
    if (interpreterGroups.isEmpty()) {
      return InterpreterHealthCheck.notRunning(interpreterSetting);
    }

    long startTimeInMillis = System.currentTimeMillis();
    // Keeps the report in the order of the groups, whether an entry needed a probe or not.
    Map<String, GroupHealth> healthByGroupId = new LinkedHashMap<>();
    Map<String, Future<GroupHealth>> probes = new LinkedHashMap<>();

    for (ManagedInterpreterGroup interpreterGroup : interpreterGroups) {
      String groupId = interpreterGroup.getId();
      if (interpreterGroup.isLaunchingInterpreterProcess()) {
        // The handle is published before the process is ready and a launch can take minutes for
        // Spark on YARN, so probing here would report a starting interpreter as broken.
        healthByGroupId.put(groupId, GroupHealth.launching(groupId));
        continue;
      }
      // Read the handle once: it is set to null as soon as the last session of the group closes, so
      // checking it and then reading it again would risk a NullPointerException in between.
      RemoteInterpreterProcess process = interpreterGroup.getInterpreterProcess();
      if (process == null) {
        healthByGroupId.put(groupId, GroupHealth.notRunning(groupId));
        continue;
      }
      try {
        healthByGroupId.put(groupId, null);
        probes.put(groupId, probeExecutor.submit(() -> probe(groupId, process)));
      } catch (RejectedExecutionException e) {
        // Only reachable once stop() ran, since probes queue rather than being rejected.
        LOGGER.warn("Not probing interpreter group {}, no longer accepting probes", groupId);
        healthByGroupId.put(groupId, GroupHealth.timedOut(groupId, 0));
      }
    }

    long deadline = startTimeInMillis + probeTimeoutInMillis;
    for (Map.Entry<String, Future<GroupHealth>> probe : probes.entrySet()) {
      healthByGroupId.put(probe.getKey(),
          awaitProbe(probe.getKey(), probe.getValue(), deadline, startTimeInMillis));
    }
    return InterpreterHealthCheck.of(interpreterSetting,
        new ArrayList<>(healthByGroupId.values()));
  }

  /**
   * Stops accepting probes. Probes already in flight are interrupted, which they may well ignore,
   * but their threads are daemons and do not hold up a shutdown.
   */
  public void stop() {
    probeExecutor.shutdownNow();
  }

  private GroupHealth probe(String groupId, RemoteInterpreterProcess process) {
    long startTimeInMillis = System.currentTimeMillis();
    try {
      boolean running = process.isRunning();
      // An interpreter that answers necessarily has a process, so isAlive() is only worth its own
      // remote call when the interpreter did not answer - which is where telling a process that is
      // gone from one that is up but mute actually helps.
      boolean alive = running || process.isAlive();
      return GroupHealth.probed(groupId, alive, running,
          System.currentTimeMillis() - startTimeInMillis);
    } catch (Exception e) {
      // The probe failing is itself an answer about reachability, so it is reported rather than
      // propagated: one unreachable group must not hide the results of the others.
      LOGGER.warn("Fail to probe interpreter group: {}", groupId, e);
      return GroupHealth.probeFailed(groupId, System.currentTimeMillis() - startTimeInMillis);
    }
  }

  private GroupHealth awaitProbe(String groupId, Future<GroupHealth> probe, long deadline,
                                 long startTimeInMillis) {
    long remainingInMillis = Math.max(deadline - System.currentTimeMillis(), 0);
    try {
      return probe.get(remainingInMillis, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      LOGGER.info("Probe of interpreter group {} did not finish within {}ms",
          groupId, probeTimeoutInMillis);
      probe.cancel(true);
      return GroupHealth.timedOut(groupId, System.currentTimeMillis() - startTimeInMillis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      probe.cancel(true);
      return GroupHealth.timedOut(groupId, System.currentTimeMillis() - startTimeInMillis);
    } catch (ExecutionException e) {
      // probe() already reports a failing probe, so reaching here means something else broke.
      LOGGER.warn("Fail to probe interpreter group: {}", groupId, e.getCause());
      return GroupHealth.probeFailed(groupId, System.currentTimeMillis() - startTimeInMillis);
    }
  }
}
