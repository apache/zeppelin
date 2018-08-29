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

package org.apache.zeppelin.sap.universe;

import org.apache.commons.lang.StringUtils;

import java.util.OptionalInt;

/**
 * Data of universe query
 */
public class UniverseQuery {
  private String select;
  private String where;
  private UniverseInfo universeInfo;
  private boolean duplicatedRows = false;
  private OptionalInt maxRowsRetrieved;

  public UniverseQuery(String select, String where, UniverseInfo universeInfo,
                       boolean duplicatedRows, OptionalInt maxRowsRetrieved) {
    this.select = select;
    this.where = where;
    this.universeInfo = universeInfo;
    this.duplicatedRows = duplicatedRows;
    this.maxRowsRetrieved = maxRowsRetrieved;
  }

  public boolean isCorrect() {
    return StringUtils.isNotBlank(select) && universeInfo != null &&
        StringUtils.isNotBlank(universeInfo.getId())
        && StringUtils.isNotBlank(universeInfo.getName());
  }

  public String getSelect() {
    return select;
  }

  public String getWhere() {
    return where;
  }

  public UniverseInfo getUniverseInfo() {
    return universeInfo;
  }

  public boolean getDuplicatedRows() {
    return duplicatedRows;
  }

  public OptionalInt getMaxRowsRetrieved() {
    return maxRowsRetrieved;
  }
}
