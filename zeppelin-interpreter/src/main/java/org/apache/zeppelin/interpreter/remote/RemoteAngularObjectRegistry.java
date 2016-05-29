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

import java.util.List;

import org.apache.thrift.TException;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.WrappedInterpreter;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * Proxy for AngularObjectRegistry that exists in remote interpreter process
 */
public class RemoteAngularObjectRegistry extends AngularObjectRegistry {
  Logger logger = LoggerFactory.getLogger(RemoteAngularObjectRegistry.class);
  private InterpreterGroup interpreterGroup;

  public RemoteAngularObjectRegistry(String interpreterId,
      AngularObjectRegistryListener listener,
      InterpreterGroup interpreterGroup) {
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
  public AngularObject addAndNotifyRemoteProcess(String name, Object o, String noteId, String
          paragraphId) {
    Gson gson = new Gson();
    RemoteInterpreterProcess remoteInterpreterProcess = getRemoteInterpreterProcess();
    if (!remoteInterpreterProcess.isRunning()) {
      return super.add(name, o, noteId, paragraphId, true);
    }

    Client client = null;
    boolean broken = false;
    try {
      client = remoteInterpreterProcess.getClient();
      client.angularObjectAdd(name, noteId, paragraphId, gson.toJson(o));
      return super.add(name, o, noteId, paragraphId, true);
    } catch (TException e) {
      broken = true;
      logger.error("Error", e);
    } catch (Exception e) {
      logger.error("Error", e);
    } finally {
      if (client != null) {
        remoteInterpreterProcess.releaseClient(client, broken);
      }
    }
    return null;
  }

  /**
   * When ZeppelinServer side code want to remove angularObject from the registry,
   * this method should be used instead of remove()
   * @param name
   * @param noteId
   * @param paragraphId
   * @return
   */
  public AngularObject removeAndNotifyRemoteProcess(String name, String noteId, String
          paragraphId) {
    RemoteInterpreterProcess remoteInterpreterProcess = getRemoteInterpreterProcess();
    if (!remoteInterpreterProcess.isRunning()) {
      return super.remove(name, noteId, paragraphId);
    }

    Client client = null;
    boolean broken = false;
    try {
      client = remoteInterpreterProcess.getClient();
      client.angularObjectRemove(name, noteId, paragraphId);
      return super.remove(name, noteId, paragraphId);
    } catch (TException e) {
      broken = true;
      logger.error("Error", e);
    } catch (Exception e) {
      logger.error("Error", e);
    } finally {
      if (client != null) {
        remoteInterpreterProcess.releaseClient(client, broken);
      }
    }
    return null;
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
