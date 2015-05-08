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

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;


/**
 * Interpreter class serializer for gson
 *
 */
public class InterpreterSerializer implements JsonSerializer<Interpreter>,
  JsonDeserializer<Interpreter> {

  @Override
  public JsonElement serialize(Interpreter interpreter, Type type,
      JsonSerializationContext context) {
    JsonObject json = new JsonObject();
    json.addProperty("class", interpreter.getClassName());
    json.addProperty(
        "name",
        Interpreter.findRegisteredInterpreterByClassName(
            interpreter.getClassName()).getName());
    return json;
  }

  @Override
  public Interpreter deserialize(JsonElement json, Type typeOfT,
      JsonDeserializationContext context) throws JsonParseException {
    return null;
  }

}
