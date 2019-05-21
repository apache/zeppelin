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

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.util.Map;

@ClientEndpoint
@ServerEndpoint(value = "/")
public class TerminalSocket {
  private static final Logger LOGGER = LoggerFactory.getLogger(TerminalSocket.class);
  private TerminalService terminalService;
  private TerminalManager terminalManager = TerminalManager.getInstance();

  private String noteId, paragraphId;

  public TerminalSocket() {
    terminalService = terminalManager.addTerminalService(this);
  }

  @OnOpen
  public void onWebSocketConnect(Session sess) {
    LOGGER.info("Socket Connected: " + sess);
    terminalService.onWebSocketConnect(sess);
  }

  @OnMessage
  public void onWebSocketText(String message) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Received TEXT message: " + message);
    }

    Map<String, String> messageMap = getMessageMap(message);

    if (messageMap.containsKey("type")) {
      String type = messageMap.get("type");
      switch (type) {
        case "TERMINAL_INIT":
          terminalService.onTerminalInit();
          this.noteId = messageMap.get("noteId");
          this.paragraphId = messageMap.get("paragraphId");
          terminalManager.onWebSocketConnect(noteId, paragraphId);
          break;
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
          LOGGER.error("Unrecodnized action: " + message);
      }
    }
  }

  @OnClose
  public void onWebSocketClose(CloseReason reason) {
    LOGGER.info("Socket Closed: " + reason);

    terminalManager.onWebSocketClose(this, noteId, paragraphId);
  }

  @OnError
  public void onWebSocketError(Throwable cause) {
    LOGGER.warn(cause.getMessage(), cause);

    terminalManager.onWebSocketError(this, noteId, paragraphId);
  }

  private Map<String, String> getMessageMap(String message) {
    Gson gson = new Gson();
    Map<String, String> map = gson.fromJson(message,
        new TypeToken<Map<String, String>>(){}.getType());
    return map;
  }
}
