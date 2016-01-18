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
 * Identifying resource
 */
public class ResourceId {
  private final String resourcePoolId;
  private final String name;

  ResourceId(String resourcePoolId, String name) {
    this.resourcePoolId = resourcePoolId;
    this.name = name;
  }

  public String getResourcePoolId() {
    return resourcePoolId;
  }

  public String getName() {
    return name;
  }

  @Override
  public int hashCode() {
    return (resourcePoolId + name).hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ResourceId) {
      ResourceId r = (ResourceId) o;
      return (r.name.equals(name) && r.resourcePoolId.equals(resourcePoolId));
    } else {
      return false;
    }
  }
}
