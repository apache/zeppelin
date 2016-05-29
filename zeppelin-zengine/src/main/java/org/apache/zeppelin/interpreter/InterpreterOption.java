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

/**
 *
 */
public class InterpreterOption {
  boolean remote;
  boolean perNoteSession;
  boolean perNoteProcess;

  public InterpreterOption() {
    remote = false;
  }

  public InterpreterOption(boolean remote) {
    this.remote = remote;
  }

  public boolean isRemote() {
    return remote;
  }

  public void setRemote(boolean remote) {
    this.remote = remote;
  }

  public boolean isPerNoteSession() {
    return perNoteSession;
  }

  public void setPerNoteSession(boolean perNoteSession) {
    this.perNoteSession = perNoteSession;
  }

  public boolean isPerNoteProcess() {
    return perNoteProcess;
  }

  public void setPerNoteProcess(boolean perNoteProcess) {
    this.perNoteProcess = perNoteProcess;
  }
}
