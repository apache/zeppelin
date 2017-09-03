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

package org.apache.zeppelin.interpreter.remote;

import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectListener;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;

/**
 * Proxy for AngularObject that exists in remote interpreter process
 */
public class RemoteAngularObject extends AngularObject {

  private transient ManagedInterpreterGroup interpreterGroup;

  RemoteAngularObject(String name, Object o, String noteId, String paragraphId,
                      ManagedInterpreterGroup interpreterGroup,
                      AngularObjectListener listener) {
    super(name, o, noteId, paragraphId, listener);
    this.interpreterGroup = interpreterGroup;
  }

  @Override
  public void set(Object o, boolean emit) {
    set(o,  emit, true);
  }

  public void set(Object o, boolean emitWeb, boolean emitRemoteProcess) {
    super.set(o, emitWeb);

    if (emitRemoteProcess) {
      // send updated value to remote interpreter
      interpreterGroup.getRemoteInterpreterProcess().
          updateRemoteAngularObject(
              getName(), getNoteId(), getParagraphId(), o);
    }
  }
}
