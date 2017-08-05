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
import org.apache.zeppelin.annotation.Experimental;
import org.apache.zeppelin.common.JsonSerializable;
import org.apache.zeppelin.dep.Repository;

import java.util.Map;

/**
 * Helium package definition
 */
@Experimental
public class HeliumPackage implements JsonSerializable {
  private static final Gson gson = new Gson();

  private HeliumType type;
  private String name;           // user friendly name of this application
  private String description;    // description
  private String artifact;       // artifact name e.g) groupId:artifactId:versionId
  private String className;      // entry point
  private String [][] resources; // resource classnames that requires
                                 // [[ .. and .. and .. ] or [ .. and .. and ..] ..]
  private String license;
  private String icon;
  private String published;

  private String groupId;        // get groupId of INTERPRETER type package
  private String artifactId;     // get artifactId of INTERPRETER type package

  private SpellPackageInfo spell;
  private Map<String, Object> config;

  public HeliumPackage(HeliumType type,
                       String name,
                       String description,
                       String artifact,
                       String className,
                       String[][] resources,
                       String license,
                       String icon) {
    this.type = type;
    this.name = name;
    this.description = description;
    this.artifact = artifact;
    this.className = className;
    this.resources = resources;
    this.license = license;
    this.icon = icon;
  }

  @Override
  public int hashCode() {
    return (type.toString() + artifact + className).hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof HeliumPackage)) {
      return false;
    }

    HeliumPackage info = (HeliumPackage) o;
    return type == info.type && artifact.equals(info.artifact) && className.equals(info.className);
  }

  public HeliumType getType() {
    return type;
  }

  public static boolean isBundleType(HeliumType type) {
    return (type == HeliumType.VISUALIZATION ||
        type == HeliumType.SPELL);
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getArtifact() {
    return artifact;
  }

  public String getClassName() {
    return className;
  }

  public String[][] getResources() {
    return resources;
  }

  public String getLicense() {
    return license;
  }

  public String getIcon() {
    return icon;
  }

  public String getPublishedDate() {
    return published;
  }

  public String getGroupId() {
    return groupId;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public SpellPackageInfo getSpellInfo() {
    return spell;
  }

  public Map<String, Object> getConfig() { return config; }

  public String toJson() {
    return gson.toJson(this);
  }

  public static HeliumPackage fromJson(String json) {
    return gson.fromJson(json, HeliumPackage.class);
  }
}
