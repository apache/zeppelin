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

package org.apache.zeppelin.plugin;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.recovery.NullRecoveryStorage;
import org.apache.zeppelin.interpreter.recovery.RecoveryStorage;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.storage.ConfigStorage;
import org.apache.zeppelin.storage.LocalConfigStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Class for loading Plugins
 */
public class PluginManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(PluginManager.class);

  private static PluginManager instance;

  private ZeppelinConfiguration zConf = ZeppelinConfiguration.create();
  private String pluginsDir = zConf.getPluginsDir();

  public static synchronized PluginManager get() {
    if (instance == null) {
      instance = new PluginManager();
    }
    return instance;
  }

  public NotebookRepo loadNotebookRepo(String notebookRepoClassName) throws IOException {
    LOGGER.info("Loading NotebookRepo Plugin: " + notebookRepoClassName);
    // load plugin from classpath directly when it is test.
    // otherwise load it from plugin folder
    String isTest = System.getenv("IS_ZEPPELIN_TEST");
    if (isTest != null && isTest.equals("true")) {
      try {
        NotebookRepo notebookRepo = (NotebookRepo)
            (Class.forName(notebookRepoClassName).newInstance());
        notebookRepo.init(zConf);
        return notebookRepo;
      } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
        LOGGER.warn("Fail to instantiate notebookrepo:" + notebookRepoClassName, e);
        return null;
      }
    }

    return loadPlugin(NotebookRepo.class, "NotebookRepo", notebookRepoClassName);
  }

  private <T extends Plugin> T loadPlugin(Class<T> clazz, String pluginType, String pluginClassName)
      throws IOException {
    String simpleClassName = pluginClassName.substring(pluginClassName.lastIndexOf(".") + 1);
    File pluginFolder = new File(pluginsDir + "/" + pluginType + "/" + simpleClassName);
    if (!pluginFolder.exists() || pluginFolder.isFile()) {
      LOGGER.warn("pluginFolder " + pluginFolder.getAbsolutePath() +
          " doesn't exist or is not a directory");
      return null;
    }
    List<URL> urls = new ArrayList<>();
    for (File file : pluginFolder.listFiles()) {
      LOGGER.debug("Add file " + file.getAbsolutePath() + " to classpath of plugin "
          + pluginClassName);
      urls.add(file.toURI().toURL());
    }
    if (urls.isEmpty()) {
      LOGGER.warn("Can not load plugin " + pluginClassName +
          ", because the plugin folder " + pluginFolder + " is empty.");
      return null;
    }
    URLClassLoader classLoader = new URLClassLoader(urls.toArray(new URL[0]));
    Iterator<T> iter = ServiceLoader.load(clazz, classLoader).iterator();
    T plugin = iter.next();
    if (plugin == null) {
      LOGGER.warn("Unable to load Plugin: " + pluginClassName);
    }
    plugin.init(zConf);
    return plugin;
  }

  public ConfigStorage loadConfigStorage(String configStorageClassName) throws IOException {
    if (configStorageClassName.equals(LocalConfigStorage.class.getName())) {
      ConfigStorage configStorage = new LocalConfigStorage();
      configStorage.init(zConf);
      return configStorage;
    }
    return loadPlugin(ConfigStorage.class, "ConfigStorage", configStorageClassName);
  }

  public RecoveryStorage loadRecoveryStorage(String recoveryStorageClassName) throws IOException {
    if (recoveryStorageClassName.equals(NullRecoveryStorage.class.getName())) {
      NullRecoveryStorage recoveryStorage = new NullRecoveryStorage();
      recoveryStorage.init(zConf);
      return recoveryStorage;
    }
    return loadPlugin(RecoveryStorage.class, "RecoveryStorage", recoveryStorageClassName);
  }

  public static void main(String[] args) {
    System.out.println(LocalConfigStorage.class.getName());
  }
}
