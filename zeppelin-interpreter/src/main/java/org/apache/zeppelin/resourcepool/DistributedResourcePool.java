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
package org.apache.zeppelin.resourcepool;

import java.io.Serializable;
import java.util.UUID;

/**
 * distributed resource pool
 */
public class DistributedResourcePool extends LocalResourcePool {

  private final ResourcePoolConnector connector;

  public DistributedResourcePool(String id, ResourcePoolConnector connector) {
    super(id);
    this.connector = connector;
  }

  /**
   * get resource by name.
   * @param name
   * @return null if resource not found.
   */
  @Override
  public Resource get(String name) {
    // try local first
    Resource resource = super.get(name);
    if (resource != null) {
      return resource;
    }

    ResourceSet resources = connector.getAllResourcesExcept(id()).filterByName(name);
    if (resources.isEmpty()) {
      return null;
    } else {
      return resources.get(0);
    }
  }

  @Override
  public ResourceSet getAll() {
    ResourceSet all = super.getAll();
    all.addAll(connector.getAllResourcesExcept(id()));
    return all;
  }
}
