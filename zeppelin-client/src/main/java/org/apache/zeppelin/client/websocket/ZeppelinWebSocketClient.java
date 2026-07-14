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
import org.apache.zeppelin.common.Message;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


/**
 * Represent websocket client.
 *
 */
@WebSocket(maxTextMessageSize = 10 * 1000 * 1024 )
public class ZeppelinWebSocketClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(ZeppelinWebSocketClient.class);
  private static final Gson GSON = new Gson();
  private static final long DEFAULT_CONNECT_TIMEOUT_MS = 30_000;

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
    CompletableFuture<Session> future = wsClient.connect(this, echoUri, request);
    try {
      future.get(DEFAULT_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      stopQuietly();
      throw new IOException("Timeout(" + DEFAULT_CONNECT_TIMEOUT_MS
              + "ms) establishing websocket connection to " + url, e);
    } catch (ExecutionException e) {
      stopQuietly();
      throw new IOException("Failed to establish websocket connection to " + url,
              e.getCause());
    }
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
  }

  @OnWebSocketMessage
  public void onText(Session session, String message) throws IOException {
    messageHandler.onMessage(message);
  }

  @OnWebSocketError
  public void onError(Throwable cause) {
    LOGGER.error("WebSocket error", cause);
  }

  public void send(Message message) throws IOException {
    session.getRemote().sendString(GSON.toJson(message));
  }

  public void stop() throws Exception {
    if (this.wsClient != null) {
      this.wsClient.stop();
    }
  }

  private void stopQuietly() {
    try {
      stop();
    } catch (Exception e) {
      LOGGER.warn("Failed to stop websocket client after connection failure", e);
    }
  }

}
