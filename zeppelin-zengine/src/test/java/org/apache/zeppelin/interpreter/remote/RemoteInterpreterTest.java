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

package org.apache.zeppelin.interpreter.remote;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.thrift.transport.TTransportException;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.interpreter.remote.mock.MockInterpreterEnv;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterResultMessage;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService.Client;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.remote.mock.MockInterpreterA;
import org.apache.zeppelin.interpreter.remote.mock.MockInterpreterB;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.scheduler.Scheduler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class RemoteInterpreterTest {


  private static final String INTERPRETER_SCRIPT =
          System.getProperty("os.name").startsWith("Windows") ?
                  "../bin/interpreter.cmd" :
                  "../bin/interpreter.sh";

  private InterpreterGroup intpGroup;
  private HashMap<String, String> env;

  @Before
  public void setUp() throws Exception {
    intpGroup = new InterpreterGroup();
    env = new HashMap<>();
    env.put("ZEPPELIN_CLASSPATH", new File("./target/test-classes").getAbsolutePath());
  }

  @After
  public void tearDown() throws Exception {
    intpGroup.close();
  }

  private RemoteInterpreter createMockInterpreterA(Properties p) {
    return createMockInterpreterA(p, "note");
  }

  private RemoteInterpreter createMockInterpreterA(Properties p, String noteId) {
    return new RemoteInterpreter(
        p,
        noteId,
        MockInterpreterA.class.getName(),
        new File(INTERPRETER_SCRIPT).getAbsolutePath(),
        "fake",
        "fakeRepo",
        env,
        10 * 1000,
        null,
        null,
        "anonymous",
        false);
  }

  private RemoteInterpreter createMockInterpreterB(Properties p) {
    return createMockInterpreterB(p, "note");
  }

  private RemoteInterpreter createMockInterpreterB(Properties p, String noteId) {
    return new RemoteInterpreter(
        p,
        noteId,
        MockInterpreterB.class.getName(),
        new File(INTERPRETER_SCRIPT).getAbsolutePath(),
        "fake",
        "fakeRepo",
        env,
        10 * 1000,
        null,
        null,
        "anonymous",
        false);
  }

  @Test
  public void testRemoteInterperterCall() throws TTransportException, IOException {
    Properties p = new Properties();
    intpGroup.put("note", new LinkedList<Interpreter>());

    RemoteInterpreter intpA = createMockInterpreterA(p);

    intpGroup.get("note").add(intpA);

    intpA.setInterpreterGroup(intpGroup);

    RemoteInterpreter intpB = createMockInterpreterB(p);

    intpGroup.get("note").add(intpB);
    intpB.setInterpreterGroup(intpGroup);


    RemoteInterpreterProcess process = intpA.getInterpreterProcess();
    process.equals(intpB.getInterpreterProcess());

    assertFalse(process.isRunning());
    assertEquals(0, process.getNumIdleClient());
    assertEquals(0, process.referenceCount());

    intpA.open(); // initializa all interpreters in the same group
    assertTrue(process.isRunning());
    assertEquals(1, process.getNumIdleClient());
    assertEquals(1, process.referenceCount());

    intpA.interpret("1",
        new InterpreterContext(
            "note",
            "id",
            null,
            "title",
            "text",
            new AuthenticationInfo(),
            new HashMap<String, Object>(),
            new GUI(),
            new AngularObjectRegistry(intpGroup.getId(), null),
            new LocalResourcePool("pool1"),
            new LinkedList<InterpreterContextRunner>(), null));

    intpB.open();
    assertEquals(1, process.referenceCount());

    intpA.close();
    assertEquals(0, process.referenceCount());
    intpB.close();
    assertEquals(0, process.referenceCount());

    assertFalse(process.isRunning());

  }

  @Test
  public void testExecuteIncorrectPrecode() throws TTransportException, IOException {
    Properties p = new Properties();
    p.put("zeppelin.MockInterpreterA.precode", "fail test");
    intpGroup.put("note", new LinkedList<Interpreter>());

    RemoteInterpreter intpA = createMockInterpreterA(p);

    intpGroup.get("note").add(intpA);

    intpA.setInterpreterGroup(intpGroup);

    RemoteInterpreterProcess process = intpA.getInterpreterProcess();

    intpA.open();
    
    InterpreterResult result = intpA.interpret("1",
        new InterpreterContext(
            "note",
            "id",
            null,
            "title",
            "text",
            new AuthenticationInfo(),
            new HashMap<String, Object>(),
            new GUI(),
            new AngularObjectRegistry(intpGroup.getId(), null),
            new LocalResourcePool("pool1"),
            new LinkedList<InterpreterContextRunner>(), null));



    intpA.close();
    assertEquals(Code.ERROR, result.code());
  }

  @Test
  public void testExecuteCorrectPrecode() throws TTransportException, IOException {
    Properties p = new Properties();
    p.put("zeppelin.MockInterpreterA.precode", "2");
    intpGroup.put("note", new LinkedList<Interpreter>());

    RemoteInterpreter intpA = createMockInterpreterA(p);

    intpGroup.get("note").add(intpA);

    intpA.setInterpreterGroup(intpGroup);

    RemoteInterpreterProcess process = intpA.getInterpreterProcess();

    intpA.open();

    InterpreterResult result = intpA.interpret("1",
        new InterpreterContext(
            "note",
            "id",
            null,
            "title",
            "text",
            new AuthenticationInfo(),
            new HashMap<String, Object>(),
            new GUI(),
            new AngularObjectRegistry(intpGroup.getId(), null),
            new LocalResourcePool("pool1"),
            new LinkedList<InterpreterContextRunner>(), null));



    intpA.close();
    assertEquals(Code.SUCCESS, result.code());
    assertEquals("1", result.message().get(0).getData());
  }

  @Test
  public void testRemoteInterperterErrorStatus() throws TTransportException, IOException {
    Properties p = new Properties();

    RemoteInterpreter intpA = createMockInterpreterA(p);

    intpGroup.put("note", new LinkedList<Interpreter>());
    intpGroup.get("note").add(intpA);
    intpA.setInterpreterGroup(intpGroup);

    intpA.open();
    InterpreterResult ret = intpA.interpret("non numeric value",
        new InterpreterContext(
            "noteId",
            "id",
            null,
            "title",
            "text",
            new AuthenticationInfo(),
            new HashMap<String, Object>(),
            new GUI(),
            new AngularObjectRegistry(intpGroup.getId(), null),
            new LocalResourcePool("pool1"),
            new LinkedList<InterpreterContextRunner>(), null));

    assertEquals(Code.ERROR, ret.code());
  }

  @Test
  public void testRemoteSchedulerSharing() throws TTransportException, IOException {
    Properties p = new Properties();
    intpGroup.put("note", new LinkedList<Interpreter>());

    RemoteInterpreter intpA = new RemoteInterpreter(
        p,
        "note",
        MockInterpreterA.class.getName(),
        new File(INTERPRETER_SCRIPT).getAbsolutePath(),
        "fake",
        "fakeRepo",
        env,
        10 * 1000,
        null,
        null,
        "anonymous",
        false);

    intpGroup.get("note").add(intpA);
    intpA.setInterpreterGroup(intpGroup);

    RemoteInterpreter intpB = new RemoteInterpreter(
        p,
        "note",
        MockInterpreterB.class.getName(),
        new File(INTERPRETER_SCRIPT).getAbsolutePath(),
        "fake",
        "fakeRepo",
        env,
        10 * 1000,
        null,
        null,
        "anonymous",
        false);

    intpGroup.get("note").add(intpB);
    intpB.setInterpreterGroup(intpGroup);

    intpA.open();
    intpB.open();

    long start = System.currentTimeMillis();
    InterpreterResult ret = intpA.interpret("500",
        new InterpreterContext(
            "note",
            "id",
            null,
            "title",
            "text",
            new AuthenticationInfo(),
            new HashMap<String, Object>(),
            new GUI(),
            new AngularObjectRegistry(intpGroup.getId(), null),
            new LocalResourcePool("pool1"),
            new LinkedList<InterpreterContextRunner>(), null));
    assertEquals("500", ret.message().get(0).getData());

    ret = intpB.interpret("500",
        new InterpreterContext(
            "note",
            "id",
            null,
            "title",
            "text",
            new AuthenticationInfo(),
            new HashMap<String, Object>(),
            new GUI(),
            new AngularObjectRegistry(intpGroup.getId(), null),
            new LocalResourcePool("pool1"),
            new LinkedList<InterpreterContextRunner>(), null));
    assertEquals("1000", ret.message().get(0).getData());
    long end = System.currentTimeMillis();
    assertTrue(end - start >= 1000);


    intpA.close();
    intpB.close();
  }

  @Test
  public void testRemoteSchedulerSharingSubmit() throws TTransportException, IOException, InterruptedException {
    Properties p = new Properties();
    intpGroup.put("note", new LinkedList<Interpreter>());

    final RemoteInterpreter intpA = createMockInterpreterA(p);

    intpGroup.get("note").add(intpA);
    intpA.setInterpreterGroup(intpGroup);

    final RemoteInterpreter intpB = createMockInterpreterB(p);

    intpGroup.get("note").add(intpB);
    intpB.setInterpreterGroup(intpGroup);

    intpA.open();
    intpB.open();

    long start = System.currentTimeMillis();
    Job jobA = new Job("jobA", null) {
      private Object r;

      @Override
      public Object getReturn() {
        return r;
      }

      @Override
      public void setResult(Object results) {
        this.r = results;
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
        return intpA.interpret("500",
            new InterpreterContext(
                "note",
                "jobA",
                null,
                "title",
                "text",
                new AuthenticationInfo(),
                new HashMap<String, Object>(),
                new GUI(),
                new AngularObjectRegistry(intpGroup.getId(), null),
                new LocalResourcePool("pool1"),
                new LinkedList<InterpreterContextRunner>(), null));
      }

      @Override
      protected boolean jobAbort() {
        return false;
      }

    };
    intpA.getScheduler().submit(jobA);

    Job jobB = new Job("jobB", null) {

      private Object r;

      @Override
      public Object getReturn() {
        return r;
      }

      @Override
      public void setResult(Object results) {
        this.r = results;
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
        return intpB.interpret("500",
            new InterpreterContext(
                "note",
                "jobB",
                null,
                "title",
                "text",
                new AuthenticationInfo(),
                new HashMap<String, Object>(),
                new GUI(),
                new AngularObjectRegistry(intpGroup.getId(), null),
                new LocalResourcePool("pool1"),
                new LinkedList<InterpreterContextRunner>(), null));
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

    assertEquals("1000", ((InterpreterResult) jobB.getReturn()).message().get(0).getData());

    intpA.close();
    intpB.close();
  }

  @Test
  public void testRunOrderPreserved() throws InterruptedException {
    Properties p = new Properties();
    intpGroup.put("note", new LinkedList<Interpreter>());

    final RemoteInterpreter intpA = createMockInterpreterA(p);

    intpGroup.get("note").add(intpA);
    intpA.setInterpreterGroup(intpGroup);

    intpA.open();

    int concurrency = 3;
    final List<InterpreterResultMessage> results = new LinkedList<>();

    Scheduler scheduler = intpA.getScheduler();
    for (int i = 0; i < concurrency; i++) {
      final String jobId = Integer.toString(i);
      scheduler.submit(new Job(jobId, Integer.toString(i), null, 200) {
        private Object r;

        @Override
        public Object getReturn() {
          return r;
        }

        @Override
        public void setResult(Object results) {
          this.r = results;
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
          InterpreterResult ret = intpA.interpret(getJobName(), new InterpreterContext(
              "note",
              jobId,
              null,
              "title",
              "text",
              new AuthenticationInfo(),
              new HashMap<String, Object>(),
              new GUI(),
              new AngularObjectRegistry(intpGroup.getId(), null),
              new LocalResourcePool("pool1"),
              new LinkedList<InterpreterContextRunner>(), null));

          synchronized (results) {
            results.addAll(ret.message());
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
    for (InterpreterResultMessage result : results) {
      assertEquals(Integer.toString(i++), result.getData());
    }
    assertEquals(concurrency, i);

    intpA.close();
  }


  @Test
  public void testRunParallel() throws InterruptedException {
    Properties p = new Properties();
    p.put("parallel", "true");
    intpGroup.put("note", new LinkedList<Interpreter>());

    final RemoteInterpreter intpA = createMockInterpreterA(p);

    intpGroup.get("note").add(intpA);
    intpA.setInterpreterGroup(intpGroup);

    intpA.open();

    int concurrency = 4;
    final int timeToSleep = 1000;
    final List<InterpreterResultMessage> results = new LinkedList<>();
    long start = System.currentTimeMillis();

    Scheduler scheduler = intpA.getScheduler();
    for (int i = 0; i < concurrency; i++) {
      final String jobId = Integer.toString(i);
      scheduler.submit(new Job(jobId, Integer.toString(i), null, 300) {
        private Object r;

        @Override
        public Object getReturn() {
          return r;
        }

        @Override
        public void setResult(Object results) {
          this.r = results;
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
          String stmt = Integer.toString(timeToSleep);
          InterpreterResult ret = intpA.interpret(stmt, new InterpreterContext(
              "note",
              jobId,
              null,
              "title",
              "text",
              new AuthenticationInfo(),
              new HashMap<String, Object>(),
              new GUI(),
              new AngularObjectRegistry(intpGroup.getId(), null),
              new LocalResourcePool("pool1"),
              new LinkedList<InterpreterContextRunner>(), null));

          synchronized (results) {
            results.addAll(ret.message());
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

  @Test
  public void testInterpreterGroupResetBeforeProcessStarts() {
    Properties p = new Properties();

    RemoteInterpreter intpA = createMockInterpreterA(p);

    intpA.setInterpreterGroup(intpGroup);
    RemoteInterpreterProcess processA = intpA.getInterpreterProcess();

    intpA.setInterpreterGroup(new InterpreterGroup(intpA.getInterpreterGroup().getId()));
    RemoteInterpreterProcess processB = intpA.getInterpreterProcess();

    assertNotSame(processA.hashCode(), processB.hashCode());
  }

  @Test
  public void testInterpreterGroupResetAfterProcessFinished() {
    Properties p = new Properties();
    intpGroup.put("note", new LinkedList<Interpreter>());

    RemoteInterpreter intpA = createMockInterpreterA(p);

    intpA.setInterpreterGroup(intpGroup);
    RemoteInterpreterProcess processA = intpA.getInterpreterProcess();
    intpA.open();

    processA.dereference();    // intpA.close();

    intpA.setInterpreterGroup(new InterpreterGroup(intpA.getInterpreterGroup().getId()));
    RemoteInterpreterProcess processB = intpA.getInterpreterProcess();

    assertNotSame(processA.hashCode(), processB.hashCode());
  }

  @Test
  public void testInterpreterGroupResetDuringProcessRunning() throws InterruptedException {
    Properties p = new Properties();
    intpGroup.put("note", new LinkedList<Interpreter>());

    final RemoteInterpreter intpA = createMockInterpreterA(p);

    intpGroup.get("note").add(intpA);
    intpA.setInterpreterGroup(intpGroup);

    intpA.open();

    Job jobA = new Job("jobA", null) {
      private Object r;

      @Override
      public Object getReturn() {
        return r;
      }

      @Override
      public void setResult(Object results) {
        this.r = results;
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
        return intpA.interpret("2000",
            new InterpreterContext(
                "note",
                "jobA",
                null,
                "title",
                "text",
                new AuthenticationInfo(),
                new HashMap<String, Object>(),
                new GUI(),
                new AngularObjectRegistry(intpGroup.getId(), null),
                new LocalResourcePool("pool1"),
                new LinkedList<InterpreterContextRunner>(), null));
      }

      @Override
      protected boolean jobAbort() {
        return false;
      }

    };
    intpA.getScheduler().submit(jobA);

    // wait for job started
    while (intpA.getScheduler().getJobsRunning().size() == 0) {
      Thread.sleep(100);
    }

    // restart interpreter
    RemoteInterpreterProcess processA = intpA.getInterpreterProcess();
    intpA.close();

    InterpreterGroup newInterpreterGroup =
        new InterpreterGroup(intpA.getInterpreterGroup().getId());
    newInterpreterGroup.put("note", new LinkedList<Interpreter>());

    intpA.setInterpreterGroup(newInterpreterGroup);
    intpA.open();
    RemoteInterpreterProcess processB = intpA.getInterpreterProcess();

    assertNotSame(processA.hashCode(), processB.hashCode());

  }

  @Test
  public void testRemoteInterpreterSharesTheSameSchedulerInstanceInTheSameGroup() {
    Properties p = new Properties();
    intpGroup.put("note", new LinkedList<Interpreter>());

    RemoteInterpreter intpA = createMockInterpreterA(p);

    intpGroup.get("note").add(intpA);
    intpA.setInterpreterGroup(intpGroup);

    RemoteInterpreter intpB = createMockInterpreterB(p);

    intpGroup.get("note").add(intpB);
    intpB.setInterpreterGroup(intpGroup);

    intpA.open();
    intpB.open();

    assertEquals(intpA.getScheduler(), intpB.getScheduler());
  }

  @Test
  public void testMultiInterpreterSession() {
    Properties p = new Properties();
    intpGroup.put("sessionA", new LinkedList<Interpreter>());
    intpGroup.put("sessionB", new LinkedList<Interpreter>());

    RemoteInterpreter intpAsessionA = createMockInterpreterA(p, "sessionA");
    intpGroup.get("sessionA").add(intpAsessionA);
    intpAsessionA.setInterpreterGroup(intpGroup);

    RemoteInterpreter intpBsessionA = createMockInterpreterB(p, "sessionA");
    intpGroup.get("sessionA").add(intpBsessionA);
    intpBsessionA.setInterpreterGroup(intpGroup);

    intpAsessionA.open();
    intpBsessionA.open();

    assertEquals(intpAsessionA.getScheduler(), intpBsessionA.getScheduler());

    RemoteInterpreter intpAsessionB = createMockInterpreterA(p, "sessionB");
    intpGroup.get("sessionB").add(intpAsessionB);
    intpAsessionB.setInterpreterGroup(intpGroup);

    RemoteInterpreter intpBsessionB = createMockInterpreterB(p, "sessionB");
    intpGroup.get("sessionB").add(intpBsessionB);
    intpBsessionB.setInterpreterGroup(intpGroup);

    intpAsessionB.open();
    intpBsessionB.open();

    assertEquals(intpAsessionB.getScheduler(), intpBsessionB.getScheduler());
    assertNotEquals(intpAsessionA.getScheduler(), intpAsessionB.getScheduler());
  }

  @Test
  public void should_push_local_angular_repo_to_remote() throws Exception {
    //Given
    final Client client = Mockito.mock(Client.class);
    final RemoteInterpreter intr = new RemoteInterpreter(new Properties(), "noteId",
        MockInterpreterA.class.getName(), "runner", "path", "localRepo", env, 10 * 1000, null,
        null, "anonymous", false);
    final AngularObjectRegistry registry = new AngularObjectRegistry("spark", null);
    registry.add("name", "DuyHai DOAN", "nodeId", "paragraphId");
    final InterpreterGroup interpreterGroup = new InterpreterGroup("groupId");
    interpreterGroup.setAngularObjectRegistry(registry);
    intr.setInterpreterGroup(interpreterGroup);

    final java.lang.reflect.Type registryType = new TypeToken<Map<String,
                Map<String, AngularObject>>>() {}.getType();
    final Gson gson = new Gson();
    final String expected = gson.toJson(registry.getRegistry(), registryType);

    //When
    intr.pushAngularObjectRegistryToRemote(client);

    //Then
    Mockito.verify(client).angularRegistryPush(expected);
  }

  @Test
  public void testEnvStringPattern() {
    assertFalse(RemoteInterpreterUtils.isEnvString(null));
    assertFalse(RemoteInterpreterUtils.isEnvString(""));
    assertFalse(RemoteInterpreterUtils.isEnvString("abcDEF"));
    assertFalse(RemoteInterpreterUtils.isEnvString("ABC-DEF"));
    assertTrue(RemoteInterpreterUtils.isEnvString("ABCDEF"));
    assertTrue(RemoteInterpreterUtils.isEnvString("ABC_DEF"));
    assertTrue(RemoteInterpreterUtils.isEnvString("ABC_DEF123"));
  }

  @Test
  public void testEnvronmentAndPropertySet() {
    Properties p = new Properties();
    p.setProperty("MY_ENV1", "env value 1");
    p.setProperty("my.property.1", "property value 1");

    RemoteInterpreter intp = new RemoteInterpreter(
        p,
        "note",
        MockInterpreterEnv.class.getName(),
        new File(INTERPRETER_SCRIPT).getAbsolutePath(),
        "fake",
        "fakeRepo",
        env,
        10 * 1000,
        null,
        null,
        "anonymous",
        false);

    intpGroup.put("note", new LinkedList<Interpreter>());
    intpGroup.get("note").add(intp);
    intp.setInterpreterGroup(intpGroup);

    intp.open();

    InterpreterContext context = new InterpreterContext(
        "noteId",
        "id",
        null,
        "title",
        "text",
        new AuthenticationInfo(),
        new HashMap<String, Object>(),
        new GUI(),
        new AngularObjectRegistry(intpGroup.getId(), null),
        new LocalResourcePool("pool1"),
        new LinkedList<InterpreterContextRunner>(), null);


    assertEquals("env value 1", intp.interpret("getEnv MY_ENV1", context).message().get(0).getData());
    assertEquals(Code.ERROR, intp.interpret("getProperty MY_ENV1", context).code());
    assertEquals(Code.ERROR, intp.interpret("getEnv my.property.1", context).code());
    assertEquals("property value 1", intp.interpret("getProperty my.property.1", context).message().get(0).getData());

    intp.close();
  }

}
