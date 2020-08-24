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

/**
 * Simple implementation of AbstractMessageHandler which only print output.
 */
public class SimpleMessageHandler extends AbstractMessageHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(SimpleMessageHandler.class);

  @Override
  public void onStatementAppendOutput(String statementId, int index, String output) {
    LOGGER.info("Append output, data: {}", output);
  }

  @Override
  public void onStatementUpdateOutput(String statementId, int index, String type, String output) {
    LOGGER.info("Update output, type: {}, data: {}", type, output);
  }
}
