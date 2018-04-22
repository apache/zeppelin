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

package org.apache.zeppelin.interpreter;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Contains authorization information for interpreter settings
 */
public class InterpreterAuthorization {
  private static final Logger LOG = LoggerFactory.getLogger(InterpreterAuthorization.class);
  private ZeppelinConfiguration conf;
  private Map<String, Set<String>> owners;
  private Map<String, Set<String>> readers;

  public InterpreterAuthorization(InterpreterSettingManager interpreterSettingManager) {
    this.conf = ZeppelinConfiguration.create();
    this.owners = interpreterSettingManager.getAllOwners();
    this.readers = interpreterSettingManager.getAllReaders();
  }

  private boolean isOwner(String settingId, Set<String> entities) {
    return isMember(entities, owners.get(settingId)) ||
           isAdmin(entities);
  }

  private boolean isReader(String settingId, Set<String> entities) {
    return isMember(entities, readers.get(settingId)) ||
           isAdmin(entities);
  }

  private boolean isWriter(String settingId, Set<String> userAndRoles) {
    return isOwner(settingId, userAndRoles) &&
           isReader(settingId, userAndRoles);
  }

  private boolean isAdmin(Set<String> entities) {
    String adminRole = conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_OWNER_ROLE);
    if (StringUtils.isBlank(adminRole)) {
      return false;
    }
    return entities.contains(adminRole);
  }

  // return true if b is empty or if (a intersection b) is non-empty
  private boolean isMember(Set<String> a, Set<String> b) {
    Set<String> intersection = new HashSet<>(b);
    intersection.retainAll(a);
    return (b.isEmpty() || (intersection.size() > 0));
  }

  public boolean hasWriteAuthorization(Set<String> userAndRoles, String settingId) {
    if (conf.isAnonymousAllowed()) {
      LOG.debug("Zeppelin runs in anonymous mode, everybody is writer");
      return true;
    }
    if (userAndRoles == null) {
      return false;
    }
    return isWriter(settingId, userAndRoles);
  }

  public boolean hasReadAuthorization(Set<String> userAndRoles, String settingId) {
    if (conf.isAnonymousAllowed()) {
      LOG.debug("Zeppelin runs in anonymous mode, everybody is reader");
      return true;
    }
    if (userAndRoles == null) {
      return false;
    }
    return isReader(settingId, userAndRoles);
  }
}
