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
package org.apache.zeppelin.interpreter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.remote.InvokeResourceMethodEventMessage;
import org.apache.zeppelin.interpreter.remote.ParagraphOutputDispatcher;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.apache.zeppelin.interpreter.thrift.InterpreterRPCException;
import org.apache.zeppelin.interpreter.thrift.OutputAppendEvent;
import org.apache.zeppelin.interpreter.thrift.OutputUpdateAllEvent;
import org.apache.zeppelin.interpreter.thrift.OutputUpdateEvent;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterResultMessage;
import org.apache.zeppelin.resource.Resource;
import org.apache.zeppelin.resource.ResourceId;


public class RemoteInterpreterEventServerTest {
  
  @Test
  void updateOutputCompletesBeforeReturning() throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    RemoteInterpreterEventServer server = serverWithListener(listener);
    try {
      server.appendOutput(new OutputAppendEvent("note", "para", 0, "before", null, null));
      server.updateOutput(new OutputUpdateEvent("note", "para", 0, "TEXT", "final", null, null));
      verify(listener).onParagraphOutputUpdated("note", "para", 0, null, InterpreterResult.Type.TEXT, "final");
      server.appendOutput(new OutputAppendEvent("note", "para", 0, "after", null, null));
      server.checkpointOutput("note", "para");
      InOrder order = inOrder(listener);
      order.verify(listener).onParagraphOutputAppend("note", "para", 0, null, "before");
      order.verify(listener).onParagraphOutputUpdated("note", "para", 0, null, InterpreterResult.Type.TEXT, "final");
      order.verify(listener).onParagraphOutputAppend("note", "para", 0, null, "after");
      order.verify(listener).checkpointOutput("note", "para");
      order.verifyNoMoreInteractions();
    } finally {
      server.stop();
    }
  }

  @Test
  void checkpointDrainsPendingOutputBeforeSaving() throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    RemoteInterpreterEventServer server = serverWithListener(listener);
    try {
      server.appendOutput(new OutputAppendEvent("note", "para", 0, "pending", null, null));
      server.checkpointOutput("note", "para");
      InOrder order = inOrder(listener);
      order.verify(listener).onParagraphOutputAppend("note", "para", 0, null, "pending");
      order.verify(listener).checkpointOutput("note", "para");
    } finally {
      server.stop();
    }
  }

  @Test
  void updateAllIsAnOrderedClearAndReplacement() throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    RemoteInterpreterEventServer server = serverWithListener(listener);
    try {
      server.appendOutput(new OutputAppendEvent("note", "para", 0, "old", null, null));
      server.updateAllOutput(new OutputUpdateAllEvent("note", "para", Arrays.asList(
          new RemoteInterpreterResultMessage("HTML", "replacement"),
          new RemoteInterpreterResultMessage("TEXT", "second")), null));
      verify(listener).onParagraphOutputUpdated("note", "para", 1, null, InterpreterResult.Type.TEXT, "second");
      server.appendOutput(new OutputAppendEvent("note", "para", 1, "new", null, null));
      server.checkpointOutput("note", "para");
      InOrder order = inOrder(listener);
      order.verify(listener).onParagraphOutputAppend("note", "para", 0, null, "old");
      order.verify(listener).onParagraphOutputClear("note", "para", null);
      order.verify(listener).onParagraphOutputUpdated("note", "para", 0, null, InterpreterResult.Type.HTML, "replacement");
      order.verify(listener).onParagraphOutputUpdated("note", "para", 1, null, InterpreterResult.Type.TEXT, "second");
      order.verify(listener).onParagraphOutputAppend("note", "para", 1, null, "new");
      order.verify(listener).checkpointOutput("note", "para");
      order.verifyNoMoreInteractions();
    } finally {
      server.stop();
    }
  }

  @Test
  void emptyUpdateAllStillClearsPendingOutput() throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    RemoteInterpreterEventServer server = serverWithListener(listener);
    try {
      server.appendOutput(new OutputAppendEvent("note", "para", 0, "old", null, null));
      server.updateAllOutput(new OutputUpdateAllEvent("note", "para", Collections.emptyList(), null));
      InOrder order = inOrder(listener);
      order.verify(listener).onParagraphOutputAppend("note", "para", 0, null, "old");
      order.verify(listener).onParagraphOutputClear("note", "para", null);
      order.verifyNoMoreInteractions();
    } finally {
      server.stop();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"UPDATE", "UPDATE_ALL", "CHECKPOINT"})
  void sameNoteBoundaryWaitsForInFlightAppend(String operation) throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    RemoteInterpreterEventServer server = serverWithListener(listener);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch started = new CountDownLatch(1);
    doAnswer(invocation -> {
      entered.countDown();
      assertTrue(release.await(5, TimeUnit.SECONDS));
      return null;
    }).when(listener).onParagraphOutputAppend("note", "first", 0, null, "old");
    ExecutorService callers = Executors.newSingleThreadExecutor();
    try {
      server.appendOutput(new OutputAppendEvent("note", "first", 0, "old", null, null));
      dispatcherOf(server).flush();
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      Future<?> boundary = callers.submit(() -> {
        started.countDown();
        callBoundary(server, "note", "second", operation);
        return null;
      });
      assertTrue(started.await(5, TimeUnit.SECONDS));
      assertThrows(TimeoutException.class, () -> boundary.get(100, TimeUnit.MILLISECONDS));
      release.countDown();
      boundary.get(5, TimeUnit.SECONDS);
      InOrder order = inOrder(listener);
      order.verify(listener).onParagraphOutputAppend("note", "first", 0, null, "old");
      if ("UPDATE".equals(operation)) {
        order.verify(listener).onParagraphOutputUpdated("note", "second", 0, null, InterpreterResult.Type.TEXT, "replacement");
      } else if ("UPDATE_ALL".equals(operation)) {
        order.verify(listener).onParagraphOutputClear("note", "second", null);
        order.verify(listener).onParagraphOutputUpdated("note", "second", 0, null, InterpreterResult.Type.TEXT, "replacement");
      } else {
        order.verify(listener).checkpointOutput("note", "second");
      }
    } finally {
      release.countDown();
      callers.shutdownNow();
      server.stop();
    }
  }

  @Test
  void independentNoteMakesProgressWhileAnotherNoteAppendIsBlocked() throws Exception {
    // These note IDs have identical hash codes, so concurrency cannot depend on hash lanes.
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    RemoteInterpreterEventServer server = serverWithListener(listener);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch otherAppend = new CountDownLatch(1);
    doAnswer(call -> {
      entered.countDown();
      assertTrue(release.await(5, TimeUnit.SECONDS));
      return null;
    }).when(listener).onParagraphOutputAppend("Aa", "para", 0, null, "blocked");
    doAnswer(call -> {
      otherAppend.countDown();
      return null;
    }).when(listener).onParagraphOutputAppend("BB", "para", 0, null, "periodic");
    ExecutorService callers = Executors.newSingleThreadExecutor();
    try {
      server.appendOutput(new OutputAppendEvent("Aa", "para", 0, "blocked", null, null));
      dispatcherOf(server).flush();
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      server.appendOutput(new OutputAppendEvent("BB", "para", 0, "periodic", null, null));
      dispatcherOf(server).flush();
      assertTrue(otherAppend.await(5, TimeUnit.SECONDS));
      Future<?> independent = callers.submit(() -> {
        callBoundary(server, "BB", "para", "UPDATE");
        callBoundary(server, "BB", "para", "UPDATE_ALL");
        callBoundary(server, "BB", "para", "CHECKPOINT");
        return null;
      });
      independent.get(5, TimeUnit.SECONDS);
      verify(listener).onParagraphOutputClear("BB", "para", null);
      verify(listener).checkpointOutput("BB", "para");
    } finally {
      release.countDown();
      callers.shutdownNow();
      server.stop();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"UPDATE", "UPDATE_ALL", "CHECKPOINT"})
  void laterAppendCannotOvertakeBoundaryCallback(String operation) throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    RemoteInterpreterEventServer server = serverWithListener(listener);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicBoolean boundaryCompleted = new AtomicBoolean();
    AtomicBoolean appendOverlappedBoundary = new AtomicBoolean();
    doAnswer(call -> {
      entered.countDown();
      assertTrue(release.await(5, TimeUnit.SECONDS));
      return null;
    }).when(listener).onParagraphOutputClear("note", "para", null);
    doAnswer(call -> {
      entered.countDown();
      assertTrue(release.await(5, TimeUnit.SECONDS));
      boundaryCompleted.set(true);
      return null;
    }).when(listener).checkpointOutput("note", "para");
    doAnswer(call -> {
      entered.countDown();
      assertTrue(release.await(5, TimeUnit.SECONDS));
      boundaryCompleted.set(true);
      return null;
    }).when(listener).onParagraphOutputUpdated("note", "para", 0, null, InterpreterResult.Type.TEXT, "replacement");
    doAnswer(call -> {
      if (!boundaryCompleted.get()) {
        appendOverlappedBoundary.set(true);
      }
      return null;
    }).when(listener).onParagraphOutputAppend("note", "para", 0, null, "later");
    ExecutorService callers = Executors.newSingleThreadExecutor();
    try {
      Future<?> boundary = callers.submit(() -> {
        callBoundary(server, "note", "para", operation);
        return null;
      });
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      server.appendOutput(new OutputAppendEvent("note", "para", 0, "later", null, null));
      dispatcherOf(server).flush();
      verify(listener, never()).onParagraphOutputAppend("note", "para", 0, null, "later");
      assertThrows(TimeoutException.class, () -> boundary.get(100, TimeUnit.MILLISECONDS));
      release.countDown();
      boundary.get(5, TimeUnit.SECONDS);
      server.checkpointOutput("note", "drained");
      assertTrue(boundaryCompleted.get());
      assertFalse(appendOverlappedBoundary.get(), "Append must wait for the entire boundary");
      InOrder order = inOrder(listener);
      if ("UPDATE".equals(operation)) {
        order.verify(listener).onParagraphOutputUpdated("note", "para", 0, null, InterpreterResult.Type.TEXT, "replacement");
      } else if ("UPDATE_ALL".equals(operation)) {
        order.verify(listener).onParagraphOutputClear("note", "para", null);
        order.verify(listener).onParagraphOutputUpdated("note", "para", 0, null, InterpreterResult.Type.TEXT, "replacement");
      } else {
        order.verify(listener).checkpointOutput("note", "para");
      }
      order.verify(listener).onParagraphOutputAppend("note", "para", 0, null, "later");
    } finally {
      release.countDown();
      callers.shutdownNow();
      server.stop();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"UPDATE", "UPDATE_ALL", "CHECKPOINT"})
  void boundaryCallbackFailureBecomesRpcExceptionAndLaterOutputStillWorks(String operation)
      throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    IllegalStateException removed = new IllegalStateException("paragraph removed");
    doThrow(removed).when(listener).onParagraphOutputUpdated("note", "gone", 0, null, InterpreterResult.Type.TEXT, "replacement");
    doThrow(removed).when(listener).onParagraphOutputClear("note", "gone", null);
    doThrow(removed).when(listener).checkpointOutput("note", "gone");
    RemoteInterpreterEventServer server = serverWithListener(listener);
    try {
      InterpreterRPCException failure = assertThrows(InterpreterRPCException.class,
          () -> callBoundary(server, "note", "gone", operation));
      assertTrue(failure.getErrorMessage().contains("paragraph removed"));
      server.appendOutput(new OutputAppendEvent("note", "present", 0, "good", null, null));
      server.checkpointOutput("note", "present");
      verify(listener).onParagraphOutputAppend("note", "present", 0, null, "good");
    } finally {
      server.stop();
    }
  }

  @Test
  void largeParagraphOutputIsDeliveredWithoutAnAdditionalDispatcherLimit() throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    RemoteInterpreterEventServer server = serverWithListener(listener);
    String output = "a".repeat(4 * 1024 * 1024 + 1);
    try {
      server.appendOutput(new OutputAppendEvent("note", "para", 0, output, null, null));
      server.updateOutput(new OutputUpdateEvent("note", "para", 0, "TEXT", output, null, null));
      server.updateAllOutput(new OutputUpdateAllEvent("note", "para", List.of(
          new RemoteInterpreterResultMessage("HTML", output)), null));
      server.checkpointOutput("note", "para");
      InOrder order = inOrder(listener);
      order.verify(listener).onParagraphOutputAppend("note", "para", 0, null, output);
      order.verify(listener).onParagraphOutputUpdated("note", "para", 0, null, InterpreterResult.Type.TEXT, output);
      order.verify(listener).onParagraphOutputClear("note", "para", null);
      order.verify(listener).onParagraphOutputUpdated("note", "para", 0, null, InterpreterResult.Type.HTML, output);
      order.verify(listener).checkpointOutput("note", "para");
      order.verifyNoMoreInteractions();
    } finally {
      server.stop();
    }
  }

  @Test
  void serverAppliesConfiguredOutputBatchSize() throws Exception {
    ZeppelinConfiguration zConf = ZeppelinConfiguration.load("zeppelin-test-site.xml");
    zConf.setProperty(ConfVars.ZEPPELIN_INTERPRETER_OUTPUT_WORKER_COUNT.getVarName(), "1");
    zConf.setProperty(ConfVars.ZEPPELIN_INTERPRETER_OUTPUT_EVENTS_PER_BATCH.getVarName(), "2");
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    RemoteInterpreterEventServer server = serverWithListener(listener, zConf);
    try {
      for (String output : List.of("a", "b", "c", "d")) {
        server.appendOutput(new OutputAppendEvent("note", "para", 0, output, null, null));
      }
      server.checkpointOutput("note", "para");
      InOrder order = inOrder(listener);
      order.verify(listener).onParagraphOutputAppend("note", "para", 0, null, "ab");
      order.verify(listener).onParagraphOutputAppend("note", "para", 0, null, "cd");
      order.verify(listener).checkpointOutput("note", "para");
      order.verifyNoMoreInteractions();
    } finally {
      server.stop();
    }
  }

  @Test
  void stoppedServerRejectsOutputWithRpcExceptions() {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    RemoteInterpreterEventServer server = serverWithListener(listener);
    server.stop();
    assertThrows(InterpreterRPCException.class, () ->
        server.appendOutput(new OutputAppendEvent("note", "para", 0, "late", null, null)));
    for (String operation : Arrays.asList("UPDATE", "UPDATE_ALL", "CHECKPOINT")) {
      assertThrows(InterpreterRPCException.class,
          () -> callBoundary(server, "note", "para", operation));
    }
    verify(listener, never()).onParagraphOutputAppend("note", "para", 0, null, "late");
  }

  @Test
  void interruptedRpcWaitPreservesInterruptAndDoesNotCancelAcceptedOutput() throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    doAnswer(call -> {
      entered.countDown();
      assertTrue(release.await(5, TimeUnit.SECONDS));
      return null;
    }).when(listener).checkpointOutput("note", "para");
    RemoteInterpreterEventServer server = serverWithListener(listener);
    AtomicBoolean interruptPreserved = new AtomicBoolean();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread caller = new Thread(() -> {
      try {
        server.checkpointOutput("note", "para");
      } catch (Throwable e) {
        failure.set(e);
        interruptPreserved.set(Thread.currentThread().isInterrupted());
      }
    });
    try {
      caller.start();
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      caller.interrupt();
      caller.join(5000);
      assertFalse(caller.isAlive());
      assertTrue(failure.get() instanceof InterpreterRPCException);
      assertTrue(interruptPreserved.get());
      release.countDown();
      server.appendOutput(new OutputAppendEvent("note", "para", 0, "after", null, null));
      server.checkpointOutput("note", "done");
      InOrder order = inOrder(listener);
      order.verify(listener).checkpointOutput("note", "para");
      order.verify(listener).onParagraphOutputAppend("note", "para", 0, null, "after");
      order.verify(listener).checkpointOutput("note", "done");
    } finally {
      release.countDown();
      caller.interrupt();
      caller.join(5000);
      server.stop();
    }
  }

  private void callBoundary(RemoteInterpreterEventServer server, String noteId,
                            String paragraphId, String operation) throws Exception {
    if ("UPDATE".equals(operation)) {
      server.updateOutput(new OutputUpdateEvent(noteId, paragraphId, 0, "TEXT", "replacement", null, null));
    } else if ("UPDATE_ALL".equals(operation)) {
      server.updateAllOutput(new OutputUpdateAllEvent(noteId, paragraphId, Collections.singletonList(new RemoteInterpreterResultMessage("TEXT", "replacement")), null));
    } else {
      server.checkpointOutput(noteId, paragraphId);
    }
  }

  private RemoteInterpreterEventServer serverWithListener(
      RemoteInterpreterProcessListener listener) {
    return serverWithListener(listener, outputConfiguration());
  }

  private RemoteInterpreterEventServer serverWithListener(
      RemoteInterpreterProcessListener listener, ZeppelinConfiguration zConf) {
    InterpreterSettingManager manager = mock(InterpreterSettingManager.class);
    when(manager.getRemoteInterpreterProcessListener()).thenReturn(listener);
    return new RemoteInterpreterEventServer(zConf, manager);
  }

  private ZeppelinConfiguration outputConfiguration() {
    ZeppelinConfiguration zConf = mock(ZeppelinConfiguration.class);
    when(zConf.getInt(ConfVars.ZEPPELIN_INTERPRETER_OUTPUT_WORKER_COUNT))
        .thenReturn(ConfVars.ZEPPELIN_INTERPRETER_OUTPUT_WORKER_COUNT.getIntValue());
    when(zConf.getInt(ConfVars.ZEPPELIN_INTERPRETER_OUTPUT_EVENTS_PER_BATCH))
        .thenReturn(ConfVars.ZEPPELIN_INTERPRETER_OUTPUT_EVENTS_PER_BATCH.getIntValue());
    return zConf;
  }

  private ParagraphOutputDispatcher dispatcherOf(RemoteInterpreterEventServer server)
      throws Exception {
    Field field = RemoteInterpreterEventServer.class.getDeclaredField("outputDispatcher");
    field.setAccessible(true);
    return (ParagraphOutputDispatcher) field.get(server);
  }

  @Test
  void invokeMethodThrowsRpcExceptionWhenSerializationFails() throws Exception {
    ZeppelinConfiguration zConf = outputConfiguration();
    InterpreterSettingManager manager = mock(InterpreterSettingManager.class);
    RemoteInterpreterEventServer server = new RemoteInterpreterEventServer(zConf, manager);

    ManagedInterpreterGroup interpreterGroup = mock(ManagedInterpreterGroup.class);
    RemoteInterpreterProcess remoteInterpreterProcess = mock(RemoteInterpreterProcess.class);
      
    when(manager.getInterpreterGroupById("pool-id"))
        .thenReturn(interpreterGroup);
    when(interpreterGroup.getRemoteInterpreterProcess())
        .thenReturn(remoteInterpreterProcess);
    when(remoteInterpreterProcess.isRunning())
        .thenReturn(true);
      
    ByteBuffer remoteResult = Resource.serializeObject(new SerializableOnlyOnce());
    doReturn(remoteResult)
        .when(remoteInterpreterProcess)
        .callRemoteFunction(any());
      
    ResourceId resourceId = ResourceId.fromJson(
        "{\"resourcePoolId\":\"pool-id\",\"name\":\"resource-name\","
            + "\"noteId\":\"note-id\",\"paragraphId\":\"paragraph-id\"}"
    );

    InvokeResourceMethodEventMessage message = new InvokeResourceMethodEventMessage(
        resourceId
        , "someMethod"
        , null
        , null
        , null);

    InterpreterRPCException exception = assertThrows(
        InterpreterRPCException.class,
        () -> server.invokeMethod("caller-group-id", message.toJson()));
      
    assertTrue(exception.toString().contains("failed on second serialization"));
  }
  private static class SerializableOnlyOnce implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int FAILURE_SERIALIZATION_COUNT = 2;

    private int serializationCount;

    private void writeObject(ObjectOutputStream outputStream) throws IOException {
      serializationCount++;

      if (serializationCount == FAILURE_SERIALIZATION_COUNT) {
        throw new IOException("failed on second serialization");
      }
      
      outputStream.defaultWriteObject();
    }
  }
}
