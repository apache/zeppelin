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
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.utils.CorsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cors filter.
 */
public class CorsFilter implements Filter {
  private static final Logger LOGGER = LoggerFactory.getLogger(CorsFilter.class);

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
      throws IOException, ServletException {
    String sourceHost = ((HttpServletRequest) request).getHeader("Origin");
    String origin = "";

    try {
      if (CorsUtils.isValidOrigin(sourceHost, ZeppelinConfiguration.create())) {
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

    ZeppelinConfiguration zeppelinConfiguration = ZeppelinConfiguration.create();
    response.setHeader("X-FRAME-OPTIONS", zeppelinConfiguration.getXFrameOptions());
    if (zeppelinConfiguration.useSsl()) {
      response.setHeader("Strict-Transport-Security", zeppelinConfiguration.getStrictTransport());
    }
    response.setHeader("X-XSS-Protection", zeppelinConfiguration.getXxssProtection());
    response.setHeader("X-Content-Type-Options", zeppelinConfiguration.getXContentTypeOptions());
    response.setHeader("Cache-Control", zeppelinConfiguration.getCacheControl());
  }

  @Override
  public void destroy() {}

  @Override
  public void init(FilterConfig filterConfig) {}
}
