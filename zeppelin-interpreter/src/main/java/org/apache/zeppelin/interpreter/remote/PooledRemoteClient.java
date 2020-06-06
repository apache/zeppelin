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

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.thrift.TException;
import org.apache.thrift.TServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Use this class to connect to remote thrift server and invoke any thrift rpc.
 * Underneath, it would create SocketClient via a ObjectPool.
 *
 * @param <T>
 */
public class PooledRemoteClient<T extends TServiceClient> {

  private static final Logger LOGGER = LoggerFactory.getLogger(PooledRemoteClient.class);

  private GenericObjectPool<T> clientPool;
  private RemoteClientFactory<T> remoteClientFactory;

  public PooledRemoteClient(SupplierWithIO<T> supplier) {
    this.remoteClientFactory = new RemoteClientFactory<>(supplier);
    this.clientPool = new GenericObjectPool<>(remoteClientFactory);
  }

  public synchronized T getClient() throws Exception {
    return clientPool.borrowObject(5_000);
  }

  public void shutdown() {
    // Close client socket connection
    if (remoteClientFactory != null) {
      remoteClientFactory.close();
    }
  }

  private void releaseClient(T client, boolean broken) {
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

  private void releaseBrokenClient(T client) {
    try {
      LOGGER.warn("release broken client");
      clientPool.invalidateObject(client);
    } catch (Exception e) {
      LOGGER.warn("exception occurred during releasing thrift client", e);
    }
  }

  public <R> R callRemoteFunction(RemoteFunction<R, T> func) {
    T client = null;
    boolean broken = false;
    try {
      client = getClient();
      if (client != null) {
        return func.call(client);
      }
    } catch (TException e) {
      broken = true;
      throw new RuntimeException(e);
    } catch (Exception e1) {
      throw new RuntimeException(e1);
    } finally {
      if (client != null) {
        releaseClient(client, broken);
      }
    }
    return null;
  }


  public interface RemoteFunction<R, T> {
    R call(T client) throws Exception;
  }
}
