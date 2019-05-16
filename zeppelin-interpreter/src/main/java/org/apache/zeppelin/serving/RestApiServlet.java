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
package org.apache.zeppelin.serving;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet that handles request to rest api endpoint added by z.addRestApi()
 */
public class RestApiServlet extends HttpServlet {
  private static final Logger LOGGER = LoggerFactory.getLogger(RestApiServlet.class);

  @Override
  protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String path = request.getRequestURI().substring(request.getContextPath().length());

    if (path.length() == 0) {
      LOGGER.warn("Empty request path");
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    RestApiServer server = RestApiServer.singleton();
    String endpoint = getEndpointNameFromRequestPath(path);
    RestApiHandler handler = server.getEndpoint(endpoint);
    if (handler == null) {
      LOGGER.warn("Endpoint {} does not exists", endpoint);
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    long start = System.currentTimeMillis();
    handler.handle(request, response);
    long end = System.currentTimeMillis();

    try {
      writeMetrics(server, endpoint, request, response, end - start);
    } catch(Throwable e) {
      LOGGER.error("Failed to write metric", e);
    }
  }

  void writeMetrics(RestApiServer server,
                    String endpoint,
                    HttpServletRequest request,
                    HttpServletResponse response,
                    long elapsed) {
    // metrics
    String keyCountPerStatus = String.format("elapsed.%d", response.getStatus());
    ConcurrentLinkedQueue<MetricStorage> metricStorages = server.getMetricStorages();
    Date now = new Date();

    String metricHeaderPrefix = "z-metric-";

    metricStorages.forEach(m -> {
      m.add(now, endpoint, keyCountPerStatus, elapsed);

      Collection<String> headers = response.getHeaderNames();
      for (String header : headers) {
        if (header.toLowerCase().startsWith(metricHeaderPrefix)) {
          String userMetricName = header.substring(metricHeaderPrefix.length());
          try {
            double userMetricValue = Double.parseDouble(response.getHeader(header));
            m.add(now, endpoint, userMetricName, userMetricValue);
          } catch (Exception e) {
            LOGGER.error("Invalid custom metric value {}: {}", header, response.getHeader(header));
          }
        }
      }
    });
  }

  String getEndpointNameFromRequestPath(String path) {
    int start = 0;
    if (path.charAt(0) == '/') {
      start = 1;
    }

    int end = path.indexOf('/', start);
    if (end < 0) {
      end = path.length();
    }

    return path.substring(start, end);
  }
}
