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

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class InterpreterOption {
  public static final transient String SHARED = "shared";
  public static final transient String SCOPED = "scoped";
  public static final transient String ISOLATED = "isolated";

  // always set it as true, keep this field just for backward compatibility
  boolean remote = true;
  String host = null;
  int port = -1;

  String perNote;
  String perUser;

  boolean isExistingProcess;
  boolean setPermission;
  List<String> owners;
  boolean isUserImpersonate;

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

  public List<String> getOwners() {
    return owners;
  }

  public boolean isUserImpersonate() {
    return isUserImpersonate;
  }

  public void setUserImpersonate(boolean userImpersonate) {
    isUserImpersonate = userImpersonate;
  }

  public InterpreterOption() {
  }

  public InterpreterOption(String perUser, String perNote) {
    if (perUser == null) {
      throw new NullPointerException("perUser can not be null.");
    }
    if (perNote == null) {
      throw new NullPointerException("perNote can not be null.");
    }

    this.perUser = perUser;
    this.perNote = perNote;
  }

  public static InterpreterOption fromInterpreterOption(InterpreterOption other) {
    InterpreterOption option = new InterpreterOption();
    option.remote = other.remote;
    option.host = other.host;
    option.port = other.port;
    option.perNote = other.perNote;
    option.perUser = other.perUser;
    option.isExistingProcess = other.isExistingProcess;
    option.setPermission = other.setPermission;
    option.owners = (null == other.owners) ?
        new ArrayList<String>() : new ArrayList<>(other.owners);

    return option;
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
