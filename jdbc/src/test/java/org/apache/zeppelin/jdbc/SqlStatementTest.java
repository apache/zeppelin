/*
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

import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SqlStatementTest {
  private String defaultSchema = "prod_dds";
  private Collection<String> schemas = new TreeSet<>();
  private Collection<String> tablesInDefaultSchema = new TreeSet<>();
  private Collection<String> keywords = new TreeSet<>();

  @Before
  public void beforeTest() {
    schemas.add("prod_dds");
    schemas.add("prod_emart");
    tablesInDefaultSchema.add("customer");
    tablesInDefaultSchema.add("account");
    keywords.add("select");
    keywords.add("where");
    keywords.add("order");
    keywords.add("limit");
    keywords.add("inner");
    keywords.add("left");
    keywords.add("join");
  }

  @Test
  public void testSqlDelimiterCharacters() {
    String statement = "select * from table;";
    int cursor = 7;

    SqlStatement sqlStatement = new SqlStatement(statement, cursor, defaultSchema,
        schemas, tablesInDefaultSchema, keywords);

    assertTrue(sqlStatement.getSqlDelimiter().isDelimiterChar("r,", 1));
    assertTrue(sqlStatement.getSqlDelimiter().isDelimiterChar("SS,", 2));
    assertTrue(sqlStatement.getSqlDelimiter().isDelimiterChar(",", 0));
    assertTrue(sqlStatement.getSqlDelimiter().isDelimiterChar("ttt,", 3));
  }

  @Test
  public void testSqlStatementActiveSchemaTables() {
    String statement = "select  from account acc left join prod_emart.application as app on ;";
    int cursor = 7;

    SqlStatement sqlStatementActiveSchemaTables = new SqlStatement(statement, cursor, defaultSchema,
        schemas, tablesInDefaultSchema, keywords);

    assertTrue(sqlStatementActiveSchemaTables.getActiveSchemaTables().contains("prod_dds.account"));
    assertTrue(sqlStatementActiveSchemaTables.getActiveSchemaTables()
        .contains("prod_emart.application"));
    assertEquals(0, sqlStatementActiveSchemaTables.getCursorPosition());
  }

  @Test
  public void testSqlStatementAliasForTableInDefaultSchema() {
    String statement = "select acc.z from account acc left join prod_emart.application app on ;";
    int cursor = 12;

    SqlStatement sqlStatementAliasForTableInDefaultSchema = new SqlStatement(statement, cursor,
        defaultSchema, schemas, tablesInDefaultSchema, keywords);

    assertEquals(defaultSchema, sqlStatementAliasForTableInDefaultSchema.getSchema());
    assertEquals("account", sqlStatementAliasForTableInDefaultSchema.getTable());
    assertEquals("z", sqlStatementAliasForTableInDefaultSchema.getColumn());
    assertEquals(1, sqlStatementAliasForTableInDefaultSchema.getCursorPosition());
  }

  @Test
  public void testSqlStatementAliasSchemaTable() {
    String statement = "select app.y from account acc left join prod_emart.application app on ;";
    int cursor = 12;

    SqlStatement sqlStatementAliasSchemaTable = new SqlStatement(statement, cursor, defaultSchema,
        schemas, tablesInDefaultSchema, keywords);

    assertEquals("prod_emart", sqlStatementAliasSchemaTable.getSchema());
    assertEquals("application", sqlStatementAliasSchemaTable.getTable());
    assertEquals("y", sqlStatementAliasSchemaTable.getColumn());
    assertEquals(1, sqlStatementAliasSchemaTable.getCursorPosition());
  }

  @Test
  public void testSqlStatementSchemaTableColumn() {
    String statement = "select prod_emart.application.yy from account acc"
        + "left join prod_emart.application app on ;";
    int cursor = 32;

    SqlStatement sqlStatementSchemaTableColumn = new SqlStatement(statement, cursor, defaultSchema,
        schemas, tablesInDefaultSchema, keywords);

    assertEquals("prod_emart", sqlStatementSchemaTableColumn.getSchema());
    assertEquals("application", sqlStatementSchemaTableColumn.getTable());
    assertEquals("yy", sqlStatementSchemaTableColumn.getColumn());
    assertEquals(2, sqlStatementSchemaTableColumn.getCursorPosition());
  }

  @Test
  public void testSqlStatementSchemaTable() {
    String statement = "select \n from prod_emart.ap \nlimit 300";
    int cursor = 27;

    SqlStatement sqlStatementSchemaTable = new SqlStatement(statement, cursor, defaultSchema,
        schemas, tablesInDefaultSchema, keywords);

    assertEquals("prod_emart", sqlStatementSchemaTable.getSchema());
    assertEquals("ap", sqlStatementSchemaTable.getTable());
    assertEquals(2, sqlStatementSchemaTable.getCursorPosition());
    assertTrue(sqlStatementSchemaTable.needLoadTables());
    assertNull(sqlStatementSchemaTable.getColumn());
    assertNull(sqlStatementSchemaTable.getCursorString());
  }

  @Test
  public void testSqlStatementSchema() {
    String statement = "select \n from prod_emart. ;";
    int cursor = 25;

    SqlStatement sqlStatementSchema = new SqlStatement(statement, cursor, defaultSchema,
        schemas, tablesInDefaultSchema, keywords);

    assertEquals("prod_emart", sqlStatementSchema.getSchema());
    assertEquals("", sqlStatementSchema.getTable());
    assertEquals(0, sqlStatementSchema.getCursorPosition());
    assertNull(sqlStatementSchema.getColumn());
    assertNull(sqlStatementSchema.getCursorString());
  }

  @Test
  public void testSqlStatementCustom() {
    String statement = "select pro from account acc left join prod_emart.application app on ;";
    int cursor = 10;

    SqlStatement sqlStatementCustom = new SqlStatement(statement, cursor, defaultSchema,
        schemas, tablesInDefaultSchema, keywords);

    assertEquals("pro", sqlStatementCustom.getCursorString());
    assertNull(sqlStatementCustom.getSchema());
    assertNull(sqlStatementCustom.getTable());
    assertNull(sqlStatementCustom.getColumn());
    assertEquals(3, sqlStatementCustom.getCursorPosition());
  }
}
