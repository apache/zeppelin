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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Manages helium packages
 */
public class Helium {
  Logger logger = LoggerFactory.getLogger(Helium.class);
  private List<HeliumRegistry> registry = new LinkedList<>();

  private final HeliumConf heliumConf;
  private final String heliumConfPath;
  private final String defaultLocalRegistryPath;
  private final Gson gson;

  public Helium(String heliumConfPath, String defaultLocalRegistryPath) throws IOException {
    this.heliumConfPath = heliumConfPath;
    this.defaultLocalRegistryPath = defaultLocalRegistryPath;

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

  private synchronized HeliumConf loadConf(String path) throws IOException {
    File heliumConfFile = new File(path);
    if (!heliumConfFile.isFile()) {
      logger.warn("{} does not exists", path);
      HeliumConf conf = new HeliumConf();
      LinkedList<HeliumRegistry> defaultRegistry = new LinkedList<>();
      defaultRegistry.add(new HeliumLocalRegistry("local", defaultLocalRegistryPath));
      conf.setRegistry(defaultRegistry);
      this.registry = conf.getRegistry();
      return conf;
    } else {
      String jsonString = FileUtils.readFileToString(heliumConfFile);
      HeliumConf conf = gson.fromJson(jsonString, HeliumConf.class);
      this.registry = conf.getRegistry();
      return conf;
    }
  }

  public synchronized void save() throws IOException {
    String jsonString;
    synchronized (registry) {
      heliumConf.setRegistry(registry);
      jsonString = gson.toJson(heliumConf);
    }

    File heliumConfFile = new File(heliumConfPath);
    if (!heliumConfFile.exists()) {
      heliumConfFile.createNewFile();
    }

    FileUtils.writeStringToFile(heliumConfFile, jsonString);
  }

  public List<HeliumPackageSearchResult> getAllPackageInfo() {
    List<HeliumPackageSearchResult> list = new LinkedList<>();
    synchronized (registry) {
      for (HeliumRegistry r : registry) {
        try {
          for (HeliumPackage pkg : r.getAll()) {
            list.add(new HeliumPackageSearchResult(r.name(), pkg));
          }
        } catch (IOException e) {
          logger.error(e.getMessage(), e);
        }
      }
    }
    return list;
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

    for (HeliumPackageSearchResult pkg : getAllPackageInfo()) {
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
    }

    suggestion.sort();
    return suggestion;
  }
}
