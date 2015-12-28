
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

package org.apache.zeppelin.drill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Properties;

import org.apache.zeppelin.interpreter.InterpreterResult;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.mockrunner.jdbc.BasicJDBCTestCaseAdapter;
import com.mockrunner.jdbc.StatementResultSetHandler;
import com.mockrunner.mock.jdbc.MockConnection;
import com.mockrunner.mock.jdbc.MockResultSet;

/**
 * Test cases for {@link DrillInterpreter}
 * 
 * @author malur
 */
public class DrillInterpreterTest
    extends BasicJDBCTestCaseAdapter {

  private MockResultSet result = null;

  private Properties properties = null;

  private MockConnection connection = null;

  @Before
  public void setup() {

    this.connection = getJDBCMockObjectFactory().getMockConnection();

    StatementResultSetHandler statementHandler = connection.getStatementResultSetHandler();
    this.result = statementHandler.createResultSet();
    statementHandler.prepareGlobalResultSet(result);

    this.properties = new Properties();
    this.properties.put(DrillInterpreter.ZK_CONNECT, "zkhost:2181");
    this.properties.put(DrillInterpreter.STORAGE_PLUGIN, "hive");
    this.properties.put(DrillInterpreter.DRILL_DIRECTORY, "/DrillDir");
    this.properties.put(DrillInterpreter.DRILL_CLUSTER_ID, "drillbits");
    this.properties.put(DrillInterpreter.DRILL_MAX_RESULT, "100");
  }

  @Test
  public void testConstructor() {

    DrillInterpreter drillInterpreter = new DrillInterpreter(this.properties);

    Assert.assertFalse("DrillInterpreter should not have been initialized",
        drillInterpreter.isInitialised());
    Assert.assertNull(drillInterpreter.getInitError());
    Assert.assertNull(drillInterpreter.getConnection());
    Assert.assertEquals("Invalid JDBC URI",
        "jdbc:drill:schema=hive;zk=zkhost:2181/DrillDir/drillbits", drillInterpreter.getJdbcURI());
    Assert.assertEquals("Wrong value for max result", 100, drillInterpreter.getMaxResult());
  }

  @Test
  public void testConstructorForDefaultProperties() {

    DrillInterpreter drillInterpreter = new DrillInterpreter(new Properties());

    Assert.assertFalse("DrillInterpreter should not have been initialized",
        drillInterpreter.isInitialised());
    Assert.assertNull(drillInterpreter.getConnection());
    Assert.assertEquals("Invalid JDBC URI",
        "jdbc:drill:schema=hive;zk=localhost:2181/Drill/drillbits1", drillInterpreter.getJdbcURI());
    Assert.assertEquals("Wrong default value for max result",
        Integer.valueOf(DrillInterpreter.DEFAULT_MAX_RESULT).intValue(),
        drillInterpreter.getMaxResult());
  }

  @Test
  public void testOpen() {

    DrillInterpreter drillInterpreter = spy(new DrillInterpreter(this.properties));
    when(drillInterpreter.getConnection()).thenReturn(this.connection);

    drillInterpreter.open();

    Assert.assertTrue("DrillInterpreter should have been initialized",
        drillInterpreter.isInitialised());
    Assert.assertNotNull(drillInterpreter.getConnection());
  }

  @Test
  public void testOpenMultipleTimes() {

    DrillInterpreter drillInterpreter = spy(new DrillInterpreter(this.properties));
    when(drillInterpreter.getConnection()).thenReturn(this.connection);

    drillInterpreter.open();
    drillInterpreter.open();
    drillInterpreter.open();

    verify(drillInterpreter, times(3)).open();
    verify(drillInterpreter, times(3)).close();
  }

  @Test
  public void testConnectionClose()
    throws SQLException {

    DrillInterpreter drillInterpreter = spy(new DrillInterpreter(this.properties));
    when(drillInterpreter.getConnection()).thenReturn(this.connection);

    drillInterpreter.close();

    verifyAllResultSetsClosed();
    verifyAllStatementsClosed();
    verifyConnectionClosed();
  }

  @Test
  public void testStatementCancel()
    throws SQLException {

    DrillInterpreter drillInterpreter = spy(new DrillInterpreter(this.properties));
    when(drillInterpreter.getConnection()).thenReturn(this.connection);

    drillInterpreter.cancel(null);

    verifyAllResultSetsClosed();
    verifyAllStatementsClosed();
    assertFalse("Cancel operation should not close the connection",
        drillInterpreter.getConnection().isClosed());
  }

  @Test
  public void testSelectQuery()
    throws SQLException {

    DrillInterpreter drillInterpreter = spy(new DrillInterpreter(this.properties));
    when(drillInterpreter.getConnection()).thenReturn(this.connection);
    drillInterpreter.open();

    String sqlQuery = "select * from t";

    result.addColumn("col1", new String[] { "val11", "val12" });
    result.addColumn("col2", new String[] { "val21", "val22" });

    InterpreterResult interpreterResult = drillInterpreter.interpret(sqlQuery, null);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.type());
    assertEquals("col1\tcol2\nval11\tval21\nval12\tval22\n", interpreterResult.message());

    drillInterpreter.close();

    verifySQLStatementExecuted(sqlQuery);
    verifyAllResultSetsClosed();
    verifyAllStatementsClosed();
  }

  @Test
  public void testNullColumnResult()
    throws SQLException {

    DrillInterpreter drillInterpreter = spy(new DrillInterpreter(this.properties));
    when(drillInterpreter.getConnection()).thenReturn(this.connection);
    drillInterpreter.open();

    String sqlQuery = "select * from t";

    result.addColumn("col1", new String[] { "val11", null });
    result.addColumn("col2", new String[] { null, "val22" });

    InterpreterResult interpreterResult = drillInterpreter.interpret(sqlQuery, null);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.type());
    assertEquals("col1\tcol2\nval11\t\n\tval22\n", interpreterResult.message());

    drillInterpreter.close();

    verifySQLStatementExecuted(sqlQuery);
    verifyAllResultSetsClosed();
    verifyAllStatementsClosed();
  }

  @Test
  public void testSelectQueryWithSpecialCharacters()
    throws SQLException {

    DrillInterpreter drillInterpreter = spy(new DrillInterpreter(this.properties));
    when(drillInterpreter.getConnection()).thenReturn(this.connection);
    drillInterpreter.open();

    String sqlQuery = "select * from t";

    result.addColumn("co\tl1", new String[] { "val11", "va\tl1\n2" });
    result.addColumn("co\nl2", new String[] { "v\nal21", "val\t22" });

    InterpreterResult interpreterResult = drillInterpreter.interpret(sqlQuery, null);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.type());
    assertEquals("co l1\tco l2\nval11\tv al21\nva l1 2\tval 22\n", interpreterResult.message());

    drillInterpreter.close();

    verifySQLStatementExecuted(sqlQuery);
    verifyAllResultSetsClosed();
    verifyAllStatementsClosed();
  }

}
