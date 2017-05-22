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

import java.util.ArrayList;
import java.util.List;

/**
 * Types of interpreter properties
 */
public enum InterpreterPropertyType {

  TEXTAREA("textarea"),
  STRING("string"),
  NUMBER("number"),
  URL("url"),
  PASSWORD("password"),
  CHECKBOX("checkbox");

  private String value;

  InterpreterPropertyType(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static InterpreterPropertyType byValue(String value) {
    for (InterpreterPropertyType e : values()) {
      if (e.getValue().equals(value)) {
        return e;
      }
    }
    return null;
  }

  public static List<String> getTypes() {
    List<String> types = new ArrayList<>();
    InterpreterPropertyType[] values = values();
    for (InterpreterPropertyType interpreterPropertyType : values) {
      types.add(interpreterPropertyType.getValue());
    }
    return types;
  }
}
