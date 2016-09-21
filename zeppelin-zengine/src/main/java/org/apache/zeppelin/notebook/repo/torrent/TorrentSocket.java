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

package org.apache.zeppelin.notebook.repo.torrent;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


/**
 * Receives torrent operation messages
 */
@WebSocket
public class TorrentSocket {
  private static final Logger LOG = LoggerFactory.getLogger(TorrentSocket.class);
  BittorrentNotebookRepo bttNotebookRepo;
  private Session session;

  public TorrentSocket(BittorrentNotebookRepo bttNotebookRepo) {
    this.bttNotebookRepo = bttNotebookRepo;
  }

  @OnWebSocketConnect
  public void onConnect(Session session) {
    LOG.info("new connection = " + session.getRemoteAddress() + " connected");
    this.session = session;
    bttNotebookRepo.addConnection(this);
  }

  @OnWebSocketMessage
  public void onText(Session session, String message) {
    LOG.info("received message from " + session.getRemoteAddress());
    //System.out.println("Message = [" + message + "]");
    bttNotebookRepo.handleMessage(message);
  }

  @OnWebSocketClose
  public void onClose(Session session, int status, String reason) {
    LOG.info("Closing connection to " + session.getRemoteAddress());
    this.session = null;
    bttNotebookRepo.removeConnection(this);
  }

  @OnWebSocketError
  public void onError(Session session, Throwable error) {
    LOG.info("Received error from " + error.getMessage() + "  " + session.getRemoteAddress());
  }

  public void sendMessage(String text) {

    if (session != null && session.isOpen()) {
      try {
        session.getRemote().sendString(text);
      } catch (IOException e) {
        e.printStackTrace();
        LOG.info("failed to send message to " + session.getRemoteAddress());
      }
    }
  }

}
