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

/**
 * Property for registered interpreter
 */
public class DefaultInterpreterProperty {
  String envName;
  String propertyName;
  Object defaultValue;
  String description;
  InterpreterPropertyWidget widget;

  public DefaultInterpreterProperty(String envName, String propertyName, Object defaultValue,
                                String description, InterpreterPropertyWidget widget) {
    this.envName = envName;
    this.propertyName = propertyName;
    this.defaultValue = defaultValue;
    this.description = description;
    this.widget = widget;
  }

  public DefaultInterpreterProperty(Object defaultValue, String description,
      InterpreterPropertyWidget widget) {
    this(null, null, defaultValue, description, widget);
  }

  public String getEnvName() {
    return envName;
  }

  public void setEnvName(String envName) {
    this.envName = envName;
  }

  public String getPropertyName() {
    return propertyName;
  }

  public void setPropertyName(String propertyName) {
    this.propertyName = propertyName;
  }

  public Object getDefaultValue() {
    return defaultValue;
  }

  public void setDefaultValue(Object defaultValue) {
    this.defaultValue = defaultValue;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public InterpreterPropertyWidget getWidget() {
    return widget;
  }

  public void setWidget(InterpreterPropertyWidget widget) {
    this.widget = widget;
  }

  public int hashCode() {
    return this.toString().hashCode();
  }

  public boolean equals(Object o) {
    if (o == null) return false;
    return this.toString().equals(o.toString());
  }

  public Object getValue() {
    if (envName != null && !envName.isEmpty()) {
      String envValue = System.getenv().get(envName);
      if (envValue != null) {
        return envValue;
      }
    }

    if (propertyName != null && !propertyName.isEmpty()) {
      String propValue = System.getProperty(propertyName);
      if (propValue != null) {
        return propValue;
      }
    }
    return defaultValue;
  }

  @Override
  public String toString() {
    return String.format("{envName=%s, propertyName=%s, defaultValue=%s, description=%20s, " +
            "type=%s}", envName, propertyName, defaultValue, description, widget);
  }
}
