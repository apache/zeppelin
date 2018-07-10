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
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.resource.ResourcePool;
import org.apache.zeppelin.resource.WellKnownResourceName;
import org.apache.zeppelin.tabledata.ColumnDef;
import org.apache.zeppelin.tabledata.InterpreterResultTableData;
import org.apache.zeppelin.tabledata.Row;
import org.apache.zeppelin.tabledata.TableData;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class JDBCPoolManager {
  private static final String paragraphIdKey = "paragraph_id=";
  private static final String noteIdKey = "note_id=";

  // todo: smarter parser
  private static String getPoolExpression(String sqlReq) {
    final int poolReqBegIndex = sqlReq.indexOf("{ResourcePool");
    final int poolReqEndIndex = sqlReq.indexOf("}", poolReqBegIndex);
    return sqlReq.substring(poolReqBegIndex, poolReqEndIndex + 1);
  }

  private static String getParagraphId(String poolExp) {
    final int pIdIndex = poolExp.indexOf(paragraphIdKey);
    return poolExp.substring(pIdIndex + paragraphIdKey.length(), poolExp.length() - 1);
  }

  private static String getNoteId(String poolExp) {
    final int nIdBegIndex = poolExp.indexOf(noteIdKey);
    final int nIdEndIndex = poolExp.indexOf(".", nIdBegIndex);
    return poolExp.substring(nIdBegIndex + noteIdKey.length(), nIdEndIndex);
  }

  private static TableData getTableDataFromResourcePool(ResourcePool resourcePool,
                                                        String noteId, String paragraphId) {
    final String str = "" + resourcePool.get(noteId, paragraphId,
        WellKnownResourceName.ZeppelinTableResult.toString()).get();
    InterpreterResultMessage msg = getMsgFromString(str);
    return new InterpreterResultTableData(msg);
  }

  private static List<String> getColumnNamesWithTypes(TableData tableData) {
    final ColumnDef[] columns = tableData.columns();
    List<String> columDefs = new LinkedList<>();
    for (final ColumnDef column : columns) {
      // todo: get type from properties
      columDefs.add(column.name() + " varchar(100)");
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

  private static InterpreterResultMessage getMsgFromString (String msg) {
    InterpreterOutput out = new InterpreterOutput(null);
    InterpreterResultMessage interpreterResultMessage = null;
    try {
      out.write(msg);
      out.flush();
      interpreterResultMessage = out.toInterpreterResultMessage().get(0);
      out.close();
    } catch (IOException e) { /*ignored*/ }
    return interpreterResultMessage;
  }


  public static String preparePoolData(String sqlReq, Statement statement,
                                       InterpreterContext context) {
    final String poolExp = getPoolExpression(sqlReq);
    final String paragraphId = getParagraphId(poolExp);
    final String noteId = getNoteId(poolExp);

    ResourcePool resourcePool = context.getResourcePool();
    TableData tableData = getTableDataFromResourcePool(resourcePool, noteId, paragraphId);

    List<String> columDefs = getColumnNamesWithTypes(tableData);
    // '-' is not allowed in sql table names, the table name must begin with a letter
    final String pIdSqlName = "p" + paragraphId.replace("-", "");

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
