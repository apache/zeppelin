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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.interpreter.thrift.InterpreterRPCException;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterContext;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterResult;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService;
import org.apache.zeppelin.user.AuthenticationInfo;

/**
 * Characterizes the current Server-to-Interpreter control-plane contract over an actual Thrift
 * socket. These tests intentionally exercise the generated client and processor instead of calling
 * {@link RemoteInterpreterServer} methods directly.
 */
public class RemoteInterpreterServiceContractTest {

  private static final String INTERPRETER_GROUP_ID = "contract-group";
  private static final String SESSION_ID = "contract-session";
  private static final String USER_NAME = "contract-user";
  private static final String LOCAL_REPOSITORY_PROPERTY = "zeppelin.interpreter.localRepo";
  private static final String FORCE_SHUTDOWN_PROPERTY = "zeppelin.interpreter.forceShutdown";
  private static final int SOCKET_TIMEOUT_MS = 10_000;
  private static final int ASYNC_TIMEOUT_SECONDS = 10;

  @TempDir
  Path localRepository;

  private RemoteInterpreterServer server;
  private TSocket executionTransport;
  private TSocket controlTransport;
  private RemoteInterpreterService.Client executionClient;
  private RemoteInterpreterService.Client controlClient;
  private String previousLocalRepository;
  private String previousForceShutdown;

  @BeforeEach
  void setUp() throws Exception {
    previousLocalRepository = System.getProperty(LOCAL_REPOSITORY_PROPERTY);
    previousForceShutdown = System.getProperty(FORCE_SHUTDOWN_PROPERTY);
    ContractInterpreter.reset();
    server = new RemoteInterpreterServer(
        "localhost",
        RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces(),
        ":",
        INTERPRETER_GROUP_ID,
        true);
    server.intpEventClient = mock(RemoteInterpreterEventClient.class);
    server.start();
    awaitServerRunning();

    executionTransport = openTransport();
    controlTransport = openTransport();
    executionClient = new RemoteInterpreterService.Client(
        new TBinaryProtocol(executionTransport));
    controlClient = new RemoteInterpreterService.Client(new TBinaryProtocol(controlTransport));

    controlClient.init(Collections.emptyMap());
    Map<String, String> properties = new HashMap<>();
    properties.put(LOCAL_REPOSITORY_PROPERTY, localRepository.toString());
    properties.put(FORCE_SHUTDOWN_PROPERTY, "false");
    controlClient.createInterpreter(
        INTERPRETER_GROUP_ID,
        SESSION_ID,
        ContractInterpreter.class.getName(),
        properties,
        USER_NAME);
  }

  @AfterEach
  void tearDown() throws Exception {
    try {
      ContractInterpreter.releaseInterpretation();
      closeTransport(executionTransport);
      closeTransport(controlTransport);

      if (server != null) {
        server.close(SESSION_ID, ContractInterpreter.class.getName());
        if (server.isRunning()) {
          server.shutdown();
        }
        awaitServerStopped();
        server.join(SOCKET_TIMEOUT_MS);
        assertFalse(server.isAlive(), "RemoteInterpreterServer did not terminate");
      }
    } finally {
      try {
        if (server != null) {
          shutdownResultCleaner();
        }
      } finally {
        restoreSystemProperty(LOCAL_REPOSITORY_PROPERTY, previousLocalRepository);
        restoreSystemProperty(FORCE_SHUTDOWN_PROPERTY, previousForceShutdown);
      }
    }
  }

  @Test
  void shouldRoundTripLifecycleAndLazyOpenOverThrift() throws Exception {
    Map<String, String> duplicateProperties = new HashMap<>();
    duplicateProperties.put(LOCAL_REPOSITORY_PROPERTY, localRepository.toString());
    controlClient.createInterpreter(
        INTERPRETER_GROUP_ID,
        SESSION_ID,
        ContractInterpreter.class.getName(),
        duplicateProperties,
        USER_NAME);
    assertEquals(0, ContractInterpreter.OPEN_CALLS.get());

    assertEquals(
        Interpreter.FormType.NATIVE.name(),
        controlClient.getFormType(SESSION_ID, ContractInterpreter.class.getName()));
    assertEquals(0, ContractInterpreter.OPEN_CALLS.get());
    assertEquals(
        0,
        controlClient.getProgress(
            SESSION_ID, ContractInterpreter.class.getName(), context("before-open")));

    List<InterpreterCompletion> completions = controlClient.completion(
        SESSION_ID,
        ContractInterpreter.class.getName(),
        "sel",
        3,
        context("completion"));
    assertEquals(1, completions.size());
    assertEquals("select", completions.get(0).getName());
    assertEquals("select *", completions.get(0).getValue());
    assertEquals("keyword", completions.get(0).getMeta());
    assertEquals("sel", ContractInterpreter.COMPLETION_BUFFER.get());
    assertEquals(3, ContractInterpreter.COMPLETION_CURSOR.get());
    assertEquals("completion", ContractInterpreter.COMPLETION_PARAGRAPH_ID.get());
    assertEquals(1, ContractInterpreter.OPEN_CALLS.get());

    RemoteInterpreterResult result = executionClient.interpret(
        SESSION_ID,
        ContractInterpreter.class.getName(),
        "echo:hello",
        context("echo"));
    assertEquals(InterpreterResult.Code.SUCCESS.name(), result.getCode());
    assertEquals(1, result.getMsgSize());
    assertEquals("hello", result.getMsg().get(0).getData());
    assertEquals(1, ContractInterpreter.OPEN_CALLS.get());

    String largePayload = "x".repeat(1024 * 1024);
    result = executionClient.interpret(
        SESSION_ID,
        ContractInterpreter.class.getName(),
        "length:" + largePayload,
        context("large-payload"));
    assertEquals(InterpreterResult.Code.SUCCESS.name(), result.getCode());
    assertEquals(Integer.toString(largePayload.length()), result.getMsg().get(0).getData());

    controlClient.close(SESSION_ID, ContractInterpreter.class.getName());
    assertEquals(1, ContractInterpreter.CLOSE_CALLS.get());
    InterpreterRPCException failure = assertThrows(
        InterpreterRPCException.class,
        () -> controlClient.getFormType(SESSION_ID, ContractInterpreter.class.getName()));
    assertTrue(failure.getErrorMessage().contains("not found"));
  }

  @Test
  void shouldDeliverCancelWhileInterpretIsRunningOverThrift() throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<RemoteInterpreterResult> interpretation = executor.submit(() ->
        executionClient.interpret(
            SESSION_ID,
            ContractInterpreter.class.getName(),
            "block-until-cancelled",
            context("running-paragraph")));

    try {
      assertTrue(
          ContractInterpreter.interpretStarted.await(
              ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS),
          "Interpreter did not start");
      assertEquals(
          "RUNNING",
          controlClient.getStatus(SESSION_ID, "running-paragraph"));
      assertEquals(
          42,
          controlClient.getProgress(
              SESSION_ID,
              ContractInterpreter.class.getName(),
              context("running-paragraph")));

      controlClient.cancel(
          SESSION_ID,
          ContractInterpreter.class.getName(),
          context("running-paragraph"));
      assertTrue(
          ContractInterpreter.cancelObserved.await(
              ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS),
          "Interpreter did not observe cancellation");
      assertEquals("contract-note", ContractInterpreter.CANCEL_NOTE_ID.get());
      assertEquals("running-paragraph", ContractInterpreter.CANCEL_PARAGRAPH_ID.get());

      interpretation.get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      assertTrue(interpretation.isDone(), "Interpret call did not return after cancellation");
    } finally {
      ContractInterpreter.releaseInterpretation();
      if (!interpretation.isDone()) {
        closeTransport(executionTransport);
      }
      interpretation.cancel(true);
      executor.shutdownNow();
      assertTrue(
          executor.awaitTermination(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS),
          "Interpret executor did not terminate");
    }
  }

  @Test
  void shouldExposeExecutionDeclaredAndRawTransportFailureShapes() throws Exception {
    RemoteInterpreterResult result = executionClient.interpret(
        SESSION_ID,
        ContractInterpreter.class.getName(),
        "throw-application-error",
        context("application-error"));
    assertEquals(InterpreterResult.Code.ERROR.name(), result.getCode());
    assertTrue(result.getMsg().get(0).getData().contains("contract interpret failure"));

    InterpreterRPCException completionFailure = assertThrows(
        InterpreterRPCException.class,
        () -> controlClient.completion(
            SESSION_ID,
            ContractInterpreter.class.getName(),
            "throw-completion-error",
            0,
            context("completion-error")));
    assertTrue(
        completionFailure.getErrorMessage().contains(
            "Fail to get completion, cause: contract completion failure"));

    InterpreterRPCException missingInterpreter = assertThrows(
        InterpreterRPCException.class,
        () -> controlClient.getFormType("missing-session", ContractInterpreter.class.getName()));
    assertTrue(missingInterpreter.getErrorMessage().contains("not initialized"));

    controlTransport.close();
    assertThrows(
        TTransportException.class,
        () -> controlClient.getStatus(SESSION_ID, "missing-job"));
  }

  private TSocket openTransport() throws TTransportException {
    TSocket transport = new TSocket("localhost", server.getPort(), SOCKET_TIMEOUT_MS);
    transport.open();
    return transport;
  }

  private RemoteInterpreterContext context(String paragraphId) {
    RemoteInterpreterContext context = new RemoteInterpreterContext();
    context.setNoteId("contract-note");
    context.setNoteName("Contract Note");
    context.setParagraphId(paragraphId);
    context.setReplName("contract");
    context.setParagraphTitle("Contract Paragraph");
    context.setParagraphText("contract text");
    context.setAuthenticationInfo(AuthenticationInfo.ANONYMOUS.toJson());
    context.setConfig("{}");
    context.setGui("{}");
    context.setNoteGui("{}");
    context.setLocalProperties(new HashMap<>());
    return context;
  }

  private void awaitServerRunning() throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SOCKET_TIMEOUT_MS);
    while (!server.isRunning() && System.nanoTime() < deadline) {
      Thread.sleep(20);
    }
    assertTrue(server.isRunning(), "RemoteInterpreterServer did not start");
  }

  private void awaitServerStopped() throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SOCKET_TIMEOUT_MS);
    while (server.isRunning() && System.nanoTime() < deadline) {
      Thread.sleep(20);
    }
    assertFalse(server.isRunning(), "RemoteInterpreterServer did not stop");
  }

  private void shutdownResultCleaner() throws Exception {
    // RemoteInterpreterServer does not expose this executor's lifecycle in test mode.
    java.lang.reflect.Field resultCleaner =
        RemoteInterpreterServer.class.getDeclaredField("resultCleanService");
    resultCleaner.setAccessible(true);
    ScheduledExecutorService executor = (ScheduledExecutorService) resultCleaner.get(server);
    executor.shutdownNow();
    executor.awaitTermination(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
  }

  private void closeTransport(TSocket transport) {
    if (transport != null) {
      transport.close();
    }
  }

  private void restoreSystemProperty(String property, String previousValue) {
    if (previousValue == null) {
      System.clearProperty(property);
    } else {
      System.setProperty(property, previousValue);
    }
  }

  public static class ContractInterpreter extends Interpreter {

    private static final AtomicInteger OPEN_CALLS = new AtomicInteger();
    private static final AtomicInteger CLOSE_CALLS = new AtomicInteger();
    private static final AtomicReference<String> COMPLETION_BUFFER = new AtomicReference<>();
    private static final AtomicInteger COMPLETION_CURSOR = new AtomicInteger();
    private static final AtomicReference<String> COMPLETION_PARAGRAPH_ID =
        new AtomicReference<>();
    private static final AtomicReference<String> CANCEL_NOTE_ID = new AtomicReference<>();
    private static final AtomicReference<String> CANCEL_PARAGRAPH_ID = new AtomicReference<>();
    private static final AtomicBoolean CANCELLED = new AtomicBoolean();
    private static CountDownLatch interpretStarted;
    private static CountDownLatch cancelObserved;
    private static CountDownLatch releaseInterpret;

    public ContractInterpreter(Properties properties) {
      super(properties);
    }

    static void reset() {
      OPEN_CALLS.set(0);
      CLOSE_CALLS.set(0);
      COMPLETION_BUFFER.set(null);
      COMPLETION_CURSOR.set(0);
      COMPLETION_PARAGRAPH_ID.set(null);
      CANCEL_NOTE_ID.set(null);
      CANCEL_PARAGRAPH_ID.set(null);
      CANCELLED.set(false);
      interpretStarted = new CountDownLatch(1);
      cancelObserved = new CountDownLatch(1);
      releaseInterpret = new CountDownLatch(1);
    }

    static void releaseInterpretation() {
      if (releaseInterpret != null) {
        releaseInterpret.countDown();
      }
    }

    @Override
    public void open() {
      OPEN_CALLS.incrementAndGet();
    }

    @Override
    public void close() {
      CLOSE_CALLS.incrementAndGet();
    }

    @Override
    public InterpreterResult interpret(String statement, InterpreterContext context)
        throws InterpreterException {
      if (statement.startsWith("echo:")) {
        return new InterpreterResult(
            InterpreterResult.Code.SUCCESS, statement.substring("echo:".length()));
      }
      if (statement.startsWith("length:")) {
        return new InterpreterResult(
            InterpreterResult.Code.SUCCESS,
            Integer.toString(statement.substring("length:".length()).length()));
      }
      if ("throw-application-error".equals(statement)) {
        throw new InterpreterException("contract interpret failure");
      }
      if ("block-until-cancelled".equals(statement)) {
        context.setProgress(42);
        interpretStarted.countDown();
        try {
          if (!releaseInterpret.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            return new InterpreterResult(InterpreterResult.Code.ERROR, "cancel timed out");
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return new InterpreterResult(InterpreterResult.Code.ERROR, "interpret interrupted");
        }
        return new InterpreterResult(
            CANCELLED.get() ? InterpreterResult.Code.SUCCESS : InterpreterResult.Code.ERROR,
            CANCELLED.get() ? "cancelled" : "released without cancellation");
      }
      return new InterpreterResult(InterpreterResult.Code.ERROR, "unsupported statement");
    }

    @Override
    public void cancel(InterpreterContext context) {
      CANCEL_NOTE_ID.set(context.getNoteId());
      CANCEL_PARAGRAPH_ID.set(context.getParagraphId());
      CANCELLED.set(true);
      cancelObserved.countDown();
      releaseInterpret.countDown();
    }

    @Override
    public FormType getFormType() {
      return FormType.NATIVE;
    }

    @Override
    public int getProgress(InterpreterContext context) {
      return 7;
    }

    @Override
    public List<InterpreterCompletion> completion(
        String buffer, int cursor, InterpreterContext context) throws InterpreterException {
      if ("throw-completion-error".equals(buffer)) {
        throw new InterpreterException("contract completion failure");
      }
      COMPLETION_BUFFER.set(buffer);
      COMPLETION_CURSOR.set(cursor);
      COMPLETION_PARAGRAPH_ID.set(context.getParagraphId());
      return Collections.singletonList(
          new InterpreterCompletion("select", "select *", "keyword"));
    }
  }
}
