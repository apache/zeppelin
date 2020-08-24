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

package org.apache.zeppelin.client.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * You can add StatementHandler for each statement.
 */
public class CompositeMessageHandler extends AbstractMessageHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(CompositeMessageHandler.class);

  private Map<String, StatementMessageHandler> messageHandlers = new HashMap();

  public void addStatementMessageHandler(String statementId,
                                         StatementMessageHandler messageHandler) {
    messageHandlers.put(statementId, messageHandler);
  }

  @Override
  public void onStatementAppendOutput(String statementId, int index, String output) {
    StatementMessageHandler messageHandler = messageHandlers.get(statementId);
    if (messageHandler == null) {
      LOGGER.warn("No messagehandler for statement: " + statementId);
      return;
    }
    messageHandler.onStatementAppendOutput(statementId, index, output);
  }

  @Override
  public void onStatementUpdateOutput(String statementId, int index, String type, String output) {
    StatementMessageHandler messageHandler = messageHandlers.get(statementId);
    if (messageHandler == null) {
      LOGGER.warn("No messagehandler for statement: " + statementId);
      return;
    }
    messageHandler.onStatementUpdateOutput(statementId, index, type, output);
  }
}
