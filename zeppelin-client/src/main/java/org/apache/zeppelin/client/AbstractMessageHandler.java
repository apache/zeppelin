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

package org.apache.zeppelin.client;

import com.google.gson.Gson;
import org.apache.zeppelin.notebook.socket.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractMessageHandler implements MessageHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractMessageHandler.class);
  private static final Gson GSON = new Gson();

  @Override
  public void onMessage(String msg) {
    try {
      Message messageReceived = GSON.fromJson(msg, Message.class);
      if (messageReceived.op != Message.OP.PING) {
        LOGGER.debug("RECEIVE: " + messageReceived.op +
                ", RECEIVE PRINCIPAL: " + messageReceived.principal +
                ", RECEIVE TICKET: " + messageReceived.ticket +
                ", RECEIVE ROLES: " + messageReceived.roles +
                ", RECEIVE DATA: " + messageReceived.data);
      }
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("RECEIVE MSG = " + messageReceived);
      }

      // Lets be elegant here
      switch (messageReceived.op) {
        case PARAGRAPH_UPDATE_OUTPUT:
          String noteId = (String) messageReceived.data.get("noteId");
          String paragraphId = (String) messageReceived.data.get("paragraphId");
          int index = (int) Double.parseDouble(messageReceived.data.get("index").toString());
          String type = (String) messageReceived.data.get("type");
          String output = (String) messageReceived.data.get("data");
          onStatementUpdateOutput(paragraphId, index, type, output);
          break;
        case PARAGRAPH_APPEND_OUTPUT:
          noteId = (String) messageReceived.data.get("noteId");
          paragraphId = (String) messageReceived.data.get("paragraphId");
          index = (int) Double.parseDouble(messageReceived.data.get("index").toString());
          output = (String) messageReceived.data.get("data");
          onStatementAppendOutput(paragraphId, index, output);
          break;
        default:
          break;
      }
    } catch (Exception e) {
      LOGGER.error("Can't handle message: " + msg, e);
    }

  }

  public abstract void onStatementAppendOutput(String statementId, int index, String output);

  public abstract void onStatementUpdateOutput(String statementId, int index, String type, String output);

}
