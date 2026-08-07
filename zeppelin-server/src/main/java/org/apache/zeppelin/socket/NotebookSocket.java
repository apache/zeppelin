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

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.service.AuthenticatedIdentity;
import org.apache.zeppelin.service.AuthenticatedSessionService;
import org.apache.zeppelin.service.SessionAuthenticationException;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.zeppelin.utils.ServerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;

/**
 * Notebook websocket.
 */
public class NotebookSocket {
  private static final Logger LOGGER = LoggerFactory.getLogger(NotebookSocket.class);

  private final Session session;
  private final Map<String, Object> headers;
  private final AuthenticatedIdentity authenticatedIdentity;
  private final SecurityManager authenticationSecurityManager;
  private final AuthenticatedSessionService authenticatedSessionService;
  private String user;

  public NotebookSocket(
      Session session,
      Map<String, Object> headers,
      AuthenticatedIdentity authenticatedIdentity,
      SecurityManager authenticationSecurityManager,
      AuthenticatedSessionService authenticatedSessionService) {
    this.session = session;
    this.headers = Collections.unmodifiableMap(new HashMap<>(headers));
    this.authenticatedIdentity = authenticatedIdentity;
    this.authenticationSecurityManager = authenticationSecurityManager;
    this.authenticatedSessionService = authenticatedSessionService;
    this.user = StringUtils.EMPTY;
    LOGGER.debug("NotebookSocket created for session: {}", session.getId());
  }

  public String getHeader(String key) {
    return String.valueOf(headers.get(key));
  }

  public void send(String serializeMessage) throws IOException {
    try {
      authenticatedSessionService.validate(
          authenticatedIdentity, authenticationSecurityManager);
    } catch (SessionAuthenticationException e) {
      try {
        close(new CloseReason(
            CloseReason.CloseCodes.VIOLATED_POLICY,
            "Authenticated session is no longer valid"));
      } catch (IOException closeFailure) {
        e.addSuppressed(closeFailure);
      }
      throw new IOException("Authenticated session is no longer valid", e);
    }
    session.getAsyncRemote().sendText(serializeMessage, result -> {
      if (result.getException() != null) {
        LOGGER.error("Failed to send async message for User {} in Session {}: {}", this.user, this.session.getId(), result.getException());
      }
    });
  }

  public void close(CloseReason closeReason) throws IOException {
    session.close(closeReason);
  }

  public AuthenticatedIdentity getAuthenticatedIdentity() {
    return authenticatedIdentity;
  }

  public SecurityManager getAuthenticationSecurityManager() {
    return authenticationSecurityManager;
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    LOGGER.debug("Setting user: {}", user);
    this.user = user;
  }

  @Override
  public String toString() {
    return ServerUtils.getRemoteAddress(session);
  }
}
