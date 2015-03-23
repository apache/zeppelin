package com.nflabs.zeppelin.scheduler;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.nflabs.zeppelin.display.GUI;
import com.nflabs.zeppelin.interpreter.InterpreterContext;
import com.nflabs.zeppelin.interpreter.InterpreterGroup;
import com.nflabs.zeppelin.interpreter.remote.RemoteInterpreter;
import com.nflabs.zeppelin.interpreter.remote.mock.MockInterpreterA;

public class RemoteSchedulerTest {

  private SchedulerFactory schedulerSvc;

  @Before
  public void setUp() throws Exception{
    schedulerSvc = new SchedulerFactory();
  }

  @After
  public void tearDown(){

  }

  @Test
  public void test() throws Exception {
    Properties p = new Properties();
    InterpreterGroup intpGroup = new InterpreterGroup();
    Map<String, String> env = new HashMap<String, String>();
    env.put("ZEPPELIN_CLASSPATH", new File("./target/test-classes").getAbsolutePath());

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

    Scheduler scheduler = schedulerSvc.createOrGetRemoteScheduler("test",
        intpA.getInterpreterProcess(),
        10);

    Job job = new Job("jobId", "jobName", null, 200) {

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
            "jobId",
            "title",
            "text",
            new HashMap<String, Object>(),
            new GUI()));
        return "1000";
      }

      @Override
      protected boolean jobAbort() {
        return false;
      }
    };
    scheduler.submit(job);

    while (job.isRunning() == false) {
      Thread.sleep(100);
    }

    Thread.sleep(500);
    assertEquals(0, scheduler.getJobsWaiting().size());
    assertEquals(1, scheduler.getJobsRunning().size());

    Thread.sleep(500);

    assertEquals(0, scheduler.getJobsWaiting().size());
    assertEquals(0, scheduler.getJobsRunning().size());

    intpA.close();
    schedulerSvc.removeScheduler("test");
  }

}
