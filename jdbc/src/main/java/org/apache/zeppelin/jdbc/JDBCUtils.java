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

package org.apache.zeppelin.jdbc;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.zeppelin.tabledata.ColumnDef;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static java.sql.Types.BIGINT;
import static java.sql.Types.DATE;
import static java.sql.Types.DECIMAL;
import static java.sql.Types.DOUBLE;
import static java.sql.Types.FLOAT;
import static java.sql.Types.INTEGER;
import static java.sql.Types.NUMERIC;
import static java.sql.Types.REAL;
import static java.sql.Types.SMALLINT;
import static java.sql.Types.TIME;
import static java.sql.Types.TIMESTAMP;
import static java.sql.Types.TIMESTAMP_WITH_TIMEZONE;
import static java.sql.Types.TIME_WITH_TIMEZONE;
import static java.sql.Types.TINYINT;

public class JDBCUtils {
  private static final String EMPTY_COLUMN_VALUE = "";
  private static final char WHITESPACE = ' ';
  private static final char NEWLINE = '\n';
  private static final char TAB = '\t';
  /**
   * For %table response replace Tab and Newline characters from the content.
   */
  public static String replaceReservedChars(String str) {
    if (str == null) {
      return EMPTY_COLUMN_VALUE;
    }
    return str.replace(TAB, WHITESPACE).replace(NEWLINE, WHITESPACE);
  }


  public static String getResults(ResultSet resultSet, MutableBoolean isComplete, int maxResult)
      throws SQLException {
    ResultSetMetaData md = resultSet.getMetaData();
    StringBuilder msg;
    msg = new StringBuilder();

    for (int i = 1; i < md.getColumnCount() + 1; i++) {
      if (i > 1) {
        msg.append(TAB);
      }
      msg.append(replaceReservedChars(md.getColumnName(i)));
    }
    msg.append(NEWLINE);

    int displayRowCount = 0;
    while (resultSet.next()) {
      if (displayRowCount >= maxResult) {
        isComplete.setValue(false);
        break;
      }
      for (int i = 1; i < md.getColumnCount() + 1; i++) {
        Object resultObject;
        String resultValue;
        resultObject = resultSet.getObject(i);
        if (resultObject == null) {
          resultValue = "null";
        } else {
          resultValue = resultSet.getString(i);
        }
        msg.append(replaceReservedChars(resultValue));
        if (i != md.getColumnCount()) {
          msg.append(TAB);
        }
      }
      msg.append(NEWLINE);
      displayRowCount++;
    }
    return msg.toString();
  }

  public static List<ColumnDef.TYPE> getResultColumnTypes(ResultSet resultSet) throws SQLException {
    List<ColumnDef.TYPE> columnTypes = new ArrayList<>();
    ResultSetMetaData md = resultSet.getMetaData();
    for (int i = 1; i < md.getColumnCount() + 1; i++) {
      final ColumnDef.TYPE columnType = castSqlTypeToColumnType(md.getColumnType(i));
      columnTypes.add(columnType);
    }
    return columnTypes;
  }

  public static ColumnDef.TYPE castSqlTypeToColumnType(int t) {
    switch(t) {
      case DATE:
      case TIME:
      case TIME_WITH_TIMEZONE:
      case TIMESTAMP:
      case TIMESTAMP_WITH_TIMEZONE:
        return ColumnDef.TYPE.DATE;
      case BIGINT:
      case DECIMAL:
      case DOUBLE:
      case FLOAT:
      case INTEGER:
      case NUMERIC:
      case REAL:
      case TINYINT:
      case SMALLINT:
        return ColumnDef.TYPE.NUMBER;
      default:
        return ColumnDef.TYPE.STRING;
    }
  }

  /*
  inspired from https://github.com/postgres/pgadmin3/blob/794527d97e2e3b01399954f3b79c8e2585b908dd/
  pgadmin/dlg/dlgProperty.cpp#L999-L1045
 */
  public static ArrayList<String> splitSqlQueries(String sql) {
    ArrayList<String> queries = new ArrayList<>();
    StringBuilder query = new StringBuilder();
    char character;

    Boolean multiLineComment = false;
    Boolean singleLineComment = false;
    Boolean quoteString = false;
    Boolean doubleQuoteString = false;

    for (int item = 0; item < sql.length(); item++) {
      character = sql.charAt(item);

      if (singleLineComment && (character == '\n' || item == sql.length() - 1)) {
        singleLineComment = false;
      }

      if (multiLineComment && character == '/' && sql.charAt(item - 1) == '*') {
        multiLineComment = false;
      }

      if (character == '\'') {
        if (quoteString) {
          quoteString = false;
        } else if (!doubleQuoteString) {
          quoteString = true;
        }
      }

      if (character == '"') {
        if (doubleQuoteString && item > 0) {
          doubleQuoteString = false;
        } else if (!quoteString) {
          doubleQuoteString = true;
        }
      }

      if (!quoteString && !doubleQuoteString && !multiLineComment && !singleLineComment
          && sql.length() > item + 1) {
        if (character == '-' && sql.charAt(item + 1) == '-') {
          singleLineComment = true;
        } else if (character == '/' && sql.charAt(item + 1) == '*') {
          multiLineComment = true;
        }
      }

      if (character == ';' && !quoteString && !doubleQuoteString && !multiLineComment
          && !singleLineComment) {
        queries.add(StringUtils.trim(query.toString()));
        query = new StringBuilder();
      } else if (item == sql.length() - 1) {
        query.append(character);
        queries.add(StringUtils.trim(query.toString()));
      } else {
        query.append(character);
      }
    }

    return queries;
  }

  public static boolean isDDLCommand(int updatedCount, int columnCount) throws SQLException {
    return updatedCount < 0 && columnCount <= 0;
  }

}
