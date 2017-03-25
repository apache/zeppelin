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

import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * Information of interpreters in this interpreter setting.
 * this will be serialized for conf/interpreter.json and REST api response.
 */
public class InterpreterInfo {
  private String name;
  @SerializedName("class") private String className;
  private boolean defaultInterpreter = false;
  private Map<String, Object> editor;

  public InterpreterInfo(String className, String name, boolean defaultInterpreter,
      Map<String, Object> editor) {
    this.className = className;
    this.name = name;
    this.defaultInterpreter = defaultInterpreter;
    this.editor = editor;
  }

  public String getName() {
    return name;
  }

  public String getClassName() {
    return className;
  }

  public void setName(String name) {
    this.name = name;
  }

  boolean isDefaultInterpreter() {
    return defaultInterpreter;
  }

  public Map<String, Object> getEditor() {
    return editor;
  }

  public void setEditor(Map<String, Object> editor) {
    this.editor = editor;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof InterpreterInfo)) {
      return false;
    }
    InterpreterInfo other = (InterpreterInfo) obj;

    boolean sameName =
        null == getName() ? null == other.getName() : getName().equals(other.getName());
    boolean sameClassName = null == getClassName() ?
        null == other.getClassName() :
        getClassName().equals(other.getClassName());
    boolean sameIsDefaultInterpreter = defaultInterpreter == other.isDefaultInterpreter();

    return sameName && sameClassName && sameIsDefaultInterpreter;
  }
}
