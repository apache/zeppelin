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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.zeppelin.service.AuthenticatedIdentity;
import org.apache.zeppelin.service.AuthenticatedSessionService;
import org.apache.zeppelin.service.SessionAuthenticationException;
import org.junit.jupiter.api.Test;

class NotebookSocketTest {

  @Test
  void outboundMessagesCloseAnExpiredSessionBeforeSending() throws Exception {
    Session session = mock(Session.class);
    when(session.getId()).thenReturn("websocket-id");
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("user1", Set.of(), true, "session-id");
    SecurityManager securityManager = mock(SecurityManager.class);
    AuthenticatedSessionService sessionService = mock(AuthenticatedSessionService.class);
    org.mockito.Mockito.doThrow(new SessionAuthenticationException("expired"))
        .when(sessionService).validate(identity, securityManager);
    NotebookSocket socket =
        new NotebookSocket(session, Map.of(), identity, securityManager, sessionService);

    assertThrows(IOException.class, () -> socket.send("message"));

    verify(session).close(any(CloseReason.class));
    verify(session, never()).getAsyncRemote();
  }
}
