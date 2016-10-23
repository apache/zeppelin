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

import com.google.common.base.Preconditions;

import java.util.List;

/**
 *
 */
public class InterpreterOption {
  public static final transient String SHARED = "shared";
  public static final transient String SCOPED = "scoped";
  public static final transient String ISOLATED = "isolated";

  boolean remote;
  String host = null;
  int port = -1;

  String perNote;
  String perUser;

  boolean isExistingProcess;
  boolean setPermission;
  List<String> users;

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
    this(false);
  }

  public InterpreterOption(boolean remote) {
    this(remote, SHARED, SHARED);
  }

  public InterpreterOption(boolean remote, String perUser, String perNote) {
    Preconditions.checkNotNull(remote);
    Preconditions.checkNotNull(perUser);
    Preconditions.checkNotNull(perNote);

    this.remote = remote;
    this.perUser = perUser;
    this.perNote = perNote;
  }

  public boolean isRemote() {
    return remote;
  }

  public void setRemote(boolean remote) {
    this.remote = remote;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }


  public boolean perUserShared() {
    return SHARED.equals(perUser);
  }

  public boolean perUserScoped() {
    return SCOPED.equals(perUser);
  }

  public boolean perUserIsolated() {
    return ISOLATED.equals(perUser);
  }

  public boolean perNoteShared() {
    return SHARED.equals(perNote);
  }

  public boolean perNoteScoped() {
    return SCOPED.equals(perNote);
  }

  public boolean perNoteIsolated() {
    return ISOLATED.equals(perNote);
  }

  public boolean isProcess() {
    return perUserIsolated() || perNoteIsolated();
  }

  public boolean isSession() {
    return perUserScoped() || perNoteScoped();
  }

  public void setPerNote(String perNote) {
    this.perNote = perNote;
  }

  public void setPerUser(String perUser) {
    this.perUser = perUser;
  }
}
