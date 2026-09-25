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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.PongMessage;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;

import org.apache.zeppelin.MiniZeppelinServer;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.utils.CorsUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotebookServerHeartbeatTest {

  private NotebookServer notebookServer;

  @AfterEach
  void tearDown() {
    if (notebookServer != null) {
      notebookServer.stopHeartbeatScheduler();
    }
  }

  private NotebookServer buildNotebookServer(long heartbeatIntervalMs) {
    return buildNotebookServer(heartbeatIntervalMs, 0);
  }

  private NotebookServer buildNotebookServer(long heartbeatIntervalMs, int maxMissedPongs) {
    ZeppelinConfiguration zConf = mock(ZeppelinConfiguration.class);
    when(zConf.getWebsocketHeartbeatInterval()).thenReturn(heartbeatIntervalMs);
    when(zConf.getWebsocketHeartbeatMaxMissedPongs()).thenReturn(maxMissedPongs);
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
  void sendHeartbeatReapsSocketThatMissedTooManyPongs() {
    NotebookServer server = buildNotebookServer(60000L, 3);
    NotebookSocket dead = mock(NotebookSocket.class);
    NotebookSocket alive = mock(NotebookSocket.class);
    when(dead.getSessionId()).thenReturn("dead-session");
    when(dead.getPingsSinceLastPong()).thenReturn(3);
    when(alive.getPingsSinceLastPong()).thenReturn(2);
    server.getConnectionManager().addConnection(dead);
    server.getConnectionManager().addConnection(alive);

    server.sendHeartbeat();

    verify(dead).close(any(CloseReason.class));
    verify(dead, never()).sendPing();
    verify(alive).sendPing();
    verify(alive, never()).close(any(CloseReason.class));
    assertFalse(server.getConnectionManager().connectedSockets.contains(dead));
    assertTrue(server.getConnectionManager().connectedSockets.contains(alive));
  }

  @Test
  void sendHeartbeatDoesNotReapWhenReapingDisabled() {
    NotebookServer server = buildNotebookServer(60000L, 0);
    NotebookSocket silent = mock(NotebookSocket.class);
    when(silent.getPingsSinceLastPong()).thenReturn(100);
    server.getConnectionManager().addConnection(silent);

    server.sendHeartbeat();

    verify(silent).sendPing();
    verify(silent, never()).close(any(CloseReason.class));
  }

  @Test
  void sendHeartbeatDoesNotReapSocketThatIsHandlingMessage() {
    NotebookServer server = buildNotebookServer(60000L, 3);
    NotebookSocket busy = mock(NotebookSocket.class);
    when(busy.isHandlingMessage()).thenReturn(true);
    when(busy.getPingsSinceLastPong()).thenReturn(5);
    server.getConnectionManager().addConnection(busy);

    server.sendHeartbeat();

    verify(busy, never()).close(any(CloseReason.class));
    verify(busy).sendPing();
    assertTrue(server.getConnectionManager().connectedSockets.contains(busy));
  }

  private Session openSession(NotebookServer server, String sessionId) throws IOException {
    Session session = mock(Session.class);
    when(session.getId()).thenReturn(sessionId);
    when(session.getBasicRemote()).thenReturn(mock(RemoteEndpoint.Basic.class));
    Map<String, Object> headers = new HashMap<>();
    headers.put(CorsUtils.HEADER_ORIGIN, "http://localhost:8080");
    EndpointConfig config = mock(EndpointConfig.class);
    when(config.getUserProperties()).thenReturn(headers);
    server.onOpen(session, config);
    return session;
  }

  @Test
  @SuppressWarnings("unchecked")
  void onOpenRegistersPongHandlerThatResetsPingCount() throws IOException {
    NotebookServer server = buildNotebookServer(60000L, 3);
    Session session = openSession(server, "session-pong");
    NotebookSocket conn = server.getConnectionManager().connectedSockets.peek();
    assertNotNull(conn);

    ArgumentCaptor<MessageHandler.Whole<PongMessage>> captor =
        ArgumentCaptor.forClass(MessageHandler.Whole.class);
    verify(session).addMessageHandler(eq(PongMessage.class), captor.capture());

    conn.sendPing();
    conn.sendPing();
    assertEquals(2, conn.getPingsSinceLastPong());

    captor.getValue().onMessage(mock(PongMessage.class));
    assertEquals(0, conn.getPingsSinceLastPong());
  }

  @Test
  void onMessageMarksSocketAsHandlingMessageUntilItReturns() throws IOException {
    NotebookServer server = spy(buildNotebookServer(60000L, 3));
    notebookServer = server;
    Session session = openSession(server, "session-busy");
    NotebookSocket conn = server.getConnectionManager().connectedSockets.peek();
    assertNotNull(conn);

    AtomicBoolean handlingDuringOp = new AtomicBoolean();
    doAnswer(invocation -> {
      handlingDuringOp.set(conn.isHandlingMessage());
      return null;
    }).when(server).onMessage(any(NotebookSocket.class), anyString());

    server.onMessage(session, "{}");

    assertTrue(handlingDuringOp.get());
    assertFalse(conn.isHandlingMessage());
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

  @Test
  void stopHeartbeatSchedulerAllowsRepeatedStartStopCycles() {
    NotebookServer server = buildNotebookServer(50L);

    for (int i = 0; i < 3; i++) {
      server.startHeartbeatScheduler();
      ScheduledExecutorService scheduler = server.heartbeatScheduler;
      assertNotNull(scheduler);

      server.stopHeartbeatScheduler();

      assertTrue(scheduler.isShutdown());
      assertNull(server.heartbeatScheduler);
    }
  }

  @Test
  void stopHeartbeatSchedulerIsSafeWhenNeverStarted() {
    NotebookServer server = buildNotebookServer(50L);

    assertDoesNotThrow(server::stopHeartbeatScheduler);
    assertDoesNotThrow(server::stopHeartbeatScheduler);
  }

  @Test
  void zeppelinServerShutdownStopsHeartbeatScheduler() throws Exception {
    MiniZeppelinServer zepServer =
        new MiniZeppelinServer(NotebookServerHeartbeatTest.class.getSimpleName());
    try {
      zepServer.start();
      NotebookServer server = zepServer.getService(NotebookServer.class);
      server.startHeartbeatScheduler();
      ScheduledExecutorService scheduler = server.heartbeatScheduler;
      assertNotNull(scheduler);

      zepServer.shutDown();

      assertTrue(scheduler.isShutdown());
    } finally {
      zepServer.destroy();
    }
  }
}
