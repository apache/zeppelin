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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.remote.AppendOutputRunner;
import org.apache.zeppelin.interpreter.remote.InvokeResourceMethodEventMessage;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.apache.zeppelin.interpreter.thrift.InterpreterRPCException;
import org.apache.zeppelin.interpreter.thrift.OutputAppendEvent;
import org.apache.zeppelin.interpreter.thrift.OutputUpdateAllEvent;
import org.apache.zeppelin.interpreter.thrift.OutputUpdateEvent;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterResultMessage;
import org.apache.zeppelin.resource.Resource;
import org.apache.zeppelin.resource.ResourceId;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;


public class RemoteInterpreterEventServerTest {
  
  @Test
  void updateOutputCompletesBeforeReturning() throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    RemoteInterpreterEventServer server =
        serverWithRunner(listener, new AppendOutputRunner(listener));
    try {
      server.updateOutput(new OutputUpdateEvent("note", "para", 0, "TEXT", "final", null));
      // A caller may publish terminal status as soon as the RPC returns.
      verify(listener).onOutputUpdated("note", "para", 0, InterpreterResult.Type.TEXT, "final");
    } finally {
      server.stop();
    }
  }

  @Test
  void checkpointDrainsPendingOutputBeforeSaving() throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    RemoteInterpreterEventServer server =
        serverWithRunner(listener, new AppendOutputRunner(listener));
    try {
      server.appendOutput(new OutputAppendEvent("note", "para", 0, "pending", null));
      server.checkpointOutput("note", "para");
      InOrder order = inOrder(listener);
      order.verify(listener).onOutputAppend("note", "para", 0, "pending");
      order.verify(listener).checkpointOutput("note", "para");
    } finally {
      server.stop();
    }
  }

  @Test
  void updateAllIsAnOrderedClearAndReplacement() throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    AppendOutputRunner runner = new AppendOutputRunner(listener);
    RemoteInterpreterEventServer server = serverWithRunner(listener, runner);
    try {
      runner.appendBuffer("note", "para", 0, "old");
      server.updateAllOutput(new OutputUpdateAllEvent("note", "para", Collections.singletonList(
          new RemoteInterpreterResultMessage("HTML", "replacement"))));
      verify(listener).onOutputUpdated("note", "para", 0,
          InterpreterResult.Type.HTML, "replacement");
      runner.appendBuffer("note", "para", 0, "new");
      runner.run();
      InOrder order = inOrder(listener);
      order.verify(listener).onOutputAppend("note", "para", 0, "old");
      order.verify(listener).onOutputClear("note", "para");
      order.verify(listener).onOutputUpdated("note", "para", 0,
          InterpreterResult.Type.HTML, "replacement");
      order.verify(listener).onOutputAppend("note", "para", 0, "new");
    } finally {
      server.stop();
    }
  }

  @Test
  void updateAllWaitsForInFlightAppendAndCompletesBeforeReturning() throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    AppendOutputRunner runner = new AppendOutputRunner(listener);
    RemoteInterpreterEventServer server = serverWithRunner(listener, runner);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch updateStarted = new CountDownLatch(1);
    doAnswer(invocation -> {
      entered.countDown();
      assertTrue(release.await(5, TimeUnit.SECONDS));
      return null;
    }).when(listener).onOutputAppend("note", "para", 0, "old");
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      runner.appendBuffer("note", "para", 0, "old");
      Future<?> first = executor.submit(runner);
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      Future<?> update = executor.submit(() -> {
        updateStarted.countDown();
        server.updateAllOutput(new OutputUpdateAllEvent("note", "para", Collections.singletonList(
            new RemoteInterpreterResultMessage("HTML", "replacement"))));
        verify(listener).onOutputUpdated("note", "para", 0,
            InterpreterResult.Type.HTML, "replacement");
        return null;
      });
      assertTrue(updateStarted.await(5, TimeUnit.SECONDS));
      assertThrows(TimeoutException.class, () -> update.get(100, TimeUnit.MILLISECONDS));
      release.countDown();
      first.get(5, TimeUnit.SECONDS);
      update.get(5, TimeUnit.SECONDS);
      InOrder order = inOrder(listener);
      order.verify(listener).onOutputAppend("note", "para", 0, "old");
      order.verify(listener).onOutputClear("note", "para");
      order.verify(listener).onOutputUpdated("note", "para", 0,
          InterpreterResult.Type.HTML, "replacement");
    } finally {
      release.countDown();
      executor.shutdownNow();
      server.stop();
    }
  }

  private RemoteInterpreterEventServer serverWithRunner(
      RemoteInterpreterProcessListener listener, AppendOutputRunner runner) throws Exception {
    InterpreterSettingManager manager = mock(InterpreterSettingManager.class);
    when(manager.getRemoteInterpreterProcessListener()).thenReturn(listener);
    RemoteInterpreterEventServer server = new RemoteInterpreterEventServer(
        mock(ZeppelinConfiguration.class), manager);
    Field field = RemoteInterpreterEventServer.class.getDeclaredField("runner");
    field.setAccessible(true);
    field.set(server, runner);
    return server;
  }

  @Test
  void invokeMethodThrowsRpcExceptionWhenSerializationFails() throws Exception {
    ZeppelinConfiguration zConf = mock(ZeppelinConfiguration.class);
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
        "{\"resourcePoolId\":\"pool-id\",\"name\":\"resource-name\",\"noteId\":\"note-id\",\"paragraphId\":\"paragraph-id\"}"
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
