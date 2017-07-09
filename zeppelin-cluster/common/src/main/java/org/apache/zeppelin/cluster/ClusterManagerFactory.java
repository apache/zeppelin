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

import com.google.common.collect.Maps;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class handle to create and initializing actual clustermanagers. It uses reflection and
 * URLClassloader to load and make an object of cluster manager.
 */
public class ClusterManagerFactory {

  private static final Logger logger = LoggerFactory.getLogger(ClusterManagerFactory.class);
  private static final String CLUSTER_COMMON_DIR_NAME = "common";
  private static final String CLUSTER_CLASS_NAME_FILE = "clustermanager-class";

  private final List<String> clusterManagerList;
  private final String zeppelinHome;
  private final String defaultClusterKey;
  private final Map<String, ClusterManager> clusterManagerMap;

  private boolean initialized;

  public ClusterManagerFactory(List<String> clusterManagerList, String zeppelinHome,
      String defaultClusterKey) {
    this.clusterManagerList = clusterManagerList;
    this.zeppelinHome = zeppelinHome;
    this.defaultClusterKey = defaultClusterKey;
    this.clusterManagerMap = Maps.newHashMap();
    this.initialized = false;
  }

  public ClusterManager getClusterManager() {
    return getClusterManager(defaultClusterKey);
  }

  public ClusterManager getClusterManager(String key) {
    if (!initialized) {
      init();
    }

    if (null == key) {
      return getClusterManager();
    }

    if (!clusterManagerMap.containsKey(key)) {
      logger.info("Not supported. {}", key);
      return null;
    }

    return clusterManagerMap.get(key);
  }

  synchronized void init() {
    if (initialized) {
      return;
    }

    findAndRegisterClusterManager();

    initialized = true;
  }

  private void findAndRegisterClusterManager() {
    if (null == clusterManagerList) {
      return;
    }

    for (String clusterManagerClassName : clusterManagerList) {
      try {
        Class clazz = Class.forName(clusterManagerClassName);
        Object cm = clazz.getConstructor().newInstance();
        String name = ((ClusterManager) cm).getClusterManagerName();
        if (null == name || name.isEmpty()) {
          throw new IllegalArgumentException("Cluster manager is null:" + clusterManagerClassName);
        }
        if (!clusterManagerMap.containsKey(name)) {
          clusterManagerMap.put(name, (ClusterManager) cm);
        }
        logger.info("ClusterManager {} is loaded with class {}", name, clusterManagerClassName);
      } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException |
          NoSuchMethodException | InstantiationException | IllegalArgumentException e) {
        logger.error("Wrong cluster manager name: {}", clusterManagerClassName);
      }
    }
  }
}
