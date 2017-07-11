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
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.zeppelin.metrics.FailureStat;
import org.apache.zeppelin.metrics.MetricType;
import org.apache.zeppelin.metrics.Metrics;
import org.apache.zeppelin.metrics.Stat;
import org.apache.zeppelin.metrics.TimedStat;
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
  public Response getCredentials(String message) throws
    IOException, IllegalArgumentException {
    return new JsonResponse(Status.OK, metricsMap()).build();
  }

  private Map<String, Number> metricsMap() {
    Map<String, Number> res = new HashMap<>();

    for (Map.Entry<MetricType, Stat> e : metrics.getStats().entrySet()) {
      Stat val = e.getValue();
      String metricName = metricName(e.getKey(), val);

      res.put(metricName + "_Count", val.getCount());

      if (val instanceof TimedStat) {
        TimedStat timed = (TimedStat) val;
        res.put(metricName + "_P99Millis", timed.getP99Millis());
        res.put(metricName + "_P50Millis", timed.getP50Millis());
        res.put(metricName + "_MaxMillis", timed.getMaxMillis());
        res.put(metricName + "_MeanMillis", timed.getMeanMillis());
      }
    }

    return res;
  }

  private String metricName(MetricType type, Stat stat) {
    if (stat instanceof FailureStat) {
      return type.name() + "_" + ((FailureStat) stat).getException().getClass().getSimpleName();
    }
    return type.name();
  }
}
