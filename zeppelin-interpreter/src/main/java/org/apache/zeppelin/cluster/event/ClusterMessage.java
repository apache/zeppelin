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
package org.apache.zeppelin.cluster.event;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.zeppelin.display.Input;

import java.util.HashMap;
import java.util.Map;

public class ClusterMessage {
  public ClusterEvent clusterEvent;
  private Map<String, String> data = new HashMap<>();

  private static Gson gson = new GsonBuilder()
      .setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
      .setPrettyPrinting()
      .registerTypeAdapterFactory(Input.TypeAdapterFactory).create();

  public ClusterMessage(ClusterEvent event) {
    this.clusterEvent = event;
  }

  public ClusterMessage put(String k, String v) {
    data.put(k, v);
    return this;
  }

  public ClusterMessage put(Map<String, String> params) {
    data.putAll(params);
    return this;
  }

  public String get(String k) {
    return data.get(k);
  }

  public Map<String, String> getData() {
    return data;
  }

  public static ClusterMessage deserializeMessage(String msg) {
    return gson.fromJson(msg, ClusterMessage.class);
  }

  public static String serializeMessage(ClusterMessage m) {
    return gson.toJson(m);
  }
}
