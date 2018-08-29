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

import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.ZeppelinClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Routine that sends PING to all connected Zeppelin ws connections. */
public class ZeppelinHeartbeat implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinHubHeartbeat.class);
  private ZeppelinClient client;

  public static ZeppelinHeartbeat newInstance(ZeppelinClient client) {
    return new ZeppelinHeartbeat(client);
  }

  private ZeppelinHeartbeat(ZeppelinClient client) {
    this.client = client;
  }

  @Override
  public void run() {
    LOG.debug("Sending PING to Zeppelin Websocket Server");
    client.ping();
  }
}
