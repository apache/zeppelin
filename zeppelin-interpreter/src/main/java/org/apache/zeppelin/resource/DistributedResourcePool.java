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

/**
 * distributed resource pool
 */
public class DistributedResourcePool extends LocalResourcePool {

  private final ResourcePoolConnector connector;

  public DistributedResourcePool(String id, ResourcePoolConnector connector) {
    super(id);
    this.connector = connector;
  }

  @Override
  public Resource get(String name) {
    return get(name, true);
  }

  @Override
  public Resource get(String noteId, String paragraphId, String name) {
    return get(noteId, paragraphId, name, true);
  }

  /**
   * get resource by name.
   * @param name
   * @param remote false only return from local resource
   * @return null if resource not found.
   */
  public Resource get(String name, boolean remote) {
    // try local first
    Resource resource = super.get(name);
    if (resource != null) {
      return resource;
    }

    if (remote) {
      ResourceSet resources = connector.getAllResources().filterByName(name);
      if (resources.isEmpty()) {
        return null;
      } else {
        return resources.get(0);
      }
    } else {
      return null;
    }
  }

  /**
   * get resource by name.
   * @param name
   * @param remote false only return from local resource
   * @return null if resource not found.
   */
  public Resource get(String noteId, String paragraphId, String name, boolean remote) {
    // try local first
    Resource resource = super.get(noteId, paragraphId, name);
    if (resource != null) {
      return resource;
    }

    if (remote) {
      ResourceSet resources = connector.getAllResources()
          .filterByNoteId(noteId)
          .filterByParagraphId(paragraphId)
          .filterByName(name);

      if (resources.isEmpty()) {
        return null;
      } else {
        return resources.get(0);
      }
    } else {
      return null;
    }
  }

  @Override
  public ResourceSet getAll() {
    return getAll(true);
  }

  /**
   * Get all resource from the pool
   * @param remote false only return local resource
   * @return
   */
  public ResourceSet getAll(boolean remote) {
    ResourceSet all = super.getAll();
    if (remote) {
      all.addAll(connector.getAllResources());
    }
    return all;
  }
}
