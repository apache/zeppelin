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
    assertEquals("Prefix not found.", interpreterResult.message().get(0).getData());
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
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
    assertEquals("ID\tNAME\na\ta_name\nb\tb_name\n", interpreterResult.message().get(0).getData());
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
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
    assertEquals("ID\tNAME\nc\tnull\n", interpreterResult.message().get(0).getData());
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
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
    assertEquals("ID\tNAME\na\ta_name\n", interpreterResult.message().get(0).getData());
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

  private Properties getDBProperty(String dbUser, String dbPassowrd) throws IOException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    if (dbUser != null) {
      properties.setProperty("default.user", dbUser);
    }
    if (dbPassowrd != null) {
      properties.setProperty("default.password", dbPassowrd);
    }
    return properties;
  }

  private AuthenticationInfo getUserAuth(String user, String entityName, String dbUser, String dbPassword){
    UserCredentials userCredentials = new UserCredentials();
    if (entityName != null && dbUser != null && dbPassword != null) {
      UsernamePassword up = new UsernamePassword(dbUser, dbPassword);
      userCredentials.putUsernamePassword(entityName, up);
    }
    AuthenticationInfo authInfo = new AuthenticationInfo();
    authInfo.setUserCredentials(userCredentials);
    authInfo.setUser(user);
    return authInfo;
  }

  @Test
  public void testMultiTenant() throws SQLException, IOException {

    /**
     * assume that the database user is 'dbuser' and password is 'dbpassword'
     * 'jdbc1' interpreter has user('dbuser')/password('dbpassword') property
     * 'jdbc2' interpreter doesn't have user/password property
     * 'user1' doesn't have Credential information.
     * 'user2' has 'jdbc2' Credential information that is same with database account.
     */

    JDBCInterpreter jdbc1 = new JDBCInterpreter(getDBProperty("dbuser", "dbpassword"));
    JDBCInterpreter jdbc2 = new JDBCInterpreter(getDBProperty(null, null));

    AuthenticationInfo user1Credential = getUserAuth("user1", null, null, null);
    AuthenticationInfo user2Credential = getUserAuth("user2", "jdbc.jdbc2", "dbuser", "dbpassword");

    // user1 runs jdbc1
    jdbc1.open();
    InterpreterContext ctx1 = new InterpreterContext("", "1", "jdbc.jdbc1", "", "", user1Credential,
      null, null, null, null, null, null);
    jdbc1.interpret("", ctx1);

    JDBCUserConfigurations user1JDBC1Conf = jdbc1.getJDBCConfiguration("user1");
    assertEquals("dbuser", user1JDBC1Conf.getPropertyMap("default").get("user"));
    assertEquals("dbpassword", user1JDBC1Conf.getPropertyMap("default").get("password"));
    jdbc1.close();

    // user1 runs jdbc2
    jdbc2.open();
    InterpreterContext ctx2 = new InterpreterContext("", "1", "jdbc.jdbc2", "", "", user1Credential,
      null, null, null, null, null, null);
    jdbc2.interpret("", ctx2);

    JDBCUserConfigurations user1JDBC2Conf = jdbc2.getJDBCConfiguration("user1");
    assertNull(user1JDBC2Conf.getPropertyMap("default").get("user"));
    assertNull(user1JDBC2Conf.getPropertyMap("default").get("password"));
    jdbc2.close();

    // user2 runs jdbc1
    jdbc1.open();
    InterpreterContext ctx3 = new InterpreterContext("", "1", "jdbc.jdbc1", "", "", user2Credential,
      null, null, null, null, null, null);
    jdbc1.interpret("", ctx3);

    JDBCUserConfigurations user2JDBC1Conf = jdbc1.getJDBCConfiguration("user2");
    assertEquals("dbuser", user2JDBC1Conf.getPropertyMap("default").get("user"));
    assertEquals("dbpassword", user2JDBC1Conf.getPropertyMap("default").get("password"));
    jdbc1.close();

    // user2 runs jdbc2
    jdbc2.open();
    InterpreterContext ctx4 = new InterpreterContext("", "1", "jdbc.jdbc2", "", "", user2Credential,
      null, null, null, null, null, null);
    jdbc2.interpret("", ctx4);

    JDBCUserConfigurations user2JDBC2Conf = jdbc2.getJDBCConfiguration("user2");
    assertNull(user2JDBC2Conf.getPropertyMap("default").get("user"));
    assertNull(user2JDBC2Conf.getPropertyMap("default").get("password"));
    jdbc2.close();
  }
 }
