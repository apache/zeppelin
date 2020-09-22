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
package org.apache.zeppelin.cluster;

import com.sun.management.OperatingSystemMXBean;
import org.apache.zeppelin.cluster.meta.ClusterMeta;
import org.apache.zeppelin.cluster.meta.ClusterMetaType;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.zeppelin.cluster.meta.ClusterMetaType.INTP_PROCESS_META;
import static org.apache.zeppelin.cluster.meta.ClusterMetaType.SERVER_META;

/**
 * cluster monitoring
 * 1. cluster monitoring is also used for zeppelin-Server and zeppelin Interperter,
 *    distinguish by member variable ClusterMetaType
 * 2. Report the average of the server resource CPU and MEMORY usage in the
 *    last few minutes to smooth the server's instantaneous peak
 * 3. checks the heartbeat timeout of the zeppelin-server and interperter processes
 */
public class ClusterMonitor {
  private static Logger LOGGER = LoggerFactory.getLogger(ClusterMonitor.class);

  // Whether the thread has started
  private static AtomicBoolean running = new AtomicBoolean(true);

  private ClusterManager clusterManager = null;

  // Save the CPU resource and MEmory usage of the server resources
  // in the last few minutes through the queue, and then average them through the queue.
  private Queue<UsageUtil> monitorUsageQueues = new LinkedList<>();
  private final int USAGE_QUEUE_LIMIT = 100; // queue length
  private int heartbeatInterval = 3000; // Heartbeat reporting interval（milliseconds）

  // The zeppelin-server leader checks the heartbeat timeout of
  // the zeppelin-server and zeppelin-interperter processes in the cluster metadata.
  // If this time is exceeded, the zeppelin-server and interperter processes
  // can have an exception and no heartbeat is reported.
  private int heartbeatTimeout = 9000;

  // Type of cluster monitoring object
  private ClusterMetaType clusterMetaType;

  // The key of the cluster monitoring object,
  // the name of the cluster when monitoring the zeppelin-server,
  // and the interperterGroupID when monitoring the interperter processes
  private String metaKey;

  public ClusterMonitor(ClusterManager clusterManagerServer) {
    this.clusterManager = clusterManagerServer;

    ZeppelinConfiguration zconf = ZeppelinConfiguration.create();
    heartbeatInterval = zconf.getClusterHeartbeatInterval();
    heartbeatTimeout = zconf.getClusterHeartbeatTimeout();

    if (heartbeatTimeout < heartbeatInterval) {
      LOGGER.error("Heartbeat timeout must be greater than heartbeat period.");
      heartbeatTimeout = heartbeatInterval * 3;
      LOGGER.info("Heartbeat timeout is modified to 3 times the heartbeat period.");
    }

    if (heartbeatTimeout < heartbeatInterval * 3) {
      LOGGER.warn("Heartbeat timeout recommended than 3 times the heartbeat period.");
    }
  }

  //
  public void start(ClusterMetaType clusterMetaType, String metaKey) {
    this.clusterMetaType = clusterMetaType;
    this.metaKey = metaKey;

    new Thread(new Runnable() {
      @Override
      public void run() {
        while (running.get()) {
          switch (clusterMetaType) {
            case SERVER_META:
              sendMachineUsage();
              checkHealthy();
              break;
            case INTP_PROCESS_META:
              sendHeartbeat();
              break;
            default:
              LOGGER.error("unknown cluster meta type:{}", clusterMetaType);
              break;
          }

          try {
            Thread.sleep(heartbeatInterval);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      }
    }).start();
  }

  public void shutdown() {
    running.set(false);
  }

  // Check the healthy of each service and interperter instance
  private void checkHealthy() {
    // only leader check cluster healthy
    if (!clusterManager.isClusterLeader()) {
      return;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("checkHealthy()");
    }

    LocalDateTime now = LocalDateTime.now();
    // check machine mate
    for (ClusterMetaType metaType : ClusterMetaType.values()) {
      Map<String, HashMap<String, Object>> clusterMeta
          = clusterManager.getClusterMeta(metaType, "");

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("clusterMeta : {}", clusterMeta);
      }

      for (Map.Entry<String, HashMap<String, Object>> entry : clusterMeta.entrySet()) {
        String key = entry.getKey();
        Map<String, Object> meta = entry.getValue();

        // Metadata that has been offline is not processed
        String status = (String) meta.get(ClusterMeta.STATUS);
        if (status.equals(ClusterMeta.OFFLINE_STATUS)) {
          continue;
        }

        Object heartbeat = meta.get(ClusterMeta.LATEST_HEARTBEAT);
        if (heartbeat instanceof LocalDateTime) {
          LocalDateTime dHeartbeat = (LocalDateTime) heartbeat;
          Duration duration = Duration.between(dHeartbeat, now);
          long timeInterval = duration.getSeconds() * 1000; // Convert to milliseconds
          if (timeInterval > heartbeatTimeout) {
            // Set the metadata for the heartbeat timeout to offline
            // Cannot delete metadata
            HashMap<String, Object> mapValues = new HashMap<>();
            mapValues.put(ClusterMeta.STATUS, ClusterMeta.OFFLINE_STATUS);
            clusterManager.putClusterMeta(metaType, key, mapValues);
            LOGGER.warn("offline heartbeat timeout[{}] meta[{}]", dHeartbeat, key);
          }
        } else {
          LOGGER.error("wrong data type");
        }
      }
    }
  }

  // The interpreter process sends a heartbeat to the cluster,
  // indicating that the process is still active.
  private void sendHeartbeat() {
    HashMap<String, Object> mapMonitorUtil = new HashMap<>();
    mapMonitorUtil.put(ClusterMeta.LATEST_HEARTBEAT, LocalDateTime.now());
    mapMonitorUtil.put(ClusterMeta.STATUS, ClusterMeta.ONLINE_STATUS);

    clusterManager.putClusterMeta(INTP_PROCESS_META, metaKey, mapMonitorUtil);
  }

  // send the usage of each service
  private void sendMachineUsage() {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("sendMachineUsage >>>");
    }

    // Limit queue size
    while (monitorUsageQueues.size() > USAGE_QUEUE_LIMIT) {
      monitorUsageQueues.poll();
    }
    UsageUtil monitorUtil = getMachineUsage();
    monitorUsageQueues.add(monitorUtil);

    UsageUtil avgMonitorUtil = new UsageUtil();
    for (UsageUtil monitor : monitorUsageQueues){
      avgMonitorUtil.memoryUsed += monitor.memoryUsed;
      avgMonitorUtil.memoryCapacity += monitor.memoryCapacity;
      avgMonitorUtil.cpuUsed += monitor.cpuUsed;
      avgMonitorUtil.cpuCapacity += monitor.cpuCapacity;
    }

    // Resource consumption average
    int queueSize = monitorUsageQueues.size();
    avgMonitorUtil.memoryUsed = avgMonitorUtil.memoryUsed / queueSize;
    avgMonitorUtil.memoryCapacity = avgMonitorUtil.memoryCapacity / queueSize;
    avgMonitorUtil.cpuUsed = avgMonitorUtil.cpuUsed / queueSize;
    avgMonitorUtil.cpuCapacity = avgMonitorUtil.cpuCapacity / queueSize;

    HashMap<String, Object> mapMonitorUtil = new HashMap<>();
    mapMonitorUtil.put(ClusterMeta.MEMORY_USED, avgMonitorUtil.memoryUsed);
    mapMonitorUtil.put(ClusterMeta.MEMORY_CAPACITY, avgMonitorUtil.memoryCapacity);
    mapMonitorUtil.put(ClusterMeta.CPU_USED, avgMonitorUtil.cpuUsed);
    mapMonitorUtil.put(ClusterMeta.CPU_CAPACITY, avgMonitorUtil.cpuCapacity);
    mapMonitorUtil.put(ClusterMeta.LATEST_HEARTBEAT, LocalDateTime.now());
    mapMonitorUtil.put(ClusterMeta.STATUS, ClusterMeta.ONLINE_STATUS);

    String clusterName = clusterManager.getClusterNodeName();
    clusterManager.putClusterMeta(SERVER_META, clusterName, mapMonitorUtil);
  }

  private UsageUtil getMachineUsage() {
    OperatingSystemMXBean operatingSystemMXBean
        = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

    // Returns the amount of free physical memory in bytes.
    long freePhysicalMemorySize = operatingSystemMXBean.getFreePhysicalMemorySize();

    // Returns the total amount of physical memory in bytes.
    long totalPhysicalMemorySize = operatingSystemMXBean.getTotalPhysicalMemorySize();

    // Returns the "recent cpu usage" for the whole system.
    double systemCpuLoad = operatingSystemMXBean.getSystemCpuLoad();

    int process = Runtime.getRuntime().availableProcessors();

    UsageUtil monitorUtil = new UsageUtil();
    monitorUtil.memoryUsed = totalPhysicalMemorySize - freePhysicalMemorySize;
    monitorUtil.memoryCapacity = totalPhysicalMemorySize;
    monitorUtil.cpuUsed = (long) (process * systemCpuLoad * 100);
    monitorUtil.cpuCapacity = process * 100;

    return monitorUtil;
  }

  private class UsageUtil {
    private long memoryUsed = 0;
    private long memoryCapacity = 0;
    private long cpuUsed = 0;
    private long cpuCapacity = 0;
  }
}
