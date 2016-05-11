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
package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.scheduler;

import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.ZeppelinhubClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Check and test if zeppelinhub connection is still open.
 * 
 */
public class ZeppelinhubConnection implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinhubConnection.class);
  private ZeppelinhubClient client;
  
  public static ZeppelinhubConnection newInstance(ZeppelinhubClient client) {
    return new ZeppelinhubConnection(client);
  }
  
  private ZeppelinhubConnection(ZeppelinhubClient client) {
    this.client = client;
  }
  
  @Override
  public void run() {
    LOG.debug("Checking if Zeppelinbhub connection is open");
    client.reconnectIfConectionLost();
  }

}

