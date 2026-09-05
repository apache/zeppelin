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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.zeppelin.interpreter.remote.InterpreterRpcContractDriver.ContractCompletion;
import org.apache.zeppelin.interpreter.remote.InterpreterRpcContractDriver.ContractContext;
import org.apache.zeppelin.interpreter.remote.InterpreterRpcContractDriver.ContractFailure;
import org.apache.zeppelin.interpreter.remote.InterpreterRpcContractDriver.ContractResult;
import org.apache.zeppelin.interpreter.remote.InterpreterRpcContractDriver.FailureCategory;
import org.apache.zeppelin.interpreter.remote.InterpreterRpcContractDriver.FormType;
import org.apache.zeppelin.interpreter.remote.InterpreterRpcContractDriver.InterpreterRef;
import org.apache.zeppelin.interpreter.remote.InterpreterRpcContractDriver.InterpreterSpec;
import org.apache.zeppelin.interpreter.remote.InterpreterRpcContractDriver.JobStatus;
import org.apache.zeppelin.interpreter.remote.InterpreterRpcContractDriver.ProbeSnapshot;
import org.apache.zeppelin.interpreter.remote.InterpreterRpcContractDriver.ResultCode;
import org.apache.zeppelin.interpreter.remote.InterpreterRpcContractDriver.ResultType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Shared behavior scenarios that every Server-to-Interpreter control transport must pass. */
public abstract class AbstractInterpreterRpcContractTest {

  private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(10);

  @TempDir
  Path localRepository;

  private InterpreterRpcContractDriver driver;
  private InterpreterSpec interpreterSpec;

  protected abstract InterpreterRpcContractDriver createDriver(
      Path localRepository, String probeId);

  @BeforeEach
  protected void setUpContractDriver() throws Exception {
    driver = createDriver(localRepository, UUID.randomUUID().toString());
    driver.start();
    interpreterSpec = driver.interpreterSpec();
    driver.createInterpreter(interpreterSpec);
  }

  @AfterEach
  protected void closeContractDriver() throws Exception {
    if (driver != null) {
      driver.close();
    }
  }

  @Test
  protected void shouldPreserveLifecycleContextResultsAndPayloadContent() throws Exception {
    InterpreterRef interpreter = interpreterSpec.getInterpreter();

    driver.createInterpreter(interpreterSpec);
    ProbeSnapshot snapshot = driver.probe().snapshot();
    assertEquals(1, snapshot.getConstructorCalls());
    assertEquals(0, snapshot.getOpenCalls());

    assertEquals(FormType.NATIVE, driver.getFormType(interpreter));
    assertEquals(0, driver.getProgress(interpreter, context("before-open")));
    assertEquals(0, driver.probe().snapshot().getOpenCalls());

    List<ContractCompletion> completions =
        driver.completion(interpreter, "sel", 3, context("completion"));
    assertEquals(1, completions.size());
    assertEquals("select", completions.get(0).getName());
    assertEquals("select *", completions.get(0).getValue());
    assertEquals("keyword", completions.get(0).getMeta());

    snapshot = driver.probe().snapshot();
    assertEquals("sel", snapshot.getCompletionBuffer());
    assertEquals(3, snapshot.getCompletionCursor());
    assertEquals("completion", snapshot.getCompletionParagraphId());
    assertEquals(1, snapshot.getOpenCalls());

    ContractContext requestContext = context("context-round-trip");
    ContractResult result = driver.interpret(interpreter, "inspect-context", requestContext);
    assertEquals(ResultCode.SUCCESS, result.getCode());
    assertEquals(1, result.getMessages().size());
    assertEquals(ResultType.TABLE, result.getMessages().get(0).getType());
    assertEquals("context-result", result.getMessages().get(0).getData());
    assertEquals(
        Map.of("request-config", "config-value", "interpreter-config", "updated"),
        result.getConfig());
    assertEquals(
        Map.of("request-gui", "gui-value", "interpreter-gui", "updated"),
        result.getGuiParameters());
    assertEquals(
        Map.of("request-note-gui", "note-gui-value", "interpreter-note-gui", "updated"),
        result.getNoteGuiParameters());
    assertEquals(requestContext, driver.probe().snapshot().getLastInterpretContext());
    assertEquals(1, driver.probe().snapshot().getOpenCalls());

    String largePayload =
        "payload-start:" + "0123456789abcdef".repeat(64 * 1024) + ":payload-end-한글";
    result = driver.interpret(interpreter, "echo:" + largePayload, context("large-payload"));
    assertEquals(ResultCode.SUCCESS, result.getCode());
    assertEquals(1, result.getMessages().size());
    assertEquals(ResultType.TEXT, result.getMessages().get(0).getType());
    assertEquals(largePayload, result.getMessages().get(0).getData());
    assertEquals(1, driver.probe().snapshot().getOpenCalls());

    driver.closeInterpreter(interpreter);
    assertEquals(1, driver.probe().snapshot().getCloseCalls());
    assertFailureCategory(
        FailureCategory.INTERPRETER_NOT_FOUND, () -> driver.getFormType(interpreter));
  }

  @Test
  protected void shouldCancelRunningInterpretAndReturnTerminalResult() throws Exception {
    InterpreterRef interpreter = interpreterSpec.getInterpreter();
    ContractContext runningContext = context("running-paragraph");
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<ContractResult> interpretation = executor.submit(() ->
        driver.interpret(interpreter, "block-until-cancelled", runningContext));

    try {
      assertTrue(
          driver.probe().awaitInterpretStarted(ASYNC_TIMEOUT),
          "Interpreter did not start");
      assertEquals(JobStatus.RUNNING, driver.getStatus(interpreter, "running-paragraph"));
      assertEquals(42, driver.getProgress(interpreter, runningContext));

      driver.cancel(interpreter, runningContext);
      assertTrue(
          driver.probe().awaitCancelObserved(ASYNC_TIMEOUT),
          "Interpreter did not observe cancellation");

      ProbeSnapshot snapshot = driver.probe().snapshot();
      assertEquals("contract-note", snapshot.getCancelNoteId());
      assertEquals("running-paragraph", snapshot.getCancelParagraphId());

      ContractResult terminalResult =
          interpretation.get(ASYNC_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      assertEquals(ResultCode.SUCCESS, terminalResult.getCode());
      assertEquals(1, terminalResult.getMessages().size());
      assertEquals(ResultType.TEXT, terminalResult.getMessages().get(0).getType());
      assertEquals("cancelled", terminalResult.getMessages().get(0).getData());
    } finally {
      driver.probe().releaseInterpretation();
      if (!interpretation.isDone()) {
        driver.faults().abortPendingCalls();
      }
      interpretation.cancel(true);
      executor.shutdownNow();
      assertTrue(
          executor.awaitTermination(ASYNC_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
          "Interpret executor did not terminate");
    }
  }

  @Test
  protected void shouldNormalizeExecutionOperationInterpreterAndTransportFailures()
      throws Exception {
    InterpreterRef interpreter = interpreterSpec.getInterpreter();

    ContractResult result =
        driver.interpret(interpreter, "throw-application-error", context("application-error"));
    assertEquals(ResultCode.ERROR, result.getCode());
    assertTrue(result.getMessages().get(0).getData().contains("contract interpret failure"));

    assertFailureCategory(
        FailureCategory.OPERATION_FAILED,
        () -> driver.completion(
            interpreter,
            "throw-completion-error",
            0,
            context("completion-error")));

    InterpreterRef missingInterpreter = interpreter.withSessionId("missing-session");
    assertFailureCategory(
        FailureCategory.INTERPRETER_NOT_FOUND,
        () -> driver.getFormType(missingInterpreter));

    driver.faults().makeTransportUnavailable();
    assertFailureCategory(
        FailureCategory.TRANSPORT_UNAVAILABLE,
        () -> driver.getStatus(interpreter, "missing-job"));
  }

  private ContractContext context(String paragraphId) {
    return new ContractContext(
        "contract-note",
        "Contract Note",
        paragraphId,
        "contract",
        "Contract Paragraph",
        "contract text",
        "contract-user",
        Set.of("contract-role", "contract-auditor"),
        "contract-ticket",
        Map.of("contract-local", "local-value"),
        Map.of("request-config", "config-value"),
        Map.of("request-gui", "gui-value"),
        Map.of("request-note-gui", "note-gui-value"));
  }

  private void assertFailureCategory(
      FailureCategory expectedCategory, ThrowingOperation operation) {
    ContractFailure failure = assertThrows(ContractFailure.class, operation::run);
    assertEquals(expectedCategory, failure.getCategory());
  }

  @FunctionalInterface
  private interface ThrowingOperation {
    void run() throws ContractFailure;
  }
}
