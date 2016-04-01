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
package org.apache.zeppelin.notebook;

import org.apache.zeppelin.helium.Application;
import org.apache.zeppelin.helium.HeliumPackage;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;

/**
 * Running ApplicationState
 */
public class ApplicationState {

  /**
   * Status of Application
   */
  public static enum ApplicationStatus {
    LOADING,
    LOADED,
    UNLOADING,
    UNLOADED
  };


  String id; // unique id for this instance similar to note id or paragraph id
  String name; // name of app
  ApplicationStatus status;
  String output;

  public ApplicationState(String id, String name) {
    this.id = id;
    this.name = name;
    status = ApplicationStatus.UNLOADED;
  }

  @Override
  public boolean equals(Object o) {
    String compareName;
    if (o instanceof ApplicationState) {
      compareName = ((ApplicationState) o).name;
    } else if (o instanceof String) {
      compareName = (String) o;
    } else {
      return false;
    }

    return name.equals(compareName);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  public String getId() {
    return id;
  }

  public void setStatus(ApplicationStatus status) {
    this.status = status;
  }

  public ApplicationStatus getStatus() {
    return status;
  }

  public String getOutput() {
    return output;
  }

  public void setOutput(String output) {
    this.output = output;
  }

  public synchronized void appendOutput(String output) {
    if (this.output == null) {
      this.output = output;
    } else {
      this.output += output;
    }
  }

  public String getName() {
    return name;
  }
}
