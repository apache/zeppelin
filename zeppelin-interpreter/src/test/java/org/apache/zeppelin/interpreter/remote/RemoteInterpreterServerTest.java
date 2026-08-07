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

import org.apache.thrift.TException;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.interpreter.thrift.InterpreterRPCException;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterContext;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterResult;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RemoteInterpreterServerTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteInterpreterServerTest.class);

  @Test
  void testStartStop() throws Exception {
    RemoteInterpreterServer server = new RemoteInterpreterServer("localhost",
        RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces(), ":", "groupId", true);

    startRemoteInterpreterServer(server, 10 * 1000);
    stopRemoteInterpreterServer(server, 10 * 10000);
  }

  @Test
  void testStartStopWithQueuedEvents() throws Exception {
    RemoteInterpreterServer server = new RemoteInterpreterServer("localhost",
        RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces(), ":", "groupId", true);
    server.intpEventClient = mock(RemoteInterpreterEventClient.class);
    startRemoteInterpreterServer(server, 10 * 1000);

    server.intpEventClient.onAppStatusUpdate("", "", "", "");
    stopRemoteInterpreterServer(server, 10 * 10000);
  }

  @Test
  void testInitDoesNotAcceptCallbackCredential() throws Exception {
    RemoteInterpreterServer server = new RemoteInterpreterServer("localhost",
        RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces(), ":", "groupId",
        true, "launch-token");
    Map<String, String> properties = new HashMap<>();
    properties.put(RemoteInterpreterEventClient.INTERPRETER_GROUP_PROPERTY, "groupId");
    properties.put(RemoteInterpreterEventClient.CALLBACK_TOKEN_PROPERTY, "untrusted-token");
    server.init(properties);

    assertFalse(server.getProperties().containsKey(
        RemoteInterpreterEventClient.CALLBACK_TOKEN_PROPERTY));
  }

  @Test
  void testExistingProcessRegistersOnlyAfterInitReadiness() throws Exception {
    RemoteInterpreterEventClient runtimeClient = mock(RemoteInterpreterEventClient.class);
    RemoteInterpreterEventClient registrationClient = mock(RemoteInterpreterEventClient.class);
    AtomicInteger createdClients = new AtomicInteger();
    RemoteInterpreterServer server = new RemoteInterpreterServer("localhost",
        RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces(), ":",
        "setting-existing_process", true, "callback-token", true) {
      @Override
      RemoteInterpreterEventClient createInterpreterEventClient(String eventServerHost,
                                                                 int eventServerPort,
                                                                 int connectionPoolSize) {
        return createdClients.getAndIncrement() == 0 ? runtimeClient : registrationClient;
      }
    };
    server.start();
    long deadline = System.currentTimeMillis() + 10_000;
    while (!server.isRunning() && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
    assertTrue(server.isRunning());
    assertEquals(0, createdClients.get());

    Map<String, String> properties = new HashMap<>();
    properties.put(RemoteInterpreterEventClient.INTERPRETER_GROUP_PROPERTY,
        "setting-existing_process");
    properties.put("zeppelin.interpreter.connection.poolsize", "1");
    server.init(properties);

    assertEquals(2, createdClients.get());
    assertSame(runtimeClient, server.intpEventClient);
    verify(registrationClient).registerInterpreterProcess(argThat(info ->
        info.getInterpreterGroupId().equals("setting-existing_process")));
    stopRemoteInterpreterServer(server, 10_000);
  }

  @Test
  void testRegistrationFailureStopsInterpreterServer() throws Exception {
    RemoteInterpreterEventClient runtimeClient = mock(RemoteInterpreterEventClient.class);
    RemoteInterpreterEventClient registrationClient = mock(RemoteInterpreterEventClient.class);
    doThrow(new RuntimeException("callback registration failed"))
        .when(registrationClient).registerInterpreterProcess(
            org.mockito.ArgumentMatchers.any());
    AtomicInteger createdClients = new AtomicInteger();
    RemoteInterpreterServer server = new RemoteInterpreterServer("localhost",
        RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces(), ":",
        "setting-existing_process", true, "callback-token", true) {
      @Override
      RemoteInterpreterEventClient createInterpreterEventClient(String eventServerHost,
                                                                 int eventServerPort,
                                                                 int connectionPoolSize) {
        return createdClients.getAndIncrement() == 0 ? runtimeClient : registrationClient;
      }

      @Override
      long getCallbackRegistrationTimeoutSeconds() {
        return 1;
      }
    };
    server.start();
    long deadline = System.currentTimeMillis() + 10_000;
    while (!server.isRunning() && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
    assertTrue(server.isRunning());

    Map<String, String> properties = new HashMap<>();
    properties.put(RemoteInterpreterEventClient.INTERPRETER_GROUP_PROPERTY,
        "setting-existing_process");
    properties.put("zeppelin.interpreter.connection.poolsize", "1");
    assertThrows(InterpreterRPCException.class, () -> server.init(properties));

    deadline = System.currentTimeMillis() + 10_000;
    while (server.isRunning() && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
    assertFalse(server.isRunning());
    verify(registrationClient, atLeastOnce()).registerInterpreterProcess(
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  void testInitKeepsReconnectedEventClient() throws Exception {
    RemoteInterpreterEventClient initialClient = mock(RemoteInterpreterEventClient.class);
    RemoteInterpreterEventClient reconnectedClient = mock(RemoteInterpreterEventClient.class);
    AtomicInteger createdClients = new AtomicInteger();
    RemoteInterpreterServer server = new RemoteInterpreterServer("localhost",
        RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces(), ":", "groupId",
        false, "callback-token", false) {
      @Override
      RemoteInterpreterEventClient createInterpreterEventClient(String eventServerHost,
                                                                 int eventServerPort,
                                                                 int connectionPoolSize) {
        return createdClients.getAndIncrement() == 0 ? initialClient : reconnectedClient;
      }
    };
    Map<String, String> properties = new HashMap<>();
    properties.put(RemoteInterpreterEventClient.INTERPRETER_GROUP_PROPERTY, "groupId");
    properties.put(RemoteInterpreterEventClient.CALLBACK_TOKEN_PROPERTY, "untrusted-token");
    properties.put("zeppelin.interpreter.connection.poolsize", "1");
    server.init(properties);
    assertSame(initialClient, server.intpEventClient);

    server.reconnect("localhost",
        RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces());
    assertNotSame(initialClient, reconnectedClient);
    assertSame(reconnectedClient, server.intpEventClient);
    verify(reconnectedClient).registerInterpreterProcess(argThat(info ->
        info.getInterpreterGroupId().equals("groupId")
            && info.getHost() != null
            && !info.getHost().isEmpty()
            && info.getPort() == server.getPort()));

    server.init(properties);
    assertSame(reconnectedClient, server.intpEventClient);
    reconnectedClient.close();
  }

  @Test
  void testFailedReconnectKeepsPreviousEventClient() throws Exception {
    RemoteInterpreterEventClient initialClient = mock(RemoteInterpreterEventClient.class);
    RemoteInterpreterEventClient failedReplacement = mock(RemoteInterpreterEventClient.class);
    doThrow(new RuntimeException("callback proof failed"))
        .when(failedReplacement).registerInterpreterProcess(
            org.mockito.ArgumentMatchers.any());
    AtomicInteger createdClients = new AtomicInteger();
    RemoteInterpreterServer server = new RemoteInterpreterServer("localhost",
        RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces(), ":", "groupId",
        false, "callback-token", false) {
      @Override
      RemoteInterpreterEventClient createInterpreterEventClient(String eventServerHost,
                                                                 int eventServerPort,
                                                                 int connectionPoolSize) {
        return createdClients.getAndIncrement() == 0 ? initialClient : failedReplacement;
      }
    };
    Map<String, String> properties = new HashMap<>();
    properties.put(RemoteInterpreterEventClient.INTERPRETER_GROUP_PROPERTY, "groupId");
    properties.put(RemoteInterpreterEventClient.CALLBACK_TOKEN_PROPERTY, "untrusted-token");
    properties.put("zeppelin.interpreter.connection.poolsize", "1");
    server.init(properties);

    assertThrows(InterpreterRPCException.class, () -> server.reconnect("localhost",
        RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces()));

    assertSame(initialClient, server.intpEventClient);
    verify(failedReplacement).close();
    verify(initialClient, never()).close();
  }

  private void startRemoteInterpreterServer(RemoteInterpreterServer server, int timeout)
          throws InterruptedException, TException {
    assertEquals(false, server.isRunning());
    server.start();
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() - startTime < timeout) {
      if (server.isRunning()) {
        break;
      }
      Thread.sleep(200);
    }
    assertEquals(true, server.isRunning());
    assertEquals(true, RemoteInterpreterUtils.checkIfRemoteEndpointAccessible("localhost",
        server.getPort()));

    server.init(new HashMap<>());
    assertNotNull(server.getProperties());
    assertNotNull(server.getLifecycleManager());
  }

  private void stopRemoteInterpreterServer(RemoteInterpreterServer server, int timeout)
      throws TException, InterruptedException {
    assertEquals(true, server.isRunning());
    server.shutdown();
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() - startTime < timeout) {
      if (!server.isRunning()) {
        break;
      }
      Thread.sleep(200);
    }
    assertEquals(false, server.isRunning());
    assertEquals(false, RemoteInterpreterUtils.checkIfRemoteEndpointAccessible("localhost",
        server.getPort()));
  }

  @Test
  void testInterpreter() throws Exception {
    final RemoteInterpreterServer server = new RemoteInterpreterServer("localhost",
        RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces(), ":", "groupId", true);
    server.init(new HashMap<>());
    server.intpEventClient = mock(RemoteInterpreterEventClient.class);

    Map<String, String> intpProperties = new HashMap<>();
    intpProperties.put("property_1", "value_1");
    intpProperties.put("zeppelin.interpreter.localRepo", "/tmp");

    // create Test1Interpreter in session_1
    server.createInterpreter("group_1", "session_1", Test1Interpreter.class.getName(),
        intpProperties, "user_1");
    Test1Interpreter interpreter1 = (Test1Interpreter)
        ((LazyOpenInterpreter) server.getInterpreterGroup().get("session_1").get(0))
            .getInnerInterpreter();
    assertEquals(1, server.getInterpreterGroup().getSessionNum());
    assertEquals(1, server.getInterpreterGroup().get("session_1").size());
    assertEquals(2, interpreter1.getProperties().size());
    assertEquals("value_1", interpreter1.getProperty("property_1"));

    // create duplicated Test1Interpreter in session_1
    server.createInterpreter("group_1", "session_1", Test1Interpreter.class.getName(),
            intpProperties, "user_1");
    assertEquals(1, server.getInterpreterGroup().get("session_1").size());

    // create Test2Interpreter in session_1
    server.createInterpreter("group_1", "session_1", Test2Interpreter.class.getName(),
        intpProperties, "user_1");
    assertEquals(2, server.getInterpreterGroup().get("session_1").size());

    // create Test1Interpreter in session_2
    server.createInterpreter("group_1", "session_2", Test1Interpreter.class.getName(),
        intpProperties, "user_1");
    assertEquals(2, server.getInterpreterGroup().getSessionNum());
    assertEquals(2, server.getInterpreterGroup().get("session_1").size());
    assertEquals(1, server.getInterpreterGroup().get("session_2").size());

    final RemoteInterpreterContext intpContext = new RemoteInterpreterContext();
    intpContext.setNoteId("note_1");
    intpContext.setParagraphId("paragraph_1");
    intpContext.setGui("{}");
    intpContext.setNoteGui("{}");
    intpContext.setLocalProperties(new HashMap<>());

    // single output of SUCCESS
    RemoteInterpreterResult result = server.interpret("session_1", Test1Interpreter.class.getName(),
        "SINGLE_OUTPUT_SUCCESS", intpContext);
    assertEquals("SUCCESS", result.code);
    assertEquals(1, result.getMsg().size());
    assertEquals("SINGLE_OUTPUT_SUCCESS", result.getMsg().get(0).getData());

    // combo output of SUCCESS
    result = server.interpret("session_1", Test1Interpreter.class.getName(), "COMBO_OUTPUT_SUCCESS",
        intpContext);
    assertEquals("SUCCESS", result.code);
    assertEquals(2, result.getMsg().size());
    assertEquals("INTERPRETER_OUT", result.getMsg().get(0).getData());
    assertEquals("SINGLE_OUTPUT_SUCCESS", result.getMsg().get(1).getData());

    // single output of ERROR
    result = server.interpret("session_1", Test1Interpreter.class.getName(), "SINGLE_OUTPUT_ERROR",
        intpContext);
    assertEquals("ERROR", result.code);
    assertEquals(1, result.getMsg().size());
    assertEquals("SINGLE_OUTPUT_ERROR", result.getMsg().get(0).getData());

    // getFormType
    String formType = server.getFormType("session_1", Test1Interpreter.class.getName());
    assertEquals("NATIVE", formType);

    // cancel
    Thread sleepThread = new Thread() {
      @Override
      public void run() {
        try {
          server.interpret("session_1", Test1Interpreter.class.getName(), "SLEEP", intpContext);
        } catch (TException e) {
          e.printStackTrace();
        }
      }
    };
    sleepThread.start();

    Thread.sleep(1000);
    assertFalse(interpreter1.cancelled.get());
    server.cancel("session_1", Test1Interpreter.class.getName(), intpContext);
    // Sleep 1 second, because cancel is async.
    Thread.sleep(1000);
    assertTrue(interpreter1.cancelled.get());

    // getProgress
    assertEquals(10, server.getProgress("session_1", Test1Interpreter.class.getName(),
        intpContext));

    // before close -> thread of Test1Interpreter is running
    assertEquals(true, isThreadRunning(interpreter1.getScheduler().getName()));

    // close opened Test1Interpreter -> remove from interpreterGroup
    server.close("session_1", Test1Interpreter.class.getName());
    assertTrue(interpreter1.closed.get());
    assertEquals(1, server.getInterpreterGroup().get("session_1").size());

    // close unopened Test2Interpreter -> keep in interpreterGroup
    server.close("session_1", Test2Interpreter.class.getName());
    assertEquals(1, server.getInterpreterGroup().get("session_1").size());

    // // Close is async process
    Thread.sleep(100);
    // after close -> thread of Test1Interpreter is not running
    assertEquals(false, isThreadRunning(interpreter1.getScheduler().getName()));
  }

  private boolean isThreadRunning(String schedulerName) {
    boolean res = false;
    Set<Thread> threads = Thread.getAllStackTraces().keySet();
    for (Thread t : threads) {
      if (!t.getName().contains(schedulerName)) continue;
      res = true;
      break;
    }
    return res;
  }

  public static class Test1Interpreter extends Interpreter {

    AtomicBoolean cancelled = new AtomicBoolean();
    AtomicBoolean closed = new AtomicBoolean();

    public Test1Interpreter(Properties properties) {
      super(properties);
    }

    @Override
    public void open() {

    }

    @Override
    public InterpreterResult interpret(String st, InterpreterContext context) {
      if (st.equals("SINGLE_OUTPUT_SUCCESS")) {
        return new InterpreterResult(InterpreterResult.Code.SUCCESS, "SINGLE_OUTPUT_SUCCESS");
      } else if (st.equals("SINGLE_OUTPUT_ERROR")) {
        return new InterpreterResult(InterpreterResult.Code.ERROR, "SINGLE_OUTPUT_ERROR");
      } else if (st.equals("COMBO_OUTPUT_SUCCESS")) {
        try {
          context.out.write("INTERPRETER_OUT");
        } catch (IOException e) {
          LOGGER.error("IO Error", e);
        }
        return new InterpreterResult(InterpreterResult.Code.SUCCESS, "SINGLE_OUTPUT_SUCCESS");
      } else if (st.equals("SLEEP")) {
        int count = 0;
        while (!cancelled.get() || count > 30) {
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            return new InterpreterResult(InterpreterResult.Code.ERROR, "SLEEP_SUCCESS");
          }
          ++count;
        }
        return new InterpreterResult(InterpreterResult.Code.SUCCESS, "SLEEP_SUCCESS");
      }
      return null;
    }

    @Override
    public void cancel(InterpreterContext context) throws InterpreterException {
      cancelled.set(true);
    }

    @Override
    public FormType getFormType() throws InterpreterException {
      return FormType.NATIVE;
    }

    @Override
    public int getProgress(InterpreterContext context) throws InterpreterException {
      return 10;
    }

    @Override
    public void close() {
      closed.set(true);
    }

  }

  public static class Test2Interpreter extends Interpreter {


    public Test2Interpreter(Properties properties) {
      super(properties);
    }

    @Override
    public void open() {

    }

    @Override
    public InterpreterResult interpret(String st, InterpreterContext context) {
      return null;
    }

    @Override
    public void cancel(InterpreterContext context) throws InterpreterException {

    }

    @Override
    public FormType getFormType() throws InterpreterException {
      return FormType.NATIVE;
    }

    @Override
    public int getProgress(InterpreterContext context) throws InterpreterException {
      return 0;
    }

    @Override
    public void close() {

    }

  }
}
