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

import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.zeppelin.notebook.Paragraph;

import java.lang.reflect.Field;
import java.lang.reflect.Type;

/**
 * Custom serializer for Paragraph
 */
public class ParagraphSerializer implements JsonSerializer<Paragraph> {
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

  private static void addFieldValue(JsonSerializationContext context, JsonObject json,
    Paragraph src, String fieldName) {
    Field field = getField(fieldName);
    if (field != null) {
      try {
        Object value = field.get(src);
        if (value != null) {
          json.add(fieldName, context.serialize(value));
        }
      } catch (IllegalAccessException e) {
      }
    }
  }

  @Override
  public JsonElement serialize(Paragraph src, Type typeOfSrc,
    JsonSerializationContext context) {
    JsonObject json = new JsonObject();
    addFieldValue(context, json, src, "title");
    String text = src.getText();
    if (text != null) {
      json.add("text", context.serialize(text.split("\n")));
    }
    addFieldValue(context, json, src, "user");
    addFieldValue(context, json, src, "dateUpdated");
    addFieldValue(context, json, src, "config");
    addFieldValue(context, json, src, "settings");
    addFieldValue(context, json, src, "jobName");
    addFieldValue(context, json, src, "id");
    addFieldValue(context, json, src, "results");
    addFieldValue(context, json, src, "dateCreated");
    addFieldValue(context, json, src, "dateStarted");
    addFieldValue(context, json, src, "dateFinished");
    addFieldValue(context, json, src, "status");
    addFieldValue(context, json, src, "errorMessage");
    addFieldValue(context, json, src, "progressUpdateIntervalMs");
    return json;
  }
}
