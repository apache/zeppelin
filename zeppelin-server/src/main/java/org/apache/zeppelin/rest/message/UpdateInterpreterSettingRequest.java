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
import java.util.Map;

import org.apache.zeppelin.dep.Dependency;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.InterpreterProperty;

/**
 * UpdateInterpreterSetting rest api request message.
 */
public class UpdateInterpreterSettingRequest {

  private final Map<String, InterpreterProperty> properties;
  private final List<Dependency> dependencies;
  private final InterpreterOption option;

  public UpdateInterpreterSettingRequest(Map<String, InterpreterProperty> properties,
      List<Dependency> dependencies, InterpreterOption option) {
    this.properties = properties;
    this.dependencies = dependencies;
    this.option = option;
  }

  /**
   * Retrieves the properties of the interpreter and, if the property type is "number"
   * and its value is a double that represents a whole number, converts that value to an integer.
   *
   * @return A map of the interpreter's properties with possible modifications to the numeric properties.
   */
  public Map<String, InterpreterProperty> getProperties() {
    properties.forEach((key, property) -> {
      if (isNumberType(property) && isWholeNumberDouble(property.getValue())) {
        convertDoubleToInt(property);
      }
    });
    return properties;
  }

  /**
   * Checks if the property type is "number".
   *
   * @param property The InterpreterProperty to check.
   * @return true if the property type is "number", false otherwise.
   */
  private boolean isNumberType(InterpreterProperty property) {
    return "number".equals(property.getType());
  }

  /**
   * Checks if the given value is a Double and represents a whole number.
   *
   * @param value The object to check.
   * @return true if the value is a Double and a whole number, false otherwise.
   */
  private boolean isWholeNumberDouble(Object value) {
    if (value instanceof Double) {
      Double doubleValue = (Double) value;
      return doubleValue == Math.floor(doubleValue);
    }
    return false;
  }

  /**
   * Converts the value of the given property from a Double to an Integer if the Double represents a whole number.
   *
   * @param property The InterpreterProperty whose value will be converted.
   */
  private void convertDoubleToInt(InterpreterProperty property) {
    Double doubleValue = (Double) property.getValue();
    property.setValue(doubleValue.intValue());
  }

  public List<Dependency> getDependencies() {
    return dependencies;
  }

  public InterpreterOption getOption() {
    return option;
  }
}
