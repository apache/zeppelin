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
package org.apache.zeppelin.service;

import org.apache.shiro.UnavailableSecurityManagerException;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.text.IniRealm;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;

/**
 * ShiroValidationService to validate shiro config
 */
public class ShiroValidationService {

  private final Logger LOGGER = LoggerFactory.getLogger(ShiroValidationService.class);

  private final ZeppelinConfiguration conf;

  @Inject
  public ShiroValidationService(ZeppelinConfiguration conf) throws Exception {
    LOGGER.info("ShiroValidationService is initialized");
    this.conf = conf;
    if (conf.getShiroPath().length() > 0) {
      try {
        Collection<Realm> realms =
            ((DefaultWebSecurityManager) org.apache.shiro.SecurityUtils.getSecurityManager())
                .getRealms();
        if (realms != null && realms.size() > 1) {
          Boolean isIniRealmEnabled = false;
          for (Realm realm : realms) {
            if (realm instanceof IniRealm && ((IniRealm) realm).getIni().get("users") != null) {
              isIniRealmEnabled = true;
              break;
            }
          }
          if (isIniRealmEnabled) {
            throw new Exception(
                "IniRealm/password based auth mechanisms should be exclusive. "
                    + "Consider removing [users] block from shiro.ini");
          }
        }
      } catch (UnavailableSecurityManagerException e) {
        LOGGER.error("Failed to initialise shiro configuration", e);
      }
    }
  }
}
