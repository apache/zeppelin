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

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.remote.InterpreterRpcContractDriver.ContractContext;
import org.apache.zeppelin.interpreter.remote.InterpreterRpcContractDriver.ContractProbe;
import org.apache.zeppelin.interpreter.remote.InterpreterRpcContractDriver.ProbeSnapshot;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.user.AuthenticationInfo;

/**
 * Reusable executable fixture for Server-to-Interpreter control RPC contract drivers.
 *
 * <p>The generated completion type remains here only while it is part of the public
 * {@link Interpreter} API. The fixture and its probe lifecycle are independent of any transport
 * adapter, so later drivers can execute the same interpreter and shared scenarios.
 */
public final class InterpreterRpcContractFixture {

  public static final String PROBE_ID_PROPERTY = "zeppelin.interpreter.contract.probe.id";

  private static final Duration INTERPRET_TIMEOUT = Duration.ofSeconds(10);
  private static final ConcurrentMap<String, ProbeState> PROBES = new ConcurrentHashMap<>();

  private InterpreterRpcContractFixture() {
  }

  public static Handle create(String probeId) {
    ProbeState probe = new ProbeState();
    ProbeState previous = PROBES.putIfAbsent(probeId, probe);
    if (previous != null) {
      throw new IllegalArgumentException("Duplicate contract probe " + probeId);
    }
    return new Handle(probeId, probe);
  }

  /** Owns one isolated fixture probe and removes it from the registry when closed. */
  public static final class Handle implements ContractProbe, AutoCloseable {
    private final String probeId;
    private final ProbeState probe;

    private Handle(String probeId, ProbeState probe) {
      this.probeId = probeId;
      this.probe = probe;
    }

    public String getProbeId() {
      return probeId;
    }

    public String getInterpreterClassName() {
      return ContractInterpreter.class.getName();
    }

    @Override
    public ProbeSnapshot snapshot() {
      return new ProbeSnapshot(
          probe.constructorCalls.get(),
          probe.openCalls.get(),
          probe.closeCalls.get(),
          probe.completionBuffer.get(),
          probe.completionCursor.get(),
          probe.completionParagraphId.get(),
          probe.cancelNoteId.get(),
          probe.cancelParagraphId.get(),
          probe.lastInterpretContext.get());
    }

    @Override
    public boolean awaitInterpretStarted(Duration timeout) throws InterruptedException {
      return probe.interpretStarted.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean awaitCancelObserved(Duration timeout) throws InterruptedException {
      return probe.cancelObserved.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void releaseInterpretation() {
      probe.releaseInterpret.countDown();
    }

    @Override
    public void close() {
      releaseInterpretation();
      PROBES.remove(probeId, probe);
    }
  }

  /** Interpreter implementation exercised by each transport contract driver. */
  public static class ContractInterpreter extends Interpreter {
    private final ProbeState probe;

    public ContractInterpreter(Properties properties) {
      super(properties);
      String probeId = properties.getProperty(PROBE_ID_PROPERTY);
      probe = PROBES.get(probeId);
      if (probe == null) {
        throw new IllegalStateException("Unknown contract probe " + probeId);
      }
      probe.constructorCalls.incrementAndGet();
    }

    @Override
    public void open() {
      probe.openCalls.incrementAndGet();
    }

    @Override
    public void close() {
      probe.closeCalls.incrementAndGet();
    }

    @Override
    public InterpreterResult interpret(String statement, InterpreterContext context)
        throws InterpreterException {
      probe.lastInterpretContext.set(toContractContext(context));
      if (statement.startsWith("echo:")) {
        return new InterpreterResult(
            InterpreterResult.Code.SUCCESS, statement.substring("echo:".length()));
      }
      if ("inspect-context".equals(statement)) {
        context.getConfig().put("interpreter-config", "updated");
        context.getGui().getParams().put("interpreter-gui", "updated");
        context.getNoteGui().getParams().put("interpreter-note-gui", "updated");
        return new InterpreterResult(
            InterpreterResult.Code.SUCCESS, InterpreterResult.Type.TABLE, "context-result");
      }
      if ("throw-application-error".equals(statement)) {
        throw new InterpreterException("contract interpret failure");
      }
      if ("block-until-cancelled".equals(statement)) {
        context.setProgress(42);
        probe.interpretStarted.countDown();
        try {
          if (!probe.releaseInterpret.await(
              INTERPRET_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            return new InterpreterResult(InterpreterResult.Code.ERROR, "cancel timed out");
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return new InterpreterResult(InterpreterResult.Code.ERROR, "interpret interrupted");
        }
        return new InterpreterResult(
            probe.cancelled.get()
                ? InterpreterResult.Code.SUCCESS
                : InterpreterResult.Code.ERROR,
            probe.cancelled.get() ? "cancelled" : "released without cancellation");
      }
      return new InterpreterResult(InterpreterResult.Code.ERROR, "unsupported statement");
    }

    @Override
    public void cancel(InterpreterContext context) {
      probe.cancelNoteId.set(context.getNoteId());
      probe.cancelParagraphId.set(context.getParagraphId());
      probe.cancelled.set(true);
      probe.cancelObserved.countDown();
      probe.releaseInterpret.countDown();
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
      probe.completionBuffer.set(buffer);
      probe.completionCursor.set(cursor);
      probe.completionParagraphId.set(context.getParagraphId());
      return Collections.singletonList(
          new InterpreterCompletion("select", "select *", "keyword"));
    }

    private ContractContext toContractContext(InterpreterContext context) {
      AuthenticationInfo authenticationInfo = context.getAuthenticationInfo();
      Set<String> roles = authenticationInfo.getRoles() == null
          ? Collections.emptySet()
          : authenticationInfo.getRoles();
      return new ContractContext(
          context.getNoteId(),
          context.getNoteName(),
          context.getParagraphId(),
          context.getReplName(),
          context.getParagraphTitle(),
          context.getParagraphText(),
          authenticationInfo.getUser(),
          roles,
          authenticationInfo.getTicket(),
          context.getLocalProperties(),
          context.getConfig(),
          context.getGui().getParams(),
          context.getNoteGui().getParams());
    }
  }

  private static final class ProbeState {
    private final AtomicInteger constructorCalls = new AtomicInteger();
    private final AtomicInteger openCalls = new AtomicInteger();
    private final AtomicInteger closeCalls = new AtomicInteger();
    private final AtomicReference<String> completionBuffer = new AtomicReference<>();
    private final AtomicInteger completionCursor = new AtomicInteger();
    private final AtomicReference<String> completionParagraphId = new AtomicReference<>();
    private final AtomicReference<String> cancelNoteId = new AtomicReference<>();
    private final AtomicReference<String> cancelParagraphId = new AtomicReference<>();
    private final AtomicReference<ContractContext> lastInterpretContext = new AtomicReference<>();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final CountDownLatch interpretStarted = new CountDownLatch(1);
    private final CountDownLatch cancelObserved = new CountDownLatch(1);
    private final CountDownLatch releaseInterpret = new CountDownLatch(1);
  }
}
