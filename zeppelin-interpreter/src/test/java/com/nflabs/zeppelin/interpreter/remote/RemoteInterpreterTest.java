package com.nflabs.zeppelin.interpreter.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.thrift.transport.TTransportException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.nflabs.zeppelin.display.GUI;
import com.nflabs.zeppelin.interpreter.InterpreterContext;
import com.nflabs.zeppelin.interpreter.InterpreterGroup;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.interpreter.remote.mock.MockInterpreterA;
import com.nflabs.zeppelin.interpreter.remote.mock.MockInterpreterB;
import com.nflabs.zeppelin.scheduler.Job;
import com.nflabs.zeppelin.scheduler.Job.Status;
import com.nflabs.zeppelin.scheduler.Scheduler;

public class RemoteInterpreterTest {


  private InterpreterGroup intpGroup;
  private HashMap<String, String> env;

  @Before
  public void setUp() throws Exception {
    intpGroup = new InterpreterGroup();
    env = new HashMap<String, String>();
    env.put("ZEPPELIN_CLASSPATH", new File("./target/test-classes").getAbsolutePath());
  }

  @After
  public void tearDown() throws Exception {
    intpGroup.clone();
    intpGroup.destroy();
  }

  @Test
  public void testRemoteInterperterCall() throws TTransportException, IOException {
    Properties p = new Properties();

    RemoteInterpreter intpA = new RemoteInterpreter(
        p,
        MockInterpreterA.class.getName(),
        new File("../bin/interpreter.sh").getAbsolutePath(),
        "fake",
        env
        );

    intpGroup.add(intpA);
    intpA.setInterpreterGroup(intpGroup);

    RemoteInterpreter intpB = new RemoteInterpreter(
        p,
        MockInterpreterB.class.getName(),
        new File("../bin/interpreter.sh").getAbsolutePath(),
        "fake",
        env
        );

    intpGroup.add(intpB);
    intpB.setInterpreterGroup(intpGroup);


    RemoteInterpreterProcess process = intpA.getInterpreterProcess();
    process.equals(intpB.getInterpreterProcess());

    assertFalse(process.isRunning());
    assertEquals(0, process.getNumIdleClient());
    assertEquals(0, process.referenceCount());

    intpA.open();
    assertTrue(process.isRunning());
    assertEquals(1, process.getNumIdleClient());
    assertEquals(1, process.referenceCount());

    intpA.interpret("1",
        new InterpreterContext(
            "id",
            "title",
            "text",
            new HashMap<String, Object>(),
            new GUI()));

    intpB.open();
    assertEquals(2, process.referenceCount());

    intpA.close();
    assertEquals(1, process.referenceCount());
    intpB.close();
    assertEquals(0, process.referenceCount());

    assertFalse(process.isRunning());

  }

  @Test
  public void testRemoteSchedulerSharing() throws TTransportException, IOException {
    Properties p = new Properties();

    RemoteInterpreter intpA = new RemoteInterpreter(
        p,
        MockInterpreterA.class.getName(),
        new File("../bin/interpreter.sh").getAbsolutePath(),
        "fake",
        env
        );

    intpGroup.add(intpA);
    intpA.setInterpreterGroup(intpGroup);

    RemoteInterpreter intpB = new RemoteInterpreter(
        p,
        MockInterpreterB.class.getName(),
        new File("../bin/interpreter.sh").getAbsolutePath(),
        "fake",
        env
        );

    intpGroup.add(intpB);
    intpB.setInterpreterGroup(intpGroup);

    intpA.open();
    intpB.open();

    long start = System.currentTimeMillis();
    InterpreterResult ret = intpA.interpret("500",
        new InterpreterContext(
            "id",
            "title",
            "text",
            new HashMap<String, Object>(),
            new GUI()));
    assertEquals("500", ret.message());

    ret = intpB.interpret("500",
        new InterpreterContext(
            "id",
            "title",
            "text",
            new HashMap<String, Object>(),
            new GUI()));
    assertEquals("1000", ret.message());
    long end = System.currentTimeMillis();
    assertTrue(end - start >= 1000);


    intpA.close();
    intpB.close();

    RemoteInterpreterProcess process = intpA.getInterpreterProcess();
    assertFalse(process.isRunning());
  }

  @Test
  public void testRemoteSchedulerSharingSubmit() throws TTransportException, IOException, InterruptedException {
    Properties p = new Properties();

    final RemoteInterpreter intpA = new RemoteInterpreter(
        p,
        MockInterpreterA.class.getName(),
        new File("../bin/interpreter.sh").getAbsolutePath(),
        "fake",
        env
        );

    intpGroup.add(intpA);
    intpA.setInterpreterGroup(intpGroup);

    final RemoteInterpreter intpB = new RemoteInterpreter(
        p,
        MockInterpreterB.class.getName(),
        new File("../bin/interpreter.sh").getAbsolutePath(),
        "fake",
        env
        );

    intpGroup.add(intpB);
    intpB.setInterpreterGroup(intpGroup);

    intpA.open();
    intpB.open();

    long start = System.currentTimeMillis();
    Job jobA = new Job("jobA", null) {

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
        return intpA.interpret("500",
            new InterpreterContext(
                "jobA",
                "title",
                "text",
                new HashMap<String, Object>(),
                new GUI()));
      }

      @Override
      protected boolean jobAbort() {
        return false;
      }

    };
    intpA.getScheduler().submit(jobA);

    Job jobB = new Job("jobB", null) {

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
        return intpB.interpret("500",
            new InterpreterContext(
                "jobB",
                "title",
                "text",
                new HashMap<String, Object>(),
                new GUI()));
      }

      @Override
      protected boolean jobAbort() {
        return false;
      }

    };
    intpB.getScheduler().submit(jobB);

    // wait until both job finished
    while (jobA.getStatus() != Status.FINISHED ||
           jobB.getStatus() != Status.FINISHED) {
      Thread.sleep(100);
    }

    long end = System.currentTimeMillis();
    assertTrue(end - start >= 1000);

    assertEquals("1000", ((InterpreterResult) jobB.getReturn()).message());

    intpA.close();
    intpB.close();

    RemoteInterpreterProcess process = intpA.getInterpreterProcess();
    assertFalse(process.isRunning());
  }

  @Test
  public void testRunOrderPreserved() throws InterruptedException {
    Properties p = new Properties();

    final RemoteInterpreter intpA = new RemoteInterpreter(
        p,
        MockInterpreterA.class.getName(),
        new File("../bin/interpreter.sh").getAbsolutePath(),
        "fake",
        env
        );

    intpGroup.add(intpA);
    intpA.setInterpreterGroup(intpGroup);

    intpA.open();

    int concurrency = 3;
    final List<String> results = new LinkedList<String>();

    Scheduler scheduler = intpA.getScheduler();
    for (int i = 0; i < concurrency; i++) {
      final String jobId = Integer.toString(i);
      scheduler.submit(new Job(jobId, Integer.toString(i), null, 200) {

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
          InterpreterResult ret = intpA.interpret(getJobName(), new InterpreterContext(
              jobId,
              "title",
              "text",
              new HashMap<String, Object>(),
              new GUI()));

          synchronized (results) {
            results.add(ret.message());
            results.notify();
          }
          return null;
        }

        @Override
        protected boolean jobAbort() {
          return false;
        }

      });
    }

    // wait for job finished
    synchronized (results) {
      while (results.size() != concurrency) {
        results.wait(300);
      }
    }

    int i = 0;
    for (String result : results) {
      assertEquals(Integer.toString(i++), result);
    }
    assertEquals(concurrency, i);

    intpA.close();
  }


  @Test
  public void testRunParallel() throws InterruptedException {
    Properties p = new Properties();
    p.put("parallel", "true");

    final RemoteInterpreter intpA = new RemoteInterpreter(
        p,
        MockInterpreterA.class.getName(),
        new File("../bin/interpreter.sh").getAbsolutePath(),
        "fake",
        env
        );

    intpGroup.add(intpA);
    intpA.setInterpreterGroup(intpGroup);

    intpA.open();

    int concurrency = 4;
    final int timeToSleep = 1000;
    final List<String> results = new LinkedList<String>();
    long start = System.currentTimeMillis();

    Scheduler scheduler = intpA.getScheduler();
    for (int i = 0; i < concurrency; i++) {
      final String jobId = Integer.toString(i);
      scheduler.submit(new Job(jobId, Integer.toString(i), null, 300) {

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
          String stmt = Integer.toString(timeToSleep);
          InterpreterResult ret = intpA.interpret(stmt, new InterpreterContext(
              jobId,
              "title",
              "text",
              new HashMap<String, Object>(),
              new GUI()));

          synchronized (results) {
            results.add(ret.message());
            results.notify();
          }
          return stmt;
        }

        @Override
        protected boolean jobAbort() {
          return false;
        }

      });
    }

    // wait for job finished
    synchronized (results) {
      while (results.size() != concurrency) {
        results.wait(300);
      }
    }

    long end = System.currentTimeMillis();

    assertTrue(end - start < timeToSleep * concurrency);

    intpA.close();
  }
}
