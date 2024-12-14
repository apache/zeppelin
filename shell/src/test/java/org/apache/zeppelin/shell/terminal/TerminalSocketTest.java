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

package org.apache.zeppelin.shell.terminal;

import java.util.ArrayList;
import java.util.List;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerminalSocketTest extends Endpoint {
  private static final Logger LOGGER = LoggerFactory.getLogger(TerminalSocketTest.class);

  public static final List<String> ReceivedMsg = new ArrayList<>();

  @Override
  public void onOpen(Session session, EndpointConfig endpointConfig) {
    LOGGER.info("Socket Connected: " + session);

    session.addMessageHandler(new jakarta.websocket.MessageHandler.Whole<String>() {
      @Override
      public void onMessage(String message) {
        LOGGER.info("Received TEXT message: " + message);
        ReceivedMsg.add(message);
      }
    });
  }

  @Override
  public void onClose(Session session, CloseReason closeReason) {
    LOGGER.info("Socket Closed: " + closeReason);
  }

  @Override
  public void onError(Session session, Throwable cause) {
    LOGGER.error(cause.getMessage(), cause);
  }
}
