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

import static org.apache.zeppelin.jdbc.JDBCUtils.splitSqlQueries;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

public class JDBCUtilsTest {
  @Test
  public void testSplitSqlQuery() {
    String sqlQuery = "insert into test_table(id, name) values ('a', ';\"');" +
        "select * from test_table;" +
        "select * from test_table WHERE ID = \";'\";" +
        "select * from test_table WHERE ID = ';';" +
        "select '\n', ';';" +
        "select replace('A\\;B', '\\', 'text');" +
        "select '\\', ';';" +
        "select '''', ';';" +
        "select /*+ scan */ * from test_table;" +
        "--singleLineComment\nselect * from test_table";

    List<String> multipleSqlArray = splitSqlQueries(sqlQuery);
    assertEquals(10, multipleSqlArray.size());
    assertEquals("insert into test_table(id, name) values ('a', ';\"')", multipleSqlArray.get(0));
    assertEquals("select * from test_table", multipleSqlArray.get(1));
    assertEquals("select * from test_table WHERE ID = \";'\"", multipleSqlArray.get(2));
    assertEquals("select * from test_table WHERE ID = ';'", multipleSqlArray.get(3));
    assertEquals("select '\n', ';'", multipleSqlArray.get(4));
    assertEquals("select replace('A\\;B', '\\', 'text')", multipleSqlArray.get(5));
    assertEquals("select '\\', ';'", multipleSqlArray.get(6));
    assertEquals("select '''', ';'", multipleSqlArray.get(7));
    assertEquals("select /*+ scan */ * from test_table", multipleSqlArray.get(8));
    assertEquals("--singleLineComment\nselect * from test_table", multipleSqlArray.get(9));
  }
}
