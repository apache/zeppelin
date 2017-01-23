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
  private final HeliumVisualizationFactory visualizationFactory;
  private final HeliumApplicationFactory applicationFactory;

  public Helium(
      String heliumConfPath,
      String registryPaths,
      File registryCacheDir,
      HeliumVisualizationFactory visualizationFactory,
      HeliumApplicationFactory applicationFactory)
      throws IOException {
    this.heliumConfPath = heliumConfPath;
    this.registryPaths = registryPaths;
    this.registryCacheDir = registryCacheDir;
    this.visualizationFactory = visualizationFactory;
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

  public HeliumVisualizationFactory getVisualizationFactory() {
    return visualizationFactory;
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
    Map<String, List<HeliumPackageSearchResult>> all = getAllPackageInfo();

    // clear visualization display order
    List<String> packageOrder = heliumConf.getVisualizationDisplayOrder();
    List<String> clearedOrder = new LinkedList<>();
    for (String pkgName : packageOrder) {
      if (all.containsKey(pkgName)) {
        clearedOrder.add(pkgName);
      }
    }
    heliumConf.setVisualizationDisplayOrder(clearedOrder);

    // clear enabled package
    Map<String, String> enabledPackages = heliumConf.getEnabledPackages();
    for (String pkgName : enabledPackages.keySet()) {
      if (!all.containsKey(pkgName)) {
        heliumConf.disablePackage(pkgName);
      }
    }
  }

  public Map<String, List<HeliumPackageSearchResult>> getAllPackageInfo() {
    Map<String, String> enabledPackageInfo = heliumConf.getEnabledPackages();

    Map<String, List<HeliumPackageSearchResult>> map = new HashMap<>();
    synchronized (registry) {
      for (HeliumRegistry r : registry) {
        try {
          for (HeliumPackage pkg : r.getAll()) {
            String name = pkg.getName();
            String artifact = enabledPackageInfo.get(name);
            boolean enabled = (artifact != null && artifact.equals(pkg.getArtifact()));

            if (!map.containsKey(name)) {
              map.put(name, new LinkedList<HeliumPackageSearchResult>());
            }
            map.get(name).add(new HeliumPackageSearchResult(r.name(), pkg, enabled));
          }
        } catch (IOException e) {
          logger.error(e.getMessage(), e);
        }
      }
    }

    // sort version (artifact)
    for (String name : map.keySet()) {
      List<HeliumPackageSearchResult> packages = map.get(name);
      Collections.sort(packages, new Comparator<HeliumPackageSearchResult>() {
        @Override
        public int compare(HeliumPackageSearchResult o1, HeliumPackageSearchResult o2) {
          return o2.getPkg().getArtifact().compareTo(o1.getPkg().getArtifact());
        }
      });
    }
    return map;
  }

  public HeliumPackageSearchResult getPackageInfo(String name, String artifact) {
    Map<String, List<HeliumPackageSearchResult>> infos = getAllPackageInfo();
    List<HeliumPackageSearchResult> packages = infos.get(name);
    if (artifact == null) {
      return packages.get(0);
    } else {
      for (HeliumPackageSearchResult pkg : packages) {
        if (pkg.getPkg().getArtifact().equals(artifact)) {
          return pkg;
        }
      }
    }

    return null;
  }

  public File recreateVisualizationBundle() throws IOException {
    return visualizationFactory.bundle(getVisualizationPackagesToBundle(), true);
  }

  public void enable(String name, String artifact) throws IOException {
    HeliumPackageSearchResult pkgInfo = getPackageInfo(name, artifact);

    // no package found.
    if (pkgInfo == null) {
      return;
    }

    // enable package
    heliumConf.enablePackage(name, artifact);

    // if package is visualization, rebuild bundle
    if (pkgInfo.getPkg().getType() == HeliumPackage.Type.VISUALIZATION) {
      visualizationFactory.bundle(getVisualizationPackagesToBundle());
    }

    save();
  }

  public void disable(String name) throws IOException {
    String artifact = heliumConf.getEnabledPackages().get(name);

    if (artifact == null) {
      return;
    }

    heliumConf.disablePackage(name);

    HeliumPackageSearchResult pkg = getPackageInfo(name, artifact);
    if (pkg == null || pkg.getPkg().getType() == HeliumPackage.Type.VISUALIZATION) {
      visualizationFactory.bundle(getVisualizationPackagesToBundle());
    }

    save();
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

    for (List<HeliumPackageSearchResult> pkgs : getAllPackageInfo().values()) {
      for (HeliumPackageSearchResult pkg : pkgs) {
        if (pkg.getPkg().getType() == HeliumPackage.Type.APPLICATION && pkg.isEnabled()) {
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
   * Get enabled visualization packages
   *
   * @return ordered list of enabled visualization package
   */
  public List<HeliumPackage> getVisualizationPackagesToBundle() {
    Map<String, List<HeliumPackageSearchResult>> allPackages = getAllPackageInfo();
    List<String> visOrder = heliumConf.getVisualizationDisplayOrder();

    List<HeliumPackage> orderedVisualizationPackages = new LinkedList<>();

    // add enabled packages in visOrder
    for (String name : visOrder) {
      List<HeliumPackageSearchResult> versions = allPackages.get(name);
      if (versions == null) {
        continue;
      }
      for (HeliumPackageSearchResult pkgInfo : versions) {
        if (pkgInfo.getPkg().getType() == HeliumPackage.Type.VISUALIZATION && pkgInfo.isEnabled()) {
          orderedVisualizationPackages.add(pkgInfo.getPkg());
          allPackages.remove(name);
          break;
        }
      }
    }

    // add enabled packages not in visOrder
    for (List<HeliumPackageSearchResult> pkgs : allPackages.values()) {
      for (HeliumPackageSearchResult pkg : pkgs) {
        if (pkg.getPkg().getType() == HeliumPackage.Type.VISUALIZATION && pkg.isEnabled()) {
          orderedVisualizationPackages.add(pkg.getPkg());
          break;
        }
      }
    }

    return orderedVisualizationPackages;
  }

  /**
   * Get enabled package list in order
   * @return
   */
  public List<String> getVisualizationPackageOrder() {
    List orderedPackageList = new LinkedList<>();
    List<HeliumPackage> packages = getVisualizationPackagesToBundle();

    for (HeliumPackage pkg : packages) {
      orderedPackageList.add(pkg.getName());
    }

    return orderedPackageList;
  }

  public void setVisualizationPackageOrder(List<String> orderedPackageList)
      throws IOException {
    heliumConf.setVisualizationDisplayOrder(orderedPackageList);

    // if package is visualization, rebuild bundle
    visualizationFactory.bundle(getVisualizationPackagesToBundle());

    save();
  }
}
