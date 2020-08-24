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

package org.apache.zeppelin.client.websocket;

import com.google.gson.Gson;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


/**
 * Represent websocket client.
 *
 */
@WebSocket(maxTextMessageSize = 10 * 1000 * 1024 )
public class ZeppelinWebSocketClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(ZeppelinWebSocketClient.class);
  private static final Gson GSON = new Gson();

  private CountDownLatch connectLatch = new CountDownLatch(1);
  private CountDownLatch closeLatch = new CountDownLatch(1);

  private Session session;
  private MessageHandler messageHandler;
  private WebSocketClient wsClient;

  public ZeppelinWebSocketClient(MessageHandler messageHandler) {
    this.messageHandler = messageHandler;
  }

  public void connect(String url) throws Exception {
    this.wsClient = new WebSocketClient();
    wsClient.start();
    URI echoUri = new URI(url);
    ClientUpgradeRequest request = new ClientUpgradeRequest();
    request.setHeader("Origin", "*");
    wsClient.connect(this, echoUri, request);
    connectLatch.await();
    LOGGER.info("WebSocket connect established");
  }

  public void addStatementMessageHandler(String statementId,
                                         StatementMessageHandler statementMessageHandler) throws Exception {
    if (messageHandler instanceof CompositeMessageHandler) {
      ((CompositeMessageHandler) messageHandler).addStatementMessageHandler(statementId, statementMessageHandler);
    } else {
      throw new Exception("StatementMessageHandler is only supported by: "
              + CompositeMessageHandler.class.getSimpleName());
    }
  }

  public boolean awaitClose(int duration, TimeUnit unit) throws InterruptedException {
    return this.closeLatch.await(duration, unit);
  }

  @OnWebSocketClose
  public void onClose(int statusCode, String reason) {
    LOGGER.info("Connection closed, statusCode: {} - reason: {}", statusCode, reason);
    this.session = null;
    this.closeLatch.countDown();
  }

  @OnWebSocketConnect
  public void onConnect(Session session) {
    LOGGER.info("Got connect: {}", session.getRemote());
    this.session = session;
    connectLatch.countDown();
  }

  @OnWebSocketMessage
  public void onText(Session session, String message) throws IOException {
    messageHandler.onMessage(message);
  }

  @OnWebSocketError
  public void onError(Throwable cause) {
    LOGGER.info("WebSocket Error: " + cause.getMessage());
    cause.printStackTrace(System.out);
  }

  public void send(Message message) throws IOException {
    session.getRemote().sendString(GSON.toJson(message));
  }

  public CountDownLatch getConnectLatch() {
    return connectLatch;
  }

  public void stop() throws Exception {
    if (this.wsClient != null) {
      this.wsClient.stop();
    }
  }

  public static void main(String[] args) throws Exception {
    String dest = "ws://localhost:18086/ws";
    WebSocketClient client = new WebSocketClient();
    try {
      ZeppelinWebSocketClient socket = new ZeppelinWebSocketClient(new SimpleMessageHandler());
      client.start();
      URI echoUri = new URI(dest);
      ClientUpgradeRequest request = new ClientUpgradeRequest();
      client.connect(socket, echoUri, request);
      socket.getConnectLatch().await();

      Message msg = new Message(Message.OP.LIST_NOTES);
      socket.send(msg);

      // wait for closed socket connection.
      socket.awaitClose(5, TimeUnit.SECONDS);
    } catch (Throwable t) {
      t.printStackTrace();
    } finally {
      try {
        client.stop();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    Thread.sleep(10000 * 100);
  }

}
