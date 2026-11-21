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
import com.google.common.base.Preconditions;

import org.apache.zeppelin.interpreter.InterpreterHealthCheck.GroupHealth;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess.CheckMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Probes the interpreter processes of one interpreter setting and reports what their launchers say
 * about them, within a deadline that holds for the whole request.
 *
 * <p>Unlike the interpreter status snapshot, which is built from server memory alone, this does
 * call into the launcher. That is only acceptable because a probe happens when a user explicitly
 * asks for one, against a single setting, and under a deadline: {@code isRunning()} costs a socket
 * connect for docker and a kube-apiserver round trip for k8s, so the same call would be an accident
 * if a listing made it on a timer. What each launcher looks at differs, and none of them asks the
 * interpreter itself, so every result carries the {@link CheckMethod} it was based on.
 *
 * <p>The deadline can only be imposed from the outside. {@code isRunning()} takes no timeout
 * argument, the socket connect behind the docker implementation hardcodes one second, and the
 * kubernetes client is created with the defaults of its library. So the probe runs on another
 * thread and this class stops waiting for it, which bounds the response but not the probe:
 * {@code Future#cancel(boolean)} interrupts, and neither a blocking socket connect nor an HTTP
 * client call ends on an interrupt. A probe thread can therefore stay busy after its result was
 * given up on, which is why the pool is small and its threads are daemons.
 *
 * <p>Once every probe thread is held that way, later probes cannot run until one comes back. They
 * are then reported as {@link InterpreterHealthCheck.Reason#PROBE_UNAVAILABLE} rather than as timed
 * out, since the interpreter was never asked, and the queue they wait in is bounded and cleared of
 * the probes that were given up on, so that pending work cannot pile up while the threads are held.
 */
public class InterpreterHealthChecker {

  private static final Logger LOGGER = LoggerFactory.getLogger(InterpreterHealthChecker.class);

  /**
   * A probe that was given up on keeps its thread, so this bounds how many can pile up while an
   * interpreter host is unreachable.
   */
  private static final int MAX_CONCURRENT_PROBES = 4;

  /**
   * Room for probes waiting for a thread. An isolated setting submits one probe per group in a
   * single request, so this has to fit a setting with many users or notes; the bound is there so
   * that held threads cannot let waiting work grow without limit. Probes beyond it are reported as
   * unavailable at once instead of waiting for the deadline.
   */
  private static final int MAX_PENDING_PROBES = 64;

  private final long probeTimeoutInMillis;
  private final long probeTimeoutInNanos;
  private final ThreadPoolExecutor probeExecutor;

  /**
   * @param probeTimeoutInMillis budget for one health check request, shared by all groups of the
   *                             setting rather than granted to each of them, so that an isolated
   *                             setting cannot turn into a per group multiple of it
   */
  public InterpreterHealthChecker(long probeTimeoutInMillis) {
    this(probeTimeoutInMillis, MAX_CONCURRENT_PROBES, MAX_PENDING_PROBES);
  }

  @VisibleForTesting
  InterpreterHealthChecker(long probeTimeoutInMillis, int maxConcurrentProbes,
                           int maxPendingProbes) {
    Preconditions.checkArgument(probeTimeoutInMillis > 0,
        "Health check timeout must be positive, but was %sms", probeTimeoutInMillis);
    this.probeTimeoutInMillis = probeTimeoutInMillis;
    // Saturates instead of overflowing, so a huge configured value means "wait very long".
    this.probeTimeoutInNanos = TimeUnit.MILLISECONDS.toNanos(probeTimeoutInMillis);
    this.probeExecutor = newProbeExecutor(maxConcurrentProbes, maxPendingProbes);
  }

  private static ThreadPoolExecutor newProbeExecutor(int maxConcurrentProbes,
                                                     int maxPendingProbes) {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(
        maxConcurrentProbes, maxConcurrentProbes, 60L, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(maxPendingProbes),
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

    // The monotonic clock, so that a wall clock adjustment cannot stretch or cut the budget.
    long startTimeInNanos = System.nanoTime();
    // Keeps the report in the order of the groups, whether an entry needed a probe or not.
    Map<String, GroupHealth> healthByGroupId = new LinkedHashMap<>();
    Map<String, Probe> probes = new LinkedHashMap<>();

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
      CheckMethod checkMethod = process.getCheckMethod();
      Probe pendingProbe = new Probe(checkMethod);
      try {
        healthByGroupId.put(groupId, null);
        pendingProbe.future = probeExecutor.submit(() -> {
          if (!pendingProbe.claimed.compareAndSet(false, true)) {
            // The request already gave up on this probe and reported it as never started.
            return null;
          }
          return probe(groupId, checkMethod, process);
        });
        probes.put(groupId, pendingProbe);
      } catch (RejectedExecutionException e) {
        // Every thread is busy and the queue is full, or stop() ran.
        LOGGER.warn("Not probing interpreter group {}, no probe thread is available", groupId);
        healthByGroupId.put(groupId, GroupHealth.unavailable(groupId, checkMethod));
      }
    }

    boolean gaveUpOnAProbe = false;
    for (Map.Entry<String, Probe> probe : probes.entrySet()) {
      GroupHealth groupHealth = awaitProbe(probe.getKey(), probe.getValue(), startTimeInNanos);
      gaveUpOnAProbe |= probe.getValue().future.isCancelled();
      healthByGroupId.put(probe.getKey(), groupHealth);
    }
    if (gaveUpOnAProbe) {
      // A cancelled probe that never started would otherwise keep its place in the queue until a
      // thread frees up, which may be never while the threads are held by unreachable hosts.
      probeExecutor.purge();
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

  @VisibleForTesting
  int getPendingProbeCount() {
    return probeExecutor.getQueue().size();
  }

  private GroupHealth probe(String groupId, CheckMethod checkMethod,
                            RemoteInterpreterProcess process) {
    long startTimeInNanos = System.nanoTime();
    try {
      boolean running = process.isRunning();
      // An interpreter that answers necessarily has a process, so isAlive() is only worth its own
      // remote call when the interpreter did not answer - which is where telling a process that is
      // gone from one that is up but mute actually helps.
      boolean alive = running || process.isAlive();
      return GroupHealth.probed(groupId, checkMethod, alive, running,
          millisSince(startTimeInNanos));
    } catch (Exception e) {
      // The probe failing is itself an answer about reachability, so it is reported rather than
      // propagated: one unreachable group must not hide the results of the others.
      LOGGER.warn("Fail to probe interpreter group: {}", groupId, e);
      return GroupHealth.probeFailed(groupId, checkMethod, millisSince(startTimeInNanos));
    }
  }

  private GroupHealth awaitProbe(String groupId, Probe probe, long startTimeInNanos) {
    // Subtracting elapsed time rather than adding the budget to the start cannot overflow.
    long remainingInNanos =
        Math.max(probeTimeoutInNanos - (System.nanoTime() - startTimeInNanos), 0);
    try {
      return probe.future.get(remainingInNanos, TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      LOGGER.info("Probe of interpreter group {} did not finish within {}ms",
          groupId, probeTimeoutInMillis);
      return giveUp(groupId, probe, startTimeInNanos);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return giveUp(groupId, probe, startTimeInNanos);
    } catch (ExecutionException e) {
      // probe() already reports a failing probe, so reaching here means something else broke.
      LOGGER.warn("Fail to probe interpreter group: {}", groupId, e.getCause());
      return GroupHealth.probeFailed(groupId, probe.checkMethod, millisSince(startTimeInNanos));
    }
  }

  private GroupHealth giveUp(String groupId, Probe probe, long startTimeInNanos) {
    // Claiming the probe settles in one step whether it ever started: if this succeeds, its thread
    // will find it claimed and not run it, so saying it never started is true for good. A timeout
    // here would blame an interpreter that was never asked.
    boolean neverStarted = probe.claimed.compareAndSet(false, true);
    probe.future.cancel(true);
    if (neverStarted) {
      return GroupHealth.unavailable(groupId, probe.checkMethod);
    }
    return GroupHealth.timedOut(groupId, probe.checkMethod, millisSince(startTimeInNanos));
  }

  private static long millisSince(long startTimeInNanos) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeInNanos);
  }

  /** A submitted probe, together with whether it has been claimed to run or to be given up on. */
  private static final class Probe {
    private final CheckMethod checkMethod;
    /**
     * Set by whichever comes first: the probe thread starting the probe, or the request giving up
     * on it before it started.
     */
    private final AtomicBoolean claimed = new AtomicBoolean();
    private Future<GroupHealth> future;

    private Probe(CheckMethod checkMethod) {
      this.checkMethod = checkMethod;
    }
  }
}
