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
package org.apache.zeppelin.postgresql;

import static com.google.common.collect.Sets.newHashSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

import jline.console.completer.ArgumentCompleter;
import jline.console.completer.Completer;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.mockrunner.jdbc.BasicJDBCTestCaseAdapter;

public class SqlCompleterTest extends BasicJDBCTestCaseAdapter {

  private Logger logger = LoggerFactory.getLogger(SqlCompleterTest.class);

  private final static Set<String> EMPTY = new HashSet<>();

  private CompleterTester tester;

  private ArgumentCompleter.WhitespaceArgumentDelimiter delimiter =
          new ArgumentCompleter.WhitespaceArgumentDelimiter();

  private SqlCompleter sqlCompleter = new SqlCompleter();

  @Before
  public void beforeTest() throws IOException, SQLException {

    Map<String, Set<String>> tables = new HashMap<>();
    Map<String, Set<String>> columns = new HashMap<>();
    Set<String> schemas = new HashSet<>();
    Set<String> keywords = new HashSet<>();

    keywords.add("SUM");
    keywords.add("SUBSTRING");
    keywords.add("SUBCLASS_ORIGIN");
    keywords.add("ORDER");
    keywords.add("SELECT");
    keywords.add("LIMIT");
    keywords.add("FROM");

    schemas.add("prod_dds");
    schemas.add("prod_emart");

    Set<String> prod_dds_tables = new HashSet<>();
    prod_dds_tables.add("financial_account");
    prod_dds_tables.add("customer");

    Set<String> prod_emart_tables = new HashSet<>();
    prod_emart_tables.add("financial_account");

    tables.put("prod_dds", prod_dds_tables);
    tables.put("prod_emart", prod_emart_tables);

    Set<String> prod_dds_financial_account_columns = new HashSet<>();
    prod_dds_financial_account_columns.add("account_rk");
    prod_dds_financial_account_columns.add("account_id");

    Set<String> prod_dds_customer_columns = new HashSet<>();
    prod_dds_customer_columns.add("customer_rk");
    prod_dds_customer_columns.add("name");
    prod_dds_customer_columns.add("birth_dt");

    Set<String> prod_emart_financial_account_columns = new HashSet<>();
    prod_emart_financial_account_columns.add("account_rk");
    prod_emart_financial_account_columns.add("balance_amt");

    columns.put("prod_dds.financial_account", prod_dds_financial_account_columns);
    columns.put("prod_dds.customer", prod_dds_customer_columns);
    columns.put("prod_emart.financial_account", prod_emart_financial_account_columns);

    sqlCompleter.init(schemas, tables, columns, keywords);

    tester = new CompleterTester(sqlCompleter);
  }

  @Test
  public void testFindAliasesInSQL_Simple(){
    String sql = "select * from prod_emart.financial_account a";
    Map<String, String> res = sqlCompleter.findAliasesInSQL(delimiter.delimit(sql, 0).getArguments());
    assertEquals(1, res.size());
    assertTrue(res.get("a").equals("prod_emart.financial_account"));
  }

  @Test
  public void testFindAliasesInSQL_Two(){
    String sql = "select * from prod_dds.financial_account a, prod_dds.customer b";
    Map<String, String> res = sqlCompleter.findAliasesInSQL(sqlCompleter.getSqlDelimiter().delimit(sql, 0).getArguments());
    assertEquals(2, res.size());
    assertTrue(res.get("a").equals("prod_dds.financial_account"));
    assertTrue(res.get("b").equals("prod_dds.customer"));
  }

  @Test
  public void testFindAliasesInSQL_WrongTables(){
    String sql = "select * from prod_ddsxx.financial_account a, prod_dds.customerxx b";
    Map<String, String> res = sqlCompleter.findAliasesInSQL(sqlCompleter.getSqlDelimiter().delimit(sql, 0).getArguments());
    assertEquals(0, res.size());
  }

  @Test
  public void testCompleteName_Empty() {
    String buffer = "";
    int cursor = 0;
    List<CharSequence> candidates = new ArrayList<>();
    Map<String, String> aliases = new HashMap<>();
    sqlCompleter.completeName(buffer, cursor, candidates, aliases, false);
    assertEquals(9, candidates.size());
    assertTrue(candidates.contains("prod_dds"));
    assertTrue(candidates.contains("prod_emart"));
    assertTrue(candidates.contains("SUM"));
    assertTrue(candidates.contains("SUBSTRING"));
    assertTrue(candidates.contains("SUBCLASS_ORIGIN"));
    assertTrue(candidates.contains("SELECT"));
    assertTrue(candidates.contains("ORDER"));
    assertTrue(candidates.contains("LIMIT"));
    assertTrue(candidates.contains("FROM"));
  }

  @Test
  public void testCompleteName_SimpleSchema() {
    String buffer = "prod_";
    int cursor = 3;
    List<CharSequence> candidates = new ArrayList<>();
    Map<String, String> aliases = new HashMap<>();
    sqlCompleter.completeName(buffer, cursor, candidates, aliases, false);
    assertEquals(2, candidates.size());
    assertTrue(candidates.contains("prod_dds"));
    assertTrue(candidates.contains("prod_emart"));
  }

  @Test
  public void testCompleteName_SimpleTable() {
    String buffer = "prod_dds.fin";
    int cursor = 11;
    List<CharSequence> candidates = new ArrayList<>();
    Map<String, String> aliases = new HashMap<>();
    sqlCompleter.completeName(buffer, cursor, candidates, aliases, false);
    assertEquals(1, candidates.size());
    assertTrue(candidates.contains("financial_account "));
  }

  @Test
  public void testCompleteName_SimpleColumn() {
    String buffer = "prod_dds.financial_account.acc";
    int cursor = 30;
    List<CharSequence> candidates = new ArrayList<>();
    Map<String, String> aliases = new HashMap<>();
    sqlCompleter.completeName(buffer, cursor, candidates, aliases, true);
    assertEquals(2, candidates.size());
    assertTrue(candidates.contains("account_rk"));
    assertTrue(candidates.contains("account_id"));
  }

  @Test
  public void testCompleteName_WithAlias() {
    String buffer = "a.acc";
    int cursor = 4;
    List<CharSequence> candidates = new ArrayList<>();
    Map<String, String> aliases = new HashMap<>();
    aliases.put("a", "prod_dds.financial_account");
    sqlCompleter.completeName(buffer, cursor, candidates, aliases, true);
    assertEquals(2, candidates.size());
    assertTrue(candidates.contains("account_rk"));
    assertTrue(candidates.contains("account_id"));
  }

  @Test
  public void testCompleteName_WithAliasAndPoint() {
    String buffer = "a.";
    int cursor = 2;
    List<CharSequence> candidates = new ArrayList<>();
    Map<String, String> aliases = new HashMap<>();
    aliases.put("a", "prod_dds.financial_account");
    sqlCompleter.completeName(buffer, cursor, candidates, aliases, true);
    assertEquals(2, candidates.size());
    assertTrue(candidates.contains("account_rk"));
    assertTrue(candidates.contains("account_id"));
  }

  public void testSchemaAndTable() {
    String buffer = "select * from prod_v_emart.fi";
    tester.buffer(buffer).from(15).to(26).expect(newHashSet("prod_v_emart ")).test();
    tester.buffer(buffer).from(27).to(29).expect(newHashSet("financial_account ")).test();
  }

  @Test
  public void testEdges() {
    String buffer = "  ORDER  ";
    tester.buffer(buffer).from(0).to(7).expect(newHashSet("ORDER ")).test();
    tester.buffer(buffer).from(8).to(15).expect(newHashSet("ORDER", "SUBCLASS_ORIGIN", "SUBSTRING",
            "prod_emart", "LIMIT", "SUM", "prod_dds", "SELECT", "FROM")).test();
  }

  @Test
  public void testMultipleWords() {
    String buffer = "SELE FRO LIM";
    tester.buffer(buffer).from(0).to(4).expect(newHashSet("SELECT ")).test();
    tester.buffer(buffer).from(5).to(8).expect(newHashSet("FROM ")).test();
    tester.buffer(buffer).from(9).to(12).expect(newHashSet("LIMIT ")).test();
  }

  @Test
  public void testMultiLineBuffer() {
    String buffer = " \n SELE\nFRO";
    tester.buffer(buffer).from(0).to(7).expect(newHashSet("SELECT ")).test();
    tester.buffer(buffer).from(8).to(11).expect(newHashSet("FROM ")).test();
  }

  @Test
  public void testMultipleCompletionSuggestions() {
    String buffer = "SU";
    tester.buffer(buffer).from(0).to(2).expect(newHashSet("SUBCLASS_ORIGIN", "SUM", "SUBSTRING"))
        .test();
  }

  @Test
  public void testSqlDelimiterCharacters() {
    assertTrue(sqlCompleter.getSqlDelimiter().isDelimiterChar("r,", 1));
    assertTrue(sqlCompleter.getSqlDelimiter().isDelimiterChar("SS,", 2));
    assertTrue(sqlCompleter.getSqlDelimiter().isDelimiterChar(",", 0));
    assertTrue(sqlCompleter.getSqlDelimiter().isDelimiterChar("ttt,", 3));
  }

  public class CompleterTester {

    private Completer completer;

    private String buffer;
    private int fromCursor;
    private int toCursor;
    private Set<String> expectedCompletions;

    public CompleterTester(Completer completer) {
      this.completer = completer;
    }

    public CompleterTester buffer(String buffer) {
      this.buffer = buffer;
      return this;
    }

    public CompleterTester from(int fromCursor) {
      this.fromCursor = fromCursor;
      return this;
    }

    public CompleterTester to(int toCursor) {
      this.toCursor = toCursor;
      return this;
    }

    public CompleterTester expect(Set<String> expectedCompletions) {
      this.expectedCompletions = expectedCompletions;
      return this;
    }

    public void test() {
      for (int c = fromCursor; c <= toCursor; c++) {
        expectedCompletions(buffer, c, expectedCompletions);
      }
    }

    private void expectedCompletions(String buffer, int cursor, Set<String> expected) {

      ArrayList<CharSequence> candidates = new ArrayList<>();

      completer.complete(buffer, cursor, candidates);

      String explain = explain(buffer, cursor, candidates);

      logger.info(explain);

      assertEquals("Buffer [" + buffer.replace(" ", ".") + "] and Cursor[" + cursor + "] "
          + explain, expected, newHashSet(candidates));
    }

    private String explain(String buffer, int cursor, ArrayList<CharSequence> candidates) {
      StringBuffer sb = new StringBuffer();

      for (int i = 0; i <= Math.max(cursor, buffer.length()); i++) {
        if (i == cursor) {
          sb.append("(");
        }
        if (i >= buffer.length()) {
          sb.append("_");
        } else {
          if (Character.isWhitespace(buffer.charAt(i))) {
            sb.append(".");
          } else {
            sb.append(buffer.charAt(i));
          }
        }
        if (i == cursor) {
          sb.append(")");
        }
      }
      sb.append(" >> [").append(Joiner.on(",").join(candidates)).append("]");

      return sb.toString();
    }
  }
}
