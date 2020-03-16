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

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.ZeppelinClient;
import org.apache.zeppelin.notebook.socket.Message;
import org.apache.zeppelin.notebook.socket.Message.OP;
import org.apache.zeppelin.notebook.socket.WatcherMessage;
import org.apache.zeppelin.ticket.TicketContainer;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zeppelin Watcher that will forward user note to ZeppelinHub.
 *
 */
public class WatcherWebsocket implements WebSocketListener {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinWebsocket.class);
  private static final String watcherPrincipal = "watcher";
  public Session connection;
  
  public static WatcherWebsocket createInstace() {
    return new WatcherWebsocket();
  }
  
  @Override
  public void onWebSocketBinary(byte[] payload, int offset, int len) {
  }

  @Override
  public void onWebSocketClose(int code, String reason) {
    LOG.info("WatcherWebsocket connection closed with code: {}, message: {}", code, reason);
  }

  @Override
  public void onWebSocketConnect(Session session) {
    LOG.info("WatcherWebsocket connection opened");
    this.connection = session;
    Message watcherMsg = new Message(OP.WATCHER);
    watcherMsg.principal = watcherPrincipal;
    watcherMsg.ticket = TicketContainer.instance.getTicket(watcherPrincipal);
    session.getRemote().sendStringByFuture(watcherMsg.toJson());
  }

  @Override
  public void onWebSocketError(Throwable cause) {
    LOG.warn("WatcherWebsocket socket connection error ", cause);
  }

  @Override
  public void onWebSocketText(String message) {
    WatcherMessage watcherMsg = WatcherMessage.fromJson(message);
    if (StringUtils.isBlank(watcherMsg.noteId)) {
      return;
    }
    try {
      ZeppelinClient zeppelinClient = ZeppelinClient.getInstance();
      if (zeppelinClient != null) {
        zeppelinClient.handleMsgFromZeppelin(watcherMsg.message, watcherMsg.noteId);
      }
    } catch (Exception e) {
      LOG.error("Failed to send message to ZeppelinHub: ", e);
    }
  }

}
