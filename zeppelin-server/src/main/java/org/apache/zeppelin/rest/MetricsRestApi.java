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

package org.apache.zeppelin.rest;

import java.io.IOException;
import java.util.TreeMap;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.zeppelin.metrics.Metrics;
import org.apache.zeppelin.server.JsonResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Metrics Rest API
 */
@Path("/metrics")
@Produces("application/json")
public class MetricsRestApi {
  Logger logger = LoggerFactory.getLogger(MetricsRestApi.class);
  private Metrics metrics = Metrics.getInstance();

  public MetricsRestApi() {
  }

  /**
   * Get metrics list REST API
   *
   * @return JSON with status.OK
   * @throws IOException, IllegalArgumentException
   */
  @GET
  public Response getMetrics(String message) throws
    IOException, IllegalArgumentException {
    return new JsonResponse(Status.OK, new TreeMap(metrics.getAllStats())).build();
  }
}
