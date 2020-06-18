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

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class SqlSplitterTest {

  @Test
  public void testNormalSql() {
    SqlSplitter sqlSplitter = new SqlSplitter();
    List<String> sqls = sqlSplitter.splitSql("show tables");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls = sqlSplitter.splitSql("show tables;");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls = sqlSplitter.splitSql("show tables;\n");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls = sqlSplitter.splitSql("show tables;\nselect * from table_1");
    assertEquals(2, sqls.size());
    assertEquals("show tables", sqls.get(0));
    assertEquals("select * from table_1", sqls.get(1));

    sqls = sqlSplitter.splitSql("show\ntables;\nselect * \nfrom table_1");
    assertEquals(2, sqls.size());
    assertEquals("show\ntables", sqls.get(0));
    assertEquals("select * \nfrom table_1", sqls.get(1));

  }

  @Test
  public void testSingleLineComment() {
    SqlSplitter sqlSplitter = new SqlSplitter();
    List<String> sqls = sqlSplitter.splitSql("show tables;\n--comment_1");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls = sqlSplitter.splitSql("show tables;\n--comment_1");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls = sqlSplitter.splitSql("show tables;\n--comment_1;");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls  = sqlSplitter.splitSql("show tables;\n--comment_1;\n");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls = sqlSplitter.splitSql("--comment_1;\nshow tables");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls = sqlSplitter.splitSql("--comment_1;\nshow tables;\n--comment_2");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls = sqlSplitter.splitSql("show tables;\ndescribe table_1");
    assertEquals(2, sqls.size());
    assertEquals("show tables", sqls.get(0));
    assertEquals("describe table_1", sqls.get(1));

    sqls = sqlSplitter.splitSql("show tables;\n--comment_1;\ndescribe table_1");
    assertEquals(2, sqls.size());
    assertEquals("show tables", sqls.get(0));
    assertEquals("describe table_1", sqls.get(1));

    sqls = sqlSplitter.splitSql("select a\nfrom table_1;\ndescribe table_1;--comment_1");
    assertEquals(2, sqls.size());
    assertEquals("select a\nfrom table_1", sqls.get(0));
    assertEquals("describe table_1", sqls.get(1));

    sqls = sqlSplitter.splitSql("--comment_1;\n--comment_2\n");
    assertEquals(0, sqls.size());

    sqls = sqlSplitter.splitSql("select a -- comment\n from table_1");
    assertEquals(1, sqls.size());
    assertEquals("select a \n from table_1", sqls.get(0));

    sqls = sqlSplitter.splitSql("--comment 1\nselect a from table_1\n--comment 2");
    assertEquals(1, sqls.size());
    assertEquals("select a from table_1", sqls.get(0));

    sqls = sqlSplitter.splitSql("--comment 1\nselect a from table_1;\n--comment 2\nselect b from table_1");
    assertEquals(2, sqls.size());
    assertEquals("select a from table_1", sqls.get(0));
    assertEquals("select b from table_1", sqls.get(1));
  }

  @Test
  public void testMultipleLineComment() {
    SqlSplitter sqlSplitter = new SqlSplitter();
    List<String> sqls = sqlSplitter.splitSql("show tables;\n/*comment_1*/");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls = sqlSplitter.splitSql("show tables;\n/*comment\n_1*/");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls = sqlSplitter.splitSql("show tables;\n/*comment_1;*/");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls  = sqlSplitter.splitSql("show tables;\n/*comment\n_1;*/\n");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls = sqlSplitter.splitSql("/*comment_1;*/\nshow tables");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls = sqlSplitter.splitSql("/*comment_1*;*/\nshow tables;\n/*--comment_2*/");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls = sqlSplitter.splitSql("show tables;\n/*comment_1;*/\ndescribe table_1");
    assertEquals(2, sqls.size());
    assertEquals("show tables", sqls.get(0));
    assertEquals("describe table_1", sqls.get(1));

    sqls = sqlSplitter.splitSql("select a\nfrom table_1;\ndescribe table_1;/*comment_1*/");
    assertEquals(2, sqls.size());
    assertEquals("select a\nfrom table_1", sqls.get(0));
    assertEquals("describe table_1", sqls.get(1));

    sqls = sqlSplitter.splitSql("/*comment_1;*/\n/*comment_2*/\n");
    assertEquals(0, sqls.size());

    sqls = sqlSplitter.splitSql("select a /*comment*/ from table_1");
    assertEquals(1, sqls.size());
    assertEquals("select a  from table_1", sqls.get(0));

    sqls = sqlSplitter.splitSql("/*comment 1*/\nselect a from table_1\n/*comment 2*/");
    assertEquals(1, sqls.size());
    assertEquals("select a from table_1", sqls.get(0));

    sqls = sqlSplitter.splitSql("/*comment 1*/\nselect a from table_1;\n/*comment 2*/select b from table_1");
    assertEquals(2, sqls.size());
    assertEquals("select a from table_1", sqls.get(0));
    assertEquals("select b from table_1", sqls.get(1));

    sqls = sqlSplitter.splitSql("/*comment 1*/\nselect a /*+ hint*/ from table_1;\n/*comment 2*/select b from table_1");
    assertEquals(2, sqls.size());
    assertEquals("select a /*+ hint*/ from table_1", sqls.get(0));
    assertEquals("select b from table_1", sqls.get(1));
  }

  @Test
  public void testInvalidSql() {
    SqlSplitter sqlSplitter = new SqlSplitter();
    List<String> sqls = sqlSplitter.splitSql("select a from table_1 where a=' and b=1");
    assertEquals(1, sqls.size());
    assertEquals("select a from table_1 where a=' and b=1", sqls.get(0));

    sqls = sqlSplitter.splitSql("--comment_1;\nselect a from table_1 where a=' and b=1");
    assertEquals(1, sqls.size());
    assertEquals("select a from table_1 where a=' and b=1", sqls.get(0));

    sqls = sqlSplitter.splitSql("select a from table_1 where a=' and b=1;\n--comment_1");
    assertEquals(1, sqls.size());
    assertEquals("select a from table_1 where a=' and b=1;\n--comment_1", sqls.get(0));
  }

  @Test
  public void testComplexSql() {
    SqlSplitter sqlSplitter = new SqlSplitter();
    String text = "/* ; */\n" +
            "-- /* comment\n" +
            "--select * from test_table\n" +
            "select * from test_table; /* some comment ; */\n" +
            "/*\n" +
            "select * from test_table;\n" +
            "*/\n" +
            "-- a ; b\n" +
            "select * from test_table WHERE ID = ';--';\n" +
            "select * from test_table WHERE ID = '/*'; -- test";
    List<String> sqls = sqlSplitter.splitSql(text);
    assertEquals(3, sqls.size());
    assertEquals("select * from test_table", sqls.get(0));
    assertEquals("select * from test_table WHERE ID = ';--'", sqls.get(1));
    assertEquals("select * from test_table WHERE ID = '/*'", sqls.get(2));
  }

  @Test
  public void testCustomSplitter_1() {
    SqlSplitter sqlSplitter = new SqlSplitter("//");
    List<String> sqls = sqlSplitter.splitSql("show tables;\n//comment_1");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls = sqlSplitter.splitSql("show tables;\n//comment_1");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls = sqlSplitter.splitSql("show tables;\n//comment_1;");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls  = sqlSplitter.splitSql("show tables;\n//comment_1;\n");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls = sqlSplitter.splitSql("//comment_1;\nshow tables");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls = sqlSplitter.splitSql("//comment_1;\nshow tables;\n//comment_2");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls = sqlSplitter.splitSql("show tables;\ndescribe table_1");
    assertEquals(2, sqls.size());
    assertEquals("show tables", sqls.get(0));
    assertEquals("describe table_1", sqls.get(1));

    sqls = sqlSplitter.splitSql("show tables;\n//comment_1;\ndescribe table_1");
    assertEquals(2, sqls.size());
    assertEquals("show tables", sqls.get(0));
    assertEquals("describe table_1", sqls.get(1));

    sqls = sqlSplitter.splitSql("select a\nfrom table_1;\ndescribe table_1;//comment_1");
    assertEquals(2, sqls.size());
    assertEquals("select a\nfrom table_1", sqls.get(0));
    assertEquals("describe table_1", sqls.get(1));

    sqls = sqlSplitter.splitSql("//comment_1;\n//comment_2\n");
    assertEquals(0, sqls.size());

    sqls = sqlSplitter.splitSql("select a // comment\n from table_1");
    assertEquals(1, sqls.size());
    assertEquals("select a \n from table_1", sqls.get(0));
  }

  @Test
  public void testCustomSplitter_2() {
    SqlSplitter sqlSplitter = new SqlSplitter("#");
    List<String> sqls = sqlSplitter.splitSql("show tables;\n#comment_1");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls = sqlSplitter.splitSql("show tables;\n#comment_1");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls = sqlSplitter.splitSql("show tables;\n#comment_1;");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls  = sqlSplitter.splitSql("show tables;\n#comment_1;\n");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls = sqlSplitter.splitSql("#comment_1;\nshow tables");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls = sqlSplitter.splitSql("#comment_1;\nshow tables;\n#comment_2");
    assertEquals(1, sqls.size());
    assertEquals("show tables", sqls.get(0));

    sqls = sqlSplitter.splitSql("show tables;\ndescribe table_1");
    assertEquals(2, sqls.size());
    assertEquals("show tables", sqls.get(0));
    assertEquals("describe table_1", sqls.get(1));

    sqls = sqlSplitter.splitSql("show tables;\n#comment_1;\ndescribe table_1");
    assertEquals(2, sqls.size());
    assertEquals("show tables", sqls.get(0));
    assertEquals("describe table_1", sqls.get(1));

    sqls = sqlSplitter.splitSql("select a\nfrom table_1;\ndescribe table_1;#comment_1");
    assertEquals(2, sqls.size());
    assertEquals("select a\nfrom table_1", sqls.get(0));
    assertEquals("describe table_1", sqls.get(1));

    sqls = sqlSplitter.splitSql("#comment_1;\n#comment_2\n");
    assertEquals(0, sqls.size());

    sqls = sqlSplitter.splitSql("select a # comment\n from table_1");
    assertEquals(1, sqls.size());
    assertEquals("select a \n from table_1", sqls.get(0));
  }
}
