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


package org.apache.zeppelin.rest;

import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.rest.message.SessionInfo;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 * Backend manager of ZSessions
 */
public class SessionManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(SessionManager.class);

  private static final int RETRY = 3;
  private Map<String, SessionInfo> sessions = new HashMap<>();
  private InterpreterSettingManager interpreterSettingManager;
  private Notebook notebook;

  public SessionManager(Notebook notebook, InterpreterSettingManager interpreterSettingManager) {
    this.notebook = notebook;
    this.interpreterSettingManager = interpreterSettingManager;
  }

  /**
   * Create a new session, including allocate new session Id and create dedicated note for this session.
   *
   * @param interpreter
   * @return
   * @throws Exception
   */
  public synchronized SessionInfo createSession(String interpreter) throws Exception {
    String sessionId = null;
    int i = 0;
    while (i < RETRY) {
      sessionId = interpreter + "_" + System.currentTimeMillis();
       if (sessions.containsKey(sessionId)) {
         try {
           Thread.sleep(1);
         } catch (InterruptedException e) {
           e.printStackTrace();
         }
       } else {
         break;
       }
    }

    if (sessionId == null) {
      throw new Exception("Unable to generate session id");
    }

    Note sessionNote = notebook.createNote("/_ZSession/" + sessionId, AuthenticationInfo.ANONYMOUS);
    SessionInfo sessionInfo = new SessionInfo(sessionId, sessionNote.getId(), interpreter);
    sessions.put(sessionId, sessionInfo);
    return sessionInfo;
  }

  /**
   * Remove and stop this session.
   *
   * @param sessionId
   */
  public void removeSession(String sessionId) {
    this.sessions.remove(sessionId);
    InterpreterGroup interpreterGroup = this.interpreterSettingManager.getInterpreterGroupById(sessionId);
    if (interpreterGroup == null) {
      LOGGER.info("No interpreterGroup for session: " + sessionId);
      return;
    }
    ((ManagedInterpreterGroup) interpreterGroup).getInterpreterSetting().closeInterpreters(sessionId);
  }

  /**
   * Get the sessionInfo.
   * It method will also update its state if these's associated interpreter process.
   *
   * @param sessionId
   * @return
   * @throws Exception
   */
  public SessionInfo getSession(String sessionId) throws Exception {
    SessionInfo sessionInfo = sessions.get(sessionId);
    if (sessionInfo == null) {
      LOGGER.warn("No such session: " + sessionId);
      return null;
    }
    InterpreterGroup interpreterGroup = this.interpreterSettingManager.getInterpreterGroupById(sessionId);
    if (interpreterGroup != null) {
      RemoteInterpreterProcess remoteInterpreterProcess =
              ((ManagedInterpreterGroup) interpreterGroup).getRemoteInterpreterProcess();
      if (remoteInterpreterProcess == null) {
        sessionInfo.setState("Ready");
      } else if (remoteInterpreterProcess != null) {
        sessionInfo.setStartTime(remoteInterpreterProcess.getStartTime());
        sessionInfo.setWeburl(interpreterGroup.getWebUrl());
        if (remoteInterpreterProcess.isRunning()) {
          sessionInfo.setState("Running");
        } else {
          sessionInfo.setState("Stopped");
        }
      }
    }

    return sessionInfo;
  }

  /**
   * Get all sessions.
   *
   * @return
   * @throws Exception
   */
  public List<SessionInfo> getAllSessions() throws Exception {
    List<SessionInfo> sessionList = new ArrayList<>();
    for (String sessionId : sessions.keySet()) {
      SessionInfo session = getSession(sessionId);
      if (session != null) {
        sessionList.add(session);
      }
    }
    return sessionList;
  }

  /**
   * Get all sessions for provided interpreter.
   *
   * @param interpreter
   * @return
   * @throws Exception
   */
  public List<SessionInfo> getAllSessions(String interpreter) throws Exception {
    List<SessionInfo> sessionList = new ArrayList<>();
    for (String sessionId : sessions.keySet()) {
      SessionInfo status = getSession(sessionId);
      if (status != null && interpreter.equals(status.getInterpreter())) {
        sessionList.add(status);
      }
    }
    return sessionList;
  }
}
