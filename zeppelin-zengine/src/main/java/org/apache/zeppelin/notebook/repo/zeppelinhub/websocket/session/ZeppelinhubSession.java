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
package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.session;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zeppelinhub session.
 */
public class ZeppelinhubSession {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinhubSession.class);
  private Session session;
  private final String token;
  
  public static final ZeppelinhubSession EMPTY = new ZeppelinhubSession(null, StringUtils.EMPTY);
  
  public static ZeppelinhubSession createInstance(Session session, String token) {
    return new ZeppelinhubSession(session, token);
  }
  
  private ZeppelinhubSession(Session session, String token) {
    this.session = session;
    this.token = token;
  }
  
  public boolean isSessionOpen() {
    return ((session != null) && (session.isOpen()));
  }
  
  public void close() {
    if (isSessionOpen()) {
      session.close();
    }
  }
  
  public void sendByFuture(String msg) {
    if (StringUtils.isBlank(msg)) {
      LOG.error("Cannot send event to Zeppelinhub, msg is empty");
    }
    if (isSessionOpen()) {
      session.getRemote().sendStringByFuture(msg);
    } else {
      LOG.error("Cannot send event to Zeppelinhub, session is close");
    }
  }
}
