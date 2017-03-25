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

package org.apache.zeppelin.elasticsearch.action;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Contains the data of a hit.
 */
public class HitWrapper {

  private final JsonParser parser = new JsonParser();

  private final String index;
  private final String type;
  private final String id;
  private final String source;

  public HitWrapper(String index, String type, String id, String source) {
    this.index = index;
    this.type = type;
    this.id = id;
    this.source = source;
  }

  public HitWrapper(String source) {
    this(null, null, null, source);
  }

  public String getSourceAsString() {
    return source;
  }

  public JsonObject getSourceAsJsonObject() {
    final JsonElement element = parser.parse(source);
    return element.getAsJsonObject();
  }

  public String getIndex() {
    return index;
  }

  public String getType() {
    return type;
  }

  public String getId() {
    return id;
  }
}
