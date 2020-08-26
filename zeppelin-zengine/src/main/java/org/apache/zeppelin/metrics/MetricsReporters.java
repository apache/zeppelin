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
package org.apache.zeppelin.metrics;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;

class MetricsReporters {

  private MetricsReporters() {
    // Not used
  }

  private static final Logger log = LoggerFactory.getLogger(MetricsReporters.class);
  private static final ZeppelinConfiguration zconf = ZeppelinConfiguration.create();

  public static void startReporters(MetricRegistry registry) {

    if (StringUtils.isBlank(zconf.getMetricsGraphiteHost())) {
      log.info("Missing metrics reporter host skipping");
      return;
    }

    String graphiteHost = zconf.getMetricsGraphiteHost();
    int graphitePort = zconf.getMetricsGraphitePort();
    final Graphite graphite = new Graphite(new InetSocketAddress(graphiteHost, graphitePort));
    final GraphiteReporter reporter = GraphiteReporter.forRegistry(registry)
      .convertRatesTo(TimeUnit.MINUTES)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .filter(MetricFilter.ALL)
      .build(graphite);
    reporter.start(30, TimeUnit.SECONDS);
  }
}
