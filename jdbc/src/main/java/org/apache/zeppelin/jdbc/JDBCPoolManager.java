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
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.resource.ResourcePool;
import org.apache.zeppelin.resource.WellKnownResourceName;
import org.apache.zeppelin.tabledata.ColumnDef;
import org.apache.zeppelin.tabledata.InterpreterResultTableData;
import org.apache.zeppelin.tabledata.Row;
import org.apache.zeppelin.tabledata.TableData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class JDBCPoolManager {
  private static Logger logger = LoggerFactory.getLogger(JDBCPoolManager.class);

  private static final String PARAGRAPH_ID_KEY = "paragraph_id=";
  private static final String NOTE_ID_KEY = "note_id=";
  private static final Pattern PARAGRAPH_ID_PATTERN = Pattern.compile("(\\d|_|-)+");
  private static final Pattern NOTE_ID_PATTERN = Pattern.compile("(\\d|_|-|[a-zA-Z])+");
  // the table name must begin with a letter
  private static final String SQL_NAME_PREFIX = "p";

  static String getParagraphId(String poolReq) {
    final int pIdIndex = poolReq.indexOf(PARAGRAPH_ID_KEY);
    Matcher m = PARAGRAPH_ID_PATTERN.matcher(
        poolReq.substring(pIdIndex + PARAGRAPH_ID_KEY.length()));
    if (!m.find()) {
      logger.error("Can't eject paragraph_id from pool request {}", poolReq);
    }
    return m.group();
  }

  static String getNoteId(String poolReq, InterpreterContext context) {
    if (poolReq.contains(NOTE_ID_KEY)) {
      final int nIdIndex = poolReq.indexOf(NOTE_ID_KEY);
      Matcher m = NOTE_ID_PATTERN.matcher(
          poolReq.substring(nIdIndex + NOTE_ID_KEY.length()));
      if (!m.find()) {
        logger.error("Can't eject note_id from pool request {}", poolReq);
      }
      return m.group();
    } else {
      return context.getNoteId();
    }
  }

  private static TableData getTableDataFromResourcePool(ResourcePool resourcePool,
                                                        String noteId, String paragraphId) {
    final String str = "" + resourcePool.get(noteId, paragraphId,
        WellKnownResourceName.ZeppelinTableResult.toString()).get();
    InterpreterResultMessage msg = InterpreterResult.getMsgsFromString(str).get(0);
    return new InterpreterResultTableData(msg);
  }

  private static List<String> getColumnNamesWithTypes(TableData tableData, String stringType) {
    final ColumnDef[] columns = tableData.columns();
    List<String> columDefs = new LinkedList<>();
    for (final ColumnDef column : columns) {
      columDefs.add(column.name() + " " + stringType);
    }
    return columDefs;
  }

  private static String getSqlDropTableReq(String tableName) {
    return "DROP TABLE IF EXISTS " + tableName + ";";
  }

  private static String getSqlCreateTableReq(List<String> columDefs, String tableName) {
    return "CREATE TABLE " + tableName + " (" + StringUtils.join(columDefs, ", ") + ");";
  }

  private static List<String> getSqlInsertReqs(TableData tableData, String tableName) {
    List<String> reqs = new LinkedList<>();
    for (Iterator<Row> iterator = tableData.rows(); iterator.hasNext(); ) {
      Row row = iterator.next();
      reqs.add("INSERT INTO " + tableName + " VALUES ("
          + StringUtils.join(
          Arrays.stream(row.get())
              .map(x -> "'" + x + "'")
              .collect(Collectors.toList()),
          ", ")
          + ");");
    }
    return reqs;
  }

  static String preparePoolData(String sqlReq, Statement statement,
                                InterpreterContext context, String stringType,
                                Integer insertRowNumber, List<String> poolReqs) {
    final Set<String> handledTables = new HashSet<>();
    String newSqlReq = sqlReq;

    for (final String poolReq : poolReqs) {
      final String poolReqWithoutWhitespaces = poolReq.replaceAll("\\s", "");
      final String paragraphId = getParagraphId(poolReqWithoutWhitespaces);
      final String noteId = getNoteId(poolReqWithoutWhitespaces, context);

      ResourcePool resourcePool = context.getResourcePool();
      TableData tableData = getTableDataFromResourcePool(resourcePool, noteId, paragraphId);

      List<String> columnDefs = getColumnNamesWithTypes(tableData, stringType);
      // '-' is not allowed in sql table names
      final String sqlTableName = SQL_NAME_PREFIX +
          paragraphId.replace("-", "");

      final boolean isTableAlreadyHandled = handledTables.contains(sqlTableName);
      if (!isTableAlreadyHandled) {
        try {
          final String sqlDrop = getSqlDropTableReq(sqlTableName);
          statement.addBatch(sqlDrop);

          final String sqlCreate = getSqlCreateTableReq(columnDefs, sqlTableName);
          statement.addBatch(sqlCreate);

          final List<String> sqlInserts = getSqlInsertReqs(tableData, sqlTableName);
          int counter = 0;
          for (final String sqlInsert : sqlInserts) {
            if (counter > insertRowNumber) {
              statement.executeBatch();
              counter = 0;
            }
            statement.addBatch(sqlInsert);
            counter++;
          }

          statement.executeBatch();
        } catch (SQLException e) { /*ignored*/ }

        // replace resource pool expression with real table\
        handledTables.add(sqlTableName);
        newSqlReq = newSqlReq.replace(poolReq, sqlTableName);
      }
    }

    return newSqlReq;
  }
}
