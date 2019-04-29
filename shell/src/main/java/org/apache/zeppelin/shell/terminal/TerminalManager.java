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

package org.apache.zeppelin.shell.terminal;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.shell.terminal.service.TerminalService;
import org.apache.zeppelin.shell.terminal.websocket.TerminalSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.apache.zeppelin.shell.ShellInterpreter.TERMINAL_SOCKET_CLOSE;
import static org.apache.zeppelin.shell.ShellInterpreter.TERMINAL_SOCKET_CONNECT;
import static org.apache.zeppelin.shell.ShellInterpreter.TERMINAL_SOCKET_ERROR;
import static org.apache.zeppelin.shell.ShellInterpreter.TERMINAL_SOCKET_STATUS;

// Each notebook supports multiple terminals
public class TerminalManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(TerminalManager.class);

  // TerminalSocket hashCode -> TerminalService
  private HashMap<Integer, TerminalService> terminalSocket2Service;

  // NoteId@ParagraphId -> InterpreterContext
  private HashMap<String, InterpreterContext> noteParagraphId2IntpContext;

  private static TerminalManager instance;

  public static synchronized TerminalManager getInstance(){
    if (instance == null) {
      instance = new TerminalManager();
    }
    return instance;
  }

  private TerminalManager() {
    terminalSocket2Service = new HashMap<>();
    noteParagraphId2IntpContext = new HashMap<>();
  }

  public TerminalService addTerminalService(TerminalSocket terminalSocket) {
    Integer terminalSocketHashcode = terminalSocket.hashCode();
    if (terminalSocket2Service.containsKey(terminalSocketHashcode)) {
      return terminalSocket2Service.get(terminalSocketHashcode);
    } else {
      TerminalService terminalService = new TerminalService();
      terminalSocket2Service.put(terminalSocketHashcode, terminalService);
      return terminalService;
    }
  }

  public void removeTerminalService(TerminalSocket terminalSocket) {
    Integer terminalSocketHashcode = terminalSocket.hashCode();
    if (terminalSocket2Service.containsKey(terminalSocketHashcode)) {
      terminalSocket2Service.remove(terminalSocketHashcode);
    } else {
      LOGGER.error("Cann't find TerminalSocket: " + terminalSocketHashcode);
      LOGGER.error(terminalSocket2Service.toString());
    }
  }

  public void cleanIntpContext(String nodeId) {
    String keyPrex = nodeId + "@";
    for (Map.Entry<String, InterpreterContext> entity : noteParagraphId2IntpContext.entrySet()) {
      String key = entity.getKey();
      if (key.contains(keyPrex)) {
        LOGGER.info("cleanIntpContext : " + key);
        noteParagraphId2IntpContext.remove(key);
      }
    }
  }

  public void runCommand(String command) {
    for (Map.Entry<Integer, TerminalService> entry : terminalSocket2Service.entrySet()) {
      entry.getValue().onCommand(command + "\r");
    }
  }

  private String formatId(String noteId, String paragraphId) {
    return noteId + "@" + paragraphId;
  }

  public void setInterpreterContext(InterpreterContext intpContext) {
    String id = formatId(intpContext.getNoteId(), intpContext.getParagraphId());
    noteParagraphId2IntpContext.put(id, intpContext);
  }

  public void onWebSocketConnect(String noteId, String paragraphId) {
    String id = formatId(noteId, paragraphId);
    InterpreterContext intpContext = noteParagraphId2IntpContext.get(id);
    if (null != intpContext) {
      intpContext.getAngularObjectRegistry().add(TERMINAL_SOCKET_STATUS, TERMINAL_SOCKET_CONNECT,
          intpContext.getNoteId(), intpContext.getParagraphId());
    } else {
      LOGGER.error("Cann't find InterpreterContext from : " + id);
      LOGGER.error(noteParagraphId2IntpContext.toString());
    }
  }

  public void onWebSocketClose(TerminalSocket terminalSocket, String noteId, String paragraphId) {
    String id = formatId(noteId, paragraphId);
    InterpreterContext intpContext = noteParagraphId2IntpContext.get(id);
    if (null != intpContext) {
      intpContext.getAngularObjectRegistry().add(TERMINAL_SOCKET_STATUS, TERMINAL_SOCKET_CLOSE,
          intpContext.getNoteId(), intpContext.getParagraphId());
    } else {
      LOGGER.error("Cann't find InterpreterContext from : " + id);
      LOGGER.error(noteParagraphId2IntpContext.toString());
    }

    removeTerminalService(terminalSocket);
  }

  // Socket error will trigger socket close
  public void onWebSocketError(TerminalSocket terminalSocket, String noteId, String paragraphId) {
    String id = formatId(noteId, paragraphId);
    InterpreterContext intpContext = noteParagraphId2IntpContext.get(id);
    if (null != intpContext) {
      intpContext.getAngularObjectRegistry().add(TERMINAL_SOCKET_STATUS, TERMINAL_SOCKET_ERROR,
          intpContext.getNoteId(), intpContext.getParagraphId());
    } else {
      LOGGER.error("Cann't find InterpreterContext from : " + id);
      LOGGER.error(noteParagraphId2IntpContext.toString());
    }
  }

}
