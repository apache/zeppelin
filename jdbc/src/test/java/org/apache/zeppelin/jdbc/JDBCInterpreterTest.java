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

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.DEFAULT_KEY;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.DEFAULT_DRIVER;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.DEFAULT_PASSWORD;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.DEFAULT_USER;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.DEFAULT_URL;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.COMMON_MAX_LINE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Properties;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.jdbc.JDBCInterpreter;
import org.junit.Before;
import org.junit.Test;

import com.mockrunner.jdbc.BasicJDBCTestCaseAdapter;
/**
 * JDBC interpreter unit tests
 */
public class JDBCInterpreterTest extends BasicJDBCTestCaseAdapter {

  static String jdbcConnection;

  private static String getJdbcConnection() throws IOException {
    if(null == jdbcConnection) {
      Path tmpDir = Files.createTempDirectory("h2-test-");
      tmpDir.toFile().deleteOnExit();
      jdbcConnection = format("jdbc:h2:%s", tmpDir);
    }
    return jdbcConnection;
  }
  
  @Before
  public void setUp() throws Exception {

    Class.forName("org.h2.Driver");
    Connection connection = DriverManager.getConnection(getJdbcConnection());
    Statement statement = connection.createStatement();
    statement.execute(
        "DROP TABLE IF EXISTS test_table; " +
        "CREATE TABLE test_table(id varchar(255), name varchar(255));");

    PreparedStatement insertStatement = connection.prepareStatement("insert into test_table(id, name) values ('a', 'a_name'),('b', 'b_name'),('c', ?);");
    insertStatement.setString(1, null);
    insertStatement.execute();
  }


  @Test
  public void testForParsePropertyKey() throws IOException {
    JDBCInterpreter t = new JDBCInterpreter(new Properties());
    
    assertEquals(t.getPropertyKey("(fake) select max(cant) from test_table where id >= 2452640"),
        "fake");
    
    assertEquals(t.getPropertyKey("() select max(cant) from test_table where id >= 2452640"),
        "");
    
    assertEquals(t.getPropertyKey(")fake( select max(cant) from test_table where id >= 2452640"),
        "default");
        
    // when you use a %jdbc(prefix1), prefix1 is the propertyKey as form part of the cmd string
    assertEquals(t.getPropertyKey("(prefix1)\n select max(cant) from test_table where id >= 2452640"),
        "prefix1");
    
    assertEquals(t.getPropertyKey("(prefix2) select max(cant) from test_table where id >= 2452640"),
            "prefix2");
    
    // when you use a %jdbc, prefix is the default
    assertEquals(t.getPropertyKey("select max(cant) from test_table where id >= 2452640"),
            "default");
  }
  
  @Test
  public void testForMapPrefix() throws SQLException, IOException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();

    String sqlQuery = "(fake) select * from test_table";

    InterpreterResult interpreterResult = t.interpret(sqlQuery, new InterpreterContext("", "1", "", "", null, null, null, null, null, null, null));

    // if prefix not found return ERROR and Prefix not found.
    assertEquals(InterpreterResult.Code.ERROR, interpreterResult.code());
    assertEquals("Prefix not found.", interpreterResult.message());
  }
  
  @Test
  public void testDefaultProperties() throws SQLException {
    JDBCInterpreter jdbcInterpreter = new JDBCInterpreter(new Properties());
    
    assertEquals("org.postgresql.Driver", jdbcInterpreter.getProperty(DEFAULT_DRIVER));
    assertEquals("jdbc:postgresql://localhost:5432/", jdbcInterpreter.getProperty(DEFAULT_URL));
    assertEquals("gpadmin", jdbcInterpreter.getProperty(DEFAULT_USER));
    assertEquals("", jdbcInterpreter.getProperty(DEFAULT_PASSWORD));
    assertEquals("1000", jdbcInterpreter.getProperty(COMMON_MAX_LINE));
  }
  
  @Test
  public void testSelectQuery() throws SQLException, IOException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();

    String sqlQuery = "select * from test_table WHERE ID in ('a', 'b')";

    InterpreterResult interpreterResult = t.interpret(sqlQuery, new InterpreterContext("", "1", "", "", null, null, null, null, null, null, null));

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.type());
    assertEquals("ID\tNAME\na\ta_name\nb\tb_name\n", interpreterResult.message());
  }

  @Test
  public void testSelectQueryWithNull() throws SQLException, IOException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();

    String sqlQuery = "select * from test_table WHERE ID = 'c'";

    InterpreterResult interpreterResult = t.interpret(sqlQuery, new InterpreterContext("", "1", "", "", null, null, null, null, null, null, null));

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.type());
    assertEquals("ID\tNAME\nc\tnull\n", interpreterResult.message());
  }


  @Test
  public void testSelectQueryMaxResult() throws SQLException, IOException {

    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();

    String sqlQuery = "select * from test_table";

    InterpreterResult interpreterResult = t.interpret(sqlQuery, new InterpreterContext("", "1", "", "", null, null, null, null, null, null, null));

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.type());
    assertEquals("ID\tNAME\na\ta_name\n", interpreterResult.message());
  }
}
