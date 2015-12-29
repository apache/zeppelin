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
package org.apache.zeppelin.hive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;
import static java.lang.String.format;

/**
 * Hive interpreter unit tests
 */
public class HiveInterpreterTest {
  static String jdbcConnection;

  private static String getJdbcConnection() throws IOException {
    if(null == jdbcConnection) {
      Path tmpDir = Files.createTempDirectory("h2-test-");
      tmpDir.toFile().deleteOnExit();
      jdbcConnection = format("jdbc:h2:%s", tmpDir);
    }
    return jdbcConnection;
  }
  @BeforeClass
  public static void setUp() throws Exception {

    Class.forName("org.h2.Driver");
    Connection connection = DriverManager.getConnection(getJdbcConnection());
    Statement statement = connection.createStatement();
    statement.execute(
        "DROP TABLE IF EXISTS test_table; " +
        "CREATE TABLE test_table(id varchar(255), name varchar(255));");
    statement.execute(
        "insert into test_table(id, name) values ('a', 'a_name'),('b', 'b_name');"
    );
  }

  @After
  public void tearDown() throws Exception {
  }

  @Test
  public void readTest() throws IOException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    HiveInterpreter t = new HiveInterpreter(properties);
    t.open();

    assertTrue(t.interpret("show databases", new InterpreterContext("", "1", "","", null,null,null,null)).message().contains("SCHEMA_NAME"));
    assertEquals("ID\tNAME\na\ta_name\nb\tb_name\n",
        t.interpret("select * from test_table", new InterpreterContext("", "1", "","", null,null,null,null)).message());
  }

  @Test
  public void readTestWithConfiguration() throws IOException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "wrong.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    properties.setProperty("h2.driver", "org.h2.Driver");
    properties.setProperty("h2.url", getJdbcConnection());
    properties.setProperty("h2.user", "");
    properties.setProperty("h2.password", "");
    HiveInterpreter t = new HiveInterpreter(properties);
    t.open();

    assertEquals("ID\tNAME\na\ta_name\nb\tb_name\n",
        t.interpret("(h2)\n select * from test_table", new InterpreterContext("", "1", "","", null,null,null,null)).message());
  }

  @Test
  public void jdbcRestart() throws IOException, SQLException, ClassNotFoundException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    HiveInterpreter t = new HiveInterpreter(properties);
    t.open();

    InterpreterResult interpreterResult =
        t.interpret("select * from test_table", new InterpreterContext("", "1", "","", null,null,null,null));
    assertEquals("ID\tNAME\na\ta_name\nb\tb_name\n", interpreterResult.message());

    t.getConnection("default").close();

    interpreterResult =
        t.interpret("select * from test_table", new InterpreterContext("", "1", "","", null,null,null,null));
    assertEquals("ID\tNAME\na\ta_name\nb\tb_name\n", interpreterResult.message());
  }

  @Test
  public void test() throws IOException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    HiveInterpreter t = new HiveInterpreter(properties);
    t.open();

    InterpreterContext interpreterContext = new InterpreterContext(null, "a", null, null, null, null, null, null);

    //simple select test
    InterpreterResult result = t.interpret("select * from test_table", interpreterContext);
    assertEquals(result.type(), InterpreterResult.Type.TABLE);

    //explain test
    result = t.interpret("explain select * from test_table", interpreterContext);
    assertEquals(result.type(), InterpreterResult.Type.TEXT);
    t.close();
  }

  @Test
  public void parseMultiplePropertiesMap() {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "defaultDriver");
    properties.setProperty("default.url", "defaultUri");
    properties.setProperty("default.user", "defaultUser");
    HiveInterpreter hi = new HiveInterpreter(properties);
    hi.open();
    assertNotNull("propertiesMap is not null", hi.getPropertiesMap());
    assertNotNull("propertiesMap.get(default) is not null", hi.getPropertiesMap().get("default"));
    assertTrue("default exists", "defaultDriver".equals(hi.getPropertiesMap().get("default").getProperty("driver")));
    hi.close();
  }

  @Test
  public void ignoreInvalidSettings() {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "defaultDriver");
    properties.setProperty("default.url", "defaultUri");
    properties.setProperty("default.user", "defaultUser");
    properties.setProperty("presto.driver", "com.facebook.presto.jdbc.PrestoDriver");
    HiveInterpreter hi = new HiveInterpreter(properties);
    hi.open();
    assertTrue("default exists", hi.getPropertiesMap().containsKey("default"));
    assertFalse("presto doesn't exists", hi.getPropertiesMap().containsKey("presto"));
    hi.close();
  }

  @Test
  public void getPropertyKey() {
    HiveInterpreter hi = new HiveInterpreter(new Properties());
    hi.open();
    String testCommand = "(default)\nshow tables";
    assertEquals("get key of default", "default", hi.getPropertyKey(testCommand));
    testCommand = "(default) show tables";
    assertEquals("get key of default", "default", hi.getPropertyKey(testCommand));
    hi.close();
  }
}