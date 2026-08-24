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

package org.apache.zeppelin.shell.terminal.websocket;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.zeppelin.shell.terminal.TerminalManager;
import org.apache.zeppelin.shell.terminal.service.TerminalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

@ClientEndpoint
@ServerEndpoint(value = "/")
public class TerminalSocket {
  private static final Logger LOGGER = LoggerFactory.getLogger(TerminalSocket.class);

  // Key under which TerminalThread publishes the per-server auth token
  public static final String AUTH_TOKEN_PROPERTY = "zeppelin.terminal.auth.token";

  private TerminalService terminalService;
  private TerminalManager terminalManager = TerminalManager.getInstance();

  private String noteId;
  private String paragraphId;
  private boolean authorized = false;

  public TerminalSocket() {
    terminalService = terminalManager.addTerminalService(this);
  }

  @OnOpen
  public void onWebSocketConnect(Session sess, EndpointConfig config) {
    // This endpoint hands out an OS shell: require the per-server secret that
    // only reaches clients through the paragraph result (note ACLs). The
    // Origin check is not authentication - any non-browser client forges it.
    String expectedToken = (String) config.getUserProperties().get(AUTH_TOKEN_PROPERTY);
    if (!isTokenValid(expectedToken, sess.getRequestParameterMap().get("token"))) {
      LOGGER.warn("Rejecting terminal websocket connection without a valid auth token: {}", sess);
      try {
        sess.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY,
            "Missing or invalid terminal auth token"));
      } catch (IOException e) {
        LOGGER.error(e.getMessage(), e);
      }
      return;
    }
    authorized = true;
    LOGGER.info("Socket Connected: {}", sess);
    terminalService.onWebSocketConnect(sess);
  }

  @OnMessage
  public void onWebSocketText(String message) {
    if (!authorized) {
      LOGGER.warn("Ignoring message from unauthorized terminal websocket connection");
      return;
    }
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Received TEXT message: {}", message);
    }

    Map<String, String> messageMap = getMessageMap(message);

    if (messageMap.containsKey("type")) {
      String type = messageMap.get("type");
      switch (type) {
        case "TERMINAL_READY":
          terminalService.onTerminalReady();
          this.noteId = messageMap.get("noteId");
          this.paragraphId = messageMap.get("paragraphId");
          terminalManager.onWebSocketConnect(noteId, paragraphId);
          break;
        case "TERMINAL_COMMAND":
          terminalService.onCommand(messageMap.get("command"));
          break;
        case "TERMINAL_RESIZE":
          terminalService.onTerminalResize(messageMap.get("columns"), messageMap.get("rows"));
          break;
        default:
          LOGGER.error("Unrecognized action: {}", message);
      }
    }
  }

  @OnClose
  public void onWebSocketClose(CloseReason reason) {
    LOGGER.info("Socket Closed: {}", reason);

    terminalManager.onWebSocketClose(this, noteId, paragraphId);
  }

  @OnError
  public void onWebSocketError(Throwable cause) {
    LOGGER.warn(cause.getMessage(), cause);

    terminalManager.onWebSocketError(this, noteId, paragraphId);
  }

  private static boolean isTokenValid(String expectedToken, List<String> suppliedTokens) {
    if (expectedToken == null || expectedToken.isEmpty()
        || suppliedTokens == null || suppliedTokens.isEmpty()) {
      return false;
    }
    return MessageDigest.isEqual(
        expectedToken.getBytes(StandardCharsets.UTF_8),
        suppliedTokens.get(0).getBytes(StandardCharsets.UTF_8));
  }

  private Map<String, String> getMessageMap(String message) {
    Gson gson = new Gson();
    Map<String, String> map = gson.fromJson(message,
        new TypeToken<Map<String, String>>(){}.getType());
    return map;
  }
}
