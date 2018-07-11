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

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class JDBCPoolManager {
  private static final String PARAGRAPH_ID_KEY = "paragraph_id=";
  private static final String NOTE_ID_KEY = "note_id=";
  // the table name must begin with a letter
  private static final String SQL_NAME_PREFIX = "p";

  private static String getPoolExpression(String sqlReq) {
    final int poolReqBegIndex = sqlReq.indexOf("{ResourcePool");
    final int poolReqEndIndex = sqlReq.indexOf("}", poolReqBegIndex);
    return sqlReq.substring(poolReqBegIndex, poolReqEndIndex + 1);
  }

  private static String getParagraphId(String poolExp) {
    final int pIdIndex = poolExp.indexOf(PARAGRAPH_ID_KEY);
    return poolExp.substring(pIdIndex + PARAGRAPH_ID_KEY.length(), poolExp.length() - 1);
  }

  private static String getNoteId(String poolExp, InterpreterContext context) {
    if (poolExp.contains(NOTE_ID_KEY)) {
      final int nIdBegIndex = poolExp.indexOf(NOTE_ID_KEY);
      final int nIdEndIndex = poolExp.indexOf(".", nIdBegIndex);
      return poolExp.substring(nIdBegIndex + NOTE_ID_KEY.length(), nIdEndIndex);
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


  public static String preparePoolData(String sqlReq, Statement statement,
                                       InterpreterContext context, String stringType) {
    final String poolExp = getPoolExpression(sqlReq);
    final String paragraphId = getParagraphId(poolExp);
    final String noteId = getNoteId(poolExp, context);

    ResourcePool resourcePool = context.getResourcePool();
    TableData tableData = getTableDataFromResourcePool(resourcePool, noteId, paragraphId);

    List<String> columDefs = getColumnNamesWithTypes(tableData, stringType);
    // '-' is not allowed in sql table names
    final String pIdSqlName = SQL_NAME_PREFIX + paragraphId.replace("-", "");

    try {
      final String sqlDrop = getSqlDropTableReq(pIdSqlName);
      statement.addBatch(sqlDrop);

      final String sqlCreate = getSqlCreateTableReq(columDefs, pIdSqlName);
      statement.addBatch(sqlCreate);

      final List<String> sqlInserts = getSqlInsertReqs(tableData, pIdSqlName);
      for (final String sqlInsert : sqlInserts) {
        statement.addBatch(sqlInsert);
      }

      statement.executeBatch();
    } catch (SQLException e) { /*ignored*/ }

    // replace resource pool expression with real table
    return sqlReq.replace(poolExp, pIdSqlName);
  }
}
