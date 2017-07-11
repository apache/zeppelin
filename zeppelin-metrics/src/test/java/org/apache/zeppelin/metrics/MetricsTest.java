package org.apache.zeppelin.metrics;

import java.lang.management.ManagementFactory;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
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

}
