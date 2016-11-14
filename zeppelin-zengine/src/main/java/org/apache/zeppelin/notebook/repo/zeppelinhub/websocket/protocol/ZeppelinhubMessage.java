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
package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.protocol;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.Client;
import org.apache.zeppelin.notebook.socket.Message;
import org.apache.zeppelin.notebook.socket.Message.OP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * Zeppelinhub message class.
 *
 */
public class ZeppelinhubMessage {
  private static final Gson gson = new Gson();
  private static final Logger LOG = LoggerFactory.getLogger(Client.class);
  public static final ZeppelinhubMessage EMPTY = new ZeppelinhubMessage();

  public Object op;
  public Object data;
  public Map<String, String> meta = Maps.newHashMap();
  
  private ZeppelinhubMessage() {
    this.op = OP.LIST_NOTES;
    this.data = null;
  }
  
  private ZeppelinhubMessage(Object op, Object data, Map<String, String> meta) {
    this.op = op;
    this.data = data;
    this.meta = meta;
  }
  
  public static ZeppelinhubMessage newMessage(Object op, Object data, Map<String, String> meta) {
    return new ZeppelinhubMessage(op, data, meta);
  }

  public static ZeppelinhubMessage newMessage(Message zeppelinMsg, Map<String, String> meta) {
    if (zeppelinMsg == null) {
      return EMPTY;
    }
    return new ZeppelinhubMessage(zeppelinMsg.op, zeppelinMsg.data, meta);
  }

  public String serialize() {
    return gson.toJson(this, ZeppelinhubMessage.class);
  }

  public static ZeppelinhubMessage deserialize(String zeppelinhubMessage) {
    if (StringUtils.isBlank(zeppelinhubMessage)) {
      return EMPTY;
    }
    ZeppelinhubMessage msg;
    try {
      msg = gson.fromJson(zeppelinhubMessage, ZeppelinhubMessage.class);
    } catch (JsonSyntaxException ex) {
      LOG.error("Cannot deserialize zeppelinhub message", ex);
      msg = EMPTY;
    }
    return msg;
  }
  
}
