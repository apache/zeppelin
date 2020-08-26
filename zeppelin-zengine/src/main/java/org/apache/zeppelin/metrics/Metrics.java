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

import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;

import org.apache.zeppelin.metrics.healthcheck.DummyHealthCheck;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.jvm.BufferPoolMetricSet;
import com.codahale.metrics.jvm.CachedThreadStatesGaugeSet;
import com.codahale.metrics.jvm.ClassLoadingGaugeSet;
import com.codahale.metrics.jvm.FileDescriptorRatioGauge;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.JvmAttributeGaugeSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;

public class Metrics {

  private static final MetricRegistry metricRegistry;
  private static final HealthCheckRegistry healthcheckReadinessRegistry;
  private static final HealthCheckRegistry healthcheckLivenessRegistry;

  static {
    metricRegistry = new MetricRegistry();
    metricRegistry.register(MetricRegistry.name("jvm", "gc"), new GarbageCollectorMetricSet());
    metricRegistry.register(MetricRegistry.name("jvm", "threads"), new CachedThreadStatesGaugeSet(10, TimeUnit.SECONDS));
    metricRegistry.register(MetricRegistry.name("jvm", "memory"), new MemoryUsageGaugeSet());
    metricRegistry.register(MetricRegistry.name("jvm", "buffers"), new BufferPoolMetricSet(ManagementFactory.getPlatformMBeanServer()));
    metricRegistry.register(MetricRegistry.name("jvm", "fd_usage"), new FileDescriptorRatioGauge());
    metricRegistry.register(MetricRegistry.name("jvm", "classes"), new ClassLoadingGaugeSet());
    metricRegistry.register("jvm", new JvmAttributeGaugeSet());

    healthcheckReadinessRegistry = new HealthCheckRegistry();
    healthcheckReadinessRegistry.register("dummy", new DummyHealthCheck());
    // Maybe add the ThreadDeadlockHealthcheck
    // healthcheckReadinessRegistry.register("deadlock", new ThreadDeadlockHealthCheck());

    healthcheckLivenessRegistry = new HealthCheckRegistry();
    healthcheckLivenessRegistry.register("dummy", new DummyHealthCheck());
  }

  private Metrics() {
    // not used
  }

  public static MetricRegistry getMetricRegistry() {
    return metricRegistry;
  }

  public static HealthCheckRegistry getHealthCheckReadinessRegistry() {
    return healthcheckReadinessRegistry;
  }

  public static HealthCheckRegistry getHealthCheckLivenessRegistry() {
    return healthcheckLivenessRegistry;
  }

  public static Timer timer(String first, String... keys) {
    return metricRegistry.timer(MetricRegistry.name(first, keys));
  }

  public static Meter meter(String first, String... keys) {
    return metricRegistry.meter(MetricRegistry.name(first, keys));
  }

  public static Counter counter(String first, String... keys) {
    return metricRegistry.counter(MetricRegistry.name(first, keys));
  }
}
