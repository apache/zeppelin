package org.apache.zeppelin.cluster;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ClusterManagerFactory {

  private static final Logger logger = LoggerFactory.getLogger(ClusterManagerFactory.class);
  private static final String CLUSTER_COMMON_DIR_NAME = "common";

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
    return clusterManagerMap.get(defaultClusterKey);
  }

  public ClusterManager getClusterManager(String key) {
    if (!initialized) {
      init();
    }
    if (!clusterManagerMap.containsKey(key)) {
      logger.info("Not supported. {}", key);
      return null;
    }

    return clusterManagerMap.get(key);
  }

  private void init() {
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
        for (Path childPath : Files
          .newDirectoryStream(Paths.get(p.toString(), "target"), new Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
              return entry.toString().endsWith(".jar");
            }
          })) {
          jars.add(childPath.toUri().toURL());
        }

        ClassLoader classLoader = new URLClassLoader(jars.toArray(new URL[0]),
          ClassLoader.getSystemClassLoader());
        try {
          Class clazz = classLoader.loadClass(ClusterManager.class.getName());
          Object cm = clazz.getConstructor().newInstance();
          logger.info("Class loaded. {}", clazz.getName());
          if (!clusterManagerMap.containsKey(key)) {
            clusterManagerMap.put(key, (ClusterManager) cm);
          }
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException
          | NoSuchMethodException | InvocationTargetException e) {
          // Do nothing
        }

      }
    } catch (IOException e) {
      logger.info("erroor while reading directory", e);
    }

    initialized = true;
  }
}
