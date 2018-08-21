/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.jdbc;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;

import com.mockrunner.jdbc.BasicJDBCTestCaseAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.resource.ResourcePool;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.Before;
import org.junit.Test;

/** JDBC interpreter Z-variable interpolation unit tests. */
public class JDBCInterpreterInterpolationTest extends BasicJDBCTestCaseAdapter {

  private static String jdbcConnection;
  private InterpreterContext interpreterContext;
  private ResourcePool resourcePool;

  private String getJdbcConnection() throws IOException {
    if (null == jdbcConnection) {
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
        "DROP TABLE IF EXISTS test_table; "
            + "CREATE TABLE test_table(id varchar(255), name varchar(255));");

    Statement insertStatement = connection.createStatement();
    insertStatement.execute(
        "insert into test_table(id, name) values "
            + "('pro', 'processor'),"
            + "('mem', 'memory'),"
            + "('key', 'keyboard'),"
            + "('mou', 'mouse');");
    resourcePool = new LocalResourcePool("JdbcInterpolationTest");

    interpreterContext =
        InterpreterContext.builder()
            .setParagraphId("paragraph_1")
            .setAuthenticationInfo(new AuthenticationInfo("testUser"))
            .setResourcePool(resourcePool)
            .build();
  }

  @Test
  public void testEnableDisableProperty() throws IOException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");

    resourcePool.put("zid", "mem");
    String sqlQuery = "select * from test_table where id = '{zid}'";

    //
    // Empty result expected because "zeppelin.jdbc.interpolation" is false by default ...
    //
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();
    InterpreterResult interpreterResult = t.interpret(sqlQuery, interpreterContext);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
    assertEquals(1, interpreterResult.message().size());
    assertEquals("ID\tNAME\n", interpreterResult.message().get(0).getData());

    //
    // 1 result expected because "zeppelin.jdbc.interpolation" set to "true" ...
    //
    properties.setProperty("zeppelin.jdbc.interpolation", "true");
    t = new JDBCInterpreter(properties);
    t.open();
    interpreterResult = t.interpret(sqlQuery, interpreterContext);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
    assertEquals(1, interpreterResult.message().size());
    assertEquals("ID\tNAME\nmem\tmemory\n", interpreterResult.message().get(0).getData());
  }

  @Test
  public void testNormalQueryInterpolation() throws IOException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");

    properties.setProperty("zeppelin.jdbc.interpolation", "true");

    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();

    //
    // Empty result expected because "kbd" is not defined ...
    //
    String sqlQuery = "select * from test_table where id = '{kbd}'";
    InterpreterResult interpreterResult = t.interpret(sqlQuery, interpreterContext);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
    assertEquals(1, interpreterResult.message().size());
    assertEquals("ID\tNAME\n", interpreterResult.message().get(0).getData());

    resourcePool.put("itemId", "key");

    //
    // 1 result expected because z-variable 'item' is 'key' ...
    //
    sqlQuery = "select * from test_table where id = '{itemId}'";
    interpreterResult = t.interpret(sqlQuery, interpreterContext);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
    assertEquals(1, interpreterResult.message().size());
    assertEquals("ID\tNAME\nkey\tkeyboard\n", interpreterResult.message().get(0).getData());
  }

  @Test
  public void testEscapedInterpolationPattern() throws IOException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");

    properties.setProperty("zeppelin.jdbc.interpolation", "true");

    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();

    //
    // 2 rows (keyboard and mouse) expected when searching names with 2 consecutive vowels ...
    // The 'regexp' keyword is specific to H2 database
    //
    String sqlQuery = "select * from test_table where name regexp '[aeiou]{{2}}'";
    InterpreterResult interpreterResult = t.interpret(sqlQuery, interpreterContext);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
    assertEquals(1, interpreterResult.message().size());
    assertEquals(
        "ID\tNAME\nkey\tkeyboard\nmou\tmouse\n", interpreterResult.message().get(0).getData());
  }
}
