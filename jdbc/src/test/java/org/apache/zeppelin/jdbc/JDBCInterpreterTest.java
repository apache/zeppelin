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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static java.lang.String.format;

import static org.apache.zeppelin.jdbc.JDBCInterpreter.COMMON_MAX_LINE;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.DEFAULT_DRIVER;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.DEFAULT_PASSWORD;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.DEFAULT_STATEMENT_PRECODE;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.DEFAULT_USER;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.DEFAULT_URL;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.DEFAULT_PRECODE;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.PRECODE_KEY_TEMPLATE;

import org.junit.Before;
import org.junit.Test;
import static org.apache.zeppelin.jdbc.JDBCInterpreter.STATEMENT_PRECODE_KEY_TEMPLATE;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.mockrunner.jdbc.BasicJDBCTestCaseAdapter;

import org.apache.zeppelin.completer.CompletionType;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.FIFOScheduler;
import org.apache.zeppelin.scheduler.ParallelScheduler;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.UserCredentials;
import org.apache.zeppelin.user.UsernamePassword;

/**
 * JDBC interpreter unit tests.
 */
public class JDBCInterpreterTest extends BasicJDBCTestCaseAdapter {
  static String jdbcConnection;
  InterpreterContext interpreterContext;

  private static String getJdbcConnection() throws IOException {
    if (null == jdbcConnection) {
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

    PreparedStatement insertStatement = connection.prepareStatement(
            "insert into test_table(id, name) values ('a', 'a_name'),('b', 'b_name'),('c', ?);");
    insertStatement.setString(1, null);
    insertStatement.execute();
    interpreterContext = InterpreterContext.builder()
        .setAuthenticationInfo(new AuthenticationInfo("testUser"))
        .build();
  }


  @Test
  public void testForParsePropertyKey() {
    JDBCInterpreter t = new JDBCInterpreter(new Properties());
    Map<String, String> localProperties = new HashMap<>();
    InterpreterContext interpreterContext = InterpreterContext.builder()
        .setLocalProperties(localProperties)
        .build();
    assertEquals(JDBCInterpreter.DEFAULT_KEY, t.getPropertyKey(interpreterContext));

    localProperties = new HashMap<>();
    localProperties.put("db", "mysql");
    interpreterContext = InterpreterContext.builder()
        .setLocalProperties(localProperties)
        .build();
    assertEquals("mysql", t.getPropertyKey(interpreterContext));

    localProperties = new HashMap<>();
    localProperties.put("hive", "hive");
    interpreterContext = InterpreterContext.builder()
        .setLocalProperties(localProperties)
        .build();
    assertEquals("hive", t.getPropertyKey(interpreterContext));
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

    String sqlQuery = "select * from test_table";
    Map<String, String> localProperties = new HashMap<>();
    localProperties.put("db", "fake");
    InterpreterContext context = InterpreterContext.builder()
        .setAuthenticationInfo(new AuthenticationInfo("testUser"))
        .setLocalProperties(localProperties)
        .build();
    InterpreterResult interpreterResult = t.interpret(sqlQuery, context);

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
  public void testColumnAliasQuery() throws IOException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();

    String sqlQuery = "select NAME as SOME_OTHER_NAME from test_table limit 1";

    InterpreterResult interpreterResult = t.interpret(sqlQuery, interpreterContext);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
    assertEquals("SOME_OTHER_NAME\na_name\n", interpreterResult.message().get(0).getData());
  }

  @Test
  public void testSplitSqlQuery() throws SQLException, IOException {
    String sqlQuery = "insert into test_table(id, name) values ('a', ';\"');" +
        "select * from test_table;" +
        "select * from test_table WHERE ID = \";'\";" +
        "select * from test_table WHERE ID = ';';" +
        "select '\n', ';';" +
        "select replace('A\\;B', '\\', 'text');" +
        "select '\\', ';';" +
        "select '''', ';';" +
        "select /*+ scan */ * from test_table;" +
        "--singleLineComment\nselect * from test_table";


    Properties properties = new Properties();
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();
    List<String> multipleSqlArray = t.splitSqlQueries(sqlQuery);
    assertEquals(10, multipleSqlArray.size());
    assertEquals("insert into test_table(id, name) values ('a', ';\"')", multipleSqlArray.get(0));
    assertEquals("select * from test_table", multipleSqlArray.get(1));
    assertEquals("select * from test_table WHERE ID = \";'\"", multipleSqlArray.get(2));
    assertEquals("select * from test_table WHERE ID = ';'", multipleSqlArray.get(3));
    assertEquals("select '\n', ';'", multipleSqlArray.get(4));
    assertEquals("select replace('A\\;B', '\\', 'text')", multipleSqlArray.get(5));
    assertEquals("select '\\', ';'", multipleSqlArray.get(6));
    assertEquals("select '''', ';'", multipleSqlArray.get(7));
    assertEquals("select /*+ scan */ * from test_table", multipleSqlArray.get(8));
    assertEquals("--singleLineComment\nselect * from test_table", multipleSqlArray.get(9));
  }

  @Test
  public void testQueryWithEscapedCharacters() throws SQLException, IOException {
    String sqlQuery = "select '\\n', ';';" +
        "select replace('A\\;B', '\\', 'text');" +
        "select '\\', ';';" +
        "select '''', ';'";

    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    properties.setProperty("default.splitQueries", "true");
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();

    InterpreterResult interpreterResult = t.interpret(sqlQuery, interpreterContext);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(1).getType());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(2).getType());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(3).getType());
    assertEquals("'\\n'\t';'\n\\n\t;\n", interpreterResult.message().get(0).getData());
    assertEquals("'Atext;B'\nAtext;B\n", interpreterResult.message().get(1).getData());
    assertEquals("'\\'\t';'\n\\\t;\n", interpreterResult.message().get(2).getData());
    assertEquals("''''\t';'\n'\t;\n", interpreterResult.message().get(3).getData());
  }

  @Test
  public void testSelectMultipleQueries() throws SQLException, IOException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    properties.setProperty("default.splitQueries", "true");
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();

    String sqlQuery = "select * from test_table;" +
        "select * from test_table WHERE ID = ';';";
    InterpreterResult interpreterResult = t.interpret(sqlQuery, interpreterContext);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(2, interpreterResult.message().size());

    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
    assertEquals("ID\tNAME\na\ta_name\nb\tb_name\nc\tnull\n",
            interpreterResult.message().get(0).getData());

    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(1).getType());
    assertEquals("ID\tNAME\n", interpreterResult.message().get(1).getData());
  }

  @Test
  public void testDefaultSplitQuries() throws SQLException, IOException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();

    String sqlQuery = "select * from test_table;" +
        "select * from test_table WHERE ID = ';';";
    InterpreterResult interpreterResult = t.interpret(sqlQuery, interpreterContext);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(1, interpreterResult.message().size());

    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
    assertEquals("ID\tNAME\na\ta_name\nb\tb_name\nc\tnull\n",
            interpreterResult.message().get(0).getData());
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
    assertEquals(InterpreterResult.Type.HTML, interpreterResult.message().get(1).getType());
    assertTrue(interpreterResult.message().get(1).getData().contains("alert-warning"));
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
  public void testAutoCompletion() throws SQLException, IOException, InterpreterException {
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

    List<InterpreterCompletion> completionList = jdbcInterpreter.completion("sel", 3,
            interpreterContext);

    InterpreterCompletion correctCompletionKeyword = new InterpreterCompletion("select", "select",
            CompletionType.keyword.name());

    assertEquals(1, completionList.size());
    assertEquals(true, completionList.contains(correctCompletionKeyword));
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

  private AuthenticationInfo getUserAuth(String user, String entityName, String dbUser,
      String dbPassword) {
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
    /*
     * assume that the database user is 'dbuser' and password is 'dbpassword'
     * 'jdbc1' interpreter has user('dbuser')/password('dbpassword') property
     * 'jdbc2' interpreter doesn't have user/password property
     * 'user1' doesn't have Credential information.
     * 'user2' has 'jdbc2' Credential information that is 'user2Id' / 'user2Pw' as id and password
     */

    JDBCInterpreter jdbc1 = new JDBCInterpreter(getDBProperty("dbuser", "dbpassword"));
    JDBCInterpreter jdbc2 = new JDBCInterpreter(getDBProperty("", ""));

    AuthenticationInfo user1Credential = getUserAuth("user1", null, null, null);
    AuthenticationInfo user2Credential = getUserAuth("user2", "jdbc.jdbc2", "user2Id", "user2Pw");

    // user1 runs jdbc1
    jdbc1.open();
    InterpreterContext ctx1 = InterpreterContext.builder()
        .setAuthenticationInfo(user1Credential)
        .setReplName("jdbc1")
        .build();
    jdbc1.interpret("", ctx1);

    JDBCUserConfigurations user1JDBC1Conf = jdbc1.getJDBCConfiguration("user1");
    assertEquals("dbuser", user1JDBC1Conf.getPropertyMap("default").get("user"));
    assertEquals("dbpassword", user1JDBC1Conf.getPropertyMap("default").get("password"));
    jdbc1.close();

    // user1 runs jdbc2
    jdbc2.open();
    InterpreterContext ctx2 = InterpreterContext.builder()
        .setAuthenticationInfo(user1Credential)
        .setReplName("jdbc2")
        .build();
    jdbc2.interpret("", ctx2);

    JDBCUserConfigurations user1JDBC2Conf = jdbc2.getJDBCConfiguration("user1");
    assertNull(user1JDBC2Conf.getPropertyMap("default").get("user"));
    assertNull(user1JDBC2Conf.getPropertyMap("default").get("password"));
    jdbc2.close();

    // user2 runs jdbc1
    jdbc1.open();
    InterpreterContext ctx3 = InterpreterContext.builder()
        .setAuthenticationInfo(user2Credential)
        .setReplName("jdbc1")
        .build();
    jdbc1.interpret("", ctx3);

    JDBCUserConfigurations user2JDBC1Conf = jdbc1.getJDBCConfiguration("user2");
    assertEquals("dbuser", user2JDBC1Conf.getPropertyMap("default").get("user"));
    assertEquals("dbpassword", user2JDBC1Conf.getPropertyMap("default").get("password"));
    jdbc1.close();

    // user2 runs jdbc2
    jdbc2.open();
    InterpreterContext ctx4 = InterpreterContext.builder()
        .setAuthenticationInfo(user2Credential)
        .setReplName("jdbc2")
        .build();
    jdbc2.interpret("", ctx4);

    JDBCUserConfigurations user2JDBC2Conf = jdbc2.getJDBCConfiguration("user2");
    assertEquals("user2Id", user2JDBC2Conf.getPropertyMap("default").get("user"));
    assertEquals("user2Pw", user2JDBC2Conf.getPropertyMap("default").get("password"));
    jdbc2.close();
  }

  @Test
  public void testPrecode() throws SQLException, IOException {
    Properties properties = new Properties();
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    properties.setProperty(DEFAULT_PRECODE,
            "create table test_precode (id int); insert into test_precode values (1);");
    JDBCInterpreter jdbcInterpreter = new JDBCInterpreter(properties);
    jdbcInterpreter.open();
    jdbcInterpreter.executePrecode(interpreterContext);

    String sqlQuery = "select *from test_precode";

    InterpreterResult interpreterResult = jdbcInterpreter.interpret(sqlQuery, interpreterContext);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
    assertEquals("ID\n1\n", interpreterResult.message().get(0).getData());
  }

  @Test
  public void testIncorrectPrecode() throws SQLException, IOException {
    Properties properties = new Properties();
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    properties.setProperty(DEFAULT_PRECODE, "select 1");
    properties.setProperty("incorrect.driver", "org.h2.Driver");
    properties.setProperty("incorrect.url", getJdbcConnection());
    properties.setProperty("incorrect.user", "");
    properties.setProperty("incorrect.password", "");
    properties.setProperty(String.format(PRECODE_KEY_TEMPLATE, "incorrect"), "incorrect command");
    JDBCInterpreter jdbcInterpreter = new JDBCInterpreter(properties);
    jdbcInterpreter.open();
    InterpreterResult interpreterResult = jdbcInterpreter.executePrecode(interpreterContext);

    assertEquals(InterpreterResult.Code.ERROR, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TEXT, interpreterResult.message().get(0).getType());
  }

  @Test
  public void testPrecodeWithAnotherPrefix() throws SQLException, IOException {
    Properties properties = new Properties();
    properties.setProperty("anotherPrefix.driver", "org.h2.Driver");
    properties.setProperty("anotherPrefix.url", getJdbcConnection());
    properties.setProperty("anotherPrefix.user", "");
    properties.setProperty("anotherPrefix.password", "");
    properties.setProperty(String.format(PRECODE_KEY_TEMPLATE, "anotherPrefix"),
            "create table test_precode_2 (id int); insert into test_precode_2 values (2);");
    JDBCInterpreter jdbcInterpreter = new JDBCInterpreter(properties);
    jdbcInterpreter.open();

    Map<String, String> localProperties = new HashMap<>();
    localProperties.put("db", "anotherPrefix");
    InterpreterContext context = InterpreterContext.builder()
        .setAuthenticationInfo(new AuthenticationInfo("testUser"))
        .setLocalProperties(localProperties)
        .build();
    jdbcInterpreter.executePrecode(context);

    String sqlQuery = "select * from test_precode_2";

    InterpreterResult interpreterResult = jdbcInterpreter.interpret(sqlQuery, context);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
    assertEquals("ID\n2\n", interpreterResult.message().get(0).getData());
  }

  @Test
  public void testStatementPrecode() throws SQLException, IOException {
    Properties properties = new Properties();
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    properties.setProperty(DEFAULT_STATEMENT_PRECODE, "set @v='statement'");
    JDBCInterpreter jdbcInterpreter = new JDBCInterpreter(properties);
    jdbcInterpreter.open();

    String sqlQuery = "select @v";

    InterpreterResult interpreterResult = jdbcInterpreter.interpret(sqlQuery, interpreterContext);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
    assertEquals("@V\nstatement\n", interpreterResult.message().get(0).getData());
  }

  @Test
  public void testIncorrectStatementPrecode() throws SQLException, IOException {
    Properties properties = new Properties();
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    properties.setProperty(DEFAULT_STATEMENT_PRECODE, "set incorrect");
    JDBCInterpreter jdbcInterpreter = new JDBCInterpreter(properties);
    jdbcInterpreter.open();

    String sqlQuery = "select 1";

    InterpreterResult interpreterResult = jdbcInterpreter.interpret(sqlQuery, interpreterContext);

    assertEquals(InterpreterResult.Code.ERROR, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TEXT, interpreterResult.message().get(0).getType());
  }

  @Test
  public void testStatementPrecodeWithAnotherPrefix() throws SQLException, IOException {
    Properties properties = new Properties();
    properties.setProperty("anotherPrefix.driver", "org.h2.Driver");
    properties.setProperty("anotherPrefix.url", getJdbcConnection());
    properties.setProperty("anotherPrefix.user", "");
    properties.setProperty("anotherPrefix.password", "");
    properties.setProperty(String.format(STATEMENT_PRECODE_KEY_TEMPLATE, "anotherPrefix"),
            "set @v='statementAnotherPrefix'");
    JDBCInterpreter jdbcInterpreter = new JDBCInterpreter(properties);
    jdbcInterpreter.open();

    Map<String, String> localProperties = new HashMap<>();
    localProperties.put("db", "anotherPrefix");
    InterpreterContext context = InterpreterContext.builder()
        .setAuthenticationInfo(new AuthenticationInfo("testUser"))
        .setLocalProperties(localProperties)
        .build();

    String sqlQuery = "select @v";

    InterpreterResult interpreterResult = jdbcInterpreter.interpret(sqlQuery, context);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
    assertEquals("@V\nstatementAnotherPrefix\n", interpreterResult.message().get(0).getData());
  }

  @Test
  public void testSplitSqlQueryWithComments() throws SQLException, IOException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    properties.setProperty("default.splitQueries", "true");
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();

    String sqlQuery = "/* ; */\n" +
        "-- /* comment\n" +
        "--select * from test_table\n" +
        "select * from test_table; /* some comment ; */\n" +
        "/*\n" +
        "select * from test_table;\n" +
        "*/\n" +
        "-- a ; b\n" +
        "select * from test_table WHERE ID = ';--';\n" +
        "select * from test_table WHERE ID = '/*' -- test";

    InterpreterResult interpreterResult = t.interpret(sqlQuery, interpreterContext);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(3, interpreterResult.message().size());
  }
}
