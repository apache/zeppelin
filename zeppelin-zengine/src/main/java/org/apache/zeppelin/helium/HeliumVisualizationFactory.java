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
package org.apache.zeppelin.helium;

import com.github.eirslett.maven.plugins.frontend.lib.*;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Load helium visualization
 */
public class HeliumVisualizationFactory {
  Logger logger = LoggerFactory.getLogger(HeliumVisualizationFactory.class);
  private final String NODE_VERSION = "v6.9.1";
  private final String NPM_VERSION = "3.10.8";
  private final String DEFAULT_NPM_REGISTRY_URL = "http://registry.npmjs.org/";

  private final FrontendPluginFactory frontEndPluginFactory;
  private final File workingDirectory;
  private File tabledataModulePath;
  private File visualizationModulePath;

  String bundleCacheKey = "";
  File currentBundle;

  public HeliumVisualizationFactory(
      File moduleDownloadPath,
      File tabledataModulePath,
      File visualizationModulePath) throws InstallationException, TaskRunnerException {
    this(moduleDownloadPath);
    this.tabledataModulePath = tabledataModulePath;
    this.visualizationModulePath = visualizationModulePath;
  }

  public HeliumVisualizationFactory(File moduleDownloadPath)
      throws InstallationException, TaskRunnerException {
    this.workingDirectory = moduleDownloadPath;
    File installDirectory = moduleDownloadPath;

    frontEndPluginFactory = new FrontendPluginFactory(
        workingDirectory, installDirectory);

    currentBundle = new File(workingDirectory, "vis.bundle.cache.js");
    installNodeAndNpm();
  }

  private void installNodeAndNpm() throws InstallationException, TaskRunnerException {
    NPMInstaller npmInstaller = frontEndPluginFactory.getNPMInstaller(getProxyConfig());
    npmInstaller.setNpmVersion(NPM_VERSION);
    npmInstaller.install();

    NodeInstaller nodeInstaller = frontEndPluginFactory.getNodeInstaller(getProxyConfig());
    nodeInstaller.setNodeVersion(NODE_VERSION);
    nodeInstaller.install();
  }

  private ProxyConfig getProxyConfig() {
    List<ProxyConfig.Proxy> proxy = new LinkedList<>();
    return new ProxyConfig(proxy);
  }

  public File bundle(List<HeliumPackage> pkgs) throws IOException, TaskRunnerException {
    // package.json
    URL pkgUrl = Resources.getResource("helium/package.json");
    String pkgJson = Resources.toString(pkgUrl, Charsets.UTF_8);
    StringBuilder dependencies = new StringBuilder();

    for (HeliumPackage pkg : pkgs) {
      String[] moduleNameVersion = getNpmModuleNameAndVersion(pkg);
      if (moduleNameVersion == null) {
        logger.error("Can't get module name and version of package " + pkg.getName());
        continue;
      }
      if (dependencies.length() > 0) {
        dependencies.append(",\n");
      }
      dependencies.append("\"" + moduleNameVersion[0] + "\": \"" + moduleNameVersion[1] + "\"");

      if (isLocalPackage(pkg)) {
        FileUtils.copyDirectory(new File(pkg.getArtifact()),
            new File(workingDirectory, "node_modules/" + pkg.getName()));
      }
    }
    pkgJson = pkgJson.replaceFirst("DEPENDENCIES", dependencies.toString());

    // check if we can use previous bundle or not
    if (dependencies.toString().equals(bundleCacheKey) && currentBundle.isFile()) {
      return currentBundle;
    }

    // webpack.config.js
    URL webpackConfigUrl = Resources.getResource("helium/webpack.config.js");
    String webpackConfig = Resources.toString(webpackConfigUrl, Charsets.UTF_8);

    // generate load.js
    StringBuilder loadJsImport = new StringBuilder();
    StringBuilder loadJsRegister = new StringBuilder();

    for (HeliumPackage pkg : pkgs) {
      String [] moduleNameVersion = getNpmModuleNameAndVersion(pkg);
      if (moduleNameVersion == null) {
        continue;
      }
      loadJsImport.append(
          "import " + moduleNameVersion[0] + " from \"" + moduleNameVersion[0] + "\"\n");
      loadJsRegister.append("visualizations.push({" +
          "id: '" + moduleNameVersion[0] + "'," +
          "name: '" + pkg.getName() + "'," +
          "icon: '" + pkg.getIcon() + "'," +
          "class: " + moduleNameVersion[0] +
          "})\n");
    }

    FileUtils.write(new File(workingDirectory, "package.json"), pkgJson);
    FileUtils.write(new File(workingDirectory, "webpack.config.js"), webpackConfig);
    FileUtils.write(new File(workingDirectory, "load.js"), loadJsImport.append(loadJsRegister).toString());

    // install tabledata module
    File tabledataModuleInstallPath = new File(workingDirectory,
        "node_modules/zeppelin-tabledata");
    if (tabledataModulePath != null && !tabledataModuleInstallPath.exists()) {
      FileUtils.copyDirectory(tabledataModulePath, tabledataModuleInstallPath);
    }

    // install visualization module
    File visModuleInstallPath = new File(workingDirectory,
        "node_modules/zeppelin-vis");
    if (visualizationModulePath != null && !visModuleInstallPath.exists()) {
      FileUtils.copyDirectory(visualizationModulePath, visModuleInstallPath);
    }

    npmCommand("install");
    npmCommand("run bundle");

    File visBundleJs = new File(workingDirectory, "vis.bundle.js");
    if (!visBundleJs.isFile()) {
      throw new IOException("Failed to create visualization bundle");
    }

    synchronized (this) {
      currentBundle.delete();
      FileUtils.moveFile(visBundleJs, currentBundle);
      bundleCacheKey = dependencies.toString();
    }
    return currentBundle;
  }

  public File getCurrentBundle() {
    synchronized (this) {
      if (currentBundle.isFile()) {
        return currentBundle;
      } else {
        return null;
      }
    }
  }

  private boolean isLocalPackage(HeliumPackage pkg) {
    return (pkg.getArtifact().startsWith(".") || pkg.getArtifact().startsWith("/"));
  }

  private String [] getNpmModuleNameAndVersion(HeliumPackage pkg) {
    String artifact = pkg.getArtifact();

    if (isLocalPackage(pkg)) {
      File packageJson = new File(artifact, "package.json");
      if (!packageJson.isFile()) {
        return null;
      }
      Gson gson = new Gson();
      try {
        NpmPackage npmPackage = gson.fromJson(
            FileUtils.readFileToString(packageJson),
            NpmPackage.class);

        String[] nameVersion = new String[2];
        nameVersion[0] = npmPackage.name;
        nameVersion[1] = npmPackage.version;
        return nameVersion;
      } catch (IOException e) {
        logger.error(e.getMessage(), e);
        return null;
      }
    } else {
      String[] nameVersion = new String[2];

      int pos;
      if ((pos = artifact.indexOf('@')) > 0) {
        nameVersion[0] = artifact.substring(0, pos);
        nameVersion[1] = artifact.substring(pos + 1);
      } else if (
          (pos = artifact.indexOf('^')) > 0 ||
              (pos = artifact.indexOf('~')) > 0) {
        nameVersion[0] = artifact.substring(0, pos);
        nameVersion[1] = artifact.substring(pos);
      } else {
        nameVersion[0] = artifact;
        nameVersion[1] = "";
      }
      return nameVersion;
    }
  }

  public synchronized void install(HeliumPackage pkg) throws TaskRunnerException {
    npmCommand("install " + pkg.getArtifact());
  }

  private void npmCommand(String args) throws TaskRunnerException {
    npmCommand(args, new HashMap<String, String>());
  }
  private void npmCommand(String args, Map<String, String> env) throws TaskRunnerException {
    NpmRunner npm = frontEndPluginFactory.getNpmRunner(getProxyConfig(), DEFAULT_NPM_REGISTRY_URL);
    npm.execute(args, env);
  }
}
