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
package org.apache.zeppelin.antlr;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.util.ArrayList;
import java.util.List;

public class SqlSplitVisitor extends SqlBaseVisitor<Object> {
  private List<String> list = new ArrayList<>();

  private String sourceSQL;

  public SqlSplitVisitor(String sql) {
    this.sourceSQL = sql;
  }

  @Override
  public Object visitSelect_stmt(SqlParser.Select_stmtContext ctx) {
    if (ctx.fullselect_stmt() != null) {
      int size = ctx.fullselect_stmt().fullselect_stmt_item().size();
      for (int i = 0; i < size; i++) {
        int start = ctx.fullselect_stmt().fullselect_stmt_item().get(i).subselect_stmt().getStart().getStartIndex();
        int end = ctx.fullselect_stmt().fullselect_stmt_item().get(i).subselect_stmt().getStop().getStopIndex();
        list.add(sourceSQL.substring(start, end + 1));
      }
    }
    return super.visitSelect_stmt(ctx);
  }

  public List<String> getSplitSQL() {
    return list;
  }

  public static List<String> splitSql(String text, String paragraphId) {
    CharStream input = CharStreams.fromString(text);
    SqlLexer lexer = new SqlLexer(input);
    CommonTokenStream tokenStream = new CommonTokenStream(lexer);
    SqlParser parser = new SqlParser(tokenStream);
    SqlSplitVisitor visitor = new SqlSplitVisitor(text);
    visitor.visit(parser.program());
    List<String> list = visitor.getSplitSQL();

    List<String> createTableList = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      String tableName = "test_zeppelin.tmp_zeppelin_" + paragraphId + "_" + i;
      String createSql = "CREATE DATABASE IF NOT EXISTS test_zeppelin; use test_zeppelin;create table " + tableName + " as " + list.get(i);
      list.add(createSql);
    }
    return createTableList;
  }
}