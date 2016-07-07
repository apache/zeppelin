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

import java.util.*;

import org.apache.zeppelin.dep.Dependency;
import org.apache.zeppelin.notebook.utility.IdHashes;

/**
 * Interpreter settings
 */
public class InterpreterSetting {
  private static final String SHARED_PROCESS = "shared_process";
  private String id;
  private String name;
  private String group;
  private String refGroup = null;
  private String description;
  private Properties properties;
  private transient InterpreterGroupFactory interpreterGroupFactory;
  private transient String InterpreterPath;

  // use 'interpreterGroup' as a field name to keep backward compatibility of
  // conf/interpreter.json file format
  private List<InterpreterInfo> interpreterGroup;
  private transient Map<String, InterpreterGroup> interpreterGroupRef = new HashMap<>();
  private List<Dependency> dependencies;
  private InterpreterOption option;
  private transient String path;

  public InterpreterSetting(String id, String name, String group, String refGroup,
      List<InterpreterInfo> interpreterInfos, Properties properties, List<Dependency> dependencies,
      InterpreterOption option, String path) {
    this.id = id;
    this.name = name;
    this.group = group;
    this.refGroup = refGroup;
    this.interpreterGroup = interpreterInfos;
    this.properties = properties;
    this.dependencies = dependencies;
    this.option = option;
    this.path = path;
  }

  public InterpreterSetting(String name, String group, List<InterpreterInfo> interpreterInfos,
      Properties properties, List<Dependency> dependencies, InterpreterOption option, String path) {
    this(generateId(), name, group, null, interpreterInfos, properties, dependencies, option, path);
  }

  public InterpreterSetting(InterpreterSetting o) {
    this(generateId(), o.getName(), o.getGroup(), o.getRefGroup(), o.getInterpreterInfos(), o.getProperties(),
        o.getDependencies(), o.getOption(), o.getPath());
    this.refGroup = o.getRefGroup();
  }

  public String id() {
    return id;
  }

  public void regenerateId() {
    this.id = generateId();
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

  public void setGroup(String group) {
    this.group = group;
  }

  public String getGroup() {
    return group;
  }

  public String getRefGroup() {
    return refGroup;
  }

  public void setRefGroup(String refGroup) {
    this.refGroup = refGroup;
  }

  private String getInterpreterProcessKey(String noteId) {
    if (getOption().isExistingProcess) {
      return Constants.EXISTING_PROCESS;
    } else if (getOption().isPerNoteProcess()) {
      return noteId;
    } else {
      return SHARED_PROCESS;
    }
  }

  public InterpreterGroup getInterpreterGroup(String noteId) {
    String key = getInterpreterProcessKey(noteId);
    synchronized (interpreterGroupRef) {
      if (!interpreterGroupRef.containsKey(key)) {
        String interpreterGroupId = id() + ":" + key;
        InterpreterGroup intpGroup =
            interpreterGroupFactory.createInterpreterGroup(interpreterGroupId, getOption());
        interpreterGroupRef.put(key, intpGroup);
      }
      return interpreterGroupRef.get(key);
    }
  }

  public Collection<InterpreterGroup> getAllInterpreterGroups() {
    synchronized (interpreterGroupRef) {
      return new LinkedList<>(interpreterGroupRef.values());
    }
  }

  public void closeAndRemoveInterpreterGroup(String noteId) {
    String key = getInterpreterProcessKey(noteId);
    InterpreterGroup groupToRemove;
    synchronized (interpreterGroupRef) {
      groupToRemove = interpreterGroupRef.remove(key);
    }

    if (groupToRemove != null) {
      groupToRemove.close();
      groupToRemove.destroy();
    }
  }

  public void closeAndRmoveAllInterpreterGroups() {
    synchronized (interpreterGroupRef) {
      HashSet<String> groupsToRemove = new HashSet<>(interpreterGroupRef.keySet());
      for (String key : groupsToRemove) {
        closeAndRemoveInterpreterGroup(key);
      }
    }
  }

  public Properties getProperties() {
    return properties;
  }

  public void setProperties(Properties properties) {
    this.properties = properties;
  }

  public List<Dependency> getDependencies() {
    if (dependencies == null) {
      return new LinkedList<>();
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

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public List<InterpreterInfo> getInterpreterInfos() {
    return interpreterGroup;
  }

  public void setInterpreterGroupFactory(InterpreterGroupFactory interpreterGroupFactory) {
    this.interpreterGroupFactory = interpreterGroupFactory;
  }

  public void appendDependencies(List<Dependency> dependencies) {
    for (Dependency dependency : dependencies) {
      if (!this.dependencies.contains(dependency)) {
        this.dependencies.add(dependency);
      }
    }
  }

  public void setInterpreterOption(InterpreterOption interpreterOption) {
    this.option = interpreterOption;
  }

  public void updateProperties(Properties p) {
    this.properties.putAll(p);
  }
}
