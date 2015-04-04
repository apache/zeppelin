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

package com.nflabs.zeppelin.interpreter.remote;

import com.nflabs.zeppelin.display.AngularObject;
import com.nflabs.zeppelin.display.AngularObjectRegistry;
import com.nflabs.zeppelin.display.AngularObjectRegistryListener;
import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterGroup;
import com.nflabs.zeppelin.interpreter.WrappedInterpreter;

/**
 *
 */
public class RemoteAngularObjectRegistry extends AngularObjectRegistry {

  private InterpreterGroup interpreterGroup;

  public RemoteAngularObjectRegistry(String interpreterId,
      AngularObjectRegistryListener listener,
      InterpreterGroup interpreterGroup) {
    super(interpreterId, listener);
    this.interpreterGroup = interpreterGroup;
  }

  private RemoteInterpreterProcess getRemoteInterpreterProcess() {
    if (interpreterGroup.size() == 0) {
      throw new RuntimeException("Can't get remoteInterpreterProcess");
    }
    Interpreter p = interpreterGroup.get(0);
    while (p instanceof WrappedInterpreter) {
      p = ((WrappedInterpreter) p).getInnerInterpreter();
    }

    if (p instanceof RemoteInterpreter) {
      return ((RemoteInterpreter) p).getInterpreterProcess();
    } else {
      throw new RuntimeException("Can't get remoteInterpreterProcess");
    }
  }

  @Override
  protected AngularObject createNewAngularObject(String name, Object o) {
    RemoteInterpreterProcess remoteInterpreterProcess = getRemoteInterpreterProcess();
    if (remoteInterpreterProcess == null) {
      throw new RuntimeException("Remote Interpreter process not found");
    }
    return new RemoteAngularObject(name, o, getInterpreterGroupId(), this,
        getRemoteInterpreterProcess());
  }
}
