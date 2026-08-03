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
package org.apache.zeppelin.utils;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import org.apache.zeppelin.conf.ZeppelinConfiguration;

public class CorsUtils {

  private CorsUtils() {
    // Helper Class
  }

  public static final String HEADER_ORIGIN = "Origin";
  public static boolean isValidOrigin(String sourceHost, ZeppelinConfiguration zConf)
      throws UnknownHostException, URISyntaxException {
    if (sourceHost == null || sourceHost.isEmpty()) {
      return false;
    }

    URI origin = new URI(sourceHost);
    String originHost = origin.getHost();
    if (originHost == null
        || origin.getScheme() == null
        || origin.getUserInfo() != null
        || origin.getQuery() != null
        || origin.getFragment() != null
        || (origin.getPath() != null && !origin.getPath().isEmpty())) {
      return false;
    }

    String normalizedOrigin = sourceHost.toLowerCase(Locale.ROOT);
    if (zConf.getAllowedOrigins().contains("*")
        || zConf.getAllowedOrigins().contains(normalizedOrigin)) {
      return true;
    }
    if (!zConf.getAllowedOrigins().isEmpty()) {
      return false;
    }

    String expectedScheme = zConf.useSsl() ? "https" : "http";
    int expectedPort = zConf.useSsl() ? zConf.getServerSslPort() : zConf.getServerPort();
    int originPort = origin.getPort();
    if (originPort < 0) {
      originPort = "https".equalsIgnoreCase(origin.getScheme()) ? 443 : 80;
    }

    String normalizedHost = originHost.toLowerCase(Locale.ROOT);
    String currentHost = InetAddress.getLocalHost().getHostName().toLowerCase(Locale.ROOT);
    boolean localOrigin = currentHost.equals(normalizedHost)
        || "localhost".equals(normalizedHost)
        || "127.0.0.1".equals(normalizedHost)
        || "::1".equals(normalizedHost)
        || "[::1]".equals(normalizedHost);
    return localOrigin
        && expectedScheme.equalsIgnoreCase(origin.getScheme())
        && expectedPort == originPort;
  }
}
