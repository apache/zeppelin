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
import java.util.Map;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MetricsTest {

  @Test
  public void testExports() throws MalformedObjectNameException, IntrospectionException, InstanceNotFoundException, ReflectionException {
    MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
    Metrics m = Metrics.getInstance();

    // all metrics are exposed as MBeans
    for (MetricType type : MetricType.values()) {
      ObjectName name = ObjectName.getInstance("org.apache.zeppelin.metrics:name=" + type.name());
      assertTrue(mbeanServer.isRegistered(name));

      MBeanInfo info = mbeanServer.getMBeanInfo(name);
      assertEquals("org.apache.zeppelin.metrics.TimedStat", info.getClassName());
    }
  }

  @Test
  public void testGetAllStats() {
    Metrics m = Metrics.getInstance();

    // check if the stats contain some expected JVM & Zeppelin stats
    Map<String, Object> stats = m.getAllStats();
    assertTrue(stats.containsKey("java_lang_type_ClassLoading_TotalLoadedClassCount"));
    assertTrue(stats.containsKey("org_apache_zeppelin_metrics_name_NotebookCreate_P50Millis"));
    assertEquals(0.0, stats.get("org_apache_zeppelin_metrics_name_NotebookCreate_P50Millis"));

    // check that blacklisted metrics aren'd exposed
    assertFalse(stats.containsKey("java_lang_type_Runtime_ClassPath"));

    // only include primivites and strings
    for (Map.Entry<String, Object> stat : stats.entrySet()) {
      assertTrue(stat.getValue() instanceof String ||
        stat.getValue() instanceof Double ||
        stat.getValue() instanceof Long ||
        stat.getValue() instanceof Boolean ||
        stat.getValue() instanceof Integer);
    }
  }

}
