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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.remote.InvokeResourceMethodEventMessage;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.thrift.InterpreterRPCException;
import org.apache.zeppelin.resource.Resource;
import org.apache.zeppelin.resource.ResourceId;
import org.junit.jupiter.api.Test;

public class RemoteInterpreterEventServerTest {
  
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
