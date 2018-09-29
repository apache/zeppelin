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
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.thrift.TException;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Interface to RemoteInterpreterProcess which is created by InterpreterLauncher. This is the component
 * that is used to for the communication from zeppelin-server process to zeppelin interpreter
 * process.
 */
public abstract class RemoteInterpreterProcess {

  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteInterpreterProcess.class);

  private GenericObjectPool<RemoteInterpreterService.Client> clientPool;
  private int connectTimeout;

  public RemoteInterpreterProcess(int connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public int getConnectTimeout() {
    return connectTimeout;
  }

  public abstract String getInterpreterSettingName();

  public abstract void start(String userName) throws IOException;

  public abstract void stop();

  public abstract String getHost();

  public abstract int getPort();

  public abstract boolean isRunning();

  private synchronized RemoteInterpreterService.Client getClient() throws Exception {
    if (clientPool == null || clientPool.isClosed()) {
      clientPool = new GenericObjectPool<>(new ClientFactory(getHost(), getPort()));
    }
    return clientPool.borrowObject();
  }

  private void releaseClient(RemoteInterpreterService.Client client, boolean broken) {
    if (broken) {
      releaseBrokenClient(client);
    } else {
      try {
        clientPool.returnObject(client);
      } catch (Exception e) {
        LOGGER.warn("exception occurred during releasing thrift client", e);
      }
    }
  }

  private void releaseBrokenClient(RemoteInterpreterService.Client client) {
    try {
      clientPool.invalidateObject(client);
    } catch (Exception e) {
      LOGGER.warn("exception occurred during releasing thrift client", e);
    }
  }

  /**
   * Called when angular object is updated in client side to propagate
   * change to the remote process
   *
   * @param name
   * @param sessionId
   * @param paragraphId
   * @param o
   */
  public void updateRemoteAngularObject(String name,
                                        String sessionId,
                                        String paragraphId,
                                        Object o) {
    callRemoteFunction(client -> {
      Gson gson = new Gson();
      client.angularObjectUpdate(name, sessionId, paragraphId, gson.toJson(o));
      return null;
    });
  }

  public <T> T callRemoteFunction(RemoteFunction<T> func) {
    RemoteInterpreterService.Client client = null;
    boolean broken = false;
    try {
      client = getClient();
      if (client != null) {
        return func.call(client);
      }
    } catch (TException e) {
      broken = true;
      throw new RuntimeException(e);
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      if (client != null) {
        releaseClient(client, broken);
      }
    }
    return null;
  }

  /**
   * @param <T>
   */
  @FunctionalInterface
  public interface RemoteFunction<T> {
    T call(RemoteInterpreterService.Client client) throws Exception;
  }

  /**
   * called by RemoteInterpreterEventServer to notify that RemoteInterpreter Process is started
   */
  public abstract void processStarted(int port, String host);
}
