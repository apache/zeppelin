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
  private ArrayList<String> statement;
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
    statement = new ArrayList<>();
    builder = new StringBuilder();
    multiLineComment = false;
    singleLineComment = false;
    quoteString = false;
    doubleQuoteString = false;
    currIndex = 0;
  }

  private void checkCommentsAndQuotes() {
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
      checkCommentsAndQuotes();

      if (currChar == ';' && !quoteString && !doubleQuoteString && !multiLineComment
          && !singleLineComment) {
        statement.add(StringUtils.trim(builder.toString()));
        builder = new StringBuilder();
      } else if (currIndex == sql.length() - 1) {
        builder.append(currChar);
        statement.add(StringUtils.trim(builder.toString()));
      } else {
        builder.append(currChar);
      }
    }

    return statement;
  }

  public ArrayList<String> resourcePoolReqs() {
    initialize();
    Boolean formingReq = false;

    for (currIndex = 0; currIndex < sql.length(); currIndex++) {
      currChar = sql.charAt(currIndex);
      checkCommentsAndQuotes();

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
        statement.add(builder.toString());
        builder = new StringBuilder();
      }
    }

    return statement;
  }

}
