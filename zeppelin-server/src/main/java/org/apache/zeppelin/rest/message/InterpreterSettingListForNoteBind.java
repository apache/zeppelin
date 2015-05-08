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

package org.apache.zeppelin.rest.message;

import java.util.List;

import org.apache.zeppelin.interpreter.Interpreter;

/**
 * InterpreterSetting information for binding
 */
public class InterpreterSettingListForNoteBind {
  String id;
  String name;
  String group;
  private boolean selected;
  private List<Interpreter> interpreters;

  public InterpreterSettingListForNoteBind(String id, String name,
      String group, List<Interpreter> interpreters, boolean selected) {
    super();
    this.id = id;
    this.name = name;
    this.group = group;
    this.interpreters = interpreters;
    this.selected = selected;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getGroup() {
    return group;
  }

  public void setGroup(String group) {
    this.group = group;
  }

  public List<Interpreter> getInterpreterNames() {
    return interpreters;
  }

  public void setInterpreterNames(List<Interpreter> interpreters) {
    this.interpreters = interpreters;
  }

  public boolean isSelected() {
    return selected;
  }

  public void setSelected(boolean selected) {
    this.selected = selected;
  }

}
