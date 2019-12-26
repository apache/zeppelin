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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.util.ArrayList;
import java.util.List;

@ClientEndpoint
@ServerEndpoint(value = "/")
public class TerminalSocketTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(TerminalSocketTest.class);

  public static final List<String> ReceivedMsg = new ArrayList();

  @OnOpen
  public void onWebSocketConnect(Session sess)
  {
    LOGGER.info("Socket Connected: " + sess);
  }

  @OnMessage
  public void onWebSocketText(String message)
  {
    LOGGER.info("Received TEXT message: " + message);
    ReceivedMsg.add(message);
  }

  @OnClose
  public void onWebSocketClose(CloseReason reason)
  {
    LOGGER.info("Socket Closed: " + reason);
  }

  @OnError
  public void onWebSocketError(Throwable cause)
  {
    LOGGER.error(cause.getMessage(), cause);
  }
}
