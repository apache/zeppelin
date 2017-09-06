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

import com.google.gson.Gson;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Proxy for AngularObjectRegistry that exists in remote interpreter process
 */
public class RemoteAngularObjectRegistry extends AngularObjectRegistry {
  Logger logger = LoggerFactory.getLogger(RemoteAngularObjectRegistry.class);
  private ManagedInterpreterGroup interpreterGroup;

  public RemoteAngularObjectRegistry(String interpreterId,
                                     AngularObjectRegistryListener listener,
                                     ManagedInterpreterGroup interpreterGroup) {
    super(interpreterId, listener);
    this.interpreterGroup = interpreterGroup;
  }

  private RemoteInterpreterProcess getRemoteInterpreterProcess() {
    return interpreterGroup.getRemoteInterpreterProcess();
  }

  /**
   * When ZeppelinServer side code want to add angularObject to the registry,
   * this method should be used instead of add()
   * @param name
   * @param o
   * @param noteId
   * @return
   */
  public AngularObject addAndNotifyRemoteProcess(final String name,
                                                 final Object o,
                                                 final String noteId,
                                                 final String paragraphId) {

    RemoteInterpreterProcess remoteInterpreterProcess = getRemoteInterpreterProcess();
    if (!remoteInterpreterProcess.isRunning()) {
      return super.add(name, o, noteId, paragraphId, true);
    }

    remoteInterpreterProcess.callRemoteFunction(
        new RemoteInterpreterProcess.RemoteFunction<Void>() {
          @Override
          public Void call(Client client) throws Exception {
            Gson gson = new Gson();
            client.angularObjectAdd(name, noteId, paragraphId, gson.toJson(o));
            return null;
          }
        }
    );

    return super.add(name, o, noteId, paragraphId, true);

  }

  /**
   * When ZeppelinServer side code want to remove angularObject from the registry,
   * this method should be used instead of remove()
   * @param name
   * @param noteId
   * @param paragraphId
   * @return
   */
  public AngularObject removeAndNotifyRemoteProcess(final String name,
                                                    final String noteId,
                                                    final String paragraphId) {
    RemoteInterpreterProcess remoteInterpreterProcess = getRemoteInterpreterProcess();
    if (remoteInterpreterProcess == null || !remoteInterpreterProcess.isRunning()) {
      return super.remove(name, noteId, paragraphId);
    }
    remoteInterpreterProcess.callRemoteFunction(
      new RemoteInterpreterProcess.RemoteFunction<Void>() {
        @Override
        public Void call(Client client) throws Exception {
          client.angularObjectRemove(name, noteId, paragraphId);
          return null;
        }
      }
    );

    return super.remove(name, noteId, paragraphId);
  }
  
  public void removeAllAndNotifyRemoteProcess(String noteId, String paragraphId) {
    List<AngularObject> all = getAll(noteId, paragraphId);
    for (AngularObject ao : all) {
      removeAndNotifyRemoteProcess(ao.getName(), noteId, paragraphId);
    }
  }

  @Override
  protected AngularObject createNewAngularObject(String name, Object o, String noteId, String
          paragraphId) {
    return new RemoteAngularObject(name, o, noteId, paragraphId, interpreterGroup,
        getAngularObjectListener());
  }
}
