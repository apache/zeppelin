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

package org.apache.zeppelin.interpreter;

import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import org.apache.zeppelin.dep.Dependency;
import org.apache.zeppelin.notebook.utility.IdHashes;

/**
 * Interpreter settings
 */
public class InterpreterSetting {
  private String id;
  private String name;
  private String group;
  private String description;
  private Properties properties;

  // use 'interpreterGroup' as a field name to keep backward compatibility of
  // conf/interpreter.json file format
  private List<InterpreterInfo> interpreterGroup;
  private transient InterpreterGroup interpreterGroupRef;
  private List<Dependency> dependencies;
  private InterpreterOption option;

  public InterpreterSetting(String id,
      String name,
      String group,
      List<InterpreterInfo> interpreterInfos,
      Properties properties,
      List<Dependency> dependencies,
      InterpreterOption option) {
    this.id = id;
    this.name = name;
    this.group = group;
    this.interpreterGroup = interpreterInfos;
    this.properties = properties;
    this.dependencies = dependencies;
    this.option = option;
  }

  public InterpreterSetting(String name,
      String group,
      List<InterpreterInfo> interpreterInfos,
      Properties properties,
      List<Dependency> dependencies,
      InterpreterOption option) {
    this(generateId(), name, group, interpreterInfos, properties, dependencies, option);
  }

  /**
   * Information of interpreters in this interpreter setting.
   * this will be serialized for conf/interpreter.json and REST api response.
   */
  public static class InterpreterInfo {
    private final String name;
    private final String className;

    public InterpreterInfo(String className, String name) {
      this.className = className;
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public String getClassName() {
      return className;
    }
  }

  public String id() {
    return id;
  }

  private static String generateId() {
    return IdHashes.encode(System.currentTimeMillis() + new Random().nextInt());
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String desc) {
    this.description = desc;
  }

  public String getGroup() {
    return group;
  }

  public InterpreterGroup getInterpreterGroup() {
    return interpreterGroupRef;
  }

  public void setInterpreterGroup(InterpreterGroup interpreterGroup) {
    this.interpreterGroupRef = interpreterGroup;
  }

  public Properties getProperties() {
    return properties;
  }

  public void setProperties(Properties properties) {
    this.properties = properties;
  }

  public List<Dependency> getDependencies() {
    if (dependencies == null) {
      return new LinkedList<Dependency>();
    }
    return dependencies;
  }

  public void setDependencies(List<Dependency> dependencies) {
    this.dependencies = dependencies;
  }

  public InterpreterOption getOption() {
    if (option == null) {
      option = new InterpreterOption();
    }

    return option;
  }

  public void setOption(InterpreterOption option) {
    this.option = option;
  }

  public List<InterpreterInfo> getInterpreterInfos() {
    return interpreterGroup;
  }
}
