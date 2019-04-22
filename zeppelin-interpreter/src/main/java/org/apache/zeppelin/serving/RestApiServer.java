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

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;

/**
 * Provides an rest api endpoint in the interpreter process.
 * z.addRestApi() will add endpoint here.
 */
public class RestApiServer extends HttpServlet {
  private static final Logger LOGGER = LoggerFactory.getLogger(RestApiServer.class);
  public static int PORT;

  private Server server;

  private static RestApiServer singletonInstance;

  private final ConcurrentHashMap<String, RestApiHandler> endpoints = new ConcurrentHashMap();
  private final ConcurrentLinkedQueue<MetricStorage> metricStorages = new ConcurrentLinkedQueue<>();

  public RestApiServer() {
  }

  public static void setPort(int port) {
    PORT = port;
  }

  public static int getPort() {
    return PORT;
  }

  public static RestApiServer singleton() {
    if (singletonInstance == null) {
      singletonInstance = new RestApiServer();
    }

    return singletonInstance;
  }

  private synchronized void start() {
    if (server != null) {
      return;
    }

    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
    context.setContextPath("/");

    server = new Server(PORT);
    server.setHandler(context);

    context.addServlet(RestApiServlet.class, "/*");

    try {
      server.start();
    } catch (Exception e) {
      LOGGER.error("Can't start rest api server", e);
    }
  }

  public void shutdown() throws Exception {
    if (server != null) {
      server.stop();
    }
  }

  public void addEndpoint(String endpoint, RestApiHandler handler) {
    // start server if not started
    start();

    endpoints.put(endpoint, handler);
  }

  public RestApiHandler getEndpoint(String endpoint) {
    return endpoints.get(endpoint);
  }

  public ConcurrentLinkedQueue<MetricStorage> getMetricStorages() {
    return metricStorages;
  }

  public void addMetricStorage(MetricStorage storage) {
    metricStorages.add(storage);
  }
}
