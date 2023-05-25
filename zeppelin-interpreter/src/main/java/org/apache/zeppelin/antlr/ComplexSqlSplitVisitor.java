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

public class ComplexSqlSplitVisitor extends SqlBaseVisitor<Object> {

  private boolean flag = true;

  private String sourceSql;

  private String targetSql;

  @Override
  public Object visitSubselect_stmt(SqlParser.Subselect_stmtContext ctx) {
    if (ctx.where_clause() != null && flag) {
      int start = ctx.from_clause().from_table_clause().from_subselect_clause().select_stmt().getStart().getStartIndex();
      int end = ctx.from_clause().from_table_clause().from_subselect_clause().select_stmt().getStop().getStopIndex();
      targetSql = sourceSql.substring(start, end + 1);
      flag = false;
    }
    return super.visitSubselect_stmt(ctx);
  }

  public ComplexSqlSplitVisitor(String sourceSql) {
    this.sourceSql = sourceSql;
  }

  public String getTargetSql() {
    return targetSql;
  }

  public static String getComplexSql(String sql) {
    CharStream input = CharStreams.fromString(sql);
    SqlLexer lexer = new SqlLexer(input);
    CommonTokenStream tokenStream = new CommonTokenStream(lexer);
    SqlParser parser = new SqlParser(tokenStream);
    ComplexSqlSplitVisitor visitor = new ComplexSqlSplitVisitor(sql);
    visitor.visit(parser.program());
    return visitor.getTargetSql();
  }

}
