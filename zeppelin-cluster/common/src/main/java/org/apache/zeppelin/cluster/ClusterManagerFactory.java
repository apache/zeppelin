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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
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

  private final String zeppelinHome;
  private final String defaultClusterKey;
  private final Map<String, ClusterManager> clusterManagerMap;

  private boolean initialized;

  public ClusterManagerFactory(String zeppelinHome, String defaultClusterKey) {
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
    // Find clusters inside source tree
    logger.info("ZeppelinHome: {}", Paths.get(zeppelinHome).toAbsolutePath().toString());
    try {
      for (Path p : Files
          .newDirectoryStream(Paths.get(zeppelinHome, "zeppelin-cluster"), new Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
              return !entry.toString().endsWith(CLUSTER_COMMON_DIR_NAME);
            }
          })) {
        String key = p.getFileName().toString();
        List<URL> jars = Lists.newArrayList();
        addJarsFromPath(Paths.get(p.toString(), "target"), jars);
        addJarsFromPath(Paths.get(p.toString(), "target", "lib"), jars);

        registerClusterManager(key, jars);
      }

      for (Path p : Files
          .newDirectoryStream(Paths.get(zeppelinHome, "lib", "cluster"), new Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
              return !entry.toString().endsWith(CLUSTER_COMMON_DIR_NAME);
            }
          })) {
        String key = p.getFileName().toString();
        List<URL> jars = Lists.newArrayList();
        addJarsFromPath(p, jars);

        registerClusterManager(key, jars);
      }
    } catch (IOException e) {
      logger.debug("error while reading directory", e);
    }

    initialized = true;
  }

  private void registerClusterManager(String key, List<URL> jars) {
    ClassLoader parentClassLoader = ClassLoader.getSystemClassLoader();
    ClassLoader classLoader = new URLClassLoader(jars.toArray(new URL[0]), parentClassLoader);
    String className = null;

    try (InputStream classNameInputstream =
             classLoader.getResourceAsStream(CLUSTER_CLASS_NAME_FILE);
         StringWriter classNameWriter = new StringWriter()) {
      if (null != classNameInputstream) {
        IOUtils.copy(classNameInputstream, classNameWriter);
        className = classNameWriter.toString();
        Class clazz = classLoader.loadClass(className);
        Object cm = clazz.getConstructor().newInstance();
        logger.info("Class loaded. {}", clazz.getName());
        if (!clusterManagerMap.containsKey(key)) {
          clusterManagerMap.put(key, (ClusterManager) cm);
        }
      } else {
        logger.debug("No resources {} in {}", CLUSTER_COMMON_DIR_NAME, jars);
      }
    } catch (ClassNotFoundException | IllegalAccessException | InstantiationException
        | NoSuchMethodException | InvocationTargetException | IOException e) {
      if (null != className) {
        logger.error("class name is defined {}, but witch doesn't exist", className);
      }
    } catch (NoClassDefFoundError e) {
      logger.info("{} exists but insufficient dependencies. jars: {}", className, jars);
    }
  }

  public void addJarsFromPath(Path dir, List<URL> jars) {
    try {
      for (Path p : Files.newDirectoryStream(dir, new Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
          return entry.toString().endsWith(".jar");
        }
      })) {
        jars.add(p.toUri().toURL());
      }
    } catch (IOException e) {
      logger.error("Error occurs while reading {}", dir.toAbsolutePath().toString());
    }
  }
}
