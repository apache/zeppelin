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

import java.io.IOException;
import java.net.URISyntaxException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.utils.CorsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cors filter.
 */
public class CorsFilter implements Filter {
  private static final Logger LOGGER = LoggerFactory.getLogger(CorsFilter.class);

  private final ZeppelinConfiguration zConf;

  public CorsFilter(ZeppelinConfiguration zConf) {
    this.zConf = zConf;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
      throws IOException, ServletException {
    String sourceHost = ((HttpServletRequest) request).getHeader("Origin");
    String origin = "";

    try {
      if (CorsUtils.isValidOrigin(sourceHost, zConf)) {
        origin = sourceHost;
      }
    } catch (URISyntaxException e) {
      LOGGER.error("Exception in WebDriverManager while getWebDriver ", e);
    }

    if (((HttpServletRequest) request).getMethod().equals("OPTIONS")) {
      HttpServletResponse resp = ((HttpServletResponse) response);
      addCorsHeaders(resp, origin);
      return;
    }

    if (response instanceof HttpServletResponse) {
      HttpServletResponse alteredResponse = ((HttpServletResponse) response);
      addCorsHeaders(alteredResponse, origin);
    }
    filterChain.doFilter(request, response);
  }

  private void addCorsHeaders(HttpServletResponse response, String origin) {
    response.setHeader("Access-Control-Allow-Origin", origin);
    response.setHeader("Access-Control-Allow-Credentials", "true");
    response.setHeader("Access-Control-Allow-Headers", "authorization,Content-Type");
    response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, PUT, HEAD, DELETE");

    response.setHeader("X-FRAME-OPTIONS", zConf.getXFrameOptions());
    if (zConf.useSsl()) {
      response.setHeader("Strict-Transport-Security", zConf.getStrictTransport());
    }
    response.setHeader("X-XSS-Protection", zConf.getXxssProtection());
    response.setHeader("X-Content-Type-Options", zConf.getXContentTypeOptions());
  }

  @Override
  public void destroy() {}

  @Override
  public void init(FilterConfig filterConfig) {}
}
