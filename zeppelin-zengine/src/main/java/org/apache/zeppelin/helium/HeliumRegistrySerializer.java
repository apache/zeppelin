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
package org.apache.zeppelin.helium;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * HeliumRegistrySerializer (and deserializer) for gson
 */
public class HeliumRegistrySerializer
    implements JsonSerializer<HeliumRegistry>, JsonDeserializer<HeliumRegistry> {
  Logger logger = LoggerFactory.getLogger(HeliumRegistrySerializer.class);

  @Override
  public HeliumRegistry deserialize(JsonElement json,
                                Type type,
                                JsonDeserializationContext jsonDeserializationContext)
      throws JsonParseException {
    JsonObject jsonObject = json.getAsJsonObject();
    String className = jsonObject.get("class").getAsString();
    String uri = jsonObject.get("uri").getAsString();
    String name = jsonObject.get("name").getAsString();

    try {
      logger.info("Restore helium registry {} {} {}", name, className, uri);
      Class<HeliumRegistry> cls =
          (Class<HeliumRegistry>) getClass().getClassLoader().loadClass(className);
      Constructor<HeliumRegistry> constructor = cls.getConstructor(String.class, String.class);
      HeliumRegistry registry = constructor.newInstance(name, uri);
      return registry;
    } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
        InstantiationException | InvocationTargetException e) {
      logger.error(e.getMessage(), e);
      return null;
    }
  }

  @Override
  public JsonElement serialize(HeliumRegistry heliumRegistry,
                               Type type,
                               JsonSerializationContext jsonSerializationContext) {
    JsonObject json = new JsonObject();
    json.addProperty("class", heliumRegistry.getClass().getName());
    json.addProperty("uri", heliumRegistry.uri());
    json.addProperty("name", heliumRegistry.name());
    return json;
  }
}
