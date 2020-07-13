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

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.thrift.TServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory class for creating thrift socket client.
 */
public class RemoteClientFactory<T extends TServiceClient> extends BasePooledObjectFactory<T>{

  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteClientFactory.class);

  private Set<T> clientSockets = ConcurrentHashMap.newKeySet();
  private SupplierWithIO<T> supplier;

  public RemoteClientFactory(SupplierWithIO<T> supplier) {
    this.supplier = supplier;
  }

  public void close() {
    for (T clientSocket: clientSockets) {
      clientSocket.getInputProtocol().getTransport().close();
      clientSocket.getOutputProtocol().getTransport().close();
    }
  }

  @Override
  public T create() throws Exception {
    T clientSocket = supplier.getWithIO();
    clientSockets.add(clientSocket);
    return clientSocket;
  }

  @Override
  public PooledObject<T> wrap(T client) {
    return new DefaultPooledObject<>(client);
  }

  @Override
  public void destroyObject(PooledObject<T> p) {
    p.getObject().getOutputProtocol().getTransport().close();
    p.getObject().getInputProtocol().getTransport().close();
    clientSockets.remove(p.getObject());
  }

  @Override
  public boolean validateObject(PooledObject<T> p) {
    return p.getObject().getOutputProtocol().getTransport().isOpen();
  }
}
