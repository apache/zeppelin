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

package org.apache.zeppelin.interpreter;

import java.util.List;

/**
 *
 */
public class InterpreterOption {
  public final transient String SHARED = "shared";
  public final transient String SCOPED = "scoped";
  public final transient String ISOLATED = "isolated";

  boolean remote;
  String host = null;
  int port = -1;

  String perNote;
  String perUser;

  boolean session;
  boolean process;
  
  boolean isExistingProcess;
  boolean setPermission;
  List<String> users;

  public boolean isGlobally() {
    if (perNote != null && perNote.equals(SHARED)
        && perUser != null && perUser.equals(SHARED)) {
      return true;
    }
    return false;
  }

  public boolean isPerNote() {
    if (isGlobally() == true) {
      return false;
    }

    if (perNote != null && !perNote.equals("")) {
      return true;
    }

    return false;
  }

  public void setPerNote(String perNote) {
    this.perNote = perNote;
  }

  public boolean isPerUser() {
    if (isGlobally() == true) {
      return false;
    }

    if (isPerNote() == true) {
      return false;
    }

    if (perUser != null && !perUser.equals("")) {
      return true;
    }
    return false;
  }

  public void setPerUser(String perUser) {
    this.perUser = perUser;
  }

  public boolean isExistingProcess() {
    return isExistingProcess;
  }

  public void setExistingProcess(boolean isExistingProcess) {
    this.isExistingProcess = isExistingProcess;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public boolean permissionIsSet() {
    return setPermission;
  }

  public void setUserPermission(boolean setPermission) {
    this.setPermission = setPermission;
  }

  public List<String> getUsers() {
    return users;
  }

  public InterpreterOption() {
    this.perNote = null;
    this.perUser = null;
    remote = false;
  }

  public InterpreterOption(boolean remote) {
    this.perNote = null;
    this.perUser = null;
    this.remote = remote;
  }

  public boolean isRemote() {
    return remote;
  }

  public void setRemote(boolean remote) {
    this.remote = remote;
  }

  public boolean isSession() {
    return session;
  }

  public void setSession(boolean session) {
    this.session = session;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public boolean isProcess() {
    return process;
  }

  public void setProcess(boolean process) {
    this.process = process;
  }
}
