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
package org.apache.zeppelin.rest;

import java.lang.reflect.Type;

import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.resource.Resource;
import org.apache.zeppelin.resource.ResourceSet;
import org.apache.zeppelin.scheduler.Job;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
/**
 * Responsible for passing along minimal information for a REST API to consume notes.
 *
 */
public class NoteRestSerializer implements JsonSerializer<Note> {

  ResourceSet resources;
  
  @Override
  public JsonElement serialize(Note src, Type typeOfSrc, JsonSerializationContext context) {
    JsonObject obj = new JsonObject();
    obj.addProperty("id", src.getId());
    obj.addProperty("name", src.getName());
    GsonBuilder builder = new GsonBuilder();
    // We don't serialize paragraph results in this case.
    builder.addSerializationExclusionStrategy(new ExclusionStrategy() {
      @Override
      public boolean shouldSkipField(FieldAttributes f) {
        return (Job.class.isAssignableFrom(f.getDeclaringClass())) && 
            (f.getName().equals("config") ||
             f.getName().equals("settings") || 
             f.getName().equals("result") ||
             f.getName().equals("jobName"));
      }
      
      @Override
      public boolean shouldSkipClass(Class<?> clazz) {
        return false;
      }
    });
    Gson gson = builder.create();
    JsonArray paragraphs = new JsonArray();
    for (Resource r: resources) {
      for (Paragraph p: src.getParagraphs()) {
        if (r.getResourceId().getParagraphId().equals(p.getId())) {
          paragraphs.add(gson.toJsonTree(p).getAsJsonObject());
          
        }
      }
    }
    obj.add("paragraphs", paragraphs);
    return obj;
  }

  public NoteRestSerializer(ResourceSet resources) {
    this.resources = resources;
  }
}
