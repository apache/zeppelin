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
 * Resource that can retrieve data from remote
 */
public class RemoteResource extends Resource {
  ResourcePoolConnector resourcePoolConnector;

  RemoteResource(ResourceId resourceId, Object r) {
    super(resourceId, r);
  }

  RemoteResource(ResourceId resourceId, boolean serializable, String className) {
    super(resourceId, serializable, className);
  }

  @Override
  public Object get() {
    if (isSerializable()) {
      Object o = resourcePoolConnector.readResource(getResourceId());
      return o;
    } else {
      return null;
    }
  }

  @Override
  public boolean isLocal() {
    return false;
  }

  public ResourcePoolConnector getResourcePoolConnector() {
    return resourcePoolConnector;
  }

  public void setResourcePoolConnector(ResourcePoolConnector resourcePoolConnector) {
    this.resourcePoolConnector = resourcePoolConnector;
  }
}
