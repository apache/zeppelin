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
import java.util.List;
import java.util.Locale;

import org.apache.zeppelin.conf.ZeppelinConfiguration;

public class CorsUtils {

  private CorsUtils() {
    // Helper Class
  }

  public static final String HEADER_ORIGIN = "Origin";
  public static boolean isValidOrigin(String sourceOrigin, ZeppelinConfiguration zConf)
      throws UnknownHostException, URISyntaxException {
    if (sourceOrigin == null || sourceOrigin.isEmpty()) {
      return false;
    }

    URI origin = parseOrigin(sourceOrigin);
    String canonicalOrigin = canonicalOrigin(origin);
    List<String> allowedOrigins = zConf.getAllowedOrigins();
    for (String allowedOrigin : allowedOrigins) {
      if ("*".equals(allowedOrigin.trim())) {
        return true;
      }
      try {
        if (canonicalOrigin.equals(canonicalOrigin(parseOrigin(allowedOrigin.trim())))) {
          return true;
        }
      } catch (URISyntaxException ignored) {
        // Ignore malformed configured entries instead of broadening the allowed set.
      }
    }

    // A configured allowlist is authoritative. Localhost is the secure convenience default only
    // when no origins were configured.
    if (!allowedOrigins.isEmpty()) {
      return false;
    }

    String expectedScheme = zConf.useSsl() ? "https" : "http";
    int expectedPort = zConf.useSsl() ? zConf.getServerSslPort() : zConf.getServerPort();
    if (!expectedScheme.equals(origin.getScheme().toLowerCase(Locale.ROOT))
        || effectivePort(origin) != expectedPort) {
      return false;
    }

    String sourceUriHost = origin.getHost().toLowerCase(Locale.ROOT);
    String currentHost = InetAddress.getLocalHost().getHostName().toLowerCase(Locale.ROOT);
    return currentHost.equals(sourceUriHost)
        || "localhost".equals(sourceUriHost)
        || "127.0.0.1".equals(sourceUriHost)
        || "[::1]".equals(sourceUriHost)
        || "::1".equals(sourceUriHost);
  }

  private static URI parseOrigin(String value) throws URISyntaxException {
    URI origin = new URI(value);
    String scheme = origin.getScheme();
    if (scheme == null
        || (!("http".equalsIgnoreCase(scheme)) && !("https".equalsIgnoreCase(scheme)))
        || origin.getHost() == null
        || origin.getUserInfo() != null
        || (origin.getRawPath() != null && !origin.getRawPath().isEmpty())
        || origin.getRawQuery() != null
        || origin.getRawFragment() != null) {
      throw new URISyntaxException(value, "Expected an HTTP Origin without path or credentials");
    }
    return origin;
  }

  private static String canonicalOrigin(URI origin) throws URISyntaxException {
    String scheme = origin.getScheme().toLowerCase(Locale.ROOT);
    int port = effectivePort(origin);
    int canonicalPort = ("http".equals(scheme) && port == 80)
        || ("https".equals(scheme) && port == 443) ? -1 : port;
    return new URI(
        scheme,
        null,
        origin.getHost().toLowerCase(Locale.ROOT),
        canonicalPort,
        null,
        null,
        null).toASCIIString();
  }

  private static int effectivePort(URI origin) {
    if (origin.getPort() >= 0) {
      return origin.getPort();
    }
    return "https".equalsIgnoreCase(origin.getScheme()) ? 443 : 80;
  }
}
