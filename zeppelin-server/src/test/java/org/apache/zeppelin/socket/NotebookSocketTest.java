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
package org.apache.zeppelin.socket;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;

import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;

import org.junit.jupiter.api.Test;

class NotebookSocketTest {

  @Test
  void sendPingWritesEmptyPingFrameToBasicRemote() throws IOException {
    Session session = mock(Session.class);
    RemoteEndpoint.Basic basicRemote = mock(RemoteEndpoint.Basic.class);
    when(session.getId()).thenReturn("session-1");
    when(session.getBasicRemote()).thenReturn(basicRemote);
    NotebookSocket notebookSocket = new NotebookSocket(session, Collections.emptyMap());

    notebookSocket.sendPing();

    verify(basicRemote).sendPing(any(ByteBuffer.class));
  }

  @Test
  void sendPingSwallowsIOExceptionFromDeadSession() throws IOException {
    Session session = mock(Session.class);
    RemoteEndpoint.Basic basicRemote = mock(RemoteEndpoint.Basic.class);
    when(session.getId()).thenReturn("session-2");
    when(session.getBasicRemote()).thenReturn(basicRemote);
    doThrow(new IOException("session already closed"))
        .when(basicRemote).sendPing(any(ByteBuffer.class));
    NotebookSocket notebookSocket = new NotebookSocket(session, Collections.emptyMap());

    assertDoesNotThrow(notebookSocket::sendPing);
  }
}
