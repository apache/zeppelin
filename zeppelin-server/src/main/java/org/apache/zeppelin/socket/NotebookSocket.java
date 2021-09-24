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
import org.eclipse.jetty.websocket.api.WebSocketAdapter;

import java.io.IOException;
import java.util.Map;

import javax.websocket.Session;

/**
 * Notebook websocket.
 */
public class NotebookSocket extends WebSocketAdapter {
  private Session session;
  private Map<String, Object> headers;
  private String user;

  public NotebookSocket(Session session, Map<String, Object> headers) {
    this.session = session;
    this.headers = headers;
    this.user = StringUtils.EMPTY;
  }

  public String getHeader(String key) {
    return String.valueOf(headers.get(key));
  }

  public synchronized void send(String serializeMessage) throws IOException {
    session.getBasicRemote().sendText(serializeMessage);
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  @Override
  public String toString() {
    return String.valueOf(session.getUserProperties().get("javax.websocket.endpoint.remoteAddress"));
  }
}
