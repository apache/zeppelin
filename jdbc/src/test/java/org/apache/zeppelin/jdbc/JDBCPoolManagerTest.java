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

import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.resource.WellKnownResourceName;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class JDBCPoolManagerTest extends JDBCAbstractTest{
  private final String POOL_STR_HEADER = "%table id\tlogin\tname\treg_date\n";
  private final String RESP_STR_HEADER = "ID\tLOGIN\tNAME\tREG_DATE\n";
  private final String STR_DATA = "1\talex\tAlexander\t2018-07-06\n" +
                          "2\tvasyan\tVasiliy\t2018-07-04\n" +
                          "3\trusick\tRuslan\t2018-05-28\n";
  private final String POOL_STR = POOL_STR_HEADER + STR_DATA;
  private final String RESP_STR = RESP_STR_HEADER + STR_DATA;
  private final String PARAGRAPH_ID = "20180711-115912_974030005";

  @Before
  public void fillResourcePool() {
    interpreterContext.getResourcePool().put(
        NOTE_ID,
        PARAGRAPH_ID,
        WellKnownResourceName.ZeppelinTableResult.toString(),
        POOL_STR
    );
  }

  @Test
  public void testSelectQueryFromResourcePool() throws SQLException, IOException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    properties.setProperty("default.stringType", "varchar(100)");
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();

    final String sqlQuery = String.format(
        "select * from {ResourcePool.note_id=%s.paragraph_id=%s}", NOTE_ID, PARAGRAPH_ID);
    InterpreterResult interpreterResult = t.interpret(sqlQuery, interpreterContext);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
    assertEquals(RESP_STR, interpreterResult.message().get(0).getData());
  }

  @Test
  public void testSelectQueryFromResourcePoolWithoutNoteId() throws SQLException, IOException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    properties.setProperty("default.stringType", "varchar(100)");
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();

    final String sqlQuery = String.format("select * from {ResourcePool.paragraph_id=%s}",
        PARAGRAPH_ID);
    InterpreterResult interpreterResult = t.interpret(sqlQuery, interpreterContext);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
    assertEquals(RESP_STR, interpreterResult.message().get(0).getData());
  }

  @Test
  public void testResourcePoolReqs() {
    final String sql =
        "SELECT * FROM {ResourcePool.note_id=SOME_NOTE_ID.paragraph_id=SOME_PARAGRAPH_ID};";
    List <String> reqs = new SqlParser(sql).resourcePoolReqs();
    assertEquals(Collections.singletonList(
        "{ResourcePool.note_id=SOME_NOTE_ID.paragraph_id=SOME_PARAGRAPH_ID}"), reqs);
  }


  @Test
  public void testResourcePoolReqsWithReqInQuotes() {
    final String sql =
        "SELECT * FROM some_table " +
            "'{ResourcePool.note_id=SOME_NOTE_ID.paragraph_id=SOME_PARAGRAPH_ID}';";
    List <String> reqs = new SqlParser(sql).resourcePoolReqs();
    assertEquals(Collections.emptyList(), reqs);
  }

  @Test
  public void testResourcePoolReqsWithiIncompleteReq() {
    final String sql =
        "SELECT * FROM {ResourcePool.note_id=SOME_NOTE_ID.paragraph_id=SOME_PARAGRAPH_ID " +
            "oops someone miss close figure bracket;";
    List <String> reqs = new SqlParser(sql).resourcePoolReqs();
    assertEquals(Collections.emptyList(), reqs);
  }

  @Test
  public void testResourcePoolReqsWithReqInComment() {
    final String sql =
        "SELECT * FROM some_table " +
            "/* {ResourcePool.note_id=SOME_NOTE_ID.paragraph_id=SOME_PARAGRAPH_ID} */; and  " +
            "\n-- {ResourcePool.note_id=SOME_NOTE_ID.paragraph_id=SOME_PARAGRAPH_ID} ";
    List <String> reqs = new SqlParser(sql).resourcePoolReqs();
    assertEquals(Collections.emptyList(), reqs);
  }

  @Test
  public void testGetParagraphId() {
    final String pId = JDBCPoolManager.getParagraphId(
        "{ResourcePool.paragraph_id=01234_56-789}");
    assertEquals("01234_56-789", pId);
  }

  @Test
  public void testGetNoteId() {
    final String pId = JDBCPoolManager.getNoteId(
        "{ResourcePool.note_id=AAAABCD123}", null);
    assertEquals("AAAABCD123", pId);
  }

}
