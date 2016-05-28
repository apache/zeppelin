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
package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.listener;

import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.ZeppelinhubClient;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.utils.ZeppelinhubUtils;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zeppelinhub websocket handler.
 */
public class ZeppelinhubWebsocket implements WebSocketListener {
  private Logger LOG = LoggerFactory.getLogger(ZeppelinhubWebsocket.class);
  private Session zeppelinHubSession;
  private final String token;
  
  private ZeppelinhubWebsocket(String token) {
    this.token = token;
  }

  public static ZeppelinhubWebsocket newInstance(String token) {
    return new ZeppelinhubWebsocket(token);
  }
  
  @Override
  public void onWebSocketBinary(byte[] payload, int offset, int len) {}

  @Override
  public void onWebSocketClose(int statusCode, String reason) {
    LOG.info("Closing websocket connection [{}] : {}", statusCode, reason);
    send(ZeppelinhubUtils.deadMessage(token));
    this.zeppelinHubSession = null;
  }

  @Override
  public void onWebSocketConnect(Session session) {
    LOG.info("Opening a new session to Zeppelinhub {}", session.hashCode());
    this.zeppelinHubSession = session;
    send(ZeppelinhubUtils.liveMessage(token));
  }

  @Override
  public void onWebSocketError(Throwable cause) {
    LOG.error("Got error", cause);
  }

  @Override
  public void onWebSocketText(String message) {
    // handle message from ZeppelinHub.
    ZeppelinhubClient client = ZeppelinhubClient.getInstance();
    if (client != null) {
      client.handleMsgFromZeppelinHub(message);
    }
  }

  private boolean isSessionOpen() {
    return ((zeppelinHubSession != null) && (zeppelinHubSession.isOpen())) ? true : false;
  }
  
  private void send(String msg) {
    if (isSessionOpen()) {
      zeppelinHubSession.getRemote().sendStringByFuture(msg);
    }
  }
}
