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

import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.resource.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages helium packages
 */
public class Helium {
  private Logger logger = LoggerFactory.getLogger(Helium.class);
  private List<HeliumRegistry> registry = new LinkedList<>();

  private HeliumConf heliumConf;
  private Map<String, List<HeliumPackageSearchResult>> allPackages = new HashMap<>();

  private final String heliumConfPath;
  private final String registryPaths;
  private final File registryCacheDir;

  private final HeliumBundleFactory bundleFactory;
  private final HeliumApplicationFactory applicationFactory;
  private final InterpreterSettingManager interpreterSettingManager;

  public Helium(
      String heliumConfPath,
      String registryPaths,
      File registryCacheDir,
      HeliumBundleFactory bundleFactory,
      HeliumApplicationFactory applicationFactory,
      InterpreterSettingManager interpreterSettingManager)
      throws IOException {
    this.heliumConfPath = heliumConfPath;
    this.registryPaths = registryPaths;
    this.registryCacheDir = registryCacheDir;
    this.bundleFactory = bundleFactory;
    this.applicationFactory = applicationFactory;
    this.interpreterSettingManager = interpreterSettingManager;
    heliumConf = loadConf(heliumConfPath);
    allPackages = getAllPackageInfo();
  }

  /**
   * Add HeliumRegistry
   *
   * @param registry
   */
  public void addRegistry(HeliumRegistry registry) {
    synchronized (this.registry) {
      this.registry.add(registry);
    }
  }

  public void clear() {
    this.registry.clear();
    this.heliumConf = new HeliumConf();
    this.allPackages = new HashMap<>();
  }

  public HeliumApplicationFactory getApplicationFactory() {
    return applicationFactory;
  }

  private synchronized HeliumConf loadConf(String path) throws IOException {
    // add registry
    if (registryPaths != null && !registryPaths.isEmpty()) {
      String[] paths = registryPaths.split(",");
      for (String uri : paths) {
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
          logger.info("Add helium online registry {}", uri);
          registry.add(new HeliumOnlineRegistry(uri, uri, registryCacheDir));
        } else {
          logger.info("Add helium local registry {}", uri);
          registry.add(new HeliumLocalRegistry(uri, uri));
        }
      }
    }

    File heliumConfFile = new File(path);
    if (!heliumConfFile.isFile()) {
      logger.warn("{} does not exists", path);
      return new HeliumConf();
    } else {
      String jsonString = FileUtils.readFileToString(heliumConfFile);
      return HeliumConf.fromJson(jsonString);
    }
  }

  public synchronized void saveConfig() throws IOException {
    String jsonString;
    synchronized (registry) {
      clearNotExistsPackages();
      jsonString = heliumConf.toJson();
    }

    File heliumConfFile = new File(heliumConfPath);
    if (!heliumConfFile.exists()) {
      heliumConfFile.createNewFile();
    }

    FileUtils.writeStringToFile(heliumConfFile, jsonString);
  }

  private void clearNotExistsPackages() {
    // clear visualization display order
    List<String> packageOrder = heliumConf.getBundleDisplayOrder();
    List<String> clearedOrder = new LinkedList<>();
    for (String pkgName : packageOrder) {
      if (allPackages.containsKey(pkgName)) {
        clearedOrder.add(pkgName);
      }
    }
    heliumConf.setBundleDisplayOrder(clearedOrder);

    // clear enabled package
    Map<String, String> enabledPackages = heliumConf.getEnabledPackages();
    for (String pkgName : enabledPackages.keySet()) {
      if (!allPackages.containsKey(pkgName)) {
        heliumConf.disablePackage(pkgName);
      }
    }
  }

  public Map<String, List<HeliumPackageSearchResult>> getAllPackageInfo() {
    return getAllPackageInfo(true, null);
  }

  /**
   * @param refresh
   * @param packageName
   */
  public Map<String, List<HeliumPackageSearchResult>> getAllPackageInfo(boolean refresh,
                                                                        String packageName) {
    Map<String, String> enabledPackageInfo = heliumConf.getEnabledPackages();

    synchronized (registry) {
      if (refresh || !allPackages.containsKey(packageName)) {
        for (HeliumRegistry r : registry) {
          try {
            for (HeliumPackage pkg : r.getAll()) {
              String name = pkg.getName();

              if (!StringUtils.isEmpty(packageName) &&
                  !name.equals(packageName)) {
                continue;
              }

              if (allPackages.containsKey(name)) {
                allPackages.remove(name);
              }
              allPackages.put(name, new LinkedList<HeliumPackageSearchResult>());
              boolean enabled = enabledPackageInfo.containsKey(pkg.getName());
              allPackages.get(name).add(new HeliumPackageSearchResult(r.name(), pkg, enabled));
            }
          } catch (IOException e) {
            logger.error(e.getMessage(), e);
          }
        }
      } else {
        for (String name : allPackages.keySet()) {
          if (!StringUtils.isEmpty(packageName) &&
              !name.equals(packageName)) {
            continue;
          }

          List<HeliumPackageSearchResult> pkgs = allPackages.get(name);
          LinkedList<HeliumPackageSearchResult> newResults = new LinkedList<>();

          for (HeliumPackageSearchResult pkg : pkgs) {
            boolean enabled = enabledPackageInfo.containsKey(pkg.getPkg().getName());
            newResults.add(new HeliumPackageSearchResult(pkg.getRegistry(), pkg.getPkg(), enabled));
          }

          allPackages.put(name, newResults);
        }
      }

      // sort version (artifact)
      for (String name : allPackages.keySet()) {
        List<HeliumPackageSearchResult> packages = allPackages.get(name);
        Collections.sort(packages, new Comparator<HeliumPackageSearchResult>() {
          @Override
          public int compare(HeliumPackageSearchResult o1, HeliumPackageSearchResult o2) {
            return o2.getPkg().getArtifact().compareTo(o1.getPkg().getArtifact());
          }
        });
      }
      return allPackages;
    }
  }

  public List<HeliumPackageSearchResult> getAllEnabledPackages() {
    Map<String, String> enabledInfo = heliumConf.getEnabledPackages();
    List<HeliumPackageSearchResult> enabledPackages = new ArrayList<>();

    for (List<HeliumPackageSearchResult> versionedPackages : allPackages.values()) {
      for (HeliumPackageSearchResult psr : versionedPackages) {
        if (enabledInfo.containsKey(psr.getPkg().getName())) {
          enabledPackages.add(psr);
          break;
        }
      }
    }
    return enabledPackages;
  }

  public List<HeliumPackageSearchResult> getSinglePackageInfo(String packageName) {
    Map<String, List<HeliumPackageSearchResult>> result = getAllPackageInfo(true, packageName);

    if (!result.containsKey(packageName)) {
      return new ArrayList<>();
    }
    return result.get(packageName);
  }

  private HeliumPackageSearchResult getEnabledPackageInfo(String packageName) {
    List<HeliumPackageSearchResult> packages = allPackages.get(packageName);

    for (HeliumPackageSearchResult pkgSearchResult : packages) {
      if (pkgSearchResult.isEnabled()) {
        return pkgSearchResult;
      }
    }

    return null;
  }

  private HeliumPackageSearchResult getPackageInfo(String pkgName, String artifact) {
    Map<String, List<HeliumPackageSearchResult>> infos = getAllPackageInfo(false, pkgName);
    List<HeliumPackageSearchResult> packages = infos.get(pkgName);
    if (StringUtils.isBlank(artifact)) {
      return packages.get(0); /** return the FIRST package */
    } else {
      for (HeliumPackageSearchResult pkg : packages) {
        if (pkg.getPkg().getArtifact().equals(artifact)) {
          return pkg;
        }
      }
    }
    return null;
  }

  public File getBundle(HeliumPackage pkg, boolean rebuild) throws IOException {
    return bundleFactory.buildPackage(pkg, rebuild, true);
  }

  public boolean enable(String name, String artifact) throws IOException {
    HeliumPackageSearchResult pkgInfo = getPackageInfo(name, artifact);

    if (pkgInfo == null) {
      logger.info("Package {} not found", name);
      return false;
    }

    // if package is bundle, rebuild bundle
    if (HeliumPackage.isBundleType(pkgInfo.getPkg().getType())) {
      bundleFactory.buildPackage(pkgInfo.getPkg(), true, true);
    }

    // set `enable` field
    heliumConf.enablePackage(name, artifact);
    // set display order
    if (pkgInfo.getPkg().getType() == HeliumType.VISUALIZATION) {
      List<String> currentDisplayOrder = heliumConf.getBundleDisplayOrder();
      if (!currentDisplayOrder.contains(name)) {
        currentDisplayOrder.add(name);
      }
    }

    saveConfig();
    return true;
  }

  public boolean disable(String name) throws IOException {
    String pkg = heliumConf.getEnabledPackages().get(name);

    if (pkg == null) {
      logger.info("Package {} not found", name);
      return false;
    }

    HeliumPackageSearchResult pkgInfo = getPackageInfo(name, pkg);

    // set `enable` field
    heliumConf.disablePackage(name);
    if (pkgInfo.getPkg().getType() == HeliumType.VISUALIZATION) {
      List<String> currentDisplayOrder = heliumConf.getBundleDisplayOrder();
      if (currentDisplayOrder.contains(name)) {
        currentDisplayOrder.remove(name);
      }
    }
    saveConfig();
    return true;
  }

  public void updatePackageConfig(String artifact, Map<String, Object> pkgConfig)
      throws IOException {

    heliumConf.updatePackageConfig(artifact, pkgConfig);
    saveConfig();
  }

  public Map<String, Map<String, Object>> getAllPackageConfig() {
    return heliumConf.getAllPackageConfigs();
  }

  private Map<String, Object> getPackagePersistedConfig(String artifact) {
    return heliumConf.getPackagePersistedConfig(artifact);
  }

  public HeliumPackageSuggestion suggestApp(Paragraph paragraph) {
    HeliumPackageSuggestion suggestion = new HeliumPackageSuggestion();

    Interpreter intp = paragraph.getBindedInterpreter();
    if (intp == null) {
      return suggestion;
    }

    ResourcePool resourcePool = intp.getInterpreterGroup().getResourcePool();
    ResourceSet allResources;

    if (resourcePool != null) {
      if (resourcePool instanceof DistributedResourcePool) {
        allResources = ((DistributedResourcePool) resourcePool).getAll(true);
      } else {
        allResources = resourcePool.getAll();
      }
    } else {
      allResources = interpreterSettingManager.getAllResources();
    }

    for (List<HeliumPackageSearchResult> pkgs : allPackages.values()) {
      for (HeliumPackageSearchResult pkg : pkgs) {
        if (pkg.getPkg().getType() == HeliumType.APPLICATION && pkg.isEnabled()) {
          ResourceSet resources = ApplicationLoader.findRequiredResourceSet(
              pkg.getPkg().getResources(),
              paragraph.getNote().getId(),
              paragraph.getId(),
              allResources);
          if (resources == null) {
            continue;
          } else {
            suggestion.addAvailablePackage(pkg);
          }
          break;
        }
      }
    }

    suggestion.sort();
    return suggestion;
  }

  /**
   * Get enabled buildBundle packages
   *
   * @return ordered list of enabled buildBundle package
   */
  public List<HeliumPackage> getBundlePackagesToBundle() {
    List<String> visOrder = heliumConf.getBundleDisplayOrder();

    Set<HeliumPackage> orderedBundlePackages = new HashSet<>();
    List<HeliumPackage> output = new LinkedList<>();

    // add enabled packages in visOrder
    for (String name : visOrder) {
      List<HeliumPackageSearchResult> versions = allPackages.get(name);
      if (versions == null) {
        continue;
      }
      for (HeliumPackageSearchResult pkgInfo : versions) {
        if (canBundle(pkgInfo)) {
          orderedBundlePackages.add(pkgInfo.getPkg());
          break;
        }
      }
    }

    // add enabled packages not in visOrder
    for (List<HeliumPackageSearchResult> pkgInfos : allPackages.values()) {
      for (HeliumPackageSearchResult pkgInfo : pkgInfos) {
        if (canBundle(pkgInfo)) {
          orderedBundlePackages.add(pkgInfo.getPkg());
          break;
        }
      }
    }
    new LinkedList<>().addAll(orderedBundlePackages);
    return output;
  }

  private boolean canBundle(HeliumPackageSearchResult pkgInfo) {
    return (pkgInfo.isEnabled() &&
        HeliumPackage.isBundleType(pkgInfo.getPkg().getType()));
  }

  /**
   * Get enabled package list in order
   * @return
   */
  public List<String> getVisualizationPackageOrder() {
    return heliumConf.getBundleDisplayOrder();
  }

  public void setVisualizationPackageOrder(List<String> orderedPackageList)
      throws IOException {
    heliumConf.setBundleDisplayOrder(orderedPackageList);
    saveConfig();
  }

  /**
   * @param packageName
   * @return { "confPersisted", "confSpec" } or return null if failed to found enabled package
   */
  public Map<String, Map<String, Object>> getSpellConfig(String packageName) {
    HeliumPackageSearchResult result = getEnabledPackageInfo(packageName);

    if (result == null) {
      return null;
    }

    HeliumPackage enabledPackage = result.getPkg();

    Map<String, Object> configSpec = enabledPackage.getConfig();
    Map<String, Object> configPersisted =
        getPackagePersistedConfig(enabledPackage.getArtifact());

    return createMixedConfig(configPersisted, configSpec);
  }

  public Map<String, Map<String, Object>> getPackageConfig(String pkgName,
                                                           String artifact) {

    HeliumPackageSearchResult result = getPackageInfo(pkgName, artifact);

    if (result == null) {
      return null;
    }

    HeliumPackage requestedPackage = result.getPkg();

    Map<String, Object> configSpec = requestedPackage.getConfig();
    Map<String, Object> configPersisted =
        getPackagePersistedConfig(artifact);

    return createMixedConfig(configPersisted, configSpec);
  }

  private static Map<String, Map<String, Object>> createMixedConfig(Map<String, Object> persisted,
                                                                   Map<String, Object> spec) {
    Map<String, Map<String, Object>> mixed = new HashMap<>();
    mixed.put("confPersisted", persisted);
    mixed.put("confSpec", spec);

    return mixed;
  }

  public ResourceSet getAllResources() {
    return getAllResourcesExcept(null);
  }

  private ResourceSet getAllResourcesExcept(String interpreterGroupExcludsion) {
    ResourceSet resourceSet = new ResourceSet();
    for (ManagedInterpreterGroup intpGroup : interpreterSettingManager.getAllInterpreterGroup()) {
      if (interpreterGroupExcludsion != null &&
          intpGroup.getId().equals(interpreterGroupExcludsion)) {
        continue;
      }

      RemoteInterpreterProcess remoteInterpreterProcess = intpGroup.getRemoteInterpreterProcess();
      if (remoteInterpreterProcess == null) {
        ResourcePool localPool = intpGroup.getResourcePool();
        if (localPool != null) {
          resourceSet.addAll(localPool.getAll());
        }
      } else if (remoteInterpreterProcess.isRunning()) {
        List<String> resourceList = remoteInterpreterProcess.callRemoteFunction(
            new RemoteInterpreterProcess.RemoteFunction<List<String>>() {
              @Override
              public List<String> call(RemoteInterpreterService.Client client) throws Exception {
                return client.resourcePoolGetAll();
              }
            }
        );
        Gson gson = new Gson();
        for (String res : resourceList) {
          resourceSet.add(gson.fromJson(res, Resource.class));
        }
      }
    }
    return resourceSet;
  }
}
