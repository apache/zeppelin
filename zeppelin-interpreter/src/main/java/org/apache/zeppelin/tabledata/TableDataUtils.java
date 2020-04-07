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

package org.apache.zeppelin.tabledata;


import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TableDataUtils {

  /**
   * Replace '\t','\r\n','\n' which represent field delimiter and row delimiter with while space.
   * @param column
   * @column
   */
  public static String normalizeColumn(String column) {
    if (column == null) {
      return "null";
    }
    return column.replace("\t", " ").replace("\r\n", " ").replace("\n", " ");
  }

  /**
   * Convert obj to String first, convert it to empty string it is null.
   * @param obj
   * @column
   */
  public static String normalizeColumn(Object obj) {
    return normalizeColumn(obj == null ? "null" : obj.toString());
  }

  public static List<String> normalizeColumns(List<Object> columns) {
    return columns.stream()
            .map(TableDataUtils::normalizeColumn)
            .collect(Collectors.toList());
  }

  public static List<String> normalizeColumns(Object[] columns) {
    return Arrays.stream(columns)
            .map(TableDataUtils::normalizeColumn)
            .collect(Collectors.toList());
  }
}
