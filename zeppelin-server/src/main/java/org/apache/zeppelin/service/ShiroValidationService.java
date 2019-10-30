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

import org.apache.shiro.config.Ini;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * ShiroValidationService to validate shiro config
 */
public class ShiroValidationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ShiroValidationService.class);

  @Inject
  public ShiroValidationService(ZeppelinConfiguration conf) throws Exception {
    LOGGER.info("ShiroValidationService is initializing");
    init(conf);
  }

  public void init(ZeppelinConfiguration conf) throws Exception {
    if (conf.getShiroPath().length() > 0) {
      Ini ini = new Ini();
      ini.loadFromPath(conf.getShiroPath());
      validateIniProp(ini);
    }
    LOGGER.info("ShiroValidationService is initialized.");
  }

  private void validateIniProp(Ini ini) throws Exception {
    Ini.Section usersSection = ini.get("users");
    String activeDirectoryRealm = ini.getSectionProperty("main","activeDirectoryRealm");
    String ldapRealm = ini.getSectionProperty("main","ldapRealm");
    String pamRealm = ini.getSectionProperty("main","pamRealm");
    String zeppelinHubRealm = ini.getSectionProperty("main","zeppelinHubRealm");
    String knoxJwtRealm = ini.getSectionProperty("main","knoxJwtRealm");

    if (usersSection != null && (activeDirectoryRealm != null || ldapRealm != null
      || pamRealm != null || zeppelinHubRealm != null || knoxJwtRealm != null)) {
      throw new Exception(
        "IniRealm/password based auth mechanisms should be exclusive. "
          + "Consider removing [users] block from shiro.ini");
    }
  }
}
