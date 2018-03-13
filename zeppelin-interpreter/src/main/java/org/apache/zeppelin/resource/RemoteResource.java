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

import com.google.gson.Gson;
import org.apache.zeppelin.common.JsonSerializable;

/**
 * Resource that can retrieve data from remote
 */
public class RemoteResource extends Resource implements JsonSerializable {
  private static final Gson gson = new Gson();

  ResourcePoolConnector resourcePoolConnector;

  RemoteResource(ResourceId resourceId, Object r) {
    super(null, resourceId, r);
  }

  RemoteResource(ResourceId resourceId, boolean serializable, String className) {
    super(null, resourceId, serializable, className);
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

  /**
   * Call a method of the object that this remote resource holds
   * @param methodName name of method to call
   * @param paramTypes method parameter types
   * @param params method parameter values
   * @return return value of the method. Null if return value is not serializable
   */
  @Override
  public Object invokeMethod(
      String methodName, Class [] paramTypes, Object [] params) {
    ResourceId resourceId = getResourceId();
    return resourcePoolConnector.invokeMethod(
        resourceId,
        methodName,
        paramTypes,
        params);
  }

  /**
   * Call a method of the object that this remote resource holds and save return value as a resource
   * @param methodName name of method to call
   * @param paramTypes method parameter types
   * @param params method parameter values
   * @param returnResourceName name of resource that return value will be saved
   * @return Resource that holds return value.
   */
  @Override
  public Resource invokeMethod(
      String methodName, Class [] paramTypes, Object [] params, String returnResourceName) {
    ResourceId resourceId = getResourceId();
    Resource resource = resourcePoolConnector.invokeMethod(
        resourceId,
        methodName,
        paramTypes,
        params,
        returnResourceName);
    return resource;
  }

  public String toJson() {
    return gson.toJson(this);
  }

  public static RemoteResource fromJson(String json) {
    return gson.fromJson(json, RemoteResource.class);
  }
}
