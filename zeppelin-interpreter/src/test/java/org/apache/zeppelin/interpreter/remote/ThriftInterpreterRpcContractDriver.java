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

import static org.mockito.Mockito.mock;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.thrift.TApplicationException;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransportException;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.interpreter.thrift.InterpreterRPCException;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterContext;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterResult;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterResultMessage;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService;
import org.apache.zeppelin.user.AuthenticationInfo;

/** Runs the neutral control RPC contract through generated Thrift clients and a real socket. */
public final class ThriftInterpreterRpcContractDriver implements InterpreterRpcContractDriver {

  private static final String INTERPRETER_GROUP_ID = "contract-group";
  private static final String SESSION_ID = "contract-session";
  private static final String USER_NAME = "contract-user";
  private static final String LOCAL_REPOSITORY_PROPERTY = "zeppelin.interpreter.localRepo";
  private static final String FORCE_SHUTDOWN_PROPERTY = "zeppelin.interpreter.forceShutdown";
  private static final int SOCKET_TIMEOUT_MILLIS = 10_000;
  private static final Duration SERVER_TIMEOUT = Duration.ofSeconds(10);
  private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();
  private static final Gson GSON = new Gson();

  private final InterpreterSpec interpreterSpec;
  private final InterpreterRpcContractFixture.Handle fixture;
  private final ContractFaults faults = new ContractFaults() {
    @Override
    public void makeTransportUnavailable() {
      closeTransport(controlTransport);
    }

    @Override
    public void abortPendingCalls() {
      closeTransport(executionTransport);
    }
  };

  private RemoteInterpreterServer server;
  private TSocket executionTransport;
  private TSocket controlTransport;
  private RemoteInterpreterService.Client executionClient;
  private RemoteInterpreterService.Client controlClient;
  private String previousLocalRepository;
  private String previousForceShutdown;
  private String previousProbeId;

  ThriftInterpreterRpcContractDriver(Path localRepository, String probeId) {
    this.fixture = InterpreterRpcContractFixture.create(probeId);

    InterpreterRef interpreter =
        new InterpreterRef(
            INTERPRETER_GROUP_ID, SESSION_ID, fixture.getInterpreterClassName());
    Map<String, String> properties = new LinkedHashMap<>();
    properties.put(LOCAL_REPOSITORY_PROPERTY, localRepository.toString());
    properties.put(FORCE_SHUTDOWN_PROPERTY, "false");
    properties.put(InterpreterRpcContractFixture.PROBE_ID_PROPERTY, fixture.getProbeId());
    this.interpreterSpec = new InterpreterSpec(interpreter, USER_NAME, properties);
  }

  @Override
  public InterpreterSpec interpreterSpec() {
    return interpreterSpec;
  }

  @Override
  public void start() throws Exception {
    previousLocalRepository = System.getProperty(LOCAL_REPOSITORY_PROPERTY);
    previousForceShutdown = System.getProperty(FORCE_SHUTDOWN_PROPERTY);
    previousProbeId = System.getProperty(InterpreterRpcContractFixture.PROBE_ID_PROPERTY);

    server =
        new RemoteInterpreterServer(
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
    executionClient =
        new RemoteInterpreterService.Client(new TBinaryProtocol(executionTransport));
    controlClient = new RemoteInterpreterService.Client(new TBinaryProtocol(controlTransport));
    controlClient.init(Collections.emptyMap());
  }

  @Override
  public void createInterpreter(InterpreterSpec spec) throws ContractFailure {
    invoke(() -> {
      controlClient.createInterpreter(
          spec.getInterpreter().getInterpreterGroupId(),
          spec.getInterpreter().getSessionId(),
          spec.getInterpreter().getClassName(),
          spec.getProperties(),
          spec.getUserName());
      return null;
    });
  }

  @Override
  public FormType getFormType(InterpreterRef interpreter) throws ContractFailure {
    return invoke(() -> FormType.valueOf(
        controlClient.getFormType(interpreter.getSessionId(), interpreter.getClassName())));
  }

  @Override
  public int getProgress(InterpreterRef interpreter, ContractContext context)
      throws ContractFailure {
    return invoke(() -> controlClient.getProgress(
        interpreter.getSessionId(), interpreter.getClassName(), toRemoteContext(context)));
  }

  @Override
  public List<ContractCompletion> completion(
      InterpreterRef interpreter, String buffer, int cursor, ContractContext context)
      throws ContractFailure {
    return invoke(() -> {
      List<ContractCompletion> completions = new ArrayList<>();
      for (InterpreterCompletion completion : controlClient.completion(
          interpreter.getSessionId(),
          interpreter.getClassName(),
          buffer,
          cursor,
          toRemoteContext(context))) {
        completions.add(
            new ContractCompletion(
                completion.getName(), completion.getValue(), completion.getMeta()));
      }
      return completions;
    });
  }

  @Override
  public ContractResult interpret(
      InterpreterRef interpreter, String statement, ContractContext context)
      throws ContractFailure {
    return invoke(() -> toContractResult(executionClient.interpret(
        interpreter.getSessionId(),
        interpreter.getClassName(),
        statement,
        toRemoteContext(context))));
  }

  @Override
  public JobStatus getStatus(InterpreterRef interpreter, String jobId) throws ContractFailure {
    return invoke(() -> JobStatus.valueOf(
        controlClient.getStatus(interpreter.getSessionId(), jobId)));
  }

  @Override
  public void cancel(InterpreterRef interpreter, ContractContext context)
      throws ContractFailure {
    invoke(() -> {
      controlClient.cancel(
          interpreter.getSessionId(), interpreter.getClassName(), toRemoteContext(context));
      return null;
    });
  }

  @Override
  public void closeInterpreter(InterpreterRef interpreter) throws ContractFailure {
    invoke(() -> {
      controlClient.close(interpreter.getSessionId(), interpreter.getClassName());
      return null;
    });
  }

  @Override
  public ContractProbe probe() {
    return fixture;
  }

  @Override
  public ContractFaults faults() {
    return faults;
  }

  void closeControlTransportForWireTest() {
    closeTransport(controlTransport);
  }

  String getRawStatusForWireTest() throws TException {
    return controlClient.getStatus(SESSION_ID, "wire-smoke-job");
  }

  String getRawMissingInterpreterFormTypeForWireTest() throws TException {
    return controlClient.getFormType("missing-session", fixture.getInterpreterClassName());
  }

  @Override
  public void close() throws Exception {
    try {
      fixture.releaseInterpretation();
      closeTransport(executionTransport);
      closeTransport(controlTransport);

      if (server != null) {
        try {
          server.close(SESSION_ID, fixture.getInterpreterClassName());
        } finally {
          if (server.isRunning()) {
            server.shutdown();
          }
          awaitServerStopped();
          server.join(SOCKET_TIMEOUT_MILLIS);
          if (server.isAlive()) {
            throw new IllegalStateException("RemoteInterpreterServer did not terminate");
          }
        }
      }
    } finally {
      try {
        if (server != null) {
          shutdownResultCleaner();
        }
      } finally {
        restoreSystemProperty(LOCAL_REPOSITORY_PROPERTY, previousLocalRepository);
        restoreSystemProperty(FORCE_SHUTDOWN_PROPERTY, previousForceShutdown);
        restoreSystemProperty(
            InterpreterRpcContractFixture.PROBE_ID_PROPERTY, previousProbeId);
        fixture.close();
      }
    }
  }

  private TSocket openTransport() throws TTransportException {
    TSocket transport = new TSocket("localhost", server.getPort(), SOCKET_TIMEOUT_MILLIS);
    transport.open();
    return transport;
  }

  private RemoteInterpreterContext toRemoteContext(ContractContext context) {
    AuthenticationInfo authenticationInfo =
        new AuthenticationInfo(
            context.getUserName(),
            new LinkedHashSet<>(context.getUserRoles()),
            context.getUserTicket());
    GUI gui = new GUI();
    gui.setParams(new LinkedHashMap<>(context.getGuiParameters()));
    GUI noteGui = new GUI();
    noteGui.setParams(new LinkedHashMap<>(context.getNoteGuiParameters()));

    RemoteInterpreterContext remoteContext = new RemoteInterpreterContext();
    remoteContext.setNoteId(context.getNoteId());
    remoteContext.setNoteName(context.getNoteName());
    remoteContext.setParagraphId(context.getParagraphId());
    remoteContext.setReplName(context.getReplName());
    remoteContext.setParagraphTitle(context.getParagraphTitle());
    remoteContext.setParagraphText(context.getParagraphText());
    remoteContext.setAuthenticationInfo(authenticationInfo.toJson());
    remoteContext.setConfig(GSON.toJson(context.getConfig()));
    remoteContext.setGui(gui.toJson());
    remoteContext.setNoteGui(noteGui.toJson());
    remoteContext.setLocalProperties(new HashMap<>(context.getLocalProperties()));
    return remoteContext;
  }

  private ContractResult toContractResult(RemoteInterpreterResult remoteResult) {
    List<ContractResultMessage> messages = new ArrayList<>();
    for (RemoteInterpreterResultMessage message : remoteResult.getMsg()) {
      messages.add(
          new ContractResultMessage(
              ResultType.valueOf(message.getType()), message.getData()));
    }
    Map<String, Object> config = GSON.fromJson(remoteResult.getConfig(), MAP_TYPE);
    Map<String, Object> guiParameters = GUI.fromJson(remoteResult.getGui()).getParams();
    Map<String, Object> noteGuiParameters = GUI.fromJson(remoteResult.getNoteGui()).getParams();
    return new ContractResult(
        ResultCode.valueOf(remoteResult.getCode()),
        messages,
        config,
        guiParameters,
        noteGuiParameters);
  }

  private <T> T invoke(ThriftCall<T> call) throws ContractFailure {
    try {
      return call.execute();
    } catch (InterpreterRPCException e) {
      String message = e.getErrorMessage();
      throw new ContractFailure(categoryForDeclaredFailure(message), message, e);
    } catch (TTransportException e) {
      throw new ContractFailure(FailureCategory.TRANSPORT_UNAVAILABLE, e.getMessage(), e);
    } catch (TApplicationException e) {
      throw new ContractFailure(FailureCategory.OPERATION_FAILED, e.getMessage(), e);
    } catch (TException e) {
      throw new ContractFailure(FailureCategory.OPERATION_FAILED, e.getMessage(), e);
    }
  }

  private FailureCategory categoryForDeclaredFailure(String message) {
    String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
    if (normalized.contains("not created")
        || normalized.contains("not initialized")
        || normalized.contains("not found")
        || normalized.contains("no interpreter")) {
      return FailureCategory.INTERPRETER_NOT_FOUND;
    }
    return FailureCategory.OPERATION_FAILED;
  }

  private void awaitServerRunning() throws InterruptedException {
    long deadline = System.nanoTime() + SERVER_TIMEOUT.toNanos();
    while (!server.isRunning() && System.nanoTime() < deadline) {
      Thread.sleep(20);
    }
    if (!server.isRunning()) {
      throw new IllegalStateException("RemoteInterpreterServer did not start");
    }
  }

  private void awaitServerStopped() throws InterruptedException {
    long deadline = System.nanoTime() + SERVER_TIMEOUT.toNanos();
    while (server.isRunning() && System.nanoTime() < deadline) {
      Thread.sleep(20);
    }
    if (server.isRunning()) {
      throw new IllegalStateException("RemoteInterpreterServer did not stop");
    }
  }

  private void shutdownResultCleaner() throws Exception {
    java.lang.reflect.Field resultCleaner =
        RemoteInterpreterServer.class.getDeclaredField("resultCleanService");
    resultCleaner.setAccessible(true);
    ScheduledExecutorService executor = (ScheduledExecutorService) resultCleaner.get(server);
    executor.shutdownNow();
    executor.awaitTermination(SERVER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
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

  @FunctionalInterface
  private interface ThriftCall<T> {
    T execute() throws TException;
  }
}
