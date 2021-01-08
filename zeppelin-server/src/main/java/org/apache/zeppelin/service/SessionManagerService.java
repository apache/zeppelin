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


package org.apache.zeppelin.service;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.common.SessionInfo;
import org.apache.zeppelin.scheduler.ExecutorFactory;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 *
 * Service class of SessionManager.
 */
public class SessionManagerService {

  private static final Logger LOGGER = LoggerFactory.getLogger(SessionManagerService.class);
  private static final int RETRY = 3;

  private Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
  private InterpreterSettingManager interpreterSettingManager;
  private Notebook notebook;
  private ScheduledExecutorService sessionCheckerExecutor;

  public SessionManagerService(Notebook notebook, InterpreterSettingManager interpreterSettingManager) {
    this.notebook = notebook;
    this.interpreterSettingManager = interpreterSettingManager;
    this.sessionCheckerExecutor = ExecutorFactory.singleton().createOrGetScheduled("Session-Checker-Executor", 1);
    int sessionCheckerInterval = ZeppelinConfiguration.create()
            .getInt(ZeppelinConfiguration.ConfVars.ZEPPELIN_SESSION_CHECK_INTERVAL);
    this.sessionCheckerExecutor.scheduleAtFixedRate(() -> {
      LOGGER.info("Start session check task");
      Iterator<Map.Entry<String, SessionInfo>> iter = sessions.entrySet().iterator();
      while(iter.hasNext()) {
        Map.Entry<String, SessionInfo> entry = iter.next();
        SessionInfo sessionInfo = null;
        try {
          sessionInfo = getSessionInfo(entry.getKey());
          if (sessionInfo != null && sessionInfo.getState().equalsIgnoreCase("Stopped")) {
            LOGGER.info("Session {} has been stopped, remove it and its associated note", entry.getKey());
            try {
              notebook.removeNote(sessionInfo.getNoteId(), AuthenticationInfo.ANONYMOUS);
            } catch (IOException e) {
              LOGGER.warn("Fail to remove session note: " + sessionInfo.getNoteId(), e);
            }
            iter.remove();
          }
        } catch (Exception e) {
          LOGGER.warn("Fail to check session for session: " + entry.getKey(), e);
        }
      }
    }, sessionCheckerInterval, sessionCheckerInterval, TimeUnit.MILLISECONDS);
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
           LOGGER.error("Interrupted", e);
         }
       } else {
         break;
       }
    }

    if (sessionId == null) {
      throw new Exception("Unable to generate session id");
    }

    Note sessionNote = notebook.createNote(buildNotePath(interpreter, sessionId), AuthenticationInfo.ANONYMOUS);
    SessionInfo sessionInfo = new SessionInfo(sessionId, sessionNote.getId(), interpreter);
    sessions.put(sessionId, sessionInfo);
    return sessionInfo;
  }

  private String buildNotePath(String interpreter, String sessionId) {
    return "/_ZSession/" + interpreter + "/" + sessionId;
  }

  /**
   * Remove and stop this session.
   * 1. Stop associated interpreter process (InterpreterGroup)
   * 2. Remove associated session note
   *
   * @param sessionId
   */
  public void stopSession(String sessionId) throws Exception {
    SessionInfo sessionInfo = this.sessions.remove(sessionId);
    if (sessionInfo == null) {
      throw new Exception("No such session: " + sessionId);
    }
    // stop the associated interpreter process
    InterpreterGroup interpreterGroup = this.interpreterSettingManager.getInterpreterGroupById(sessionId);
    if (interpreterGroup == null) {
      LOGGER.info("No interpreterGroup for session: {}", sessionId);
      return;
    }
    ((ManagedInterpreterGroup) interpreterGroup).getInterpreterSetting().closeInterpreters(sessionId);

    // remove associated session note
    notebook.removeNote(sessionInfo.getNoteId(), AuthenticationInfo.ANONYMOUS);
  }

  /**
   * Get the sessionInfo.
   * It method will also update its state if there's an associated interpreter process.
   *
   * @param sessionId
   * @return
   * @throws Exception
   */
  public SessionInfo getSessionInfo(String sessionId) throws Exception {
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
        sessionInfo.setState(SessionState.READY.name());
      } else if (remoteInterpreterProcess != null) {
        sessionInfo.setStartTime(remoteInterpreterProcess.getStartTime());
        sessionInfo.setWeburl(interpreterGroup.getWebUrl());
        if (remoteInterpreterProcess.isRunning()) {
          sessionInfo.setState(SessionState.RUNNING.name());
        } else {
          // if it is running before, but interpreterGroup is not running now, that means the session is stopped.
          // e.g. InterpreterProcess is terminated for whatever unexpected reason.
          if (SessionState.RUNNING.name().equalsIgnoreCase(sessionInfo.getState())) {
            sessionInfo.setState(SessionState.STOPPED.name());
          }
        }
      }
    } else {
      if (SessionState.RUNNING.name().equalsIgnoreCase(sessionInfo.getState())) {
        // if it is running before, but interpreterGroup is null now, that means the session is stopped.
        // e.g. InterpreterProcess is killed if it exceed idle timeout threshold.
        sessionInfo.setState(SessionState.STOPPED.name());
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
  public List<SessionInfo> listSessions() {
    List<SessionInfo> sessionList = new ArrayList<>();
    for (String sessionId : sessions.keySet()) {
      SessionInfo session = null;
      try {
        session = getSessionInfo(sessionId);
      } catch (Exception e) {
        LOGGER.warn("Fail to get sessionInfo for session: " + sessionId, e);
      }
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
  public List<SessionInfo> listSessions(String interpreter) {
    List<SessionInfo> sessionList = new ArrayList<>();
    for (String sessionId : sessions.keySet()) {
      SessionInfo status = null;
      try {
        status = getSessionInfo(sessionId);
      } catch (Exception e) {
        LOGGER.warn("Fail to get sessionInfo for session: " + sessionId, e);
      }
      if (status != null && interpreter.equals(status.getInterpreter())) {
        sessionList.add(status);
      }
    }
    return sessionList;
  }

  enum SessionState {
    READY,
    RUNNING,
    STOPPED
  }
}
