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
import org.apache.zeppelin.interpreter.recovery.RecoveryStorage;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.inject.Inject;

/**
 * Class for loading Plugins
 */
public class ZPluginManager implements IPluginManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(ZPluginManager.class);

  private final ZeppelinConfiguration zConf;
  private final PluginManager pluginManager;

  @Inject
  public ZPluginManager(ZeppelinConfiguration zConf) {
    this.zConf = zConf;
    Path plugin = Paths.get(zConf.getPluginsDir());
    LOGGER.info("Plugin-Dir: {}", plugin);
    this.pluginManager = new DefaultPluginManager(plugin);
  }

  @Override
  public void loadAndStartPlugins() {
    pluginManager.loadPlugins();
    pluginManager.startPlugins();
    LOGGER.info("InterpreterLauncher:");
    List<InterpreterLauncher> interpreterLaunchers = pluginManager.getExtensions(InterpreterLauncher.class);
    for (InterpreterLauncher interpreterLauncher : interpreterLaunchers) {
      LOGGER.info(interpreterLauncher.getClass().getSimpleName());
    }
    LOGGER.info("NotebookRepo:");
    List<NotebookRepo> notebookRepos = pluginManager.getExtensions(NotebookRepo.class);
    for (NotebookRepo notebookRepo : notebookRepos) {
      LOGGER.info(notebookRepo.getClass().getName());
    }
  }

  @Override
  public NotebookRepo createNotebookRepo(String notebookRepoClassName) throws IOException {
    LOGGER.info("Loading NotebookRepo Plugin: {}", notebookRepoClassName);
    List<NotebookRepo> notebookRepos = pluginManager.getExtensions(NotebookRepo.class);
    for (NotebookRepo notebookRepo : notebookRepos) {
      if (notebookRepoClassName.equals(notebookRepo.getClass().getName())) {
        return notebookRepo;
      }
    }
    throw new IOException("Fail to instantiate notebookrepo " + notebookRepoClassName + " from plugin");
  }

  @Override
  public synchronized InterpreterLauncher createInterpreterLauncher(String launcherPlugin,
                                                                  RecoveryStorage recoveryStorage)
      throws IOException {
    LOGGER.info("Loading launcher Plugin: {}", launcherPlugin);
    List<InterpreterLauncher> interpreterLaunchers = pluginManager.getExtensions(InterpreterLauncher.class);
    for (InterpreterLauncher interpreterLauncher : interpreterLaunchers) {
      if (launcherPlugin.equals(interpreterLauncher.getClass().getSimpleName())) {
        interpreterLauncher.init(zConf, recoveryStorage);
        return interpreterLauncher;
      }
    }
    throw new IOException("Fail to instantiate launcher " + launcherPlugin + " from plugin");
  }

}
