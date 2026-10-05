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
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;

/**
 * Notebook websocket.
 */
public class NotebookSocket {
  private static final Logger LOGGER = LoggerFactory.getLogger(NotebookSocket.class);

  // WebSocket protocol ping frames (RFC 6455 5.5.2) carry no meaningful payload here, so a
  // single empty, effectively immutable (zero remaining bytes) buffer can be reused for every
  // send instead of allocating one per heartbeat tick.
  private static final ByteBuffer PING_PAYLOAD = ByteBuffer.allocate(0);

  private Session session;
  private Map<String, Object> headers;
  private String user;

  // Liveness tracking (ZEPPELIN-6694). Written from the heartbeat thread (sendPing) and from
  // the websocket container thread (onPong), hence atomic/volatile.
  private final AtomicInteger unansweredPings = new AtomicInteger();
  private volatile long lastPongTimestamp;
  // True while onMessage is running for this session. Jetty reads the next frame (pongs
  // included) only after onMessage returns, so pongs pile up unread during a long operation.
  private volatile boolean handlingMessage;

  public NotebookSocket(Session session, Map<String, Object> headers) {
    this.session = session;
    this.headers = headers;
    this.user = StringUtils.EMPTY;
    this.lastPongTimestamp = System.currentTimeMillis();
    LOGGER.debug("NotebookSocket created for session: {}", session.getId());
  }

  public String getSessionId() {
    return session.getId();
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

  /**
   * Sends a WebSocket protocol ping frame to keep this connection alive. The peer's WebSocket
   * implementation answers automatically with a pong (RFC 6455 5.5.2), and writing to the
   * session resets Jetty's idle timeout as well as any intermediate proxy's idle timer, so no
   * application-level handling is required on the client. Exceptions are swallowed and logged
   * so a single dead session cannot break the caller's heartbeat loop over all sessions.
   * Every call counts as one outstanding ping until {@link #onPong()} is called, including
   * calls whose write failed, since a failed write is itself a sign the peer is gone.
   */
  public void sendPing() {
    unansweredPings.incrementAndGet();
    try {
      session.getBasicRemote().sendPing(PING_PAYLOAD);
    } catch (IOException | IllegalArgumentException | IllegalStateException e) {
      LOGGER.warn("Failed to send heartbeat ping to session {}: {}", session.getId(), e.toString());
    }
  }

  /**
   * Records a pong frame from the peer. Any pong proves the connection is alive, so the
   * outstanding-ping counter is reset rather than decremented.
   */
  public void onPong() {
    lastPongTimestamp = System.currentTimeMillis();
    unansweredPings.set(0);
  }

  public int getPingsSinceLastPong() {
    return unansweredPings.get();
  }

  public long getLastPongTimestamp() {
    return lastPongTimestamp;
  }

  public boolean isHandlingMessage() {
    return handlingMessage;
  }

  public void setHandlingMessage(boolean handlingMessage) {
    this.handlingMessage = handlingMessage;
  }

  /**
   * Closes the underlying session. Exceptions are swallowed and logged because this is used to
   * reap connections that are already presumed dead.
   */
  public void close(CloseReason closeReason) {
    try {
      session.close(closeReason);
    } catch (IOException | IllegalStateException e) {
      LOGGER.debug("Failed to close session {}: {}", session.getId(), e.toString());
    }
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
