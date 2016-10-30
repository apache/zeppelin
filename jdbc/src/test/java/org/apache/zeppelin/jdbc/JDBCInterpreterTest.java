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
import static org.apache.zeppelin.interpreter.Interpreter.logger;
import static org.apache.zeppelin.interpreter.Interpreter.register;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.DEFAULT_KEY;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.DEFAULT_DRIVER;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.DEFAULT_PASSWORD;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.DEFAULT_USER;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.DEFAULT_URL;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.COMMON_MAX_LINE;
import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.jdbc.JDBCInterpreter;
import org.apache.zeppelin.scheduler.FIFOScheduler;
import org.apache.zeppelin.scheduler.ParallelScheduler;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.apache.zeppelin.user.UserCredentials;
import org.apache.zeppelin.user.UsernamePassword;
import org.junit.Before;
import org.junit.Test;

import com.mockrunner.jdbc.BasicJDBCTestCaseAdapter;
/**
 * JDBC interpreter unit tests
 */
public class JDBCInterpreterTest extends BasicJDBCTestCaseAdapter {

  static String jdbcConnection;
  InterpreterContext interpreterContext;

  private static String getJdbcConnection() throws IOException {
    if(null == jdbcConnection) {
      Path tmpDir = Files.createTempDirectory("h2-test-");
      tmpDir.toFile().deleteOnExit();
      jdbcConnection = format("jdbc:h2:%s", tmpDir);
    }
    return jdbcConnection;
  }

  public static Properties getJDBCTestProperties() {
    Properties p = new Properties();
    p.setProperty("default.driver", "org.postgresql.Driver");
    p.setProperty("default.url", "jdbc:postgresql://localhost:5432/");
    p.setProperty("default.user", "gpadmin");
    p.setProperty("default.password", "");
    p.setProperty("common.max_count", "1000");

    return p;
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
    interpreterContext = new InterpreterContext("", "1", null, "", "", new AuthenticationInfo(), null, null, null, null,
        null, null);
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

    InterpreterResult interpreterResult = t.interpret(sqlQuery, interpreterContext);

    // if prefix not found return ERROR and Prefix not found.
    assertEquals(InterpreterResult.Code.ERROR, interpreterResult.code());
    assertEquals("Prefix not found.", interpreterResult.message());
  }

  @Test
  public void testDefaultProperties() throws SQLException {
    JDBCInterpreter jdbcInterpreter = new JDBCInterpreter(getJDBCTestProperties());

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

    InterpreterResult interpreterResult = t.interpret(sqlQuery, interpreterContext);

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

    InterpreterResult interpreterResult = t.interpret(sqlQuery, interpreterContext);

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

    InterpreterResult interpreterResult = t.interpret(sqlQuery, interpreterContext);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.type());
    assertEquals("ID\tNAME\na\ta_name\n", interpreterResult.message());
  }

  @Test
  public void concurrentSettingTest() {
    Properties properties = new Properties();
    properties.setProperty("zeppelin.jdbc.concurrent.use", "true");
    properties.setProperty("zeppelin.jdbc.concurrent.max_connection", "10");
    JDBCInterpreter jdbcInterpreter = new JDBCInterpreter(properties);

    assertTrue(jdbcInterpreter.isConcurrentExecution());
    assertEquals(10, jdbcInterpreter.getMaxConcurrentConnection());

    Scheduler scheduler = jdbcInterpreter.getScheduler();
    assertTrue(scheduler instanceof ParallelScheduler);

    properties.clear();
    properties.setProperty("zeppelin.jdbc.concurrent.use", "false");
    jdbcInterpreter = new JDBCInterpreter(properties);

    assertFalse(jdbcInterpreter.isConcurrentExecution());

    scheduler = jdbcInterpreter.getScheduler();
    assertTrue(scheduler instanceof FIFOScheduler);
  }

  @Test
  public void testAutoCompletion() throws SQLException, IOException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    JDBCInterpreter jdbcInterpreter = new JDBCInterpreter(properties);
    jdbcInterpreter.open();

    jdbcInterpreter.interpret("", interpreterContext);

    List<InterpreterCompletion> completionList = jdbcInterpreter.completion("SEL", 0);

    InterpreterCompletion correctCompletionKeyword = new InterpreterCompletion("SELECT", "SELECT");

    assertEquals(2, completionList.size());
    assertEquals(true, completionList.contains(correctCompletionKeyword));
    assertEquals(0, jdbcInterpreter.completion("SEL", 100).size());
  }

  @Test
  public void testMultiTenant() throws SQLException, IOException {
    String replName1 = "jdbc1";
    String replName2 = "jdbc2";
    String entityName = "jdbc.jdbc1";

    // create an user credential with replName1(entity name).
    UserCredentials userCredentials = new UserCredentials();
    UsernamePassword up = new UsernamePassword("user2", "pass2");
    userCredentials.putUsernamePassword(entityName, up);
    AuthenticationInfo authInfo = new AuthenticationInfo();
    authInfo.setUserCredentials(userCredentials);
    assertEquals(up.getUsername(), "user2");
    assertEquals(up.getPassword(), "pass2");

    /**
     *  Verify that the user and password in the JDBC Interpreter properties are the same with the argument properties
     *  if replName is same.
     */
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "user1");
    properties.setProperty("default.password", "pass1");
    JDBCInterpreter jdbcInterpreter1 = new JDBCInterpreter(properties);
    jdbcInterpreter1.open();

    InterpreterContext itCtx1 = new InterpreterContext("", "1", replName1, "", "", authInfo, null, null, null, null,
      null, null);

    jdbcInterpreter1.setAccountOfCredential("default", itCtx1);

    assertEquals(properties.getProperty("default.user"),
      jdbcInterpreter1.getPropertiesMap().get("default").getProperty("user"));
    assertEquals(properties.getProperty("default.password"),
      jdbcInterpreter1.getPropertiesMap().get("default").getProperty("password"));
    jdbcInterpreter1.close();

    /**
     *  Verify if setting user and password of JDBC Interpreter properties as UserCredentials when replName is same.
     */
    Properties properties2 = new Properties();
    properties2.setProperty("common.max_count", "1000");
    properties2.setProperty("common.max_retry", "3");
    properties2.setProperty("default.driver", "org.h2.Driver");
    properties2.setProperty("default.url", getJdbcConnection());
    JDBCInterpreter jdbcInterpreter2 = new JDBCInterpreter(properties2);
    jdbcInterpreter2.open();

    InterpreterContext itCtx2 = new InterpreterContext("", "1", replName1, "", "", authInfo, null, null, null, null,
      null, null);
    jdbcInterpreter2.setAccountOfCredential("default", itCtx2);

    assertEquals(up.getUsername(),
      jdbcInterpreter2.getPropertiesMap().get("default").getProperty("user"));
    assertEquals(up.getPassword(),
      jdbcInterpreter2.getPropertiesMap().get("default").getProperty("password"));
    jdbcInterpreter2.close();


    /**
     *  Verify if not setting user and password of the JDBC Interpreter properties when replName is different.
     */
    Properties properties3 = new Properties();
    properties3.setProperty("common.max_count", "1000");
    properties3.setProperty("common.max_retry", "3");
    properties3.setProperty("default.driver", "org.h2.Driver");
    properties3.setProperty("default.url", getJdbcConnection());
    JDBCInterpreter jdbcInterpreter3 = new JDBCInterpreter(properties3);
    jdbcInterpreter3.open();

    InterpreterContext itCtx3 = new InterpreterContext("", "1", replName2, "", "", authInfo, null, null, null, null,
      null, null);
    jdbcInterpreter3.setAccountOfCredential("default", itCtx3);

    assertNull(jdbcInterpreter3.getPropertiesMap().get("default").getProperty("user"));
    assertNull(jdbcInterpreter3.getPropertiesMap().get("default").getProperty("password"));
    jdbcInterpreter3.close();
  }
 }
