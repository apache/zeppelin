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
import org.apache.zeppelin.interpreter.launcher.InterpreterLauncher;
import org.apache.zeppelin.interpreter.launcher.SparkInterpreterLauncher;
import org.apache.zeppelin.interpreter.launcher.StandardInterpreterLauncher;
import org.apache.zeppelin.interpreter.recovery.RecoveryStorage;
import org.apache.zeppelin.interpreter.remote.ProcessLaunchObserver;
import org.apache.zeppelin.notebook.repo.GitNotebookRepo;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import jakarta.inject.Inject;

/**
 * Class for loading Plugins. It is singleton and factory class.
 *
 */
public class PluginManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(PluginManager.class);

  private final String pluginsDir;
  private final ZeppelinConfiguration zConf;

  private final Map<String, InterpreterLauncher> cachedLaunchers = new HashMap<>();
  private final Map<String, URLClassLoader> pluginClassLoaders = new HashMap<>();

  private List<String> builtinLauncherClassNames = Arrays.asList(
          StandardInterpreterLauncher.class.getName(),
          SparkInterpreterLauncher.class.getName());
  private List<String> builtinNotebookRepoClassNames = Arrays.asList(
          VFSNotebookRepo.class.getName(),
          GitNotebookRepo.class.getName());

  @Inject
  public PluginManager(ZeppelinConfiguration zConf) {
    pluginsDir = zConf.getPluginsDir();
    this.zConf = zConf;
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
      notebookRepo = withContextClassLoader(pluginClassLoader, () ->
          (NotebookRepo) Class.forName(notebookRepoClassName, true, pluginClassLoader)
              .getDeclaredConstructor()
              .newInstance());
    } catch (ReflectiveOperationException e) {
      throw new IOException("Fail to instantiate notebookrepo " + notebookRepoClassName +
          " from plugin classpath:" + pluginsDir, e);
    }

    return notebookRepo;
  }

  private String getOldNotebookRepoClassName(String notebookRepoClassName) {
    int pos = notebookRepoClassName.lastIndexOf(".");
    return notebookRepoClassName.substring(0, pos) + ".Old" + notebookRepoClassName.substring(pos + 1);
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
        InterpreterLauncher launcher = (InterpreterLauncher)
                (Class.forName(launcherClassName))
                        .getConstructor(ZeppelinConfiguration.class, RecoveryStorage.class)
                        .newInstance(zConf, recoveryStorage);
        configureProcessLaunchObservers(launcher);
        return launcher;
      } catch (InstantiationException | IllegalAccessException | ClassNotFoundException
              | NoSuchMethodException | InvocationTargetException e) {
        throw new IOException("Fail to instantiate InterpreterLauncher from classpath directly:"
                + launcherClassName, e);
      }
    }

    URLClassLoader pluginClassLoader = getPluginClassLoader(pluginsDir, "Launcher", launcherPlugin);
    InterpreterLauncher launcher = null;
    try {
      launcher = withContextClassLoader(pluginClassLoader, () ->
          (InterpreterLauncher) Class.forName(launcherClassName, true, pluginClassLoader)
              .getConstructor(ZeppelinConfiguration.class, RecoveryStorage.class)
              .newInstance(zConf, recoveryStorage));
    } catch (ReflectiveOperationException e) {
      throw new IOException("Fail to instantiate Launcher " + launcherPlugin +
              " from plugin pluginDir: " + pluginsDir, e);
    }

    configureProcessLaunchObservers(launcher);
    cachedLaunchers.put(launcherPlugin, launcher);
    return launcher;
  }

  private void configureProcessLaunchObservers(InterpreterLauncher launcher) throws IOException {
    if (launcher instanceof StandardInterpreterLauncher) {
      ((StandardInterpreterLauncher) launcher)
          .setProcessLaunchObservers(loadServiceProviders(ProcessLaunchObserver.class));
    }
  }

  private URLClassLoader getPluginClassLoader(String pluginsDir,
                                              String pluginType,
                                              String pluginName) throws IOException {

    File pluginFolder = new File(pluginsDir + "/" + pluginType + "/" + pluginName);
    return getPluginClassLoader(pluginFolder);
  }

  private synchronized URLClassLoader getPluginClassLoader(File pluginFolder) throws IOException {
    if (!pluginFolder.exists() || pluginFolder.isFile()) {
      LOGGER.warn("PluginFolder {} doesn't exist or is not a directory", pluginFolder.getAbsolutePath());
      return null;
    }
    String pluginFolderPath = pluginFolder.getCanonicalPath();
    if (pluginClassLoaders.containsKey(pluginFolderPath)) {
      return pluginClassLoaders.get(pluginFolderPath);
    }
    List<URL> urls = new ArrayList<>();
    File[] pluginFiles = pluginFolder.listFiles();
    if (pluginFiles != null) {
      for (File file : pluginFiles) {
        LOGGER.debug("Add file {} to classpath of plugin: {}",
            file.getAbsolutePath(), pluginFolder.getName());
        urls.add(file.toURI().toURL());
      }
    }
    if (urls.isEmpty()) {
      LOGGER.warn("Can not load plugin, because the plugin folder {} is empty.", pluginFolder);
      return null;
    }
    URLClassLoader classLoader = new URLClassLoader(
        urls.toArray(new URL[0]), PluginManager.class.getClassLoader());
    pluginClassLoaders.put(pluginFolderPath, classLoader);
    return classLoader;
  }

  /**
   * Load an extension class from any plugin directory.
   *
   * <p>This is used by configurable extension points such as ConfigStorage and RecoveryStorage,
   * whose implementation class name is stored in zeppelin-site.xml. The implementation and all
   * of its third-party dependencies stay in the plugin classloader.</p>
   */
  public Class<?> loadPluginClass(String className) throws IOException {
    try {
      return Class.forName(className, true, PluginManager.class.getClassLoader());
    } catch (ClassNotFoundException e) {
      File pluginFolder = findPluginFolder(className);
      if (pluginFolder == null) {
        throw new IOException("Unable to find plugin class: " + className, e);
      }
      try {
        URLClassLoader classLoader = getPluginClassLoader(pluginFolder);
        return withContextClassLoader(
            classLoader, () -> Class.forName(className, true, classLoader));
      } catch (ClassNotFoundException pluginError) {
        throw new IOException("Unable to load plugin class: " + className, pluginError);
      } catch (ReflectiveOperationException pluginError) {
        throw new IOException("Unable to initialize plugin class: " + className, pluginError);
      }
    }
  }

  public <T> T createPluginInstance(String className,
                                    Class<?>[] parameterTypes,
                                    Object[] parameters) throws IOException {
    try {
      Class<?> pluginClass = loadPluginClass(className);
      @SuppressWarnings("unchecked") T instance = withContextClassLoader(
          pluginClass.getClassLoader(),
          () -> (T) pluginClass.getConstructor(parameterTypes).newInstance(parameters));
      return instance;
    } catch (ReflectiveOperationException e) {
      throw new IOException("Unable to instantiate plugin class: " + className, e);
    }
  }

  /** Load service providers without adding their dependency jars to the server classpath. */
  public <T> List<T> loadServiceProviders(Class<T> serviceType) throws IOException {
    List<T> providers = new ArrayList<>();
    Set<String> providerClassNames = new LinkedHashSet<>();
    for (File pluginFolder : getPluginFolders()) {
      URLClassLoader classLoader = getPluginClassLoader(pluginFolder);
      if (classLoader == null) {
        continue;
      }
      Thread thread = Thread.currentThread();
      ClassLoader previousClassLoader = thread.getContextClassLoader();
      try {
        thread.setContextClassLoader(classLoader);
        for (T provider : ServiceLoader.load(serviceType, classLoader)) {
          if (provider.getClass().getClassLoader() == classLoader &&
              providerClassNames.add(provider.getClass().getName())) {
            providers.add(provider);
          }
        }
      } finally {
        thread.setContextClassLoader(previousClassLoader);
      }
    }
    return providers;
  }

  /** Return the isolated classpath containing a configured plugin class. */
  public List<File> getPluginClasspath(String className) throws IOException {
    File pluginFolder = findPluginFolder(className);
    if (pluginFolder == null) {
      return Collections.emptyList();
    }
    File[] files = pluginFolder.listFiles();
    if (files == null) {
      return Collections.emptyList();
    }
    return Arrays.asList(files);
  }

  private File findPluginFolder(String className) throws IOException {
    String classResource = className.replace('.', '/') + ".class";
    for (File pluginFolder : getPluginFolders()) {
      URLClassLoader classLoader = getPluginClassLoader(pluginFolder);
      if (classLoader != null && classLoader.findResource(classResource) != null) {
        return pluginFolder;
      }
    }
    return null;
  }

  private List<File> getPluginFolders() {
    File root = new File(pluginsDir);
    File[] pluginTypes = root.listFiles(File::isDirectory);
    if (pluginTypes == null) {
      return Collections.emptyList();
    }
    List<File> pluginFolders = new ArrayList<>();
    for (File pluginType : pluginTypes) {
      File[] folders = pluginType.listFiles(File::isDirectory);
      if (folders != null) {
        pluginFolders.addAll(Arrays.asList(folders));
      }
    }
    return pluginFolders;
  }

  @FunctionalInterface
  private interface ReflectiveAction<T> {
    T run() throws ReflectiveOperationException;
  }

  private static <T> T withContextClassLoader(
      ClassLoader classLoader, ReflectiveAction<T> action) throws ReflectiveOperationException {
    Thread thread = Thread.currentThread();
    ClassLoader previousClassLoader = thread.getContextClassLoader();
    try {
      thread.setContextClassLoader(classLoader);
      return action.run();
    } finally {
      thread.setContextClassLoader(previousClassLoader);
    }
  }
}
