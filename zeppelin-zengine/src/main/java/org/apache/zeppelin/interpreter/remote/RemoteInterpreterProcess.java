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
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransportException;
import org.apache.zeppelin.interpreter.launcher.InterpreterClient;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Abstract class for interpreter process
 */
public abstract class RemoteInterpreterProcess implements InterpreterClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteInterpreterProcess.class);
  private static final Gson GSON = new Gson();

  private int connectTimeout;
  protected String intpEventServerHost;
  protected int intpEventServerPort;
  private PooledRemoteClient<Client> remoteClient;

  public RemoteInterpreterProcess(int connectTimeout,
                                  String intpEventServerHost,
                                  int intpEventServerPort) {
    this.connectTimeout = connectTimeout;
    this.intpEventServerHost = intpEventServerHost;
    this.intpEventServerPort = intpEventServerPort;
    this.remoteClient = new PooledRemoteClient<Client>(() -> {
      TSocket transport = new TSocket(getHost(), getPort());
      try {
        transport.open();
      } catch (TTransportException e) {
        throw new IOException(e);
      }
      TProtocol protocol = new  TBinaryProtocol(transport);
      return new Client(protocol);
    });
  }

  public int getConnectTimeout() {
    return connectTimeout;
  }

  public void shutdown() {
    if (remoteClient != null) {
      remoteClient.shutdown();
    }
  }

  /**
   * Called when angular object is updated in client side to propagate
   * change to the remote process
   * @param name
   * @param o
   */
  public void updateRemoteAngularObject(String name,
                                        String noteId,
                                        String paragraphId,
                                        Object o) {
    remoteClient.callRemoteFunction((PooledRemoteClient.RemoteFunction<Void, Client>) client -> {
       client.angularObjectUpdate(name, noteId, paragraphId, GSON.toJson(o));
       return null;
    });
  }

  public <R> R callRemoteFunction(PooledRemoteClient.RemoteFunction<R, Client> func) {
    return remoteClient.callRemoteFunction(func);
  }

  @Override
  public boolean recover() {
    try {
      remoteClient.callRemoteFunction(client -> {
        client.reconnect(intpEventServerHost, intpEventServerPort);
        return null;
      });
      return true;
    } catch (Exception e) {
      LOGGER.error("Fail to recover remote interpreter process: {}" , e.getMessage());
      return false;
    }
  }


  /**
   * called by RemoteInterpreterEventServer to notify that RemoteInterpreter Process is started
   */
  public abstract void processStarted(int port, String host);

  public abstract String getErrorMessage();
}
