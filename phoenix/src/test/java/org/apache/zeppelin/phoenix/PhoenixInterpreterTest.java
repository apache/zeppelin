/**
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
package org.apache.zeppelin.phoenix;

import static org.apache.zeppelin.phoenix.PhoenixInterpreter.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import java.sql.SQLException;
import java.util.Properties;

import org.apache.zeppelin.interpreter.InterpreterResult;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;

import com.mockrunner.jdbc.BasicJDBCTestCaseAdapter;
import com.mockrunner.jdbc.StatementResultSetHandler;
import com.mockrunner.mock.jdbc.MockConnection;
import com.mockrunner.mock.jdbc.MockResultSet;

/**
 * Phoenix interpreter unit tests
 */
public class PhoenixInterpreterTest extends BasicJDBCTestCaseAdapter {
  private PhoenixInterpreter phoenixInterpreter = null;
  private MockResultSet result = null;

  @Before
  public void beforeTest() {
    MockConnection connection = getJDBCMockObjectFactory().getMockConnection();

    StatementResultSetHandler statementHandler = connection.getStatementResultSetHandler();
    result = statementHandler.createResultSet();
    statementHandler.prepareGlobalResultSet(result);

    Properties properties = new Properties();
    properties.put(PHOENIX_JDBC_DRIVER_NAME, DEFAULT_JDBC_DRIVER_NAME);
    properties.put(PHOENIX_JDBC_URL, DEFAULT_JDBC_URL);
    properties.put(PHOENIX_JDBC_USER, DEFAULT_JDBC_USER);
    properties.put(PHOENIX_JDBC_PASSWORD, DEFAULT_JDBC_PASSWORD);
    properties.put(PHOENIX_MAX_RESULT, DEFAULT_MAX_RESULT);

    phoenixInterpreter = spy(new PhoenixInterpreter(properties));
    when(phoenixInterpreter.getJdbcConnection()).thenReturn(connection);
  }

  @Test
  public void testOpenCommandIdempotency() throws SQLException {
    // Ensure that an attempt to open new connection will clean any remaining connections
    phoenixInterpreter.open();
    phoenixInterpreter.open();
    phoenixInterpreter.open();

    verify(phoenixInterpreter, times(3)).open();
    verify(phoenixInterpreter, times(3)).close();
  }

  @Test
  public void testDefaultProperties() throws SQLException {

    PhoenixInterpreter phoenixInterpreter = new PhoenixInterpreter(new Properties());

    assertEquals(DEFAULT_JDBC_DRIVER_NAME,
        phoenixInterpreter.getProperty(PHOENIX_JDBC_DRIVER_NAME));
    assertEquals(DEFAULT_JDBC_URL, phoenixInterpreter.getProperty(PHOENIX_JDBC_URL));
    assertEquals(DEFAULT_JDBC_USER, phoenixInterpreter.getProperty(PHOENIX_JDBC_USER));
    assertEquals(DEFAULT_JDBC_PASSWORD,
        phoenixInterpreter.getProperty(PHOENIX_JDBC_PASSWORD));
    assertEquals(DEFAULT_MAX_RESULT, phoenixInterpreter.getProperty(PHOENIX_MAX_RESULT));
  }

  @Test
  public void testConnectionClose() throws SQLException {

    PhoenixInterpreter phoenixInterpreter = spy(new PhoenixInterpreter(new Properties()));

    when(phoenixInterpreter.getJdbcConnection()).thenReturn(
        getJDBCMockObjectFactory().getMockConnection());

    phoenixInterpreter.close();

    verifyAllResultSetsClosed();
    verifyAllStatementsClosed();
    verifyConnectionClosed();
  }

  @Test
  public void testStatementCancel() throws SQLException {

    PhoenixInterpreter phoenixInterpreter = spy(new PhoenixInterpreter(new Properties()));

    when(phoenixInterpreter.getJdbcConnection()).thenReturn(
        getJDBCMockObjectFactory().getMockConnection());

    phoenixInterpreter.cancel(null);

    verifyAllResultSetsClosed();
    verifyAllStatementsClosed();
    assertFalse("Cancel operation should not close the connection", phoenixInterpreter
        .getJdbcConnection().isClosed());
  }

  @Test
  public void testSelectQuery() throws SQLException {

    when(phoenixInterpreter.getMaxResult()).thenReturn(1000);

    String sqlQuery = "select * from t";

    result.addColumn("col1", new String[] {"val11", "val12"});
    result.addColumn("col2", new String[] {"val21", "val22"});

    InterpreterResult interpreterResult = phoenixInterpreter.interpret(sqlQuery, null);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.type());
    assertEquals("col1\tcol2\nval11\tval21\nval12\tval22\n", interpreterResult.message());

    verifySQLStatementExecuted(sqlQuery);
    verifyAllResultSetsClosed();
    verifyAllStatementsClosed();
  }

  @Test
  public void testSelectQueryMaxResult() throws SQLException {

    when(phoenixInterpreter.getMaxResult()).thenReturn(1);

    String sqlQuery = "select * from t";

    result.addColumn("col1", new String[] {"val11", "val12"});
    result.addColumn("col2", new String[] {"val21", "val22"});

    InterpreterResult interpreterResult = phoenixInterpreter.interpret(sqlQuery, null);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.type());
    assertEquals("col1\tcol2\nval11\tval21\n", interpreterResult.message());

    verifySQLStatementExecuted(sqlQuery);
    verifyAllResultSetsClosed();
    verifyAllStatementsClosed();
  }

  @Test
  public void testSelectQueryWithSpecialCharacters() throws SQLException {

    when(phoenixInterpreter.getMaxResult()).thenReturn(1000);

    String sqlQuery = "select * from t";

    result.addColumn("co\tl1", new String[] {"val11", "va\tl1\n2"});
    result.addColumn("co\nl2", new String[] {"v\nal21", "val\t22"});

    InterpreterResult interpreterResult = phoenixInterpreter.interpret(sqlQuery, null);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.type());
    assertEquals("co l1\tco l2\nval11\tv al21\nva l1 2\tval 22\n", interpreterResult.message());

    verifySQLStatementExecuted(sqlQuery);
    verifyAllResultSetsClosed();
    verifyAllStatementsClosed();
  }

  @Test
  public void testExplainQuery() throws SQLException {

    when(phoenixInterpreter.getMaxResult()).thenReturn(1000);

    String sqlQuery = "explain select * from t";

    result.addColumn("col1", new String[] {"val11", "val12"});

    InterpreterResult interpreterResult = phoenixInterpreter.interpret(sqlQuery, null);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TEXT, interpreterResult.type());
    assertEquals("col1\nval11\nval12\n", interpreterResult.message());

    verifySQLStatementExecuted(sqlQuery);
    verifyAllResultSetsClosed();
    verifyAllStatementsClosed();
  }

  @Test
  public void testExplainQueryWithSpecialCharachters() throws SQLException {

    when(phoenixInterpreter.getMaxResult()).thenReturn(1000);

    String sqlQuery = "explain select * from t";

    result.addColumn("co\tl\n1", new String[] {"va\nl11", "va\tl\n12"});

    InterpreterResult interpreterResult = phoenixInterpreter.interpret(sqlQuery, null);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TEXT, interpreterResult.type());
    assertEquals("co\tl\n1\nva\nl11\nva\tl\n12\n", interpreterResult.message());

    verifySQLStatementExecuted(sqlQuery);
    verifyAllResultSetsClosed();
    verifyAllStatementsClosed();
  }
}
