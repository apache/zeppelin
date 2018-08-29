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
 * Identifying resource
 */
public class ResourceId implements JsonSerializable {
  private static final Gson gson = new Gson();

  private final String resourcePoolId;
  private final String name;
  private final String noteId;
  private final String paragraphId;

  ResourceId(String resourcePoolId, String name) {
    this.resourcePoolId = resourcePoolId;
    this.noteId = null;
    this.paragraphId = null;
    this.name = name;
  }

  ResourceId(String resourcePoolId, String noteId, String paragraphId, String name) {
    this.resourcePoolId = resourcePoolId;
    this.noteId = noteId;
    this.paragraphId = paragraphId;
    this.name = name;
  }

  public String getResourcePoolId() {
    return resourcePoolId;
  }

  public String getName() {
    return name;
  }

  public String getNoteId() {
    return noteId;
  }

  public String getParagraphId() {
    return paragraphId;
  }

  @Override
  public int hashCode() {
    return (resourcePoolId + noteId + paragraphId + name).hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ResourceId) {
      ResourceId r = (ResourceId) o;
      return equals(r.name, name) && equals(r.resourcePoolId, resourcePoolId) &&
          equals(r.noteId, noteId) && equals(r.paragraphId, paragraphId);
    } else {
      return false;
    }
  }

  private boolean equals(String a, String b) {
    if (a == null && b == null) {
      return true;
    } else if (a != null && b != null) {
      return a.equals(b);
    } else {
      return false;
    }
  }

  public String toJson() {
    return gson.toJson(this);
  }

  public static ResourceId fromJson(String json) {
    return gson.fromJson(json, ResourceId.class);
  }
}
