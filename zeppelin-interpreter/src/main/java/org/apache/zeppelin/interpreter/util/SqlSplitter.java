/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.interpreter.util;


import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Split text into multiple sql statements.
 *
 * inspired from https://github.com/postgres/pgadmin3/blob/794527d97e2e3b01399954f3b79c8e2585b908dd/
 * pgadmin/dlg/dlgProperty.cpp#L999-L1045
 *
 *
 */
public class SqlSplitter {

  // it must be either 1 character or 2 character
  private Set<String> singleLineCommentPrefixList = new HashSet<>();

  public SqlSplitter() {
    this.singleLineCommentPrefixList.add("--");
  }

  /**
   *
   * @param additionalSingleCommentPrefixList Besides the standard single line comment prefix '--',
   *                                          you can also specify other characters for sql dialect
   */
  public SqlSplitter(String... additionalSingleCommentPrefixList) {
    for (String singleLineCommentPrefix : additionalSingleCommentPrefixList) {
      if (singleLineCommentPrefix.length() > 2) {
        throw new RuntimeException("Invalid singleLineCommentPrefix: " + singleLineCommentPrefix +
                ", it is at most 2 characters");
      }
      this.singleLineCommentPrefixList.add(singleLineCommentPrefix);
    }
  }

  public List<String> splitSql(String text) {
    text = text.trim();
    List<String> queries = new ArrayList<>();
    StringBuilder query = new StringBuilder();
    char character;

    boolean multiLineComment = false;
    boolean singleLineComment = false;
    boolean singleQuoteString = false;
    boolean doubleQuoteString = false;

    for (int index = 0; index < text.length(); index++) {
      character = text.charAt(index);

      // end of single line comment
      if (singleLineComment && (character == '\n')) {
        singleLineComment = false;
        if (query.toString().trim().isEmpty()) {
          continue;
        }
      }

      // end of multiple line comment
      if (multiLineComment && (index - 1) >= 0 && text.charAt(index - 1) == '/'
              && (index - 2) >= 0 && text.charAt(index - 2) == '*') {
        multiLineComment = false;
      }

      if (character == '\'') {
        if (singleQuoteString) {
          singleQuoteString = false;
        } else if (!doubleQuoteString) {
          singleQuoteString = true;
        }
      }

      if (character == '"') {
        if (doubleQuoteString && index > 0) {
          doubleQuoteString = false;
        } else if (!singleQuoteString) {
          doubleQuoteString = true;
        }
      }

      if (!singleQuoteString && !doubleQuoteString && !multiLineComment && !singleLineComment
              && text.length() > (index + 1)) {
        if (isSingleLineComment(text.charAt(index), text.charAt(index + 1))) {
          singleLineComment = true;
        } else if (text.charAt(index) == '/' && text.length() > (index + 2)
                && text.charAt(index + 1) == '*' && text.charAt(index + 2) != '+') {
          multiLineComment = true;
        }
      }

      if (character == ';' && !singleQuoteString && !doubleQuoteString && !multiLineComment && !singleLineComment) {
        // meet the end of semicolon
        if (!query.toString().trim().isEmpty()) {
          queries.add(query.toString().trim());
          query = new StringBuilder();
        }
      } else if (index == (text.length() - 1)) {
        // meet the last character
        if (!singleLineComment && !multiLineComment) {
          query.append(character);
        }
        if (!query.toString().trim().isEmpty()) {
          queries.add(query.toString().trim());
          query = new StringBuilder();
        }
      } else if (!singleLineComment && !multiLineComment) {
        // normal case, not in single line comment and not in multiple line comment
        query.append(character);
      }
    }

    return queries;
  }

  private boolean isSingleLineComment(char curChar, char nextChar) {
    for (String singleCommentPrefix : singleLineCommentPrefixList) {
      if (singleCommentPrefix.length() == 1) {
        if (curChar == singleCommentPrefix.charAt(0)) {
          return true;
        }
      }
      if (singleCommentPrefix.length() == 2) {
        if (curChar == singleCommentPrefix.charAt(0) &&
                nextChar == singleCommentPrefix.charAt(1)) {
          return true;
        }
      }
    }
    return false;
  }
}
