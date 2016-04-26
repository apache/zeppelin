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
package org.apache.zeppelin.resource;

import java.util.List;

import org.apache.thrift.TException;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService.Client;

import com.google.gson.Gson;
/**
 *  Makes a remote interpreter service client act as a resource pool connector.
 */
public class RemoteInterpreterProcessResourcePoolConnector implements ResourcePoolConnector {

  private Client client;
  
  public RemoteInterpreterProcessResourcePoolConnector(Client client) {
    this.client = client;
  }

  @Override
  public ResourceSet getAllResources() {
    try {
      List<String> resourceList = client.resourcePoolGetAll();
      ResourceSet resources = new ResourceSet();
      Gson gson = new Gson();
      
      for (String res : resourceList) {
        RemoteResource r = gson.fromJson(res, RemoteResource.class);
        r.setResourcePoolConnector(this);
        resources.add(r);
      }
      
      return resources;
    } catch (TException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Object readResource(ResourceId id) {
    try {
      // TODO(Object): Deserialize object
      return client.resourceGet(id.getNoteId(), id.getParagraphId(), id.getName());
    } catch (TException e) {
      throw new RuntimeException(e);
    }
  }

}
