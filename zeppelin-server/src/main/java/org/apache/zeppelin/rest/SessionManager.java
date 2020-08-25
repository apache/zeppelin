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
import org.apache.zeppelin.rest.message.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SessionManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(SessionManager.class);

  private static final int RETRY = 3;
  private Set<String> sessions = new HashSet<>();
  private InterpreterSettingManager interpreterSettingManager;

  public SessionManager(InterpreterSettingManager interpreterSettingManager) {
    this.interpreterSettingManager = interpreterSettingManager;
  }

  public synchronized String newSession(String interpreter) throws Exception {
    int i = 0;
    while (i < RETRY) {
       String sessionId = interpreter + "_" + System.currentTimeMillis();
       if (sessions.contains(sessionId)) {
         try {
           Thread.sleep(1);
         } catch (InterruptedException e) {
           e.printStackTrace();
         }
       } else {
         sessions.add(sessionId);
         return sessionId;
       }
    }

    throw new Exception("Unable to generate session id");
  }

  public void removeSession(String sessionId) {
    this.sessions.remove(sessionId);
  }

  public Session getSession(String sessionId) throws Exception {
    InterpreterGroup interpreterGroup = this.interpreterSettingManager.getInterpreterGroupById(sessionId);
    if (interpreterGroup != null) {
      RemoteInterpreterProcess remoteInterpreterProcess =
              ((ManagedInterpreterGroup) interpreterGroup).getRemoteInterpreterProcess();
      String state = "";
      String startTime = "";
      if (remoteInterpreterProcess == null) {
        state = "Ready";
      } else if (remoteInterpreterProcess != null) {
        startTime = remoteInterpreterProcess.getStartTime();
        if (remoteInterpreterProcess.isRunning()) {
          state = "Running";
        } else {
          state = "Stopped";
        }
      }
      return new Session(sessionId,
              ((ManagedInterpreterGroup) interpreterGroup).getInterpreterSetting().getName(),
              state, interpreterGroup.getWebUrl(), startTime);
    }
    LOGGER.warn("No such session: " + sessionId);
    return null;
  }

  public List<Session> getAllSessionStatus() throws Exception {
    List<Session> sessionList = new ArrayList<>();
    for (String sessionId : sessions) {
      Session status = getSession(sessionId);
      if (status != null) {
        sessionList.add(status);
      }
    }
    return sessionList;
  }

  public List<Session> getAllSessionStatus(String interpreterGroup) throws Exception {
    List<Session> sessionList = new ArrayList<>();
    for (String sessionId : sessions) {
      Session status = getSession(sessionId);
      if (status != null && interpreterGroup.equals(status.getInterpreter())) {
        sessionList.add(status);
      }
    }
    return sessionList;
  }
}
