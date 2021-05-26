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
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

/**
 * Notebook websocket.
 */
public class NotebookSocket extends WebSocketAdapter {
  private Session connection;
  private final NotebookSocketListener listener;
  private final HttpServletRequest request;
  private final String protocol;
  private String user;

  public NotebookSocket(HttpServletRequest req, String protocol,
      NotebookSocketListener listener) {
    this.listener = listener;
    this.request = req;
    this.protocol = protocol;
    this.user = StringUtils.EMPTY;
  }

  @Override
  public void onWebSocketClose(int closeCode, String message) {
    listener.onClose(this, closeCode, message);
  }

  @Override
  public void onWebSocketConnect(Session connection) {
    this.connection = connection;
    listener.onOpen(this);
  }

  @Override
  public void onWebSocketText(String message) {
    listener.onMessage(this, message);
  }

  public HttpServletRequest getRequest() {
    return request;
  }

  public String getProtocol() {
    return protocol;
  }

  public synchronized void send(String serializeMessage) throws IOException {
    connection.getRemote().sendStringByFuture(serializeMessage);
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  @Override
  public String toString() {
    return request.getRemoteHost() + ":" + request.getRemotePort();
  }
}
