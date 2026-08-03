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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import jakarta.inject.Provider;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.zeppelin.common.Message;
import org.apache.zeppelin.common.Message.OP;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.service.AuthenticatedIdentity;
import org.apache.zeppelin.service.AuthenticatedSessionService;
import org.apache.zeppelin.service.NotebookService;
import org.apache.zeppelin.service.ServiceContext;
import org.apache.zeppelin.service.SessionAuthenticationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotebookServerAuthenticationTest {

  @Test
  void noteExportRequiresReaderPermissionBeforeAccessingNotebookData() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("user1", Set.of("role1"), true, "session-id");
    AuthenticatedSessionService sessionService = mock(AuthenticatedSessionService.class);
    SecurityManager securityManager = mock(SecurityManager.class);
    when(sessionService.refresh(identity, securityManager, true)).thenReturn(identity);
    AuthorizationService authorizationService = mock(AuthorizationService.class);
    Provider<Notebook> notebookProvider = mock(Provider.class);
    NotebookServer server = server(
        sessionService, mock(NotebookService.class), authorizationService);
    server.setNotebook(notebookProvider);
    NotebookSocket socket = authenticatedSocket(identity, securityManager);

    server.onMessage(socket,
        new Message(OP.CONVERT_NOTE_NBFORMAT).put("noteId", "private-note").toJson());

    verify(authorizationService).isReader(
        "private-note", Set.of("user1", "role1"));
    verify(notebookProvider, never()).get();
  }

  @Test
  void interpreterBindingChangesRequireWriterPermission() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("user1", Set.of(), true, "session-id");
    AuthenticatedSessionService sessionService = mock(AuthenticatedSessionService.class);
    SecurityManager securityManager = mock(SecurityManager.class);
    when(sessionService.refresh(identity, securityManager, true)).thenReturn(identity);
    AuthorizationService authorizationService = mock(AuthorizationService.class);
    Provider<Notebook> notebookProvider = mock(Provider.class);
    NotebookServer server = server(
        sessionService, mock(NotebookService.class), authorizationService);
    server.setNotebook(notebookProvider);

    server.onMessage(authenticatedSocket(identity, securityManager),
        new Message(OP.SAVE_INTERPRETER_BINDINGS)
            .put("noteId", "private-note")
            .put("selectedSettingIds", "[]")
            .toJson());

    verify(authorizationService).isWriter("private-note", Set.of("user1"));
    verify(notebookProvider, never()).get();
  }

  @Test
  void angularObjectMutationRequiresRunnerPermission() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("user1", Set.of(), true, "session-id");
    AuthenticatedSessionService sessionService = mock(AuthenticatedSessionService.class);
    SecurityManager securityManager = mock(SecurityManager.class);
    when(sessionService.refresh(identity, securityManager, true)).thenReturn(identity);
    AuthorizationService authorizationService = mock(AuthorizationService.class);
    Provider<Notebook> notebookProvider = mock(Provider.class);
    NotebookServer server = server(
        sessionService, mock(NotebookService.class), authorizationService);
    server.setNotebook(notebookProvider);

    server.onMessage(authenticatedSocket(identity, securityManager),
        new Message(OP.ANGULAR_OBJECT_CLIENT_BIND)
            .put("noteId", "private-note")
            .put("paragraphId", "paragraph-id")
            .put("name", "value")
            .toJson());

    verify(authorizationService).isRunner("private-note", Set.of("user1"));
    verify(notebookProvider, never()).get();
  }

  @Test
  void logoutBetweenHandshakeValidationAndRegistrationStillClosesTheSocket() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("user1", Set.of(), true, "session-id");
    SecurityManager securityManager = mock(SecurityManager.class);
    AuthenticatedSessionService sessionService = mock(AuthenticatedSessionService.class);
    when(sessionService.refresh(identity, securityManager, false)).thenReturn(identity);
    org.mockito.Mockito.doThrow(new SessionAuthenticationException("logged out"))
        .when(sessionService).validate(identity, securityManager);
    NotebookServer server = server(sessionService, mock(NotebookService.class));
    Session session = mock(Session.class);
    when(session.getId()).thenReturn("websocket-id");
    ServerEndpointConfig endpointConfig = ServerEndpointConfig.Builder
        .create(NotebookServer.class, "/ws")
        .build();
    endpointConfig.getUserProperties().put(SessionConfigurator.AUTHENTICATED_IDENTITY, identity);
    endpointConfig.getUserProperties().put(
        SessionConfigurator.AUTHENTICATION_SECURITY_MANAGER, securityManager);

    server.onOpen(session, endpointConfig);

    ArgumentCaptor<CloseReason> closeReason = ArgumentCaptor.forClass(CloseReason.class);
    verify(session).close(closeReason.capture());
    assertEquals(CloseReason.CloseCodes.VIOLATED_POLICY,
        closeReason.getValue().getCloseCode());
  }

  @Test
  void clientSuppliedIdentityFieldsCannotOverrideTheAuthenticatedSession() throws Exception {
    AuthenticatedIdentity connectionIdentity =
        new AuthenticatedIdentity("user1", Set.of("role1"), true, "session-id");
    AuthenticatedIdentity refreshedIdentity =
        new AuthenticatedIdentity("user1", Set.of("role2"), true, "session-id");
    AuthenticatedSessionService sessionService = mock(AuthenticatedSessionService.class);
    SecurityManager securityManager = mock(SecurityManager.class);
    when(sessionService.refresh(connectionIdentity, securityManager, true))
        .thenReturn(refreshedIdentity);
    NotebookService notebookService = mock(NotebookService.class);
    NotebookServer server = server(sessionService, notebookService);
    NotebookSocket socket = mock(NotebookSocket.class);
    when(socket.getAuthenticatedIdentity()).thenReturn(connectionIdentity);
    when(socket.getAuthenticationSecurityManager()).thenReturn(securityManager);
    when(socket.getUser()).thenReturn("user1");

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
  void invalidSessionClosesTheSocketWithPolicyViolationBeforeDispatch() throws Exception {
    AuthenticatedIdentity connectionIdentity =
        new AuthenticatedIdentity("user1", Set.of(), true, "session-id");
    AuthenticatedSessionService sessionService = mock(AuthenticatedSessionService.class);
    SecurityManager securityManager = mock(SecurityManager.class);
    when(sessionService.refresh(connectionIdentity, securityManager, true)).thenThrow(
        new SessionAuthenticationException("expired"));
    NotebookService notebookService = mock(NotebookService.class);
    NotebookServer server = server(sessionService, notebookService);
    NotebookSocket socket = mock(NotebookSocket.class);
    when(socket.getAuthenticatedIdentity()).thenReturn(connectionIdentity);
    when(socket.getAuthenticationSecurityManager()).thenReturn(securityManager);

    server.onMessage(socket, new Message(OP.LIST_NOTES).toJson());

    ArgumentCaptor<CloseReason> closeReason = ArgumentCaptor.forClass(CloseReason.class);
    verify(socket).close(closeReason.capture());
    assertEquals(CloseReason.CloseCodes.VIOLATED_POLICY,
        closeReason.getValue().getCloseCode());
    verify(notebookService, never()).listNotesInfo(any(Boolean.class), any(), any());
    verify(socket, never()).send(any());
  }

  @Test
  void pingRevalidatesWithoutTouchingTheSession() {
    AuthenticatedIdentity connectionIdentity =
        new AuthenticatedIdentity("user1", Set.of(), true, "session-id");
    AuthenticatedSessionService sessionService = mock(AuthenticatedSessionService.class);
    SecurityManager securityManager = mock(SecurityManager.class);
    NotebookServer server = server(sessionService, mock(NotebookService.class));
    NotebookSocket socket = mock(NotebookSocket.class);
    when(socket.getAuthenticatedIdentity()).thenReturn(connectionIdentity);
    when(socket.getAuthenticationSecurityManager()).thenReturn(securityManager);
    when(socket.getUser()).thenReturn("user1");

    server.onMessage(socket, new Message(OP.PING).toJson());

    verify(sessionService).validate(connectionIdentity, securityManager);
    verify(sessionService, never()).refresh(any(), any(), anyBoolean());
    verify(sessionService, never()).refresh(any(), any(), anyBoolean(), anyLong());
  }

  @Test
  void jobUpdatesAreSentOnlyToSubscribersWhoOwnTheNote() throws Exception {
    AuthenticatedIdentity allowedIdentity =
        new AuthenticatedIdentity("reader", Set.of("reader-role"), true, "reader-session");
    AuthenticatedIdentity deniedIdentity =
        new AuthenticatedIdentity("other", Set.of(), true, "other-session");
    NotebookSocket allowed = mock(NotebookSocket.class);
    NotebookSocket denied = mock(NotebookSocket.class);
    SecurityManager allowedSecurityManager = mock(SecurityManager.class);
    SecurityManager deniedSecurityManager = mock(SecurityManager.class);
    when(allowed.getAuthenticatedIdentity()).thenReturn(allowedIdentity);
    when(denied.getAuthenticatedIdentity()).thenReturn(deniedIdentity);
    when(allowed.getAuthenticationSecurityManager()).thenReturn(allowedSecurityManager);
    when(denied.getAuthenticationSecurityManager()).thenReturn(deniedSecurityManager);
    AuthenticatedSessionService sessionService = mock(AuthenticatedSessionService.class);
    when(sessionService.refresh(allowedIdentity, allowedSecurityManager, false, 1_000L))
        .thenReturn(allowedIdentity);
    when(sessionService.refresh(deniedIdentity, deniedSecurityManager, false, 1_000L))
        .thenReturn(deniedIdentity);
    AuthorizationService authorizationService = mock(AuthorizationService.class);
    when(authorizationService.isOwner(Set.of("reader", "reader-role"), "note-id"))
        .thenReturn(true);
    ConnectionManager connectionManager = mock(ConnectionManager.class);
    when(connectionManager.getNoteConnections("JOB_MANAGER_PAGE"))
        .thenReturn(List.of(allowed, denied));
    NotebookServer server = server(
        sessionService,
        mock(NotebookService.class),
        authorizationService);
    server.setConnectionManager(connectionManager);
    Note note = mock(Note.class);
    when(note.getId()).thenReturn("note-id");
    Message message = new Message(OP.LIST_UPDATE_NOTE_JOBS);

    server.broadcastJobUpdateToAuthorizedSubscribers(note, message);

    verify(allowed).send(server.serializeMessage(message));
    verify(denied, never()).send(any());
  }

  @Test
  void noteBroadcastsStopImmediatelyAfterReadAccessIsRevoked() throws Exception {
    AuthenticatedIdentity connectionIdentity =
        new AuthenticatedIdentity("user", Set.of("former-reader"), true, "session-id");
    AuthenticatedIdentity refreshedIdentity =
        new AuthenticatedIdentity("user", Set.of(), true, "session-id");
    SecurityManager securityManager = mock(SecurityManager.class);
    NotebookSocket connection = authenticatedSocket(connectionIdentity, securityManager);
    AuthenticatedSessionService sessionService = mock(AuthenticatedSessionService.class);
    when(sessionService.refresh(connectionIdentity, securityManager, false, 1_000L))
        .thenReturn(refreshedIdentity);
    AuthorizationService authorizationService = mock(AuthorizationService.class);
    ConnectionManager connectionManager = mock(ConnectionManager.class);
    when(connectionManager.getNoteConnections("note-id"))
        .thenReturn(List.of(connection));
    NotebookServer server = server(
        sessionService, mock(NotebookService.class), authorizationService);
    server.setConnectionManager(connectionManager);

    server.broadcastToAuthorizedNoteSubscribers(
        "note-id", new Message(OP.NOTE).put("note", "private-content"));

    verify(authorizationService).isReader("note-id", Set.of("user"));
    verify(connectionManager).removeNoteConnection("note-id", connection);
    verify(connection, never()).send(any());
  }

  @Test
  void noteBroadcastsUseBoundedRoleSnapshotForAuthorization() throws Exception {
    AuthenticatedIdentity connectionIdentity =
        new AuthenticatedIdentity("user", Set.of(), true, "session-id");
    AuthenticatedIdentity refreshedIdentity =
        new AuthenticatedIdentity("user", Set.of("new-reader"), true, "session-id");
    SecurityManager securityManager = mock(SecurityManager.class);
    NotebookSocket connection = authenticatedSocket(connectionIdentity, securityManager);
    AuthenticatedSessionService sessionService = mock(AuthenticatedSessionService.class);
    when(sessionService.refresh(connectionIdentity, securityManager, false, 1_000L))
        .thenReturn(refreshedIdentity);
    AuthorizationService authorizationService = mock(AuthorizationService.class);
    when(authorizationService.isReader("note-id", Set.of("user", "new-reader")))
        .thenReturn(true);
    ConnectionManager connectionManager = mock(ConnectionManager.class);
    when(connectionManager.getNoteConnections("note-id"))
        .thenReturn(List.of(connection));
    NotebookServer server = server(
        sessionService, mock(NotebookService.class), authorizationService);
    server.setConnectionManager(connectionManager);
    Message message = new Message(OP.NOTE).put("note", "private-content");

    server.broadcastToAuthorizedNoteSubscribers("note-id", message);

    verify(connection).send(server.serializeMessage(message));
  }

  @Test
  void jobUpdatesUseBoundedRoleSnapshotAfterRoleRevocation() throws Exception {
    AuthenticatedIdentity connectionIdentity =
        new AuthenticatedIdentity("user", Set.of("owner-role"), true, "session-id");
    AuthenticatedIdentity refreshedIdentity =
        new AuthenticatedIdentity("user", Set.of(), true, "session-id");
    SecurityManager securityManager = mock(SecurityManager.class);
    NotebookSocket connection = authenticatedSocket(connectionIdentity, securityManager);
    AuthenticatedSessionService sessionService = mock(AuthenticatedSessionService.class);
    when(sessionService.refresh(connectionIdentity, securityManager, false, 1_000L))
        .thenReturn(refreshedIdentity);
    AuthorizationService authorizationService = mock(AuthorizationService.class);
    ConnectionManager connectionManager = mock(ConnectionManager.class);
    when(connectionManager.getNoteConnections("JOB_MANAGER_PAGE"))
        .thenReturn(List.of(connection));
    NotebookServer server = server(
        sessionService, mock(NotebookService.class), authorizationService);
    server.setConnectionManager(connectionManager);
    Note note = mock(Note.class);
    when(note.getId()).thenReturn("note-id");

    server.broadcastJobUpdateToAuthorizedSubscribers(
        note, new Message(OP.LIST_UPDATE_NOTE_JOBS));

    verify(authorizationService).isOwner(Set.of("user"), "note-id");
    verify(connection, never()).send(any());
  }

  @Test
  void invalidJobSubscriberIsClosedAndUnsubscribed() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("user", Set.of(), true, "session-id");
    SecurityManager securityManager = mock(SecurityManager.class);
    NotebookSocket connection = authenticatedSocket(identity, securityManager);
    AuthenticatedSessionService sessionService = mock(AuthenticatedSessionService.class);
    when(sessionService.refresh(identity, securityManager, false, 1_000L))
        .thenThrow(new SessionAuthenticationException("expired"));
    ConnectionManager connectionManager = mock(ConnectionManager.class);
    when(connectionManager.getNoteConnections("JOB_MANAGER_PAGE"))
        .thenReturn(List.of(connection));
    NotebookServer server = server(sessionService, mock(NotebookService.class));
    server.setConnectionManager(connectionManager);
    Note note = mock(Note.class);
    when(note.getId()).thenReturn("note-id");

    server.broadcastJobUpdateToAuthorizedSubscribers(
        note, new Message(OP.LIST_UPDATE_NOTE_JOBS));

    verify(connectionManager).removeNoteConnection("JOB_MANAGER_PAGE", connection);
    ArgumentCaptor<CloseReason> closeReason = ArgumentCaptor.forClass(CloseReason.class);
    verify(connection).close(closeReason.capture());
    assertEquals(
        CloseReason.CloseCodes.VIOLATED_POLICY, closeReason.getValue().getCloseCode());
  }

  @Test
  void noteListBroadcastUsesBoundedRoleSnapshot() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("user", Set.of("reader-role"), true, "session-id");
    SecurityManager securityManager = mock(SecurityManager.class);
    NotebookSocket connection = authenticatedSocket(identity, securityManager);
    AuthenticatedSessionService sessionService = mock(AuthenticatedSessionService.class);
    when(sessionService.refresh(identity, securityManager, false, 1_000L))
        .thenReturn(identity);
    AuthorizationService authorizationService = mock(AuthorizationService.class);
    ConnectionManager connectionManager = mock(ConnectionManager.class);
    when(connectionManager.getConnections()).thenReturn(List.of(connection));
    Notebook notebook = mock(Notebook.class);
    when(notebook.getNotesInfo(any())).thenReturn(List.of());
    NotebookServer server = server(
        sessionService, mock(NotebookService.class), authorizationService);
    server.setConnectionManager(connectionManager);
    server.setNotebook(() -> notebook);

    server.broadcastNoteListUpdate();

    verify(sessionService).refresh(identity, securityManager, false, 1_000L);
    verify(connectionManager).unicast(any(Message.class), eq(connection));
  }

  @Test
  void repositoryReloadRequiresTheConfiguredAdministratorRole() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("user", Set.of("reader"), true, "session-id");
    SecurityManager securityManager = mock(SecurityManager.class);
    AuthenticatedSessionService sessionService = mock(AuthenticatedSessionService.class);
    when(sessionService.refresh(identity, securityManager, true)).thenReturn(identity);
    Provider<Notebook> notebookProvider = mock(Provider.class);
    NotebookServer server = server(sessionService, mock(NotebookService.class));
    ZeppelinConfiguration zConf = mock(ZeppelinConfiguration.class);
    when(zConf.getString(ConfVars.ZEPPELIN_OWNER_ROLE)).thenReturn("admin");
    server.setZeppelinConfiguration(zConf);
    server.setNotebook(notebookProvider);

    server.onMessage(
        authenticatedSocket(identity, securityManager),
        new Message(OP.RELOAD_NOTES_FROM_REPO).toJson());

    verify(notebookProvider, never()).get();
  }

  @Test
  void repositoryReloadAllowsTheConfiguredAdministratorRole() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("user", Set.of("admin"), true, "session-id");
    SecurityManager securityManager = mock(SecurityManager.class);
    AuthenticatedSessionService sessionService = mock(AuthenticatedSessionService.class);
    when(sessionService.refresh(identity, securityManager, true)).thenReturn(identity);
    Notebook notebook = mock(Notebook.class);
    NotebookServer server = server(sessionService, mock(NotebookService.class));
    ZeppelinConfiguration zConf = mock(ZeppelinConfiguration.class);
    when(zConf.getString(ConfVars.ZEPPELIN_OWNER_ROLE)).thenReturn("admin");
    server.setZeppelinConfiguration(zConf);
    server.setNotebook(() -> notebook);

    server.onMessage(
        authenticatedSocket(identity, securityManager),
        new Message(OP.RELOAD_NOTES_FROM_REPO).toJson());

    verify(notebook).reloadAllNotes(any());
  }

  private static NotebookServer server(
      AuthenticatedSessionService sessionService, NotebookService notebookService) {
    return server(sessionService, notebookService, mock(AuthorizationService.class));
  }

  private static NotebookServer server(
      AuthenticatedSessionService sessionService,
      NotebookService notebookService,
      AuthorizationService authorizationService) {
    NotebookServer server = new NotebookServer();
    ZeppelinConfiguration zConf = mock(ZeppelinConfiguration.class);
    when(zConf.getWebsocketAuthorizationRolesRefreshIntervalMs()).thenReturn(1_000L);
    server.setZeppelinConfiguration(zConf);
    server.setAuthenticatedSessionService(sessionService);
    server.setNotebookService(() -> notebookService);
    server.setConnectionManager(mock(ConnectionManager.class));
    server.setAuthorizationService(authorizationService);
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
