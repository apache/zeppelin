/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.zeppelin.jdbc;

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;

public class SqlParser {
  private ArrayList<String> res;
  private StringBuilder builder;

  private Boolean multiLineComment;
  private Boolean singleLineComment;
  private Boolean quoteString;
  private Boolean doubleQuoteString;
  private final String sql;
  private int currIndex;
  private char currChar;

  public SqlParser(String sql) {
    this.sql = sql;
  }

  private void initialize() {
    res = new ArrayList<>();
    builder = new StringBuilder();
    multiLineComment = false;
    singleLineComment = false;
    quoteString = false;
    doubleQuoteString = false;
    currIndex = 0;
  }

  private void checkCommentsAndQuetes() {
    if (singleLineComment && (currChar == '\n' || currIndex == sql.length() - 1)) {
      singleLineComment = false;
    }

    if (multiLineComment && currChar == '/' && sql.charAt(currIndex - 1) == '*') {
      multiLineComment = false;
    }

    if (currChar == '\'') {
      if (quoteString) {
        quoteString = false;
      } else if (!doubleQuoteString) {
        quoteString = true;
      }
    }

    if (currChar == '"') {
      if (doubleQuoteString && currIndex > 0) {
        doubleQuoteString = false;
      } else if (!quoteString) {
        doubleQuoteString = true;
      }
    }

    if (!quoteString && !doubleQuoteString && !multiLineComment && !singleLineComment
        && sql.length() > currIndex + 1) {
      if (currChar == '-' && sql.charAt(currIndex + 1) == '-') {
        singleLineComment = true;
      } else if (currChar == '/' && sql.charAt(currIndex + 1) == '*') {
        multiLineComment = true;
      }
    }
  }

  public ArrayList<String> splitSqlQueries() {
    initialize();

    for (currIndex = 0; currIndex < sql.length(); currIndex++) {
      currChar = sql.charAt(currIndex);
      checkCommentsAndQuetes();

      if (currChar == ';' && !quoteString && !doubleQuoteString && !multiLineComment
          && !singleLineComment) {
        res.add(StringUtils.trim(builder.toString()));
        builder = new StringBuilder();
      } else if (currIndex == sql.length() - 1) {
        builder.append(currChar);
        res.add(StringUtils.trim(builder.toString()));
      } else {
        builder.append(currChar);
      }
    }

    return res;
  }

  public ArrayList<String> resourcePoolReqs() {
    initialize();
    Boolean formingReq = false;

    for (currIndex = 0; currIndex < sql.length(); currIndex++) {
      currChar = sql.charAt(currIndex);
      checkCommentsAndQuetes();

      if (currChar == '{' && sql.indexOf(JDBCInterpreter.POOL_REQ_PREFIX, currIndex) == currIndex &&
          !quoteString && !doubleQuoteString && !multiLineComment && !singleLineComment) {
        formingReq = true;
      }

      if (formingReq) {
        builder.append(currChar);
      }

      if (currChar == '}' && formingReq && !quoteString && !doubleQuoteString && !multiLineComment
          && !singleLineComment) {
        formingReq = false;
        res.add(builder.toString());
        builder = new StringBuilder();
      }
    }

    return res;
  }

}
