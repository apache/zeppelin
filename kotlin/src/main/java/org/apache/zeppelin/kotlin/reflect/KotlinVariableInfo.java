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

package org.apache.zeppelin.kotlin.reflect;

import java.lang.reflect.Field;

public class KotlinVariableInfo {
  private final String name;
  private final Object value;
  private final Field descriptor;

  public KotlinVariableInfo(String name, Object value, Field descriptor) {
    this.name = name;
    this.value = value;
    this.descriptor = descriptor;
  }

  public String getName() {
    return name;
  }

  public Object getValue() {
    return value;
  }

  public Field getDescriptor() {
    return descriptor;
  }

  public String kotlinTypeName() {
    return KotlinReflectUtil.kotlinTypeName(value);
  }

  @Override
  public String toString() {
    return name + ": " + kotlinTypeName() + " = " + getValue();
  }
}
