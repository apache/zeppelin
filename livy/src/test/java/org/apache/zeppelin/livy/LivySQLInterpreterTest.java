/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.livy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Properties;

/**
 * Unit test for LivySQLInterpreter.
 */
public class LivySQLInterpreterTest {

  private LivySparkSQLInterpreter sqlInterpreter;

  @Before
  public void setUp() {
    Properties properties = new Properties();
    properties.setProperty("zeppelin.livy.url", "http://localhost:8998");
    properties.setProperty("zeppelin.livy.session.create_timeout", "120");
    properties.setProperty("zeppelin.livy.spark.sql.maxResult", "3");
    properties.setProperty("zeppelin.livy.http.headers", "HEADER_1: VALUE_1_${HOME}");
    sqlInterpreter = new LivySparkSQLInterpreter(properties);
  }

  @Test
  public void testHttpHeaders() {
    assertEquals(1, sqlInterpreter.getCustomHeaders().size());
    assertTrue(sqlInterpreter.getCustomHeaders().get("HEADER_1").startsWith("VALUE_1_"));
    assertNotEquals("VALUE_1_${HOME}", sqlInterpreter.getCustomHeaders().get("HEADER_1"));
  }

  @Test
  public void testParseSQLOutput() {
    // Empty sql output
    //    +---+---+
    //    |  a|  b|
    //    +---+---+
    //    +---+---+
    List<String> rows = sqlInterpreter.parseSQLOutput("+---+---+\n" +
                                  "|  a|  b|\n" +
                                  "+---+---+\n" +
                                  "+---+---+");
    assertEquals(1, rows.size());
    assertEquals("a\tb", rows.get(0));


    //  sql output with 2 rows
    //    +---+---+
    //    |  a|  b|
    //    +---+---+
    //    |  1| 1a|
    //    |  2| 2b|
    //    +---+---+
    rows = sqlInterpreter.parseSQLOutput("+---+---+\n" +
        "|  a|  b|\n" +
        "+---+---+\n" +
        "|  1| 1a|\n" +
        "|  2| 2b|\n" +
        "+---+---+");
    assertEquals(3, rows.size());
    assertEquals("a\tb", rows.get(0));
    assertEquals("1\t1a", rows.get(1));
    assertEquals("2\t2b", rows.get(2));


    //  sql output with 3 rows and showing "only showing top 3 rows"
    //    +---+---+
    //    |  a|  b|
    //    +---+---+
    //    |  1| 1a|
    //    |  2| 2b|
    //    |  3| 3c|
    //    +---+---+
    //    only showing top 3 rows
    rows = sqlInterpreter.parseSQLOutput("+---+---+\n" +
        "|  a|  b|\n" +
        "+---+---+\n" +
        "|  1| 1a|\n" +
        "|  2| 2b|\n" +
        "|  3| 3c|\n" +
        "+---+---+\n" +
        "only showing top 3 rows");
    assertEquals(4, rows.size());
    assertEquals("a\tb", rows.get(0));
    assertEquals("1\t1a", rows.get(1));
    assertEquals("2\t2b", rows.get(2));
    assertEquals("3\t3c", rows.get(3));


    //  sql output with 1 rows and showing "only showing top 1 rows"
    //    +---+
    //    |  a|
    //    +---+
    //    |  1|
    //    +---+
    //    only showing top 1 rows
    rows = sqlInterpreter.parseSQLOutput("+---+\n" +
        "|  a|\n" +
        "+---+\n" +
        "|  1|\n" +
        "+---+\n" +
        "only showing top 1 rows");
    assertEquals(2, rows.size());
    assertEquals("a", rows.get(0));
    assertEquals("1", rows.get(1));


    //  sql output with 3 rows, 3 columns, showing "only showing top 3 rows" with a line break in
    //  the data
    //    +---+---+---+
    //    |  a|  b|  c|
    //    +---+---+---+
    //    | 1a| 1b| 1c|
    //    | 2a| 2
    //    b| 2c|
    //    | 3a| 3b| 3c|
    //    +---+---+---+
    //    only showing top 3 rows
    rows = sqlInterpreter.parseSQLOutput("+---+----+---+\n" +
            "|  a|   b|  c|\n" +
            "+---+----+---+\n" +
            "| 1a|  1b| 1c|\n" +
            "| 2a| 2\nb| 2c|\n" +
            "| 3a|  3b| 3c|\n" +
            "+---+---+---+\n" +
            "only showing top 3 rows");
    assertEquals(4, rows.size());
    assertEquals("a\tb\tc", rows.get(0));
    assertEquals("1a\t1b\t1c", rows.get(1));
    assertEquals("2a\t2\\nb\t2c", rows.get(2));
    assertEquals("3a\t3b\t3c", rows.get(3));


    //  sql output with 2 rows and one containing a tab
    //    +---+---+
    //    |  a|  b|
    //    +---+---+
    //    |  1| \ta|
    //    |  2| 2b|
    //    +---+---+
    rows = sqlInterpreter.parseSQLOutput("+---+---+\n" +
            "|  a|  b|\n" +
            "+---+---+\n" +
            "|  1| \ta|\n" +
            "|  2| 2b|\n" +
            "+---+---+");
    assertEquals(3, rows.size());
    assertEquals("a\tb", rows.get(0));
    assertEquals("1\t\\ta", rows.get(1));
    assertEquals("2\t2b", rows.get(2));
  }

  @Test
  public void parseSQLJsonOutput() {
    // Empty sql output
    //    id	name
    List<String> rows = sqlInterpreter.parseSQLJsonOutput("\nid\tname\n");
    assertEquals(1, rows.size());
    assertEquals("id\tname", rows.get(0));


    //  sql output with 2 rows
    // id	name
    // {"id":1,"name":"1a"}
    // {"id":2,"name":"2a"}
    rows = sqlInterpreter.parseSQLJsonOutput("\nid\tname\n"
        + "{\"id\":1,\"name\":\"1a\"}\n"
        + "{\"id\":2,\"name\":\"2a\"}");
    assertEquals(3, rows.size());
    assertEquals("id\tname", rows.get(0));
    assertEquals("1\t1a", rows.get(1));
    assertEquals("2\t2a", rows.get(2));


    //  sql output with 3 rows and showing "only showing top 3 rows"
    // id	name
    // {"id":1,"name":"1a"}
    // {"id":2,"name":"2a"}
    // {"id":3,"name":"3a"}
    //    only showing top 3 rows
    rows = sqlInterpreter.parseSQLJsonOutput("\nid\tname\n"
        + "{\"id\":1,\"name\":\"1a\"}\n"
        + "{\"id\":2,\"name\":\"2a\"}\n"
        + "{\"id\":3,\"name\":\"3a\"}");
    assertEquals(4, rows.size());
    assertEquals("id\tname", rows.get(0));
    assertEquals("1\t1a", rows.get(1));
    assertEquals("2\t2a", rows.get(2));
    assertEquals("3\t3a", rows.get(3));


    //  sql output with 1 rows and showing "only showing top 1 rows"
    // id
    // {"id":1}
    // {"id":2}
    // {"id":3}
    //    only showing top 1 rows
    rows = sqlInterpreter.parseSQLJsonOutput("\nid\n"
        + "{\"id\":1}");
    assertEquals(2, rows.size());
    assertEquals("id", rows.get(0));
    assertEquals("1", rows.get(1));



    //  sql output with 3 rows, 3 columns, showing "only showing top 3 rows" with a line break in
    //  the data
    //    id	name	destination
    //    {"id":1,"name":"1a","destination":"1b"}
    //    {"id":2,"name":"2\na","destination":"2b"}
    //    {"id":3,"name":"3a","destination":"3b"}
    //    only showing top 3 rows
    rows = sqlInterpreter.parseSQLJsonOutput("\nid\tname\tdestination\n"
        + "{\"id\":1,\"name\":\"1a\",\"destination\":\"1b\"}\n"
        + "{\"id\":2,\"name\":\"2\\na\",\"destination\":\"2b\"}\n"
        + "{\"id\":3,\"name\":\"3a\",\"destination\":\"3b\"}");
    assertEquals(4, rows.size());
    assertEquals("id\tname\tdestination", rows.get(0));
    assertEquals("1\t1a\t1b", rows.get(1));
    assertEquals("2\t2\\na\t2b", rows.get(2));
    assertEquals("3\t3a\t3b", rows.get(3));


    //  sql output with 3 rows and one containing a tab
    // id	name
    // {"id":1,"name":"1a"}
    // {"id":2,"name":"2\ta"}
    // {"id":3,"name":"3a"}
    //    only showing top 3 rows
    rows = sqlInterpreter.parseSQLJsonOutput("\nid\tname\n"
        + "{\"id\":1,\"name\":\"1a\"}\n"
        + "{\"id\":2,\"name\":\"2\ta\"}\n"
        + "{\"id\":3,\"name\":\"3a\"}");
    assertEquals(4, rows.size());
    assertEquals("id\tname", rows.get(0));
    assertEquals("1\t1a", rows.get(1));
    assertEquals("2\t2\\ta", rows.get(2));
    assertEquals("3\t3a", rows.get(3));

    //  sql output with 3 rows and one containing a Japanese characters
    // id	name
    // {"id":1,"name":"1a"}
    // {"id":2,"name":"みんく"}
    // {"id":3,"name":"3a"}
    //    only showing top 3 rows
    rows = sqlInterpreter.parseSQLJsonOutput("\nid\tname\n"
        + "{\"id\":1,\"name\":\"1a\"}\n"
        + "{\"id\":2,\"name\":\"みんく\"}\n"
        + "{\"id\":3,\"name\":\"3a\"}");
    assertEquals(4, rows.size());
    assertEquals("id\tname", rows.get(0));
    assertEquals("1\t1a", rows.get(1));
    assertEquals("2\tみんく", rows.get(2));
    assertEquals("3\t3a", rows.get(3));
  }
}
