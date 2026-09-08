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

import org.apache.zeppelin.interpreter.AbstractInterpreterTest;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteSchedulerTest extends AbstractInterpreterTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteSchedulerTest.class);
  private InterpreterSetting interpreterSetting;
  private SchedulerFactory schedulerSvc;
  private static final int TICK_WAIT = 100;
  private static final int MAX_WAIT_CYCLES = 100;
  private static final int CONCURRENT_JOB_SLEEP_MS = 3000;
  private static final int OVERLAP_WAIT_CYCLES = 30;
  private String note1Id;

  @Override
  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    note1Id = notebook.createNote("/note_1", AuthenticationInfo.ANONYMOUS);
    schedulerSvc = SchedulerFactory.singleton();
    interpreterSetting = interpreterSettingManager.getInterpreterSettingByName("test");
  }

  @Override
  @AfterEach
  public void tearDown() {
    interpreterSetting.close();
  }

  @Test
  void test() throws Exception {
    final RemoteInterpreter intpA = (RemoteInterpreter) interpreterSetting.getInterpreter("user1", note1Id, "mock");

    intpA.open();

    Scheduler scheduler = intpA.getScheduler();

    Job<Object> job = new Job<Object>("jobId", "jobName", null) {
      Object results;

      @Override
      public Object getReturn() {
        return results;
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
        intpA.interpret("1000", InterpreterContext.builder()
                .setNoteId("noteId")
                .setParagraphId("jobId")
                .setResourcePool(new LocalResourcePool("pool1"))
                .build());
        return "1000";
      }

      @Override
      protected boolean jobAbort() {
        return false;
      }

      @Override
      public void setResult(Object results) {
        this.results = results;
      }
    };
    scheduler.submit(job);

    int cycles = 0;
    while (!job.isRunning() && cycles < MAX_WAIT_CYCLES) {
      LOGGER.info("Status:" + job.getStatus());
      Thread.sleep(TICK_WAIT);
      cycles++;
    }
    assertTrue(job.isRunning());

    Thread.sleep(5 * TICK_WAIT);

    cycles = 0;
    while (!job.isTerminated() && cycles < MAX_WAIT_CYCLES) {
      Thread.sleep(TICK_WAIT);
      cycles++;
    }

    assertTrue(job.isTerminated());

    intpA.close();
    schedulerSvc.removeScheduler("test");
  }

  @Test
  void testAbortOnPending_noteModeSerial() throws Exception {
    final RemoteInterpreter intpA = (RemoteInterpreter) interpreterSetting.getInterpreter("user1", note1Id, "mock");
    // Force "note" execution mode: RemoteScheduler keeps a single-threaded pool for it and its
    // local dispatch gate (runJobInScheduler) blocks until job1 is fully executed - not just
    // submitted - before even attempting job2. So job2 is deterministically still PENDING, and
    // never dispatched, when it is aborted below, regardless of the paragraph-mode pool now
    // being multi-threaded for every interpreter (ZEPPELIN-6129).
    intpA.setProperty(".execution.mode", "note");
    intpA.setProperty(".noteId", note1Id);
    intpA.open();

    Scheduler scheduler = intpA.getScheduler();

    Job<Object> job1 = new Job<Object>("jobId1", "jobName1", null) {
      Object results;
      InterpreterContext context = InterpreterContext.builder()
          .setNoteId("noteId")
          .setParagraphId("jobId1")
          .setResourcePool(new LocalResourcePool("pool1"))
          .build();

      @Override
      public Object getReturn() {
        return results;
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
        intpA.interpret("1000", context);
        return "1000";
      }

      @Override
      protected boolean jobAbort() {
        if (isRunning()) {
          try {
            intpA.cancel(context);
          } catch (InterpreterException e) {
            e.printStackTrace();
          }
        }
        return true;
      }

      @Override
      public void setResult(Object results) {
        this.results = results;
      }
    };

    Job<Object> job2 = new Job<Object>("jobId2", "jobName2", null) {
      public Object results;
      InterpreterContext context = InterpreterContext.builder()
          .setNoteId("noteId")
          .setParagraphId("jobId2")
          .setResourcePool(new LocalResourcePool("pool1"))
          .build();

      @Override
      public Object getReturn() {
        return results;
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
        intpA.interpret("1000", context);
        return "1000";
      }

      @Override
      protected boolean jobAbort() {
        if (isRunning()) {
          try {
            intpA.cancel(context);
          } catch (InterpreterException e) {
            e.printStackTrace();
          }
        }
        return true;
      }

      @Override
      public void setResult(Object results) {
        this.results = results;
      }
    };

    job2.setResult("result2");

    scheduler.submit(job1);
    scheduler.submit(job2);

    CountDownLatch job1Running = new CountDownLatch(1);
    Thread runningWatcher = new Thread(() -> {
      int cycles = 0;
      while (job1Running.getCount() > 0 && cycles < MAX_WAIT_CYCLES) {
        if (job1.isRunning()) {
          job1Running.countDown();
          return;
        }
        try {
          Thread.sleep(TICK_WAIT);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
        cycles++;
      }
    });
    runningWatcher.start();

    assertTrue(job1Running.await(MAX_WAIT_CYCLES * TICK_WAIT, TimeUnit.MILLISECONDS),
        "job1 should reach RUNNING");
    runningWatcher.join(TICK_WAIT);

    assertTrue(job1.isRunning());
    assertEquals(Status.PENDING, job2.getStatus());

    job2.abort();

    int cycles = 0;
    while (!job1.isTerminated() && cycles < MAX_WAIT_CYCLES) {
      Thread.sleep(TICK_WAIT);
      cycles++;
    }

    // job1 terminating only unblocks the scheduler thread to dequeue and abort job2; give it
    // its own bounded wait instead of assuming it is already processed the instant job1 is done.
    cycles = 0;
    while (!job2.isTerminated() && cycles < MAX_WAIT_CYCLES) {
      Thread.sleep(TICK_WAIT);
      cycles++;
    }

    assertNotNull(job1.getDateFinished());
    assertTrue(job1.isTerminated());
    assertEquals("1000", job1.getReturn());
    assertNull(job2.getDateFinished());
    assertTrue(job2.isTerminated());
    assertEquals("result2", job2.getReturn());

    intpA.close();
    schedulerSvc.removeScheduler("test");
  }

  @Test
  void testParallelExecution_bothJobsRunConcurrently() throws Exception {
    final RemoteInterpreter intpA =
        (RemoteInterpreter) interpreterSetting.getInterpreter("user1", note1Id, "mock");
    // enable parallel execution on the remote interpreter side so that the two jobs
    // are not serialized by the interpreter's own scheduler. RemoteScheduler itself must
    // stay interpreter-neutral: no JDBC-specific property is needed to unlock concurrency.
    intpA.setProperty("parallel", "true");
    intpA.open();

    Scheduler scheduler = intpA.getScheduler();

    Job<Object> job1 = createSleepingJob("jobId1", intpA, CONCURRENT_JOB_SLEEP_MS);
    Job<Object> job2 = createSleepingJob("jobId2", intpA, CONCURRENT_JOB_SLEEP_MS);

    scheduler.submit(job1);
    scheduler.submit(job2);

    CountDownLatch overlapDetected = new CountDownLatch(1);
    Thread overlapWatcher = new Thread(() -> {
      int cycles = 0;
      while (overlapDetected.getCount() > 0 && cycles < OVERLAP_WAIT_CYCLES) {
        if (job1.isRunning() && job2.isRunning()) {
          overlapDetected.countDown();
          return;
        }
        try {
          Thread.sleep(TICK_WAIT);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
        cycles++;
      }
    });
    overlapWatcher.start();

    boolean bothRanConcurrently =
        overlapDetected.await(OVERLAP_WAIT_CYCLES * TICK_WAIT, TimeUnit.MILLISECONDS);
    overlapWatcher.join(TICK_WAIT);

    assertTrue(bothRanConcurrently, "job1 and job2 should both be RUNNING at the same time");

    intpA.close();
    schedulerSvc.removeScheduler("test");
  }

  private Job<Object> createSleepingJob(String jobId, RemoteInterpreter intpA, int sleepMillis) {
    return new Job<Object>(jobId, jobId, null) {
      Object results;
      InterpreterContext context = InterpreterContext.builder()
          .setNoteId("noteId")
          .setParagraphId(jobId)
          .setResourcePool(new LocalResourcePool("pool-" + jobId))
          .build();

      @Override
      public Object getReturn() {
        return results;
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
        intpA.interpret(String.valueOf(sleepMillis), context);
        return String.valueOf(sleepMillis);
      }

      @Override
      protected boolean jobAbort() {
        return false;
      }

      @Override
      public void setResult(Object results) {
        this.results = results;
      }
    };
  }

}
