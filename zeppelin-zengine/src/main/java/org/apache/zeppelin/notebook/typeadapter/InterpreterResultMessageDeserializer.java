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

package org.apache.zeppelin.notebook.typeadapter;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.commons.lang.StringUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.lang.reflect.InvocationTargetException;

/**
 * Custom deserializer for InterpreterResultMessage
 */
public class InterpreterResultMessageDeserializer
    implements JsonDeserializer<InterpreterResultMessage> {
  @Override
  public InterpreterResultMessage deserialize(JsonElement json, Type typeOfT,
    JsonDeserializationContext context) throws JsonParseException {
    JsonObject jsonObject = (JsonObject) json;

    JsonElement typeObject = jsonObject.get("type");
    InterpreterResult.Type type = InterpreterResult.Type.NULL;
    if (typeObject.isJsonPrimitive()) {
      type = context.deserialize(typeObject, InterpreterResult.Type.class);
    }

    JsonElement dataObject = jsonObject.get("data");
    String data = "";
    if (dataObject != null) {
      if (dataObject.isJsonArray()) {
        String[] value = context.deserialize(dataObject, String[].class);
        data = StringUtils.join(value, "\n");
      } else if (dataObject.isJsonPrimitive()) {
        data = dataObject.getAsJsonPrimitive().getAsString();
      }
    }

    return new InterpreterResultMessage(type, data);
  }
}
