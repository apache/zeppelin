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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.apache.zeppelin.interpreter.remote.mock.MockInterpreterA;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

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
    final InterpreterGroup intpGroup = new InterpreterGroup();
    Map<String, String> env = new HashMap<String, String>();
    env.put("ZEPPELIN_CLASSPATH", new File("./target/test-classes").getAbsolutePath());

    final RemoteInterpreter intpA = new RemoteInterpreter(
        p,
        MockInterpreterA.class.getName(),
        new File("../bin/interpreter.sh").getAbsolutePath(),
        "fake",
        env,
        10 * 1000
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
            new GUI(),
            new AngularObjectRegistry(intpGroup.getId(), null),
            new LinkedList<InterpreterContextRunner>()));
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
