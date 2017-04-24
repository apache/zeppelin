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

import java.util.*;

/**
 * ResourcePool
 */
public class LocalResourcePool implements ResourcePool {
  private final String resourcePoolId;
  private final Map<ResourceId, Resource> resources = Collections.synchronizedMap(
      new HashMap<ResourceId, Resource>());

  /**
   * @param id unique id
   */
  public LocalResourcePool(String id) {
    resourcePoolId = id;
  }

  /**
   * Get unique id of this resource pool
   * @return
   */
  @Override
  public String id() {
    return resourcePoolId;
  }

  /**
   * Get resource
   * @return null if resource not found
   */
  @Override
  public Resource get(String name) {
    ResourceId resourceId = new ResourceId(resourcePoolId, name);
    return resources.get(resourceId);
  }

  @Override
  public Resource get(String noteId, String paragraphId, String name) {
    ResourceId resourceId = new ResourceId(resourcePoolId, noteId, paragraphId, name);
    return resources.get(resourceId);
  }

  @Override
  public ResourceSet getAll() {
    return new ResourceSet(resources.values());
  }

  /**
   * Put resource into the pull
   * @param
   * @param object object to put into the resource
   */
  @Override
  public void put(String name, Object object) {
    ResourceId resourceId = new ResourceId(resourcePoolId, name);

    Resource resource = new Resource(this, resourceId, object);
    resources.put(resourceId, resource);
  }

  @Override
  public void put(String noteId, String paragraphId, String name, Object object) {
    ResourceId resourceId = new ResourceId(resourcePoolId, noteId, paragraphId, name);

    Resource resource = new Resource(this, resourceId, object);
    resources.put(resourceId, resource);
  }

  @Override
  public Resource remove(String name) {
    return resources.remove(new ResourceId(resourcePoolId, name));
  }

  @Override
  public Resource remove(String noteId, String paragraphId, String name) {
    return resources.remove(new ResourceId(resourcePoolId, noteId, paragraphId, name));
  }
}
