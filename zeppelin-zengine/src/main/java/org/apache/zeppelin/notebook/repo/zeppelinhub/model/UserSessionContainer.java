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
package org.apache.zeppelin.notebook.repo.zeppelinhub.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.StringUtils;

/**
 * Simple and yet dummy container for zeppelinhub session.
 * 
 */
public class UserSessionContainer {
  private static class Entity {
    public final String userSession;
    
    Entity(String userSession) {
      this.userSession = userSession;
    }
  }

  private Map<String, Entity> sessions = new ConcurrentHashMap<>();

  public static final UserSessionContainer instance = new UserSessionContainer();

  public synchronized String getSession(String principal) {
    Entity entry = sessions.get(principal);
    if (entry == null) {
      return StringUtils.EMPTY;
    }
    return entry.userSession;
  }
  
  public synchronized String setSession(String principal, String userSession) {
    Entity entry = new Entity(userSession);
    sessions.put(principal, entry);
    return entry.userSession;
  }
}
