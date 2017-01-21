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

import java.util.*;

/**
 * Helium config. This object will be persisted to conf/heliumc.conf
 */
public class HeliumConf {
  List<HeliumRegistry> registry = new LinkedList<>();

  // enabled packages {name, version}
  Map<String, String> enabled = Collections.synchronizedMap(new HashMap<String, String>());

  // enabled visualization package display order
  List<String> visualizationDisplayOrder = new LinkedList<>();


  public List<HeliumRegistry> getRegistry() {
    return registry;
  }

  public void setRegistry(List<HeliumRegistry> registry) {
    this.registry = registry;
  }

  public Map<String, String> getEnabledPackages() {
    return new HashMap<>(enabled);
  }

  public void enablePackage(HeliumPackage pkg) {
    enablePackage(pkg.getName(), pkg.getArtifact());
  }

  public void enablePackage(String name, String artifact) {
    enabled.put(name, artifact);
  }

  public void disablePackage(HeliumPackage pkg) {
    disablePackage(pkg.getName());
  }

  public void disablePackage(String name) {
    enabled.remove(name);
  }

  public List<String> getVisualizationDisplayOrder() {
    if (visualizationDisplayOrder == null) {
      return new LinkedList<String>();
    } else {
      return visualizationDisplayOrder;
    }
  }

  public void setVisualizationDisplayOrder(List<String> orderedPackageList) {
    visualizationDisplayOrder = orderedPackageList;
  }
}
