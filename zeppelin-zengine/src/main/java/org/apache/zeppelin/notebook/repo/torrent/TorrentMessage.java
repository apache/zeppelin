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

package org.apache.zeppelin.notebook.repo.torrent;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;


/**
 * Torrent Message
 */
public class TorrentMessage {
  private static final Gson gson = new Gson();

  public TorrentOp op;
  public Map<String, Object> data = new HashMap<>();

  public TorrentMessage(TorrentOp op) {
    this.op = op;

  }

  public static TorrentMessage deserilize(String message) {
    return gson.fromJson(message, TorrentMessage.class);
  }

  public void put(String key, Object o) {
    data.put(key, o);
  }

  public Object get(String key) {
    return data.get(key);
  }

  public String serialize() {
    return gson.toJson(this);
  }

}
