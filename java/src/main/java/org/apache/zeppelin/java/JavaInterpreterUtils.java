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

package org.apache.zeppelin.java;

import java.util.Map;
import java.util.stream.Collectors;

/** Java interpreter utility methods */
public class JavaInterpreterUtils {

  /**
   * Convert a map to %table display system to leverage Zeppelin's built in visualization
   *
   * @param keyName Key column name
   * @param valueName Value column name
   * @param rows Map of keys and values
   * @return Zeppelin %table
   */
  public static String displayTableFromSimpleMap(String keyName, String valueName, Map<?, ?> rows) {
    String table = "%table\n";
    table += keyName + "\t" + valueName + "\n";
    table +=
        rows.entrySet()
            .stream()
            .map(e -> e.getKey() + "\t" + e.getValue())
            .collect(Collectors.joining("\n"));
    return table;
  }
}
