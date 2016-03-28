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

import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
/**
 * Serializes and Deserializes resources if they are serializable.
 */
public class ResourceSerializer implements JsonDeserializer<Resource>, JsonSerializer<Resource> {

  public ResourceSerializer() {
  }

  @Override
  public JsonElement serialize(Resource src, Type typeOfSrc, JsonSerializationContext context) {
    // This is straightforward at the moment.
    Gson gson = new Gson();
    JsonElement elem = gson.toJsonTree(src);
    JsonObject obj = elem.getAsJsonObject();
    if (src.isSerializable()) {
      obj.add("r", gson.toJsonTree(src.get()));
    }
    return obj;
  }

  @Override
  public Resource deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    // This requires that we use the class that's stored in the element to deserialize.
    JsonObject obj =  json.getAsJsonObject();
    String className = obj.getAsJsonPrimitive("className").getAsString();

    Gson gson = new Gson();
    Object r;
    try {
      r = gson.fromJson(obj.get("r"), Class.forName(className));
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Unable to deserialize the resource");
    }
    ResourceId id = gson.fromJson(obj.get("resourceId"), ResourceId.class);

    return new Resource(id, r);
  }

}
