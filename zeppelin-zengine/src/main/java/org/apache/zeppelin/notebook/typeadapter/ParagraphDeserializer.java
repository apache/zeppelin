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

import org.apache.zeppelin.notebook.Paragraph;
import org.apache.commons.lang.StringUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.lang.reflect.InvocationTargetException;

/**
 * Custom deserializer for Paragraph
 */
public class ParagraphDeserializer implements JsonDeserializer<Paragraph> {
  private static Paragraph createParagraph() {
    try {
      Constructor<Paragraph> constructor = Paragraph.class.getDeclaredConstructor();
      if (!constructor.isAccessible()) {
        constructor.setAccessible(true);
      }
      return constructor.newInstance();
    } catch (NoSuchMethodException e) {
    } catch (InstantiationException e) {
    } catch (IllegalAccessException e) {
    } catch (InvocationTargetException e) {
    }
    return null;
  }

  private static Field getField(String fieldName) {
    Class klass = Paragraph.class;
    while (klass != Object.class) {
      try {
        Field field = klass.getDeclaredField(fieldName);
        if (!field.isAccessible()) {
          field.setAccessible(true);
        }
        return field;
      } catch (NoSuchFieldException e) {
      }
      klass = klass.getSuperclass();
    }
    return null;
  }

  private static void extractFieldValue(JsonDeserializationContext context, JsonObject json,
    Paragraph dst, String fieldName) {
    Field field = getField(fieldName);
    if (field != null) {
      Object value = context.deserialize(json.get(fieldName), field.getType());
      try {
        field.set(dst, value);
      } catch (IllegalAccessException e) {
      }
    }
  }

  @Override
  public Paragraph deserialize(JsonElement json, Type typeOfT,
    JsonDeserializationContext context) throws JsonParseException {
    JsonObject jsonObject = (JsonObject) json;
    Paragraph dst = createParagraph();
    if (dst == null) {
      return null;
    }
    extractFieldValue(context, jsonObject, dst, "title");
    JsonElement textObject = jsonObject.get("text");
    if (textObject != null) {
      if (textObject.isJsonArray()) {
        String[] value = context.deserialize(textObject, String[].class);
        dst.setText(StringUtils.join(value, "\n"));
      } else if (textObject.isJsonPrimitive()) {
        dst.setText(textObject.getAsJsonPrimitive().getAsString());
      }
    }
    extractFieldValue(context, jsonObject, dst, "user");
    extractFieldValue(context, jsonObject, dst, "dateUpdated");
    extractFieldValue(context, jsonObject, dst, "config");
    extractFieldValue(context, jsonObject, dst, "settings");
    extractFieldValue(context, jsonObject, dst, "jobName");
    extractFieldValue(context, jsonObject, dst, "id");
    extractFieldValue(context, jsonObject, dst, "results");
    extractFieldValue(context, jsonObject, dst, "dateCreated");
    extractFieldValue(context, jsonObject, dst, "dateStarted");
    extractFieldValue(context, jsonObject, dst, "dateFinished");
    extractFieldValue(context, jsonObject, dst, "status");
    extractFieldValue(context, jsonObject, dst, "errorMessage");
    extractFieldValue(context, jsonObject, dst, "progressUpdateIntervalMs");
    return dst;
  }
}
