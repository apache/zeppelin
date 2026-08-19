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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;

import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;

import org.apache.shiro.mgt.SecurityManager;
import org.apache.zeppelin.common.Message;
import org.apache.zeppelin.common.Message.OP;
import org.apache.zeppelin.service.AuthenticatedIdentity;
import org.apache.zeppelin.service.AuthenticatedSessionService;
import org.apache.zeppelin.service.NotebookService;
import org.apache.zeppelin.service.ServiceContext;
import org.apache.zeppelin.service.AuthenticatedSessionService.SessionAuthenticationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotebookServerAuthenticationTest {

  @Test
  void missingHandshakeIdentityFailsClosedBeforeSessionLookup() throws Exception {
    AuthenticatedSessionService sessionService = mock(AuthenticatedSessionService.class);
    ConnectionManager connectionManager = mock(ConnectionManager.class);
    NotebookServer server = server(
        sessionService, mock(NotebookService.class), connectionManager);
    Session session = mock(Session.class);
    ServerEndpointConfig endpointConfig = ServerEndpointConfig.Builder
        .create(NotebookServer.class, "/ws")
        .build();

    server.onOpen(session, endpointConfig);

    ArgumentCaptor<CloseReason> closeReason = ArgumentCaptor.forClass(CloseReason.class);
    verify(session).close(closeReason.capture());
    assertEquals(CloseReason.CloseCodes.VIOLATED_POLICY,
        closeReason.getValue().getCloseCode());
    verifyNoInteractions(sessionService, connectionManager);
  }

  @Test
  void logoutBetweenHandshakeValidationAndRegistrationClosesTheSocket() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("user1", Set.of(), true, "session-id");
    SecurityManager securityManager = mock(SecurityManager.class);
    AuthenticatedSessionService sessionService = mock(AuthenticatedSessionService.class);
    when(sessionService.refresh(identity, securityManager, false)).thenReturn(identity);
    doThrow(new SessionAuthenticationException("logged out"))
        .when(sessionService).validate(identity, securityManager);
    ConnectionManager connectionManager = mock(ConnectionManager.class);
    NotebookServer server = server(sessionService, mock(NotebookService.class), connectionManager);
    Session session = mock(Session.class);
    when(session.getId()).thenReturn("websocket-id");
    ServerEndpointConfig endpointConfig = ServerEndpointConfig.Builder
        .create(NotebookServer.class, "/ws")
        .build();
    endpointConfig.getUserProperties().put(SessionConfigurator.AUTHENTICATED_IDENTITY, identity);
    endpointConfig.getUserProperties().put(
        SessionConfigurator.AUTHENTICATION_SECURITY_MANAGER, securityManager);

    server.onOpen(session, endpointConfig);

    verify(connectionManager).addUserConnection(eq("user1"), any(NotebookSocket.class));
    verify(connectionManager).removeConnection(any(NotebookSocket.class));
    verify(connectionManager).removeConnectionFromAllNote(any(NotebookSocket.class));
    verify(connectionManager).removeUserConnection(anyString(), any(NotebookSocket.class));
    ArgumentCaptor<CloseReason> closeReason = ArgumentCaptor.forClass(CloseReason.class);
    verify(session).close(closeReason.capture());
    assertEquals(CloseReason.CloseCodes.VIOLATED_POLICY,
        closeReason.getValue().getCloseCode());
  }

  @Test
  void clientIdentityFieldsCannotOverrideTheAuthenticatedSession() throws Exception {
    AuthenticatedIdentity connectionIdentity =
        new AuthenticatedIdentity("user1", Set.of("role1"), true, "session-id");
    AuthenticatedIdentity refreshedIdentity =
        new AuthenticatedIdentity("user1", Set.of("role2"), true, "session-id");
    AuthenticatedSessionService sessionService = mock(AuthenticatedSessionService.class);
    SecurityManager securityManager = mock(SecurityManager.class);
    when(sessionService.refresh(connectionIdentity, securityManager, true))
        .thenReturn(refreshedIdentity);
    NotebookService notebookService = mock(NotebookService.class);
    NotebookServer server = server(
        sessionService, notebookService, mock(ConnectionManager.class));
    NotebookSocket socket = authenticatedSocket(connectionIdentity, securityManager);

    Message message = new Message(OP.LIST_NOTES);
    message.principal = "admin";
    message.roles = "[\"admin\"]";
    message.ticket = "forged-ticket";
    server.onMessage(socket, message.toJson());

    ArgumentCaptor<ServiceContext> context = ArgumentCaptor.forClass(ServiceContext.class);
    verify(notebookService).listNotesInfo(eq(false), context.capture(), any());
    assertEquals("user1", context.getValue().getAutheInfo().getUser());
    assertEquals(Set.of("role2"), context.getValue().getAutheInfo().getRoles());
    assertNull(context.getValue().getAutheInfo().getTicket());
  }

  @Test
  void invalidSessionClosesBeforeDispatch() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("user1", Set.of(), true, "session-id");
    AuthenticatedSessionService sessionService = mock(AuthenticatedSessionService.class);
    SecurityManager securityManager = mock(SecurityManager.class);
    when(sessionService.refresh(identity, securityManager, true))
        .thenThrow(new SessionAuthenticationException("expired"));
    NotebookService notebookService = mock(NotebookService.class);
    NotebookServer server = server(
        sessionService, notebookService, mock(ConnectionManager.class));
    NotebookSocket socket = authenticatedSocket(identity, securityManager);

    server.onMessage(socket, new Message(OP.LIST_NOTES).toJson());

    ArgumentCaptor<CloseReason> closeReason = ArgumentCaptor.forClass(CloseReason.class);
    verify(socket).close(closeReason.capture());
    assertEquals(CloseReason.CloseCodes.VIOLATED_POLICY,
        closeReason.getValue().getCloseCode());
    verify(notebookService, never()).listNotesInfo(anyBoolean(), any(), any());
    verify(socket, never()).send(any());
  }

  @Test
  void pingRevalidatesWithoutTouchingTheSession() {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("user1", Set.of(), true, "session-id");
    AuthenticatedSessionService sessionService = mock(AuthenticatedSessionService.class);
    SecurityManager securityManager = mock(SecurityManager.class);
    NotebookServer server = server(
        sessionService, mock(NotebookService.class), mock(ConnectionManager.class));
    NotebookSocket socket = authenticatedSocket(identity, securityManager);

    server.onMessage(socket, new Message(OP.PING).toJson());

    verify(sessionService).validate(identity, securityManager);
    verify(sessionService, never()).refresh(any(), any(), anyBoolean());
  }

  private static NotebookServer server(
      AuthenticatedSessionService sessionService,
      NotebookService notebookService,
      ConnectionManager connectionManager) {
    NotebookServer server = new NotebookServer();
    server.setAuthenticatedSessionService(sessionService);
    server.setNotebookService(() -> notebookService);
    server.setConnectionManager(connectionManager);
    return server;
  }

  private static NotebookSocket authenticatedSocket(
      AuthenticatedIdentity identity, SecurityManager securityManager) {
    NotebookSocket socket = mock(NotebookSocket.class);
    when(socket.getAuthenticatedIdentity()).thenReturn(identity);
    when(socket.getAuthenticationSecurityManager()).thenReturn(securityManager);
    when(socket.getUser()).thenReturn(identity.getPrincipal());
    return socket;
  }
}
