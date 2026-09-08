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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NotebookServerHeartbeatTest {

  private NotebookServer notebookServer;

  @AfterEach
  void tearDown() {
    if (notebookServer != null) {
      notebookServer.stopHeartbeatScheduler();
    }
  }

  private NotebookServer buildNotebookServer(long heartbeatIntervalMs) {
    ZeppelinConfiguration zConf = mock(ZeppelinConfiguration.class);
    when(zConf.getWebsocketHeartbeatInterval()).thenReturn(heartbeatIntervalMs);
    AuthorizationService authorizationService = mock(AuthorizationService.class);
    ConnectionManager connectionManager = new ConnectionManager(authorizationService, zConf);

    notebookServer = new NotebookServer();
    notebookServer.setZeppelinConfiguration(zConf);
    notebookServer.setConnectionManager(connectionManager);
    return notebookServer;
  }

  @Test
  void sendHeartbeatSendsPingToEveryConnectedSocket() {
    NotebookServer server = buildNotebookServer(60000L);
    NotebookSocket first = mock(NotebookSocket.class);
    NotebookSocket second = mock(NotebookSocket.class);
    server.getConnectionManager().addConnection(first);
    server.getConnectionManager().addConnection(second);

    server.sendHeartbeat();

    verify(first).sendPing();
    verify(second).sendPing();
  }

  @Test
  void sendHeartbeatContinuesWhenOneSocketThrows() {
    NotebookServer server = buildNotebookServer(60000L);
    NotebookSocket failing = mock(NotebookSocket.class);
    NotebookSocket healthy = mock(NotebookSocket.class);
    doThrow(new RuntimeException("connection reset")).when(failing).sendPing();
    server.getConnectionManager().addConnection(failing);
    server.getConnectionManager().addConnection(healthy);

    assertDoesNotThrow(server::sendHeartbeat);

    verify(healthy).sendPing();
  }

  @Test
  void startHeartbeatSchedulerStartsWhenIntervalPositive() {
    NotebookServer server = buildNotebookServer(50L);

    server.startHeartbeatScheduler();

    assertNotNull(server.heartbeatScheduler);
  }

  @Test
  void startHeartbeatSchedulerDoesNotStartWhenIntervalIsZero() {
    NotebookServer server = buildNotebookServer(0L);

    server.startHeartbeatScheduler();

    assertNull(server.heartbeatScheduler);
  }

  @Test
  void startHeartbeatSchedulerDoesNotStartWhenIntervalIsNegative() {
    NotebookServer server = buildNotebookServer(-1L);

    server.startHeartbeatScheduler();

    assertNull(server.heartbeatScheduler);
  }
}
