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
import org.apache.log4j.Appender;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LoggingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.util.*;

/**
 * Load helium visualization
 */
public class HeliumBundleFactory {
  Logger logger = LoggerFactory.getLogger(HeliumBundleFactory.class);
  private final String NODE_VERSION = "v6.9.1";
  private final String NPM_VERSION = "3.10.8";
  private final String DEFAULT_NPM_REGISTRY_URL = "http://registry.npmjs.org/";
  public static final String HELIUM_LOCAL_REPO = "helium-bundle";
  public static final String HELIUM_BUNDLE_CACHE = "helium.bundle.cache.js";
  public static final String HELIUM_BUNDLE = "helium.bundle.js";
  public static final String HELIUM_BUNDLES_VAR = "heliumBundles";
  private final int FETCH_RETRY_COUNT = 2;
  private final int FETCH_RETRY_FACTOR_COUNT = 1;
  // Milliseconds
  private final int FETCH_RETRY_MIN_TIMEOUT = 5000;

  private final FrontendPluginFactory frontEndPluginFactory;
  private final File workingDirectory;
  private File tabledataModulePath;
  private File visualizationModulePath;
  private File spellModulePath;
  private Gson gson;

  String bundleCacheKey = "";
  File currentCacheBundle;

  ByteArrayOutputStream out  = new ByteArrayOutputStream();

  public HeliumBundleFactory(
      File moduleDownloadPath,
      File tabledataModulePath,
      File visualizationModulePath,
      File spellModulePath) throws TaskRunnerException {
    this(moduleDownloadPath);
    this.tabledataModulePath = tabledataModulePath;
    this.visualizationModulePath = visualizationModulePath;
    this.spellModulePath = spellModulePath;
  }

  public HeliumBundleFactory(File moduleDownloadPath) throws TaskRunnerException {
    this.workingDirectory = new File(moduleDownloadPath, HELIUM_LOCAL_REPO);
    File installDirectory = workingDirectory;

    frontEndPluginFactory = new FrontendPluginFactory(
        workingDirectory, installDirectory);

    currentCacheBundle = new File(workingDirectory, HELIUM_BUNDLE_CACHE);
    gson = new Gson();
    installNodeAndNpm();
    configureLogger();
  }

  private void installNodeAndNpm() {
    try {
      NPMInstaller npmInstaller = frontEndPluginFactory.getNPMInstaller(getProxyConfig());
      npmInstaller.setNpmVersion(NPM_VERSION);
      npmInstaller.install();

      NodeInstaller nodeInstaller = frontEndPluginFactory.getNodeInstaller(getProxyConfig());
      nodeInstaller.setNodeVersion(NODE_VERSION);
      nodeInstaller.install();
    } catch (InstallationException e) {
      logger.error(e.getMessage(), e);
    }
  }

  private ProxyConfig getProxyConfig() {
    List<ProxyConfig.Proxy> proxy = new LinkedList<>();
    return new ProxyConfig(proxy);
  }

  public File buildBundle(List<HeliumPackage> pkgs) throws IOException {
    return buildBundle(pkgs, false);
  }

  public synchronized File buildBundle(List<HeliumPackage> pkgs, boolean forceRefresh)
      throws IOException {
    // package.json
    URL pkgUrl = Resources.getResource("helium/package.json");
    String pkgJson = Resources.toString(pkgUrl, Charsets.UTF_8);
    StringBuilder dependencies = new StringBuilder();
    StringBuilder cacheKeyBuilder = new StringBuilder();

    FileFilter npmPackageCopyFilter = new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        String fileName = pathname.getName();
        if (fileName.startsWith(".") || fileName.startsWith("#") || fileName.startsWith("~")) {
          return false;
        } else {
          return true;
        }
      }
    };

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
      cacheKeyBuilder.append(pkg.getName() + pkg.getArtifact());

      File pkgInstallDir = new File(workingDirectory, "node_modules/" + pkg.getName());
      if (pkgInstallDir.exists()) {
        FileUtils.deleteDirectory(pkgInstallDir);
      }

      if (isLocalPackage(pkg)) {
        FileUtils.copyDirectory(
            new File(pkg.getArtifact()),
            pkgInstallDir,
            npmPackageCopyFilter);
      }
    }
    pkgJson = pkgJson.replaceFirst("DEPENDENCIES", dependencies.toString());

    // check if we can use previous buildBundle or not
    if (cacheKeyBuilder.toString().equals(bundleCacheKey) &&
        currentCacheBundle.isFile() && !forceRefresh) {
      return currentCacheBundle;
    }

    // webpack.config.js
    URL webpackConfigUrl = Resources.getResource("helium/webpack.config.js");
    String webpackConfig = Resources.toString(webpackConfigUrl, Charsets.UTF_8);

    // generate load.js
    StringBuilder loadJsImport = new StringBuilder();
    StringBuilder loadJsRegister = new StringBuilder();

    long idx = 0;
    for (HeliumPackage pkg : pkgs) {
      String[] moduleNameVersion = getNpmModuleNameAndVersion(pkg);
      if (moduleNameVersion == null) {
        continue;
      }

      String className = "bundles" + idx++;
      loadJsImport.append(
          "import " + className + " from \"" + moduleNameVersion[0] + "\"\n");

      loadJsRegister.append(HELIUM_BUNDLES_VAR + ".push({\n");
      loadJsRegister.append("id: \"" + moduleNameVersion[0] + "\",\n");
      loadJsRegister.append("name: \"" + pkg.getName() + "\",\n");
      loadJsRegister.append("icon: " + gson.toJson(pkg.getIcon()) + ",\n");
      loadJsRegister.append("type: \"" + pkg.getType() + "\",\n");
      loadJsRegister.append("class: " + className + "\n");
      loadJsRegister.append("})\n");
    }

    FileUtils.write(new File(workingDirectory, "package.json"), pkgJson);
    FileUtils.write(new File(workingDirectory, "webpack.config.js"), webpackConfig);
    FileUtils.write(new File(workingDirectory, "load.js"),
        loadJsImport.append(loadJsRegister).toString());

    copyFrameworkModuleToInstallPath(npmPackageCopyFilter);

    try {
      out.reset();
      String commandForNpmInstall =
              String.format("install --fetch-retries=%d --fetch-retry-factor=%d " +
                              "--fetch-retry-mintimeout=%d",
                      FETCH_RETRY_COUNT, FETCH_RETRY_FACTOR_COUNT, FETCH_RETRY_MIN_TIMEOUT);
      npmCommand(commandForNpmInstall);
    } catch (TaskRunnerException e) {
      // ignore `(empty)` warning
      String cause = new String(out.toByteArray());
      if (!cause.contains("(empty)")) {
        throw new IOException(cause);
      }
    }

    try {
      out.reset();
      npmCommand("run bundle");
    } catch (TaskRunnerException e) {
      throw new IOException(new String(out.toByteArray()));
    }

    String bundleStdoutResult = new String(out.toByteArray());

    File heliumBundle = new File(workingDirectory, HELIUM_BUNDLE);
    if (!heliumBundle.isFile()) {
      throw new IOException(
          "Can't create bundle: \n" + bundleStdoutResult);
    }

    WebpackResult result = getWebpackResultFromOutput(bundleStdoutResult);
    if (result.errors.length > 0) {
      heliumBundle.delete();
      throw new IOException(result.errors[0]);
    }

    synchronized (this) {
      currentCacheBundle.delete();
      FileUtils.moveFile(heliumBundle, currentCacheBundle);
      bundleCacheKey = cacheKeyBuilder.toString();
    }
    return currentCacheBundle;
  }

  private void copyFrameworkModuleToInstallPath(FileFilter npmPackageCopyFilter)
      throws IOException {
    // install tabledata module
    File tabledataModuleInstallPath = new File(workingDirectory,
        "node_modules/zeppelin-tabledata");
    if (tabledataModulePath != null) {
      if (tabledataModuleInstallPath.exists()) {
        FileUtils.deleteDirectory(tabledataModuleInstallPath);
      }
      FileUtils.copyDirectory(
          tabledataModulePath,
          tabledataModuleInstallPath,
          npmPackageCopyFilter);
    }

    // install visualization module
    File visModuleInstallPath = new File(workingDirectory,
        "node_modules/zeppelin-vis");
    if (visualizationModulePath != null) {
      if (visModuleInstallPath.exists()) {
        // when zeppelin-vis and zeppelin-table package is published to npm repository
        // we don't need to remove module because npm install command will take care
        // dependency version change. However, when two dependencies are copied manually
        // into node_modules directory, changing vis package version results inconsistent npm
        // install behavior.
        //
        // Remote vis package everytime and let npm download every time bundle as a workaround
        FileUtils.deleteDirectory(visModuleInstallPath);
      }
      FileUtils.copyDirectory(visualizationModulePath, visModuleInstallPath, npmPackageCopyFilter);
    }

    // install spell module
    File spellModuleInstallPath = new File(workingDirectory,
        "node_modules/zeppelin-spell");
    if (spellModulePath != null) {
      if (spellModuleInstallPath.exists()) {
        FileUtils.deleteDirectory(spellModuleInstallPath);
      }

      FileUtils.copyDirectory(
          spellModulePath,
          spellModuleInstallPath,
          npmPackageCopyFilter);
    }
  }

  private WebpackResult getWebpackResultFromOutput(String output) {
    BufferedReader reader = new BufferedReader(new StringReader(output));

    String line;
    boolean webpackRunDetected = false;
    boolean resultJsonDetected = false;
    StringBuffer sb = new StringBuffer();
    try {
      while ((line = reader.readLine()) != null) {
        if (!webpackRunDetected) {
          if (line.contains("webpack.js") && line.endsWith("--json")) {
            webpackRunDetected = true;
          }
          continue;
        }

        if (!resultJsonDetected) {
          if (line.equals("{")) {
            sb.append(line);
            resultJsonDetected = true;
          }
          continue;
        }

        if (resultJsonDetected && webpackRunDetected) {
          sb.append(line);
        }
      }

      Gson gson = new Gson();
      return gson.fromJson(sb.toString(), WebpackResult.class);
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
      return new WebpackResult();
    }
  }

  public File getCurrentCacheBundle() {
    synchronized (this) {
      if (currentCacheBundle.isFile()) {
        return currentCacheBundle;
      } else {
        return null;
      }
    }
  }

  private boolean isLocalPackage(HeliumPackage pkg) {
    return (pkg.getArtifact().startsWith(".") || pkg.getArtifact().startsWith("/"));
  }

  private String[] getNpmModuleNameAndVersion(HeliumPackage pkg) {
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
    String commandForNpmInstallArtifact =
        String.format("install %s --fetch-retries=%d --fetch-retry-factor=%d " +
                        "--fetch-retry-mintimeout=%d", pkg.getArtifact(),
                FETCH_RETRY_COUNT, FETCH_RETRY_FACTOR_COUNT, FETCH_RETRY_MIN_TIMEOUT);
    npmCommand(commandForNpmInstallArtifact);
  }

  private void npmCommand(String args) throws TaskRunnerException {
    npmCommand(args, new HashMap<String, String>());
  }

  private void npmCommand(String args, Map<String, String> env) throws TaskRunnerException {
    NpmRunner npm = frontEndPluginFactory.getNpmRunner(getProxyConfig(), DEFAULT_NPM_REGISTRY_URL);

    npm.execute(args, env);
  }

  private void configureLogger() {
    org.apache.log4j.Logger npmLogger = org.apache.log4j.Logger.getLogger(
        "com.github.eirslett.maven.plugins.frontend.lib.DefaultNpmRunner");
    Enumeration appenders = org.apache.log4j.Logger.getRootLogger().getAllAppenders();

    if (appenders != null) {
      while (appenders.hasMoreElements()) {
        Appender appender = (Appender) appenders.nextElement();
        appender.addFilter(new Filter() {

          @Override
          public int decide(LoggingEvent loggingEvent) {
            if (loggingEvent.getLoggerName().contains("DefaultNpmRunner")) {
              return DENY;
            } else {
              return NEUTRAL;
            }
          }
        });
      }
    }
    npmLogger.addAppender(new WriterAppender(
        new PatternLayout("%m%n"),
        out
    ));
  }
}
