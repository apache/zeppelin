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
package org.apache.zeppelin.server;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates given host name is allowed 
 */
public class OriginValidator {
  Logger logger = LoggerFactory.getLogger(OriginValidator.class);

  private static OriginValidator singletonInstance;
  private ZeppelinConfiguration conf;
  private final List<String> allowedOrigins;
  private final String ALLOW_ALL = "*";

  public OriginValidator(ZeppelinConfiguration conf) {
    this.conf = conf;
    allowedOrigins = new LinkedList<String>();
    initAllowedOrigins();
    singletonInstance = this;
  }
  
  /**
   * 
   * @param origin origin to check
   * @return
   */
  public boolean validate(String origin) {
    try {
      // just get host if origin is form of URI
      URI sourceUri = new URI(origin);
      String sourceHost = sourceUri.getHost();
      if (sourceHost != null && !sourceHost.isEmpty()) {
        origin = sourceHost;
      }
    } catch (URISyntaxException e) {
      // we can silently ignore this error
    }
    
    if (origin == null) {
      return false;
    }

    for (String p : allowedOrigins) {
      if (p == null || p.trim().length() == 0) {
        continue;
      }
      if (p.trim().compareToIgnoreCase(origin) == 0 || p.trim().equals(ALLOW_ALL)) {
        return true;
      }      
    }
    return false;
  }
  
  private void initAllowedOrigins() {
    String currentHost;
    try {
      currentHost = java.net.InetAddress.getLocalHost().getHostName();
      allowedOrigins.add(currentHost);
    } catch (UnknownHostException e) {
      logger.error("Can't get hostname", e);
    }
    
    
    String origins = conf.getString(ConfVars.ZEPPELIN_SERVER_ORIGINS);
    if (origins == null || origins.length() == 0) {
      return;
    } else {
      for (String origin : origins.split(",")) {
        allowedOrigins.add(origin);
      }
    }
  }

  public static OriginValidator singleton() {
    return singletonInstance;
  }
}
