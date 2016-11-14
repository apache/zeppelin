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
package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.socket.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client to connect Zeppelin and ZeppelinHub via websocket API.
 * Implemented using singleton pattern.
 * 
 */
public class Client {
  private static final Logger LOG = LoggerFactory.getLogger(Client.class);
  private final ZeppelinhubClient zeppelinhubClient;
  private final ZeppelinClient zeppelinClient;
  private static Client instance = null;

  private static final int MB = 1048576;
  private static final int MAXIMUM_NOTE_SIZE = 64 * MB;

  public static Client initialize(String zeppelinUri, String zeppelinhubUri, String token, 
      ZeppelinConfiguration conf) {
    if (instance == null) {
      instance = new Client(zeppelinUri, zeppelinhubUri, token, conf);
    }
    return instance;
  }

  public static Client getInstance() {
    return instance;
  }

  private Client(String zeppelinUri, String zeppelinhubUri, String token,
      ZeppelinConfiguration conf) {
    LOG.debug("Init Client");
    zeppelinhubClient = ZeppelinhubClient.initialize(zeppelinhubUri, token);
    zeppelinClient = ZeppelinClient.initialize(zeppelinUri, token, conf);
  }

  public void start() {
    if (zeppelinhubClient != null) {
      zeppelinhubClient.start();
    }
    if (zeppelinClient != null) {
      zeppelinClient.start();
    }
  }

  public void stop() {
    if (zeppelinhubClient != null) {
      zeppelinhubClient.stop();
    }
    if (zeppelinClient != null) {
      zeppelinClient.stop();
    }
  }

  public void relayToZeppelinHub(String message) {
    zeppelinhubClient.send(message);
  }

  public void relayToZeppelin(Message message, String noteId) {
    zeppelinClient.send(message, noteId);
  }

  public static int getMaxNoteSize() {
    return MAXIMUM_NOTE_SIZE;
  }
}
