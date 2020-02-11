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
import org.apache.zeppelin.interpreter.recovery.RecoveryStorage;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.OldNotebookRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class for loading Plugins
 */
public class PluginManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(PluginManager.class);

  private static PluginManager instance;

  private ZeppelinConfiguration zConf = ZeppelinConfiguration.create();
  private String pluginsDir = zConf.getPluginsDir();

  private Map<String, InterpreterLauncher> cachedLaunchers = new HashMap<>();

  public static synchronized PluginManager get() {
    if (instance == null) {
      instance = new PluginManager();
    }
    return instance;
  }

  public NotebookRepo loadNotebookRepo(String notebookRepoClassName) throws IOException {
    LOGGER.info("Loading NotebookRepo Plugin: " + notebookRepoClassName);
    // load plugin from classpath directly first for these builtin NotebookRepo (such as VFSNoteBookRepo
    // and GitNotebookRepo). If fails, then try to load it from plugin folder
    try {
      NotebookRepo notebookRepo = (NotebookRepo)
              (Class.forName(notebookRepoClassName).newInstance());
      return notebookRepo;
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
      LOGGER.warn("Fail to instantiate notebookrepo from classpath directly:" + notebookRepoClassName);
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
      LOGGER.warn("Fail to instantiate notebookrepo from plugin classpath:" + notebookRepoClassName, e);
    }

    if (notebookRepo == null) {
      LOGGER.warn("Unable to load NotebookRepo Plugin: " + notebookRepoClassName);
    }
    return notebookRepo;
  }

  private String getOldNotebookRepoClassName(String notebookRepoClassName) {
    int pos = notebookRepoClassName.lastIndexOf(".");
    return notebookRepoClassName.substring(0, pos) + ".Old" + notebookRepoClassName.substring(pos + 1);
  }

  /**
   * This is a temporary class which is used for loading old implemention of NotebookRepo.
   *
   * @param notebookRepoClassName
   * @return
   * @throws IOException
   */
  public OldNotebookRepo loadOldNotebookRepo(String notebookRepoClassName) throws IOException {
    String oldNotebookRepoClassName = getOldNotebookRepoClassName(notebookRepoClassName);
    LOGGER.info("Loading OldNotebookRepo Plugin: " + oldNotebookRepoClassName);
    // load plugin from classpath directly first for these builtin NotebookRepo (such as VFSNoteBookRepo
    // and GitNotebookRepo). If fails, then try to load it from plugin folder
    try {
      OldNotebookRepo notebookRepo = (OldNotebookRepo)
          (Class.forName(oldNotebookRepoClassName).newInstance());
      return notebookRepo;
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
      LOGGER.warn("Fail to instantiate notebookrepo from classpath directly:" + oldNotebookRepoClassName);
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
      LOGGER.warn("Fail to instantiate notebookrepo from plugin classpath:" + oldNotebookRepoClassName, e);
    }

    if (notebookRepo == null) {
      LOGGER.warn("Unable to load NotebookRepo Plugin: " + oldNotebookRepoClassName);
    }
    return notebookRepo;
  }

  public synchronized InterpreterLauncher loadInterpreterLauncher(String launcherPlugin,
                                                                  RecoveryStorage recoveryStorage)
      throws IOException {

    if (cachedLaunchers.containsKey(launcherPlugin)) {
      return cachedLaunchers.get(launcherPlugin);
    }
    LOGGER.info("Loading Interpreter Launcher Plugin: " + launcherPlugin);
    // load plugin from classpath directly first for these builtin InterpreterLauncher.
    // If fails, then try to load it from plugin folder.
    try {
      InterpreterLauncher launcher = (InterpreterLauncher)
              (Class.forName("org.apache.zeppelin.interpreter.launcher." + launcherPlugin))
                      .getConstructor(ZeppelinConfiguration.class, RecoveryStorage.class)
                      .newInstance(zConf, recoveryStorage);
      return launcher;
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException
            | NoSuchMethodException | InvocationTargetException e) {
      LOGGER.warn("Fail to instantiate InterpreterLauncher from classpath directly:" + launcherPlugin, e);
    }

    URLClassLoader pluginClassLoader = getPluginClassLoader(pluginsDir, "Launcher", launcherPlugin);
    String pluginClass = "org.apache.zeppelin.interpreter.launcher." + launcherPlugin;
    InterpreterLauncher launcher = null;
    try {
      launcher = (InterpreterLauncher) (Class.forName(pluginClass, true, pluginClassLoader))
          .getConstructor(ZeppelinConfiguration.class, RecoveryStorage.class)
          .newInstance(zConf, recoveryStorage);
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException
        | NoSuchMethodException | InvocationTargetException e) {
      LOGGER.warn("Fail to instantiate Launcher from plugin classpath:" + launcherPlugin, e);
    }

    if (launcher == null) {
      throw new IOException("Fail to load plugin: " + launcherPlugin);
    }
    cachedLaunchers.put(launcherPlugin, launcher);
    return launcher;
  }

  private URLClassLoader getPluginClassLoader(String pluginsDir,
                                              String pluginType,
                                              String pluginName) throws IOException {

    File pluginFolder = new File(pluginsDir + "/" + pluginType + "/" + pluginName);
    if (!pluginFolder.exists() || pluginFolder.isFile()) {
      LOGGER.warn("PluginFolder " + pluginFolder.getAbsolutePath() +
          " doesn't exist or is not a directory");
      return null;
    }
    List<URL> urls = new ArrayList<>();
    for (File file : pluginFolder.listFiles()) {
      LOGGER.debug("Add file " + file.getAbsolutePath() + " to classpath of plugin: "
          + pluginName);
      urls.add(file.toURI().toURL());
    }
    if (urls.isEmpty()) {
      LOGGER.warn("Can not load plugin " + pluginName +
          ", because the plugin folder " + pluginFolder + " is empty.");
      return null;
    }
    return new URLClassLoader(urls.toArray(new URL[0]));
  }

  @VisibleForTesting
  public static void reset() {
    instance = null;
  }
}
