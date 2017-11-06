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

import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterInfo;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterRunner;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.apache.zeppelin.interpreter.remote.mock.MockInterpreterA;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RemoteSchedulerTest implements RemoteInterpreterProcessListener {

  private static final String INTERPRETER_SCRIPT =
      System.getProperty("os.name").startsWith("Windows") ?
          "../bin/interpreter.cmd" :
          "../bin/interpreter.sh";

  private InterpreterSetting interpreterSetting;
  private SchedulerFactory schedulerSvc;
  private static final int TICK_WAIT = 100;
  private static final int MAX_WAIT_CYCLES = 100;

  @Before
  public void setUp() throws Exception {
    schedulerSvc = new SchedulerFactory();

    InterpreterOption interpreterOption = new InterpreterOption();
    InterpreterInfo interpreterInfo1 = new InterpreterInfo(MockInterpreterA.class.getName(), "mock", true, new HashMap<String, Object>());
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(interpreterInfo1);
    InterpreterRunner runner = new InterpreterRunner(INTERPRETER_SCRIPT, INTERPRETER_SCRIPT);
    interpreterSetting = new InterpreterSetting.Builder()
        .setId("test")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(interpreterInfos)
        .setOption(interpreterOption)
        .setRunner(runner)
        .setInterpreterDir("../interpeters/test")
        .create();
  }

  @After
  public void tearDown() {
    interpreterSetting.close();
  }

  @Test
  public void test() throws Exception {
    final RemoteInterpreter intpA = (RemoteInterpreter) interpreterSetting.getDefaultInterpreter("user1", "note1");

    intpA.open();

    Scheduler scheduler = intpA.getScheduler();

    Job job = new Job("jobId", "jobName", null, 200) {
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
        intpA.interpret("1000", new InterpreterContext(
            "note",
            "jobId",
            null,
            "title",
            "text",
            new AuthenticationInfo(),
            new HashMap<String, Object>(),
            new GUI(),
            null,
            new LocalResourcePool("pool1"),
            new LinkedList<InterpreterContextRunner>(), null));
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
      Thread.sleep(TICK_WAIT);
      cycles++;
    }
    assertTrue(job.isRunning());

    Thread.sleep(5 * TICK_WAIT);
    assertEquals(0, scheduler.getJobsWaiting().size());
    assertEquals(1, scheduler.getJobsRunning().size());

    cycles = 0;
    while (!job.isTerminated() && cycles < MAX_WAIT_CYCLES) {
      Thread.sleep(TICK_WAIT);
      cycles++;
    }

    assertTrue(job.isTerminated());
    assertEquals(0, scheduler.getJobsWaiting().size());
    assertEquals(0, scheduler.getJobsRunning().size());

    intpA.close();
    schedulerSvc.removeScheduler("test");
  }

  @Test
  public void testAbortOnPending() throws Exception {
    final RemoteInterpreter intpA = (RemoteInterpreter) interpreterSetting.getDefaultInterpreter("user1", "note1");
    intpA.open();

    Scheduler scheduler = intpA.getScheduler();

    Job job1 = new Job("jobId1", "jobName1", null, 200) {
      Object results;
      InterpreterContext context = new InterpreterContext(
          "note",
          "jobId1",
          null,
          "title",
          "text",
          new AuthenticationInfo(),
          new HashMap<String, Object>(),
          new GUI(),
          null,
          new LocalResourcePool("pool1"),
          new LinkedList<InterpreterContextRunner>(), null);

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

    Job job2 = new Job("jobId2", "jobName2", null, 200) {
      public Object results;
      InterpreterContext context = new InterpreterContext(
          "note",
          "jobId2",
          null,
          "title",
          "text",
          new AuthenticationInfo(),
          new HashMap<String, Object>(),
          new GUI(),
          null,
          new LocalResourcePool("pool1"),
          new LinkedList<InterpreterContextRunner>(), null);

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


    int cycles = 0;
    while (!job1.isRunning() && cycles < MAX_WAIT_CYCLES) {
      Thread.sleep(TICK_WAIT);
      cycles++;
    }
    assertTrue(job1.isRunning());
    assertTrue(job2.getStatus() == Status.PENDING);

    job2.abort();

    cycles = 0;
    while (!job1.isTerminated() && cycles < MAX_WAIT_CYCLES) {
      Thread.sleep(TICK_WAIT);
      cycles++;
    }

    assertNotNull(job1.getDateFinished());
    assertTrue(job1.isTerminated());
    assertNull(job2.getDateFinished());
    assertTrue(job2.isTerminated());
    assertEquals("result2", job2.getReturn());

    intpA.close();
    schedulerSvc.removeScheduler("test");
  }

  @Override
  public void onOutputAppend(String noteId, String paragraphId, int index, String output) {

  }

  @Override
  public void onOutputUpdated(String noteId, String paragraphId, int index, InterpreterResult.Type type, String output) {

  }

  @Override
  public void onOutputClear(String noteId, String paragraphId) {

  }

  @Override
  public void onMetaInfosReceived(String settingId, Map<String, String> metaInfos) {

  }

  @Override
  public void onGetParagraphRunners(String noteId, String paragraphId, RemoteWorksEventListener callback) {
    if (callback != null) {
      callback.onFinished(new LinkedList<>());
    }
  }

  @Override
  public void onRemoteRunParagraph(String noteId, String PsaragraphID) throws Exception {
  }

  @Override
  public void onParaInfosReceived(String noteId, String paragraphId,
                                  String interpreterSettingId, Map<String, String> metaInfos) {
  }
}
