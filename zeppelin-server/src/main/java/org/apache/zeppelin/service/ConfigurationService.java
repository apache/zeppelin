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

import javax.inject.Inject;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class ConfigurationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationService.class);
  private static ConfigurationService self;

  private ZeppelinConfiguration zConf;

  @Inject
  public ConfigurationService(ZeppelinConfiguration zConf) {
    this.zConf = zConf;
    ConfigurationService.self = this;
  }

  /**
   * This is a temporal trick to connect injected class to non-injected class like {@link
   * org.apache.zeppelin.socket.NotebookServer}. This will be removed after refactoring of Notebook*
   * classes.
   *
   * @return ConfigurationService
   */
  public static ConfigurationService getInstance() {
    if (null == ConfigurationService.self) {
      throw new IllegalStateException("ConfigurationService should be called after injection");
    } else {
      return ConfigurationService.self;
    }
  }

  public Map<String, String> getAllProperties(ServiceContext context,
                                              ServiceCallback<Map<String, String>> callback)
      throws IOException {
    Map<String, String> properties = zConf.dumpConfigurations(key ->
        !key.contains("password") &&
            !key.equals(ZeppelinConfiguration.ConfVars
                .ZEPPELIN_NOTEBOOK_AZURE_CONNECTION_STRING.getVarName()));
    callback.onSuccess(properties, context);
    return properties;
  }

  public Map<String, String> getPropertiesWithPrefix(String prefix,
                                                     ServiceContext context,
                                                     ServiceCallback<Map<String, String>> callback)
      throws IOException {
    Map<String, String> properties = zConf.dumpConfigurations(key ->
        !key.contains("password") &&
            !key.equals(ZeppelinConfiguration.ConfVars
                    .ZEPPELIN_NOTEBOOK_AZURE_CONNECTION_STRING
                    .getVarName()) &&
            key.startsWith(prefix));
    callback.onSuccess(properties, context);
    return properties;
  }
}
