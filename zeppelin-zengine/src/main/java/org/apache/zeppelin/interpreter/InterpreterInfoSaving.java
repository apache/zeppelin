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
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.common.JsonSerializable;
import org.eclipse.aether.repository.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

/**
 *
 */
public class InterpreterInfoSaving implements JsonSerializable {

  private static final Logger LOGGER = LoggerFactory.getLogger(InterpreterInfoSaving.class);

  // Authentication is an interface so that we need to create an InterfaceAdapter for that.
  private static final Gson GSON =  new GsonBuilder().setPrettyPrinting()
          .registerTypeAdapter(Authentication.class, new InterfaceAdapter<Authentication>())
          .create();

  public Map<String, InterpreterSetting> interpreterSettings = new HashMap<>();
  public List<RemoteRepository> interpreterRepositories = new ArrayList<>();

  public static InterpreterInfoSaving loadFromFile(Path file) throws IOException {
    LOGGER.info("Load interpreter setting from file: {}", file);
    InterpreterInfoSaving infoSaving = null;
    try (BufferedReader json = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      JsonObject jsonObject = JsonParser.parseReader(json).getAsJsonObject();
      infoSaving = InterpreterInfoSaving.fromJson(jsonObject.toString());

      if (infoSaving != null && infoSaving.interpreterSettings != null) {
        for (InterpreterSetting interpreterSetting : infoSaving.interpreterSettings.values()) {
          interpreterSetting.convertPermissionsFromUsersToOwners(
                  jsonObject.getAsJsonObject("interpreterSettings")
                          .getAsJsonObject(interpreterSetting.getId()));
        }
      }
    }
    return infoSaving == null ? new InterpreterInfoSaving() : infoSaving;
  }

  public void saveToFile(Path file) throws IOException {
    if (!Files.exists(file)) {
      Files.createFile(file);
      try {
        Set<PosixFilePermission> permissions = EnumSet.of(OWNER_READ, OWNER_WRITE);
        Files.setPosixFilePermissions(file, permissions);
      } catch (UnsupportedOperationException e) {
        // File system does not support Posix file permissions (likely windows) - continue anyway.
        LOGGER.warn("unable to setPosixFilePermissions on '{}'.", file);
      }
    }
    LOGGER.info("Save Interpreter Settings to {}", file);
    IOUtils.write(this.toJson(), new FileOutputStream(file.toFile()), StandardCharsets.UTF_8);
  }

  @Override
  public String toJson() {
    return GSON.toJson(this);
  }

  public static InterpreterInfoSaving fromJson(String json) {
    return GSON.fromJson(json, InterpreterInfoSaving.class);
  }

  static class InterfaceAdapter<T> implements JsonSerializer<T>, JsonDeserializer<T> {
    @Override
    public JsonElement serialize(T object, Type interfaceType, JsonSerializationContext context) {
      final JsonObject wrapper = new JsonObject();
      wrapper.addProperty("type", object.getClass().getName());
      wrapper.add("data", context.serialize(object));
      return wrapper;
    }

    @Override
    public T deserialize(JsonElement elem,
                         Type interfaceType,
                         JsonDeserializationContext context) throws JsonParseException {
      final JsonObject wrapper = (JsonObject) elem;
      final JsonElement typeName = get(wrapper, "type");
      final JsonElement data = get(wrapper, "data");
      final Type actualType = typeForName(typeName);
      return context.deserialize(data, actualType);
    }

    private Type typeForName(final JsonElement typeElem) {
      try {
        return Class.forName(typeElem.getAsString());
      } catch (ClassNotFoundException e) {
        throw new JsonParseException(e);
      }
    }

    private JsonElement get(final JsonObject wrapper, String memberName) {
      final JsonElement elem = wrapper.get(memberName);
      if (elem == null)
        throw new JsonParseException("no '" + memberName +
                "' member found in what was expected to be an interface wrapper");
      return elem;
    }
  }
}
