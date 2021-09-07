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

import com.google.common.annotations.VisibleForTesting;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.launcher.InterpreterLauncher;
import org.apache.zeppelin.interpreter.launcher.SparkInterpreterLauncher;
import org.apache.zeppelin.interpreter.launcher.StandardInterpreterLauncher;
import org.apache.zeppelin.interpreter.recovery.RecoveryStorage;
import org.apache.zeppelin.notebook.repo.GitNotebookRepo;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.OldGitNotebookRepo;
import org.apache.zeppelin.notebook.repo.OldNotebookRepo;
import org.apache.zeppelin.notebook.repo.OldVFSNotebookRepo;
import org.apache.zeppelin.notebook.repo.VFSNotebookRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class for loading Plugins. It is singleton and factory class.
 *
 */
public class PluginManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(PluginManager.class);

  private static PluginManager instance;

  private ZeppelinConfiguration zConf = ZeppelinConfiguration.create();
  private String pluginsDir = zConf.getPluginsDir();

  private Map<String, InterpreterLauncher> cachedLaunchers = new HashMap<>();

  private List<String> builtinLauncherClassNames = Arrays.asList(
          StandardInterpreterLauncher.class.getName(),
          SparkInterpreterLauncher.class.getName());
  private List<String> builtinNotebookRepoClassNames = Arrays.asList(
          VFSNotebookRepo.class.getName(),
          GitNotebookRepo.class.getName());
  private List<String> builtinOldNotebookRepoClassNames = Arrays.asList(
          OldVFSNotebookRepo.class.getName(),
          OldGitNotebookRepo.class.getName());

  public static synchronized PluginManager get() {
    if (instance == null) {
      instance = new PluginManager();
    }
    return instance;
  }

  public NotebookRepo loadNotebookRepo(String notebookRepoClassName) throws IOException {
    LOGGER.info("Loading NotebookRepo Plugin: {}", notebookRepoClassName);
    if (builtinNotebookRepoClassNames.contains(notebookRepoClassName) ||
            Boolean.parseBoolean(System.getProperty("zeppelin.isTest", "false"))) {
      try {
        return (NotebookRepo) (Class.forName(notebookRepoClassName).newInstance());
      } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
        throw new IOException("Fail to instantiate notebookrepo from classpath directly:"
                + notebookRepoClassName, e);
      }
    }

    String simpleClassName = notebookRepoClassName.substring(notebookRepoClassName.lastIndexOf(".") + 1);
    URLClassLoader pluginClassLoader = getPluginClassLoader(pluginsDir, "NotebookRepo", simpleClassName);
    if (pluginClassLoader == null) {
      return null;
    }
    NotebookRepo notebookRepo = null;
    try {
      notebookRepo = (NotebookRepo) (Class.forName(notebookRepoClassName, true, pluginClassLoader)).newInstance();
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
      throw new IOException("Fail to instantiate notebookrepo " + notebookRepoClassName +
              " from plugin classpath:" + pluginsDir, e);
    }

    return notebookRepo;
  }

  private String getOldNotebookRepoClassName(String notebookRepoClassName) {
    int pos = notebookRepoClassName.lastIndexOf(".");
    return notebookRepoClassName.substring(0, pos) + ".Old" + notebookRepoClassName.substring(pos + 1);
  }

  /**
   * This is a temporary class which is used for loading old implementation of NotebookRepo.
   *
   * @param notebookRepoClassName
   * @return
   * @throws IOException
   */
  public OldNotebookRepo loadOldNotebookRepo(String notebookRepoClassName) throws IOException {
    String oldNotebookRepoClassName = getOldNotebookRepoClassName(notebookRepoClassName);
    LOGGER.info("Loading OldNotebookRepo Plugin: {}", oldNotebookRepoClassName);
    if (builtinOldNotebookRepoClassNames.contains(oldNotebookRepoClassName) ||
            Boolean.parseBoolean(System.getProperty("zeppelin.isTest", "false"))) {
      try {
        return (OldNotebookRepo) (Class.forName(oldNotebookRepoClassName).newInstance());
      } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
        throw new IOException("Fail to instantiate notebookrepo from classpath directly:"
                + oldNotebookRepoClassName);
      }
    }

    String simpleClassName = notebookRepoClassName.substring(notebookRepoClassName.lastIndexOf(".") + 1);
    URLClassLoader pluginClassLoader = getPluginClassLoader(pluginsDir, "NotebookRepo", simpleClassName);
    if (pluginClassLoader == null) {
      return null;
    }
    OldNotebookRepo notebookRepo = null;
    try {
      notebookRepo = (OldNotebookRepo) (Class.forName(oldNotebookRepoClassName, true, pluginClassLoader)).newInstance();
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
      throw new IOException("Fail to instantiate notebookrepo " + oldNotebookRepoClassName +
              " from plugin classpath:" + pluginsDir, e);
    }

    return notebookRepo;
  }

  public synchronized InterpreterLauncher loadInterpreterLauncher(String launcherPlugin,
                                                                  RecoveryStorage recoveryStorage)
      throws IOException {

    if (cachedLaunchers.containsKey(launcherPlugin)) {
      return cachedLaunchers.get(launcherPlugin);
    }
    String launcherClassName = "org.apache.zeppelin.interpreter.launcher." + launcherPlugin;
    LOGGER.info("Loading Interpreter Launcher Plugin: {}", launcherClassName);

    if (builtinLauncherClassNames.contains(launcherClassName) ||
            Boolean.parseBoolean(System.getProperty("zeppelin.isTest", "false"))) {
      try {
        return (InterpreterLauncher)
                (Class.forName(launcherClassName))
                        .getConstructor(ZeppelinConfiguration.class, RecoveryStorage.class)
                        .newInstance(zConf, recoveryStorage);
      } catch (InstantiationException | IllegalAccessException | ClassNotFoundException
              | NoSuchMethodException | InvocationTargetException e) {
        throw new IOException("Fail to instantiate InterpreterLauncher from classpath directly:"
                + launcherClassName, e);
      }
    }

    URLClassLoader pluginClassLoader = getPluginClassLoader(pluginsDir, "Launcher", launcherPlugin);
    InterpreterLauncher launcher = null;
    try {
      launcher = (InterpreterLauncher) (Class.forName(launcherClassName, true, pluginClassLoader))
          .getConstructor(ZeppelinConfiguration.class, RecoveryStorage.class)
          .newInstance(zConf, recoveryStorage);
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException
        | NoSuchMethodException | InvocationTargetException e) {
      throw new IOException("Fail to instantiate Launcher " + launcherPlugin +
              " from plugin pluginDir: " + pluginsDir, e);
    }

    cachedLaunchers.put(launcherPlugin, launcher);
    return launcher;
  }

  private URLClassLoader getPluginClassLoader(String pluginsDir,
                                              String pluginType,
                                              String pluginName) throws IOException {

    File pluginFolder = new File(pluginsDir + "/" + pluginType + "/" + pluginName);
    if (!pluginFolder.exists() || pluginFolder.isFile()) {
      LOGGER.warn("PluginFolder {} doesn't exist or is not a directory", pluginFolder.getAbsolutePath());
      return null;
    }
    List<URL> urls = new ArrayList<>();
    for (File file : pluginFolder.listFiles()) {
      LOGGER.debug("Add file {} to classpath of plugin: {}", file.getAbsolutePath(),  pluginName);
      urls.add(file.toURI().toURL());
    }
    if (urls.isEmpty()) {
      LOGGER.warn("Can not load plugin {}, because the plugin folder {} is empty.", pluginName , pluginFolder);
      return null;
    }
    return new URLClassLoader(urls.toArray(new URL[0]));
  }

  @VisibleForTesting
  public static void reset() {
    instance = null;
  }
}
