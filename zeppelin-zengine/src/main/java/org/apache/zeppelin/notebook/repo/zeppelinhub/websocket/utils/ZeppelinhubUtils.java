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
package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.utils;

import java.util.HashMap;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.protocol.ZeppelinHubOp;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.protocol.ZeppelinhubMessage;
import org.apache.zeppelin.notebook.socket.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class.
 *
 */
public class ZeppelinhubUtils {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinhubUtils.class);

  public static String liveMessage(String token) {
    if (StringUtils.isBlank(token)) {
      LOG.error("Cannot create Live message: token is null or empty");
      return ZeppelinhubMessage.EMPTY.serialize();
    }
    HashMap<String, Object> data = new HashMap<String, Object>();
    data.put("token", token);
    return ZeppelinhubMessage
             .newMessage(ZeppelinHubOp.LIVE, data, new HashMap<String, String>())
             .serialize();
  }
  
  public static String deadMessage(String token) {
    if (StringUtils.isBlank(token)) {
      LOG.error("Cannot create Dead message: token is null or empty");
      return ZeppelinhubMessage.EMPTY.serialize();
    }
    HashMap<String, Object> data = new HashMap<String, Object>();
    data.put("token", token);
    return ZeppelinhubMessage
             .newMessage(ZeppelinHubOp.DEAD, data, new HashMap<String, String>())
             .serialize();
  }
  
  public static String pingMessage(String token) {
    if (StringUtils.isBlank(token)) {
      LOG.error("Cannot create Ping message: token is null or empty");
      return ZeppelinhubMessage.EMPTY.serialize();
    }
    HashMap<String, Object> data = new HashMap<String, Object>();
    data.put("token", token);
    return ZeppelinhubMessage
             .newMessage(ZeppelinHubOp.PING, data, new HashMap<String, String>())
             .serialize();
  }

  public static ZeppelinHubOp toZeppelinHubOp(String text) {
    ZeppelinHubOp hubOp = null;
    try {
      hubOp = ZeppelinHubOp.valueOf(text);
    } catch (IllegalArgumentException e) {
      // in case of non Hub op
    }
    return hubOp;
  }

  public static boolean isZeppelinHubOp(String text) {
    return (toZeppelinHubOp(text) != null); 
  }

  public static Message.OP toZeppelinOp(String text) {
    Message.OP zeppelinOp = null;
    try {
      zeppelinOp = Message.OP.valueOf(text);
    } catch (IllegalArgumentException e) {
      // in case of non Hub op
    }
    return zeppelinOp;
  }

  public static boolean isZeppelinOp(String text) {
    return (toZeppelinOp(text) != null); 
  }
}
