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
package org.apache.zeppelin.metric;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.exporter.common.TextFormat;

public class PrometheusServlet extends HttpServlet{

  private static final Logger LOGGER = LoggerFactory.getLogger(PrometheusServlet.class);
  /**
   *
   */
  private static final long serialVersionUID = 3954804532706721368L;

  private final PrometheusMeterRegistry promMetricRegistry;

  public PrometheusServlet(PrometheusMeterRegistry promMetricRegistry) {
    this.promMetricRegistry = promMetricRegistry;
  }

  private static final String CACHE_CONTROL = "Cache-Control";
  private static final String NO_CACHE = "must-revalidate,no-cache,no-store";
  @Override
  protected void doGet(HttpServletRequest req,
                       HttpServletResponse resp) throws ServletException, IOException {
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setHeader(CACHE_CONTROL, NO_CACHE);
    resp.setContentType(TextFormat.CONTENT_TYPE_004);
    try (PrintWriter writer = resp.getWriter()) {
      promMetricRegistry.scrape(writer);
    } catch (IOException e){
      LOGGER.error("IOException in PrometheusServlet", e);
    }
  }
}
