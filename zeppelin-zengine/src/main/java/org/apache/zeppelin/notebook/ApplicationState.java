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

import org.apache.zeppelin.helium.HeliumPackage;
import org.apache.zeppelin.interpreter.InterpreterGroup;

/**
 * Current state of application
 */
public class ApplicationState {

  /**
   * Status of Application
   */
  public static enum Status {
    LOADING,
    LOADED,
    UNLOADING,
    UNLOADED,
    ERROR
  };

  Status status = Status.UNLOADED;

  String id;   // unique id for this instance. Similar to note id or paragraph id
  HeliumPackage pkg;
  String output;

  public ApplicationState(String id, HeliumPackage pkg) {
    this.id = id;
    this.pkg = pkg;
  }

  /**
   * After ApplicationState is restored from NotebookRepo,
   * such as after Zeppelin daemon starts or Notebook import,
   * Application status need to be reset.
   */
  public void resetStatus() {
    if (status != Status.ERROR) {
      status = Status.UNLOADED;
    }
  }


  @Override
  public boolean equals(Object o) {
    String compareName;
    if (o instanceof ApplicationState) {
      return pkg.equals(((ApplicationState) o).getHeliumPackage());
    } else if (o instanceof HeliumPackage) {
      return pkg.equals((HeliumPackage) o);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return pkg.hashCode();
  }

  public String getId() {
    return id;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public Status getStatus() {
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

  public HeliumPackage getHeliumPackage() {
    return pkg;
  }
}
