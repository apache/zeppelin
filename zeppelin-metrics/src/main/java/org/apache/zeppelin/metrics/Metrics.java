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
import java.util.HashMap;
import java.util.Map;

import org.weakref.jmx.MBeanExporter;

/**
 * JMX-backed Metrics manager
 * <p>
 * This class is used to instantiate "MBeans" - JMX POJOs you can call getters/setters on in order
 * to update metrics of all kinds.
 */
public class Metrics {
  private static Metrics instance;
  private MBeanExporter exporter;

  private final Map<MetricType, Stat> stats = new HashMap<>();
  private final Map<MetricType, Map<Exception, Stat>> failureStats = new HashMap<>();

  public static Metrics getInstance() {
    if (instance == null) {
      instance = new Metrics();
    }

    return instance;
  }

  protected Metrics() {
    exporter = new MBeanExporter(ManagementFactory.getPlatformMBeanServer());

    MetricType[] timedMetrics = {
      MetricType.ParagraphRun,
      MetricType.NotebookRun,
      MetricType.NotebookCreate,
      MetricType.NotebookView,
    };
    for (MetricType timedMetric : timedMetrics) {
      stats.put(
        timedMetric,
        exported(
          timedMetric.name(),
          new TimedStat())
      );
    }

    MetricType[] countMetrics = {
    };
    for (MetricType countMetric : countMetrics) {
      stats.put(
        countMetric,
        exported(
          countMetric.name(),
          new CounterStat())
      );
    }

  }

  public Map<MetricType, Stat> getStats() {
    return stats;
  }

  public Map<MetricType, Map<Exception, Stat>> getFailureStats() {
    return failureStats;
  }

  public void increment(MetricType type) {
    stats.get(type).record(1);
  }

  public void save(TimedExecution run) {
    run.finish();
    stats.get(run.getMetricType()).record(run.getDuration());
  }

  public void saveFailure(Exception e, MetricType type) {
    Map<Exception, Stat> exceptions = failureStats.get(type);
    if (exceptions == null) {
      exceptions = new HashMap<>();
      failureStats.put(type, exceptions);
    }

    Stat stat = exceptions.get(e);
    if (stat == null) {
      stat = exported(
        type.name() + "_" + e.getClass().getSimpleName(),
        new FailureStat(e)
      );
      exceptions.put(e, stat);
    }

    stat.record(1);
  }

  public TimedExecution startMeasurement(MetricType type) {
    return new TimedExecution(type);
  }

  // "manage" a bean, so that changes to it will be saved
  private <T> T exported(String name, T bean) {
    exporter.export("org.apache.zeppelin.metrics:name=" + name, bean);
    return bean;
  }
}
