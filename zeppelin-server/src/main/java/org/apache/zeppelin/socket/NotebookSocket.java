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
import org.apache.zeppelin.utils.ServerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

import jakarta.websocket.Session;

/**
 * Notebook websocket.
 */
public class NotebookSocket {
  private static final Logger LOGGER = LoggerFactory.getLogger(NotebookSocket.class);

  private Session session;
  private Map<String, Object> headers;
  private String user;

  public NotebookSocket(Session session, Map<String, Object> headers) {
    this.session = session;
    this.headers = headers;
    this.user = StringUtils.EMPTY;
    LOGGER.debug("NotebookSocket created for session: {}", session.getId());
  }

  public String getHeader(String key) {
    return String.valueOf(headers.get(key));
  }

  public void send(String serializeMessage) throws IOException {
    session.getAsyncRemote().sendText(serializeMessage, result -> {
      if (result.getException() != null) {
        LOGGER.error("Failed to send async message for User {} in Session {}: {}", this.user, this.session.getId(), result.getException());
      }
    });
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
