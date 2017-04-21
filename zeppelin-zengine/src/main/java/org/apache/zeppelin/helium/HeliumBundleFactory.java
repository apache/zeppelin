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
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Appender;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LoggingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

import org.apache.zeppelin.conf.ZeppelinConfiguration;

/**
 * Load helium visualization & spell
 */
public class HeliumBundleFactory {
  Logger logger = LoggerFactory.getLogger(HeliumBundleFactory.class);
  private final String NODE_VERSION = "v6.9.1";
  private final String NPM_VERSION = "3.10.8";
  private final String YARN_VERSION = "v0.21.3";
  public static final String HELIUM_LOCAL_REPO = "helium-bundle";
  public static final String HELIUM_BUNDLES_DIR = "bundles";
  public static final String HELIUM_LOCAL_MODULE_DIR = "local_modules";
  public static final String HELIUM_BUNDLES_SRC_DIR = "src";
  public static final String HELIUM_BUNDLES_SRC = "load.js";
  public static final String YARN_CACHE_DIR = "yarn-cache";
  public static final String PACKAGE_JSON = "package.json";
  public static final String HELIUM_BUNDLE_CACHE = "helium.bundle.cache.js";
  public static final String HELIUM_BUNDLE = "helium.bundle.js";
  public static final String HELIUM_BUNDLES_VAR = "heliumBundles";
  private final int FETCH_RETRY_COUNT = 2;
  private final int FETCH_RETRY_FACTOR_COUNT = 1;
  private final int FETCH_RETRY_MIN_TIMEOUT = 5000; // Milliseconds

  private final FrontendPluginFactory frontEndPluginFactory;
  private final File nodeInstallationDirectory;
  private final File heliumLocalRepoDirectory;
  private final File heliumBundleDirectory;
  private final File heliumLocalModuleDirectory;
  private final File yarnCacheDir;
  private ZeppelinConfiguration conf;
  private File tabledataModulePath;
  private File visualizationModulePath;
  private File spellModulePath;
  private String defaultNpmRegistryUrl;
  private Gson gson;
  private boolean nodeAndNpmInstalled = false;

  ByteArrayOutputStream out  = new ByteArrayOutputStream();

  public HeliumBundleFactory(
      ZeppelinConfiguration conf,
      File nodeInstallationDir,
      File moduleDownloadPath,
      File tabledataModulePath,
      File visualizationModulePath,
      File spellModulePath) throws TaskRunnerException {
    this(conf, nodeInstallationDir, moduleDownloadPath);
    this.tabledataModulePath = tabledataModulePath;
    this.visualizationModulePath = visualizationModulePath;
    this.spellModulePath = spellModulePath;
  }

  public HeliumBundleFactory(
      ZeppelinConfiguration conf,
      File nodeInstallationDir,
      File moduleDownloadPath) throws TaskRunnerException {
    this.heliumLocalRepoDirectory = new File(moduleDownloadPath, HELIUM_LOCAL_REPO);
    this.heliumBundleDirectory = new File(heliumLocalRepoDirectory, HELIUM_BUNDLES_DIR);
    this.heliumLocalModuleDirectory = new File(heliumLocalRepoDirectory, HELIUM_LOCAL_MODULE_DIR);
    this.yarnCacheDir = new File(heliumLocalRepoDirectory, YARN_CACHE_DIR);
    this.conf = conf;
    this.defaultNpmRegistryUrl = conf.getHeliumNpmRegistry();

    nodeInstallationDirectory = (nodeInstallationDir == null) ?
        heliumLocalRepoDirectory : nodeInstallationDir;

    frontEndPluginFactory = new FrontendPluginFactory(
            heliumLocalRepoDirectory, nodeInstallationDirectory);

    gson = new Gson();
  }

  void installNodeAndNpm() throws TaskRunnerException {
    if (nodeAndNpmInstalled) {
      return;
    }
    try {
      NodeInstaller nodeInstaller = frontEndPluginFactory.getNodeInstaller(getProxyConfig());
      nodeInstaller.setNodeVersion(NODE_VERSION);
      nodeInstaller.install();

      NPMInstaller npmInstaller = frontEndPluginFactory.getNPMInstaller(getProxyConfig());
      npmInstaller.setNpmVersion(NPM_VERSION);
      npmInstaller.install();

      YarnInstaller yarnInstaller = frontEndPluginFactory.getYarnInstaller(getProxyConfig());
      yarnInstaller.setYarnVersion(YARN_VERSION);
      yarnInstaller.install();
      String yarnCacheDirPath = yarnCacheDir.getAbsolutePath();
      yarnCommand(frontEndPluginFactory, "config set cache-folder " + yarnCacheDirPath);

      configureLogger();
      nodeAndNpmInstalled = true;
    } catch (InstallationException e) {
      logger.error(e.getMessage(), e);
    }
  }

  private ProxyConfig getProxyConfig() {
    List<ProxyConfig.Proxy> proxy = new LinkedList<>();
    return new ProxyConfig(proxy);
  }

  public void buildAllPackages(List<HeliumPackage> pkgs) throws IOException {
    buildAllPackages(pkgs, false);
  }

  public File getHeliumPackageDirectory(String pkgName) {
    return new File(heliumBundleDirectory, pkgName);
  }

  public File getHeliumPackageSourceDirectory(String pkgName) {
    return new File(heliumBundleDirectory, pkgName + "/" + HELIUM_BUNDLES_SRC_DIR);
  }

  public File getHeliumPackageBundleCache(String pkgName) {
    return new File(heliumBundleDirectory, pkgName + "/" + HELIUM_BUNDLE_CACHE);
  }

  public static List<String> unTgz(File tarFile, File directory) throws IOException {
    List<String> result = new ArrayList<String>();
    InputStream is = new FileInputStream(tarFile);
    GzipCompressorInputStream gcis = new GzipCompressorInputStream(is);
    TarArchiveInputStream in = new TarArchiveInputStream(gcis);
    TarArchiveEntry entry = in.getNextTarEntry();
    while (entry != null) {
      if (entry.isDirectory()) {
        entry = in.getNextTarEntry();
        continue;
      }
      File curfile = new File(directory, entry.getName());
      File parent = curfile.getParentFile();
      if (!parent.exists()) {
        parent.mkdirs();
      }
      OutputStream out = new FileOutputStream(curfile);
      IOUtils.copy(in, out);
      out.close();
      result.add(entry.getName());
      entry = in.getNextTarEntry();
    }
    in.close();
    return result;
  }

  /**
   * @return main file name of this helium package (relative path)
   */
  public String downloadPackage(HeliumPackage pkg, String[] nameAndVersion, File bundleDir,
                                String templateWebpackConfig, String templatePackageJson,
                                FrontendPluginFactory fpf) throws IOException, TaskRunnerException {
    if (bundleDir.exists()) {
      FileUtils.deleteQuietly(bundleDir);
    }
    FileUtils.forceMkdir(bundleDir);

    FileFilter copyFilter = new FileFilter() {
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

    if (isLocalPackage(pkg)) {
      FileUtils.copyDirectory(
              new File(pkg.getArtifact()),
              bundleDir,
              copyFilter);
    } else {
      // if online package
      String version = nameAndVersion[1];
      File tgz = new File(heliumLocalRepoDirectory, pkg.getName() + "-" + version + ".tgz");
      tgz.delete();

      // wget, extract and move dir to `bundles/${pkg.getName()}`, and remove tgz
      npmCommand(fpf, "pack " + pkg.getArtifact());
      File extracted = new File(heliumBundleDirectory, "package");
      FileUtils.deleteDirectory(extracted);
      unTgz(tgz, heliumBundleDirectory);
      tgz.delete();
      FileUtils.copyDirectory(extracted, bundleDir);
      FileUtils.deleteDirectory(extracted);
    }

    // 1. setup package.json
    File existingPackageJson = new File(bundleDir, "package.json");
    JsonReader reader = new JsonReader(new FileReader(existingPackageJson));
    Map<String, Object> packageJson = gson.fromJson(reader,
            new TypeToken<Map<String, Object>>(){}.getType());
    Map<String, String> existingDeps = (Map<String, String>) packageJson.get("dependencies");
    String mainFileName = (String) packageJson.get("main");

    StringBuilder dependencies = new StringBuilder();
    int index = 0;
    for (Map.Entry<String, String> e: existingDeps.entrySet()) {
      dependencies.append("    \"").append(e.getKey()).append("\": ");
      if (e.getKey().equals("zeppelin-vis") ||
          e.getKey().equals("zeppelin-tabledata") ||
          e.getKey().equals("zeppelin-spell")) {
        dependencies.append("\"file:../../" + HELIUM_LOCAL_MODULE_DIR + "/")
                .append(e.getKey()).append("\"");
      } else {
        dependencies.append("\"").append(e.getValue()).append("\"");
      }

      if (index < existingDeps.size() - 1) {
        dependencies.append(",\n");
      }
      index = index + 1;
    }

    FileUtils.deleteQuietly(new File(bundleDir, PACKAGE_JSON));
    templatePackageJson = templatePackageJson.replaceFirst("PACKAGE_NAME", pkg.getName());
    templatePackageJson = templatePackageJson.replaceFirst("MAIN_FILE", mainFileName);
    templatePackageJson = templatePackageJson.replaceFirst("DEPENDENCIES", dependencies.toString());
    FileUtils.write(new File(bundleDir, PACKAGE_JSON), templatePackageJson);

    // 2. setup webpack.config
    FileUtils.write(new File(bundleDir, "webpack.config.js"), templateWebpackConfig);

    return mainFileName;
  }

  public void prepareSource(HeliumPackage pkg, String[] moduleNameVersion,
                            String mainFileName) throws IOException {
    StringBuilder loadJsImport = new StringBuilder();
    StringBuilder loadJsRegister = new StringBuilder();
    String className = "bundles" + pkg.getName().replaceAll("[-_]", "");

    // remove postfix `.js` for ES6 import
    if (mainFileName.endsWith(".js")) {
      mainFileName = mainFileName.substring(0, mainFileName.length() - 3);
    }

    loadJsImport
        .append("import ")
        .append(className)
        .append(" from \"../" + mainFileName + "\"\n");

    loadJsRegister.append(HELIUM_BUNDLES_VAR + ".push({\n");
    loadJsRegister.append("id: \"" + moduleNameVersion[0] + "\",\n");
    loadJsRegister.append("name: \"" + pkg.getName() + "\",\n");
    loadJsRegister.append("icon: " + gson.toJson(pkg.getIcon()) + ",\n");
    loadJsRegister.append("type: \"" + pkg.getType() + "\",\n");
    loadJsRegister.append("class: " + className + "\n");
    loadJsRegister.append("})\n");

    File srcDir = getHeliumPackageSourceDirectory(pkg.getName());
    FileUtils.forceMkdir(srcDir);
    FileUtils.write(new File(srcDir, HELIUM_BUNDLES_SRC),
            loadJsImport.append(loadJsRegister).toString());
  }

  public synchronized void installNodeModules(FrontendPluginFactory fpf) throws IOException {
    try {
      out.reset();
      String commandForNpmInstall =
              String.format("install --fetch-retries=%d --fetch-retry-factor=%d " +
                              "--fetch-retry-mintimeout=%d",
                      FETCH_RETRY_COUNT, FETCH_RETRY_FACTOR_COUNT, FETCH_RETRY_MIN_TIMEOUT);
      logger.info("Installing required node modules");
      yarnCommand(fpf, commandForNpmInstall);
      logger.info("Installed required node modules");
    } catch (TaskRunnerException e) {
      throw new IOException(e);
    }
  }

  public synchronized File bundleHeliumPackage(FrontendPluginFactory fpf,
                                               File bundleDir) throws IOException {
    try {
      out.reset();
      logger.info("Bundling helium packages");
      yarnCommand(fpf, "run bundle");
      logger.info("Bundled helium packages");
    } catch (TaskRunnerException e) {
      throw new IOException(new String(out.toByteArray()));
    }

    String bundleStdoutResult = new String(out.toByteArray());
    File heliumBundle = new File(bundleDir, HELIUM_BUNDLE);
    if (!heliumBundle.isFile()) {
      throw new IOException(
              "Can't create bundle: \n" + bundleStdoutResult);
    }

    WebpackResult result = getWebpackResultFromOutput(bundleStdoutResult);
    if (result.errors.length > 0) {
      FileUtils.deleteQuietly(heliumBundle);
      throw new IOException(result.errors[0]);
    }

    return heliumBundle;
  }

  public synchronized File buildPackage(HeliumPackage pkg,
                                        boolean rebuild,
                                        boolean recopyLocalModule) throws IOException {
    if (pkg == null) {
      return null;
    }

    String[] moduleNameVersion = getNpmModuleNameAndVersion(pkg);
    if (moduleNameVersion == null) {
      return null;
    }

    if (moduleNameVersion == null) {
      logger.error("Can't get module name and version of package " + pkg.getName());
      return null;
    }

    String pkgName = pkg.getName();
    File bundleDir = getHeliumPackageDirectory(pkgName);
    File bundleCache = getHeliumPackageBundleCache(pkgName);

    if (!rebuild && bundleCache.exists() && !bundleCache.isDirectory()) {
      return bundleCache;
    }

    // 0. install node, npm (should be called before `downloadPackage`
    try {
      installNodeAndNpm();
    } catch (TaskRunnerException e) {
      throw new IOException(e);
    }

    // 1. prepare directories
    if (!heliumLocalRepoDirectory.exists() || !heliumLocalRepoDirectory.isDirectory()) {
      FileUtils.deleteQuietly(heliumLocalRepoDirectory);
      FileUtils.forceMkdir(heliumLocalRepoDirectory);
    }
    FrontendPluginFactory fpf = new FrontendPluginFactory(
            bundleDir, nodeInstallationDirectory);

    // resources: webpack.js, package.json
    String templateWebpackConfig = Resources.toString(
        Resources.getResource("helium/webpack.config.js"), Charsets.UTF_8);
    String templatePackageJson = Resources.toString(
        Resources.getResource("helium/" + PACKAGE_JSON), Charsets.UTF_8);

    // 2. download helium package using `npm pack`
    String mainFileName = null;
    try {
      mainFileName = downloadPackage(pkg, moduleNameVersion, bundleDir,
              templateWebpackConfig, templatePackageJson, fpf);
    } catch (TaskRunnerException e) {
      throw new IOException(e);
    }

    // 3. prepare bundle source
    prepareSource(pkg, moduleNameVersion, mainFileName);

    // 4. install node and local modules for a bundle
    copyFrameworkModulesToInstallPath(recopyLocalModule); // should copy local modules first
    installNodeModules(fpf);

    // 5. let's bundle and update cache
    File heliumBundle = bundleHeliumPackage(fpf, bundleDir);
    bundleCache.delete();
    FileUtils.moveFile(heliumBundle, bundleCache);

    return bundleCache;
  }

  public synchronized void buildAllPackages(List<HeliumPackage> pkgs, boolean rebuild)
      throws IOException {

    if (pkgs == null || pkgs.size() == 0) {
      return;
    }

    // DON't recopy local modules when build all packages to avoid duplicated copies.
    boolean recopyLocalModules = false;

    for (HeliumPackage pkg : pkgs) {
      try {
        buildPackage(pkg, rebuild, recopyLocalModules);
      } catch (IOException e) {
        logger.error("Failed to build helium package: " + pkg.getArtifact(), e);
      }
    }
  }

  void copyFrameworkModule(boolean recopy, FileFilter filter,
                           File src, File dest) throws IOException {
    if (src != null) {
      if (recopy && dest.exists()) {
        FileUtils.deleteDirectory(dest);
      }

      if (!dest.exists()) {
        FileUtils.copyDirectory(
            src,
            dest,
            filter);
      }
    }
  }

  void deleteYarnCache() {
    FilenameFilter filter = new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        if ((name.startsWith("npm-zeppelin-vis-") ||
            name.startsWith("npm-zeppelin-tabledata-") ||
            name.startsWith("npm-zeppelin-spell-")) &&
            dir.isDirectory()) {
          return true;
        }

        return false;
      }
    };

    File[] localModuleCaches = yarnCacheDir.listFiles(filter);
    if (localModuleCaches != null) {
      for (File f : localModuleCaches) {
        FileUtils.deleteQuietly(f);
      }
    }
  }

  void copyFrameworkModulesToInstallPath(boolean recopy)
      throws IOException {

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

    FileUtils.forceMkdir(heliumLocalModuleDirectory);
    // should delete yarn caches for local modules since they might be updated
    deleteYarnCache();

    // install tabledata module
    File tabledataModuleInstallPath = new File(heliumLocalModuleDirectory,
        "zeppelin-tabledata");
    copyFrameworkModule(recopy, npmPackageCopyFilter,
        tabledataModulePath, tabledataModuleInstallPath);

    // install visualization module
    File visModuleInstallPath = new File(heliumLocalModuleDirectory,
        "zeppelin-vis");
    copyFrameworkModule(recopy, npmPackageCopyFilter,
        visualizationModulePath, visModuleInstallPath);

    // install spell module
    File spellModuleInstallPath = new File(heliumLocalModuleDirectory,
        "zeppelin-spell");
    copyFrameworkModule(recopy, npmPackageCopyFilter,
        spellModulePath, spellModuleInstallPath);
  }

  private WebpackResult getWebpackResultFromOutput(String output) {
    BufferedReader reader = new BufferedReader(new StringReader(output));

    boolean webpackRunDetected = false;
    boolean resultJsonDetected = false;
    StringBuffer sb = new StringBuffer();
    try {
      String next, line = reader.readLine();
      for (boolean last = (line == null); !last; line = next) {
        last = ((next = reader.readLine()) == null);

        if (!webpackRunDetected) {
          String trimed = line.trim();
          if (trimed.contains("webpack") && trimed.endsWith("--json")) {
            webpackRunDetected = true;
          }
          continue;
        }

        if (!resultJsonDetected) {
          if (line.trim().equals("{")) {
            sb.append(line);
            resultJsonDetected = true;
          }
          continue;
        }

        if (resultJsonDetected && webpackRunDetected) {
          // yarn command always ends with `Done in ... seconds `
          if (!last) {
            sb.append(line);
          }
        }
      }
      Gson gson = new Gson();
      return gson.fromJson(sb.toString(), WebpackResult.class);
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
      return new WebpackResult();
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

  synchronized void install(HeliumPackage pkg) throws TaskRunnerException {
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
    NpmRunner npm = frontEndPluginFactory.getNpmRunner(getProxyConfig(), defaultNpmRegistryUrl);
    npm.execute(args, env);
  }

  private void npmCommand(FrontendPluginFactory fpf, String args) throws TaskRunnerException {
    npmCommand(args, new HashMap<String, String>());
  }

  private void yarnCommand(FrontendPluginFactory fpf, String args) throws TaskRunnerException {
    yarnCommand(fpf, args, new HashMap<String, String>());
  }

  private void yarnCommand(FrontendPluginFactory fpf,
                           String args, Map<String, String> env) throws TaskRunnerException {
    YarnRunner yarn = fpf.getYarnRunner(getProxyConfig(), defaultNpmRegistryUrl);
    yarn.execute(args, env);
  }

  private synchronized void configureLogger() {
    org.apache.log4j.Logger npmLogger = org.apache.log4j.Logger.getLogger(
        "com.github.eirslett.maven.plugins.frontend.lib.DefaultYarnRunner");
    Enumeration appenders = org.apache.log4j.Logger.getRootLogger().getAllAppenders();

    if (appenders != null) {
      while (appenders.hasMoreElements()) {
        Appender appender = (Appender) appenders.nextElement();
        appender.addFilter(new Filter() {

          @Override
          public int decide(LoggingEvent loggingEvent) {
            if (loggingEvent.getLoggerName().contains("DefaultYarnRunner")) {
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
