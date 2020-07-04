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

package org.apache.zeppelin.kotlin.script;

import org.jetbrains.annotations.NotNull;
import kotlin.reflect.KFunction;

public class KotlinFunctionInfo implements Comparable<KotlinFunctionInfo> {
  private final KFunction<?> function;

  public KotlinFunctionInfo(KFunction<?> function) {
    this.function = function;
  }

  public KFunction<?> getFunction() {
    return function;
  }

  public String getName() {
    return function.getName();
  }

  public String toString(boolean shortenTypes) {
    if (shortenTypes) {
      return KotlinReflectUtil.shorten(toString());
    }
    return toString();
  }

  @Override
  public String toString() {
    return KotlinReflectUtil.functionSignature(function);
  }

  @Override
  public int compareTo(@NotNull KotlinFunctionInfo f) {
    return this.toString().compareTo(f.toString());
  }

  @Override
  public int hashCode() {
    return this.toString().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof KotlinFunctionInfo) {
      return this.toString().equals(obj.toString());
    }
    return false;
  }
}
