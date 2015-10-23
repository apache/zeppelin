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

import static org.apache.zeppelin.jdbc.JDBCInterpreter.DEFAULT_JDBC_DRIVER_NAME;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.DEFAULT_JDBC_URL;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.DEFAULT_JDBC_USER_NAME;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.DEFAULT_JDBC_USER_PASSWORD;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.DEFAULT_MAX_RESULT;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.JDBC_SERVER_DRIVER_NAME;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.JDBC_SERVER_MAX_RESULT;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.JDBC_SERVER_PASSWORD;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.JDBC_SERVER_URL;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.JDBC_SERVER_USER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Properties;

import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.jdbc.JDBCInterpreter;
import org.junit.Before;
import org.junit.Test;

import com.mockrunner.jdbc.BasicJDBCTestCaseAdapter;
import com.mockrunner.jdbc.StatementResultSetHandler;
import com.mockrunner.mock.jdbc.MockConnection;
import com.mockrunner.mock.jdbc.MockResultSet;

/**
 * JDBC interpreter unit tests
 */
public class JDBCInterpreterTest extends BasicJDBCTestCaseAdapter {

  private JDBCInterpreter jdbcInterpreter = null;
  private MockResultSet result = null;

  @Before
  public void beforeTest() {
    MockConnection connection = getJDBCMockObjectFactory().getMockConnection();

    StatementResultSetHandler statementHandler = connection.getStatementResultSetHandler();
    result = statementHandler.createResultSet();
    statementHandler.prepareGlobalResultSet(result);

    Properties properties = new Properties();
    properties.put(JDBC_SERVER_DRIVER_NAME, DEFAULT_JDBC_DRIVER_NAME);
    properties.put(JDBC_SERVER_URL, DEFAULT_JDBC_URL);
    properties.put(JDBC_SERVER_USER, DEFAULT_JDBC_USER_NAME);
    properties.put(JDBC_SERVER_PASSWORD, DEFAULT_JDBC_USER_PASSWORD);
    properties.put(JDBC_SERVER_MAX_RESULT, DEFAULT_MAX_RESULT);

    jdbcInterpreter = spy(new JDBCInterpreter(properties));
    when(jdbcInterpreter.getJdbcConnection()).thenReturn(connection);
  }

  @Test
  public void testOpenCommandIndempotency() throws SQLException {
    // Ensure that an attempt to open new connection will clean any remaining connections
    jdbcInterpreter.open();
    jdbcInterpreter.open();
    jdbcInterpreter.open();

    verify(jdbcInterpreter, times(3)).open();
    verify(jdbcInterpreter, times(3)).close();
  }

  @Test
  public void testDefaultProperties() throws SQLException {

    JDBCInterpreter jdbcInterpreter = new JDBCInterpreter(new Properties());

    assertEquals(DEFAULT_JDBC_DRIVER_NAME,
        jdbcInterpreter.getProperty(JDBC_SERVER_DRIVER_NAME));
    assertEquals(DEFAULT_JDBC_URL, jdbcInterpreter.getProperty(JDBC_SERVER_URL));
    assertEquals(DEFAULT_JDBC_USER_NAME, jdbcInterpreter.getProperty(JDBC_SERVER_USER));
    assertEquals(DEFAULT_JDBC_USER_PASSWORD,
        jdbcInterpreter.getProperty(JDBC_SERVER_PASSWORD));
    assertEquals(DEFAULT_MAX_RESULT, jdbcInterpreter.getProperty(JDBC_SERVER_MAX_RESULT));
  }

  @Test
  public void testConnectionClose() throws SQLException {

    JDBCInterpreter jdbcInterpreter = spy(new JDBCInterpreter(new Properties()));

    when(jdbcInterpreter.getJdbcConnection()).thenReturn(
        getJDBCMockObjectFactory().getMockConnection());

    jdbcInterpreter.close();

    verifyAllResultSetsClosed();
    verifyAllStatementsClosed();
    verifyConnectionClosed();
  }

  @Test
  public void testStatementCancel() throws SQLException {

    JDBCInterpreter jdbcInterpreter = spy(new JDBCInterpreter(new Properties()));

    when(jdbcInterpreter.getJdbcConnection()).thenReturn(
        getJDBCMockObjectFactory().getMockConnection());

    jdbcInterpreter.cancel(null);

    verifyAllResultSetsClosed();
    verifyAllStatementsClosed();
    assertFalse("Cancel operation should not close the connection", jdbcInterpreter
        .getJdbcConnection().isClosed());
  }

  @Test
  public void testNullColumnResult() throws SQLException {

    when(jdbcInterpreter.getMaxResult()).thenReturn(1000);

    String sqlQuery = "select * from t";

    result.addColumn("col1", new String[] {"val11", null});
    result.addColumn("col2", new String[] {null, "val22"});

    InterpreterResult interpreterResult = jdbcInterpreter.interpret(sqlQuery, null);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.type());
    assertEquals("col1\tcol2\nval11\t\n\tval22\n", interpreterResult.message());

    verifySQLStatementExecuted(sqlQuery);
    verifyAllResultSetsClosed();
    verifyAllStatementsClosed();
  }

  @Test
  public void testSelectQuery() throws SQLException {

    when(jdbcInterpreter.getMaxResult()).thenReturn(1000);

    String sqlQuery = "select * from t";

    result.addColumn("col1", new String[] {"val11", "val12"});
    result.addColumn("col2", new String[] {"val21", "val22"});

    InterpreterResult interpreterResult = jdbcInterpreter.interpret(sqlQuery, null);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.type());
    assertEquals("col1\tcol2\nval11\tval21\nval12\tval22\n", interpreterResult.message());

    verifySQLStatementExecuted(sqlQuery);
    verifyAllResultSetsClosed();
    verifyAllStatementsClosed();
  }

  @Test
  public void testSelectQueryMaxResult() throws SQLException {

    when(jdbcInterpreter.getMaxResult()).thenReturn(1);

    String sqlQuery = "select * from t";

    result.addColumn("col1", new String[] {"val11", "val12"});
    result.addColumn("col2", new String[] {"val21", "val22"});

    InterpreterResult interpreterResult = jdbcInterpreter.interpret(sqlQuery, null);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.type());
    assertEquals("col1\tcol2\nval11\tval21\n", interpreterResult.message());

    verifySQLStatementExecuted(sqlQuery);
    verifyAllResultSetsClosed();
    verifyAllStatementsClosed();
  }

  @Test
  public void testSelectQueryWithSpecialCharacters() throws SQLException {

    when(jdbcInterpreter.getMaxResult()).thenReturn(1000);

    String sqlQuery = "select * from t";

    result.addColumn("co\tl1", new String[] {"val11", "va\tl1\n2"});
    result.addColumn("co\nl2", new String[] {"v\nal21", "val\t22"});

    InterpreterResult interpreterResult = jdbcInterpreter.interpret(sqlQuery, null);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.type());
    assertEquals("co l1\tco l2\nval11\tv al21\nva l1 2\tval 22\n", interpreterResult.message());

    verifySQLStatementExecuted(sqlQuery);
    verifyAllResultSetsClosed();
    verifyAllStatementsClosed();
  }

  @Test
  public void testExplainQuery() throws SQLException {

    when(jdbcInterpreter.getMaxResult()).thenReturn(1000);

    String sqlQuery = "explain select * from t";

    result.addColumn("col1", new String[] {"val11", "val12"});

    InterpreterResult interpreterResult = jdbcInterpreter.interpret(sqlQuery, null);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TEXT, interpreterResult.type());
    assertEquals("col1\nval11\nval12\n", interpreterResult.message());

    verifySQLStatementExecuted(sqlQuery);
    verifyAllResultSetsClosed();
    verifyAllStatementsClosed();
  }

  @Test
  public void testExplainQueryWithSpecialCharachters() throws SQLException {

    when(jdbcInterpreter.getMaxResult()).thenReturn(1000);

    String sqlQuery = "explain select * from t";

    result.addColumn("co\tl\n1", new String[] {"va\nl11", "va\tl\n12"});

    InterpreterResult interpreterResult = jdbcInterpreter.interpret(sqlQuery, null);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TEXT, interpreterResult.type());
    assertEquals("co\tl\n1\nva\nl11\nva\tl\n12\n", interpreterResult.message());

    verifySQLStatementExecuted(sqlQuery);
    verifyAllResultSetsClosed();
    verifyAllStatementsClosed();
  }

  @Test
  public void testAutoCompletion() throws SQLException {
    jdbcInterpreter.open();
    assertEquals(1, jdbcInterpreter.completion("SEL", 0).size());
    assertEquals("SELECT ", jdbcInterpreter.completion("SEL", 0).iterator().next());
    assertEquals(0, jdbcInterpreter.completion("SEL", 100).size());
  }
}
