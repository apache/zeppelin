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

package org.apache.zeppelin.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the "abort right before run" race between a cancelling thread
 * ({@link AbstractScheduler#cancel(String)}) and the scheduler thread that is about to invoke
 * {@link AbstractScheduler#runJob(Job)}.
 *
 * <p>Honest limitation: a pure memory-visibility race on a non-volatile field cannot be
 * reproduced deterministically without a specialized harness (e.g. jcstress) because it depends
 * on JVM safepoints/JIT reordering. This class therefore verifies the observable behavior
 * contract instead: (1) abort()/isAborted() agree, (2) a PENDING job aborted before runJob() is
 * invoked never has its run() executed and ends in ABORT, and (3) cancel() and the runJob() gate
 * are mutually exclusive on the same job monitor, which is a deterministic, latch-driven proof
 * that the race window described in ZEPPELIN-6129 Task 2 is closed.
 */
class AbstractSchedulerAbortRaceTest {

  private FIFOScheduler scheduler;

  @AfterEach
  void tearDown() {
    if (scheduler != null) {
      scheduler.stop();
    }
  }

  @Test
  void testAbortSetsIsAbortedTrue() {
    SleepingJob job = new SleepingJob("abortJob", null, 5000);

    assertFalse(job.isAborted());
    job.abort();

    assertTrue(job.isAborted());
  }

  @Test
  void testCancelBeforeRunJobBlocksExecutionThroughSchedulerCancelPath() {
    scheduler = new FIFOScheduler("cancel-gate-test");
    SleepingJob job = new SleepingJob("job1", null, 5000);
    scheduler.submit(job);

    scheduler.cancel(job.getId());
    scheduler.runJob(job);

    assertEquals(Job.Status.ABORT, job.getStatus());
    assertNull(job.getReturn());
  }

  @Test
  void testCancelAndRunJobGateAreMutuallyExclusiveOnJobMonitor() throws Exception {
    scheduler = new FIFOScheduler("mutex-test");
    BlockingAbortJob job = new BlockingAbortJob("job1");
    scheduler.submit(job);

    Thread cancelThread = new Thread(() -> scheduler.cancel(job.getId()), "cancel-thread");
    cancelThread.start();

    assertTrue(job.abortEntered.await(2, TimeUnit.SECONDS),
        "cancel thread must reach jobAbort() and hold the job monitor");

    Thread runJobThread = new Thread(() -> scheduler.runJob(job), "runjob-thread");
    runJobThread.start();

    assertTrue(waitForState(runJobThread, Thread.State.BLOCKED, 2000),
        "runJob() must block waiting for the same job monitor held by cancel()");
    assertFalse(job.runCalled, "job.run() must not start while cancel() still holds the monitor");

    job.releaseAbort.countDown();
    cancelThread.join(2000);
    runJobThread.join(2000);

    assertFalse(job.runCalled, "aborted job must never invoke run()");
    assertEquals(Job.Status.ABORT, job.getStatus());
  }

  private static boolean waitForState(Thread thread, Thread.State expected, long timeoutMs)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      if (thread.getState() == expected) {
        return true;
      }
      Thread.sleep(10);
    }
    return thread.getState() == expected;
  }

  /**
   * Job whose {@code jobAbort()} blocks on a latch so the test can control exactly how long the
   * cancelling thread holds the job monitor.
   */
  private static class BlockingAbortJob extends Job<Object> {

    private final CountDownLatch abortEntered = new CountDownLatch(1);
    private final CountDownLatch releaseAbort = new CountDownLatch(1);
    private volatile boolean runCalled = false;

    BlockingAbortJob(String name) {
      super(name, null);
    }

    @Override
    protected Object jobRun() {
      runCalled = true;
      return null;
    }

    @Override
    protected boolean jobAbort() {
      abortEntered.countDown();
      try {
        releaseAbort.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return true;
    }

    @Override
    public void setResult(Object result) {
    }

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
      return Collections.emptyMap();
    }
  }
}
