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

package org.apache.zeppelin.spark;

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Type;
import org.cassandraunit.CassandraCQLUnit;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class CassandraSparkSqlInterpreterTest {

  private SparkSqlInterpreter sql;
  private SparkInterpreter repl;
  private InterpreterContext context;
  private InterpreterGroup intpGroup;

  @Rule
  public CassandraCQLUnit cassandraCQLUnit = new CassandraCQLUnit(new ClassPathCQLDataSet("cassandra.cql","sparkkeyspace"));


  @Before
  public void setUp() throws Exception {
    Properties p = new Properties();
    p.setProperty("zeppelin.spark.useCassandraContext", "true");
    p.setProperty("zeppelin.spark.useHiveContext", "false");
    p.setProperty("spark.cassandra.connection.host", "127.0.0.1");
    p.setProperty("spark.cassandra.connection.port", "9142");

    if (repl == null) {

      if (SparkInterpreterTest.repl == null) {
        repl = new SparkInterpreter(p);
        repl.open();
        SparkInterpreterTest.repl = repl;
      } else {
        repl = SparkInterpreterTest.repl;
      }

    sql = new SparkSqlInterpreter(p);

    intpGroup = new InterpreterGroup();
      intpGroup.add(repl);
      intpGroup.add(sql);
      sql.setInterpreterGroup(intpGroup);
      sql.open();
    }
    context = new InterpreterContext("note", "id", "title", "text", new HashMap<String, Object>(), new GUI(),
        new AngularObjectRegistry(intpGroup.getId(), null),
        new LinkedList<InterpreterContextRunner>());
  }

  @After
  public void tearDown() throws Exception {
  }

  @Test
  public void test() {
    InterpreterResult ret = sql.interpret("select name, age from sparkkeyspace.test where age < 40", context);
    assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals(Type.TABLE, ret.type());
    assertEquals("name\tage\nmoon\t33\npark\t34\n", ret.message());

    assertEquals(InterpreterResult.Code.SUCCESS, sql.interpret("select * FROM sparkkeyspace.test as t1 INNER JOIN sparkkeyspace.test as t2 on t1.name = t2.name", context).code());
  }
}
