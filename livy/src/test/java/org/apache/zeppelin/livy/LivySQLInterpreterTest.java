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

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Properties;
import static org.junit.Assert.*;

/**
 * Unit test for LivySQLInterpreter
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
  }
}
