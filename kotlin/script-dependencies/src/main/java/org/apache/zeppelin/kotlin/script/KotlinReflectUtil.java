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

import kotlin.reflect.KFunction;

/**
 * Util class for pretty-printing Kotlin variables and functions.
 */
public class KotlinReflectUtil {
  public static final String SCRIPT_PREFIX = "zeppelin";

  private static final String functionSignatureRegex = "Line_\\d+_" + SCRIPT_PREFIX + "\\.";

  public static String functionSignature(KFunction<?> function) {
    return function.toString().replaceAll(functionSignatureRegex, "");
  }

  public static String shorten(String name) {
    if (name == null) {
      return null;
    }
    // kotlin.collections.List<kotlin.Int> -> List<Int>
    return name.replaceAll("(\\b[_a-zA-Z$][_a-zA-Z0-9$]*\\b\\.)+", "");
  }
}
