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

package org.apache.zeppelin.interpreter;

import com.google.gson.Gson;
import org.apache.zeppelin.common.JsonSerializable;

/** Remote Zeppelin Server Resource */
public class RemoteZeppelinServerResource implements JsonSerializable {
  private static final Gson gson = new Gson();

  /** Resource Type for Zeppelin Server */
  public enum Type {
    PARAGRAPH_RUNNERS
  }

  private String ownerKey;
  private Type resourceType;
  private Object data;

  public Type getResourceType() {
    return resourceType;
  }

  public String getOwnerKey() {
    return ownerKey;
  }

  public void setOwnerKey(String ownerKey) {
    this.ownerKey = ownerKey;
  }

  public void setResourceType(Type resourceType) {
    this.resourceType = resourceType;
  }

  public Object getData() {
    return data;
  }

  public void setData(Object data) {
    this.data = data;
  }

  public String toJson() {
    return gson.toJson(this);
  }

  public static RemoteZeppelinServerResource fromJson(String json) {
    return gson.fromJson(json, RemoteZeppelinServerResource.class);
  }
}
