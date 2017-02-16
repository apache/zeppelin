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
 * Helium config. This object will be persisted to conf/helium.conf
 */
public class HeliumConf {
  // enabled packages {name, version}
  private Map<String, String> enabled = Collections.synchronizedMap(new HashMap<String, String>());

  // {artifact, {configKey, configValue}}
  private Map<String, Map<String, Object>> packageConfig =
      Collections.synchronizedMap(
          new HashMap<String, Map<String, Object>>());

  // enabled visualization package display order
  private List<String> bundleDisplayOrder = new LinkedList<>();

  public Map<String, String> getEnabledPackages() {
    return new HashMap<>(enabled);
  }

  public void enablePackage(HeliumPackage pkg) {
    enablePackage(pkg.getName(), pkg.getArtifact());
  }

  public void enablePackage(String name, String artifact) {
    enabled.put(name, artifact);
  }

  public void updatePackageConfig(String artifact,
                                  Map<String, Object> newConfig) {
    if (!packageConfig.containsKey(artifact)) {
      packageConfig.put(artifact,
          Collections.synchronizedMap(new HashMap<String, Object>()));
    }

    packageConfig.put(artifact, newConfig);
  }

  /**
   * @return versioned package config `{artifact, {configKey, configVal}}`
   */
  public Map<String, Map<String, Object>> getAllPackageConfigs () {
    return packageConfig;
  }

  public Map<String, Object> getPackagePersistedConfig(String artifact) {
    if (!packageConfig.containsKey(artifact)) {
      packageConfig.put(artifact,
          Collections.synchronizedMap(new HashMap<String, Object>()));
    }

    return packageConfig.get(artifact);
  }

  public void disablePackage(HeliumPackage pkg) {
    disablePackage(pkg.getName());
  }

  public void disablePackage(String name) {
    enabled.remove(name);
  }

  public List<String> getBundleDisplayOrder() {
    if (bundleDisplayOrder == null) {
      return new LinkedList<String>();
    } else {
      return bundleDisplayOrder;
    }
  }

  public void setBundleDisplayOrder(List<String> orderedPackageList) {
    bundleDisplayOrder = orderedPackageList;
  }
}
