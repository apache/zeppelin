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

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransportException;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService.Client;

/**
 *
 */
public class ClientFactory extends BasePooledObjectFactory<Client>{
  private String host;
  private int port;
  Map<Client, TSocket> clientSocketMap = new HashMap<>();

  public ClientFactory(String host, int port) {
    this.host = host;
    this.port = port;
  }

  @Override
  public Client create() throws Exception {
    TSocket transport = new TSocket(host, port);
    try {
      transport.open();
    } catch (TTransportException e) {
      throw new InterpreterException(e);
    }

    TProtocol protocol = new  TBinaryProtocol(transport);
    Client client = new RemoteInterpreterService.Client(protocol);

    synchronized (clientSocketMap) {
      clientSocketMap.put(client, transport);
    }
    return client;
  }

  @Override
  public PooledObject<Client> wrap(Client client) {
    return new DefaultPooledObject<>(client);
  }

  @Override
  public void destroyObject(PooledObject<Client> p) {
    synchronized (clientSocketMap) {
      if (clientSocketMap.containsKey(p.getObject())) {
        clientSocketMap.get(p.getObject()).close();
        clientSocketMap.remove(p.getObject());
      }
    }
  }

  @Override
  public boolean validateObject(PooledObject<Client> p) {
    return p.getObject().getOutputProtocol().getTransport().isOpen();
  }
}
