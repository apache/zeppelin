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
import com.google.gson.GsonBuilder;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.resource.DistributedResourcePool;
import org.apache.zeppelin.resource.ResourcePool;
import org.apache.zeppelin.resource.ResourcePoolUtils;
import org.apache.zeppelin.resource.ResourceSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages helium packages
 */
public class Helium {
  Logger logger = LoggerFactory.getLogger(Helium.class);
  private List<HeliumRegistry> registry = new LinkedList<>();

  private final HeliumConf heliumConf;
  private final String heliumConfPath;
  private final String registryPaths;
  private final File registryCacheDir;

  private final Gson gson;
  private final HeliumBundleFactory bundleFactory;
  private final HeliumApplicationFactory applicationFactory;

  Map<String, List<HeliumPackageSearchResult>> allPackages;

  public Helium(
      String heliumConfPath,
      String registryPaths,
      File registryCacheDir,
      HeliumBundleFactory bundleFactory,
      HeliumApplicationFactory applicationFactory)
      throws IOException {
    this.heliumConfPath = heliumConfPath;
    this.registryPaths = registryPaths;
    this.registryCacheDir = registryCacheDir;
    this.bundleFactory = bundleFactory;
    this.applicationFactory = applicationFactory;

    GsonBuilder builder = new GsonBuilder();
    builder.setPrettyPrinting();
    builder.registerTypeAdapter(
        HeliumRegistry.class, new HeliumRegistrySerializer());
    gson = builder.create();

    heliumConf = loadConf(heliumConfPath);
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

  public List<HeliumRegistry> getAllRegistry() {
    synchronized (this.registry) {
      List list = new LinkedList<>();
      for (HeliumRegistry r : registry) {
        list.add(r);
      }
      return list;
    }
  }

  public HeliumApplicationFactory getApplicationFactory() {
    return applicationFactory;
  }

  public HeliumBundleFactory getBundleFactory() {
    return bundleFactory;
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
      HeliumConf conf = new HeliumConf();
      return conf;
    } else {
      String jsonString = FileUtils.readFileToString(heliumConfFile);
      HeliumConf conf = gson.fromJson(jsonString, HeliumConf.class);
      return conf;
    }
  }

  public synchronized void save() throws IOException {
    String jsonString;
    synchronized (registry) {
      clearNotExistsPackages();
      jsonString = gson.toJson(heliumConf);
    }

    File heliumConfFile = new File(heliumConfPath);
    if (!heliumConfFile.exists()) {
      heliumConfFile.createNewFile();
    }

    FileUtils.writeStringToFile(heliumConfFile, jsonString);
  }

  private void clearNotExistsPackages() {
    Map<String, List<HeliumPackageSearchResult>> all = getAllPackageInfoWithoutRefresh();

    // clear visualization display order
    List<String> packageOrder = heliumConf.getBundleDisplayOrder();
    List<String> clearedOrder = new LinkedList<>();
    for (String pkgName : packageOrder) {
      if (all.containsKey(pkgName)) {
        clearedOrder.add(pkgName);
      }
    }
    heliumConf.setBundleDisplayOrder(clearedOrder);

    // clear enabled package
    Map<String, String> enabledPackages = heliumConf.getEnabledPackages();
    for (String pkgName : enabledPackages.keySet()) {
      if (!all.containsKey(pkgName)) {
        heliumConf.disablePackage(pkgName);
      }
    }
  }

  public Map<String, List<HeliumPackageSearchResult>> getAllPackageInfoWithoutRefresh() {
    return getAllPackageInfo(false, null);
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
      if (refresh || allPackages == null) {
        allPackages = new HashMap<>();
        for (HeliumRegistry r : registry) {
          try {
            for (HeliumPackage pkg : r.getAll()) {
              String name = pkg.getName();

              if (!StringUtils.isEmpty(packageName) &&
                  !name.equals(packageName)) {
                continue;
              }

              String artifact = enabledPackageInfo.get(name);
              boolean enabled = (artifact != null && artifact.equals(pkg.getArtifact()));

              if (!allPackages.containsKey(name)) {
                allPackages.put(name, new LinkedList<HeliumPackageSearchResult>());
              }
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
          String artifact = enabledPackageInfo.get(name);
          LinkedList<HeliumPackageSearchResult> newResults =
              new LinkedList<HeliumPackageSearchResult>();

          for (HeliumPackageSearchResult pkg : pkgs) {
            boolean enabled = (artifact != null && artifact.equals(pkg.getPkg().getArtifact()));
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
    Map<String, List<HeliumPackageSearchResult>> allPackages = getAllPackageInfoWithoutRefresh();
    List<HeliumPackageSearchResult> enabledPackages = new ArrayList<>();

    for (List<HeliumPackageSearchResult> versionedPackages : allPackages.values()) {
      for (HeliumPackageSearchResult psr : versionedPackages) {
        if (psr.isEnabled()) {
          enabledPackages.add(psr);
          break;
        }
      }
    }

    return enabledPackages;
  }

  public List<HeliumPackageSearchResult> getSinglePackageInfo(String packageName) {
    Map<String, List<HeliumPackageSearchResult>> result = getAllPackageInfo(false, packageName);

    if (!result.containsKey(packageName)) {
      return new ArrayList<>();
    }

    return result.get(packageName);
  }

  public HeliumPackageSearchResult getEnabledPackageInfo(String packageName) {
    Map<String, List<HeliumPackageSearchResult>> infos = getAllPackageInfoWithoutRefresh();
    List<HeliumPackageSearchResult> packages = infos.get(packageName);

    for (HeliumPackageSearchResult pkgSearchResult : packages) {
      if (pkgSearchResult.isEnabled()) {
        return pkgSearchResult;
      }
    }

    return null;
  }

  public HeliumPackageSearchResult getPackageInfo(String pkgName, String artifact) {
    Map<String, List<HeliumPackageSearchResult>> infos = getAllPackageInfo(false, pkgName);
    List<HeliumPackageSearchResult> packages = infos.get(pkgName);
    if (artifact == null) {
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

  public void enable(String name, String artifact) throws IOException {
    HeliumPackageSearchResult pkgInfo = getPackageInfo(name, artifact);

    // no package found.
    if (pkgInfo == null) {
      return;
    }

    // if package is visualization, rebuild bundle
    if (HeliumPackage.isBundleType(pkgInfo.getPkg().getType())) {
      bundleFactory.buildPackage(pkgInfo.getPkg(), true, true);
    }

    // update conf and save
    heliumConf.enablePackage(name, artifact);
    save();
  }

  public void disable(String name) throws IOException {
    String artifact = heliumConf.getEnabledPackages().get(name);

    if (artifact == null) {
      return;
    }

    // update conf and save
    heliumConf.disablePackage(name);
    save();
  }

  public void updatePackageConfig(String artifact, Map<String, Object> pkgConfig)
      throws IOException {

    heliumConf.updatePackageConfig(artifact, pkgConfig);
    save();
  }

  public Map<String, Map<String, Object>> getAllPackageConfig() {
    return heliumConf.getAllPackageConfigs();
  }

  public Map<String, Object> getPackagePersistedConfig(String artifact) {
    return heliumConf.getPackagePersistedConfig(artifact);
  }

  public HeliumPackageSuggestion suggestApp(Paragraph paragraph) {
    HeliumPackageSuggestion suggestion = new HeliumPackageSuggestion();

    Interpreter intp = paragraph.getCurrentRepl();
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
      allResources = ResourcePoolUtils.getAllResources();
    }

    for (List<HeliumPackageSearchResult> pkgs : getAllPackageInfoWithoutRefresh().values()) {
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
    Map<String, List<HeliumPackageSearchResult>> allPackages = getAllPackageInfoWithoutRefresh();
    List<String> visOrder = heliumConf.getBundleDisplayOrder();

    List<HeliumPackage> orderedBundlePackages = new LinkedList<>();

    // add enabled packages in visOrder
    for (String name : visOrder) {
      List<HeliumPackageSearchResult> versions = allPackages.get(name);
      if (versions == null) {
        continue;
      }
      for (HeliumPackageSearchResult pkgInfo : versions) {
        if (canBundle(pkgInfo)) {
          orderedBundlePackages.add(pkgInfo.getPkg());
          allPackages.remove(name);
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

    return orderedBundlePackages;
  }

  public boolean canBundle(HeliumPackageSearchResult pkgInfo) {
    return (pkgInfo.isEnabled() &&
        HeliumPackage.isBundleType(pkgInfo.getPkg().getType()));
  }

  /**
   * Get enabled package list in order
   * @return
   */
  public List<String> setVisualizationPackageOrder() {
    List orderedPackageList = new LinkedList<>();
    List<HeliumPackage> packages = getBundlePackagesToBundle();

    for (HeliumPackage pkg : packages) {
      if (HeliumType.VISUALIZATION == pkg.getType()) {
        orderedPackageList.add(pkg.getName());
      }
    }

    return orderedPackageList;
  }

  public void setVisualizationPackageOrder(List<String> orderedPackageList)
      throws IOException {
    heliumConf.setBundleDisplayOrder(orderedPackageList);

    // if package is visualization, rebuild buildBundle
    bundleFactory.buildAllPackages(getBundlePackagesToBundle());

    save();
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

  public static Map<String, Map<String, Object>> createMixedConfig(Map<String, Object> persisted,
                                                                   Map<String, Object> spec) {
    Map<String, Map<String, Object>> mixed = new HashMap<>();
    mixed.put("confPersisted", persisted);
    mixed.put("confSpec", spec);

    return mixed;
  }
}
