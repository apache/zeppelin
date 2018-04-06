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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Set;
import java.util.HashSet;

/**
 * Interpreter options object
 */
public class InterpreterOption {
  public static final transient String SHARED = "shared";
  public static final transient String SCOPED = "scoped";
  public static final transient String ISOLATED = "isolated";

  // always set it as true, keep this field just for backward compatibility
  boolean remote = true;
  private String host = null;
  private int port = -1;

  String perNote;
  String perUser;

  boolean isExistingProcess;
  private boolean setPermission;
  private Set<String> owners;
  private Set<String> readers;
  private boolean isUserImpersonate;
  private boolean disallowCustomInterpreter;

  public InterpreterOption() { }

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

  public boolean isExistingProcess() {
    return isExistingProcess;
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

  public Set<String> getOwners() {
    return owners;
  }

  public Set<String> getReaders() {
    return readers;
  }

  public boolean isUserImpersonate() {
    return isUserImpersonate;
  }

  public void setUserImpersonate(boolean userImpersonate) {
    isUserImpersonate = userImpersonate;
  }

  public boolean getDisallowCustomInterpreter() {
    return disallowCustomInterpreter;
  }

  public void setDisallowCustomInterpreter(boolean customInterpreter) {
    disallowCustomInterpreter = customInterpreter;
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
        new HashSet<String>() : new HashSet<>(other.owners);
    option.readers = (null == other.readers) ?
            new HashSet<String>() : new HashSet<>(other.readers);
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

  public void convertToOwners(JsonObject jsonObject) {
    if (jsonObject != null) {
      JsonObject option = jsonObject.getAsJsonObject("option");
      if (option != null) {
        JsonArray users = option.getAsJsonArray("users");
        if (users != null) {
          if (this.getOwners() == null) {
            this.owners = new HashSet<>();
          }
          for (JsonElement user : users) {
            this.getOwners().add(user.getAsString());
          }
        }
      }
    }
  }

  public void convertToReaders(JsonObject jsonObject) {
    if (jsonObject != null) {
      JsonObject option = jsonObject.getAsJsonObject("option");
      if (option != null) {
        JsonArray users = option.getAsJsonArray("users");
        if (users != null) {
          if (this.getReaders() == null) {
            this.readers = new HashSet<>();
          }
          for (JsonElement user : users) {
            this.getReaders().add(user.getAsString());
          }
        }
      }
    }
  }
}
