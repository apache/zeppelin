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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weakref.jmx.MBeanExporter;

/**
 * JMX-backed Metrics manager
 * <p>
 * This class is used to instantiate "MBeans" - JMX POJOs you can call getters/setters on in order
 * to update metrics of all kinds.
 */
public class Metrics {
  private static Metrics instance;
  private final MBeanServer server;
  private MBeanExporter exporter;

  private Logger logger = LoggerFactory.getLogger(this.getClass());
  private Set<Class> validStatTypes = new HashSet<>();
  private Set<String> blacklistedAttributes = new HashSet<>();
  private final Map<MetricType, Stat> stats = new HashMap<>();
  private final Map<MetricType, Map<Exception, Stat>> failureStats = new HashMap<>();

  public static Metrics getInstance() {
    if (instance == null) {
      instance = new Metrics();
    }

    return instance;
  }

  protected Metrics() {
    server = ManagementFactory.getPlatformMBeanServer();
    exporter = new MBeanExporter(server);

    validStatTypes.addAll(Arrays.asList(
      Boolean.class,
      Long.class,
      Integer.class,
      String.class,
      Double.class));

    blacklistedAttributes.addAll(Arrays.asList("ClassPath", "BootClassPath", "LibraryPath"));

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

  /*
   * Get all stat values, with normalized names
   */
  public Map<String, Object> getAllStats() {
    Map<String, Object> stats = new HashMap<>();

    Set<ObjectInstance> objs = server.queryMBeans(null, null);
    for (ObjectInstance obj : objs) {
      try {
        MBeanAttributeInfo[] attrs = server.getMBeanInfo(obj.getObjectName()).getAttributes();
        for (MBeanAttributeInfo attr : attrs) {
          if (attrs[0].isReadable()) {
            Object value = server.getAttribute(obj.getObjectName(), attr.getName());
            if (shouldExpose(attr, value)) {
              stats.put(
                nameFor(obj, attr),
                value
              );
            }
          }
        }

      } catch (Exception e) {
        logger.error("Couldn't process MBean", e);
      }
    }

    return stats;
  }


  private boolean shouldExpose(MBeanAttributeInfo attr, Object value) {
    return !blacklistedAttributes.contains(attr.getName()) &&
      validStatTypes.contains(value.getClass());
  }

  private String nameFor(ObjectInstance obj, MBeanAttributeInfo attr) {
    return dashed(obj.getObjectName().getCanonicalName()) + "_" + dashed(attr.getName());
  }

  private String dashed(String name) {
    return name.replaceAll("[=:.,]", "_");
  }

  public void save(TimedExecution run) {
    stats.get(run.getMetricType()).record(
      System.currentTimeMillis() - run.getStart()
    );
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
