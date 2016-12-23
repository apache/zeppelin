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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.InterpreterResult.Type;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SparkSqlInterpreterTest {

  @Rule
  public TemporaryFolder tmpDir = new TemporaryFolder();

  private SparkSqlInterpreter sql;
  private SparkInterpreter repl;
  private InterpreterContext context;
  private InterpreterGroup intpGroup;

  @Before
  public void setUp() throws Exception {
    Properties p = new Properties();
    p.putAll(SparkInterpreterTest.getSparkTestProperties(tmpDir));
    p.setProperty("zeppelin.spark.maxResult", "1000");
    p.setProperty("zeppelin.spark.concurrentSQL", "false");
    p.setProperty("zeppelin.spark.sql.stacktrace", "false");

    if (repl == null) {

      if (SparkInterpreterTest.repl == null) {
        repl = new SparkInterpreter(p);
        intpGroup = new InterpreterGroup();
        repl.setInterpreterGroup(intpGroup);
        repl.open();
        SparkInterpreterTest.repl = repl;
        SparkInterpreterTest.intpGroup = intpGroup;
      } else {
        repl = SparkInterpreterTest.repl;
        intpGroup = SparkInterpreterTest.intpGroup;
      }

      sql = new SparkSqlInterpreter(p);

      intpGroup = new InterpreterGroup();
      intpGroup.put("note", new LinkedList<Interpreter>());
      intpGroup.get("note").add(repl);
      intpGroup.get("note").add(sql);
      sql.setInterpreterGroup(intpGroup);
      sql.open();
    }
    context = new InterpreterContext("note", "id", null, "title", "text", new AuthenticationInfo(),
        new HashMap<String, Object>(), new GUI(),
        new AngularObjectRegistry(intpGroup.getId(), null),
        new LocalResourcePool("id"),
        new LinkedList<InterpreterContextRunner>(), new InterpreterOutput(null));
  }

  boolean isDataFrameSupported() {
    return SparkInterpreterTest.getSparkVersionNumber() >= 13;
  }

  @Test
  public void test() {
    repl.interpret("case class Test(name:String, age:Int)", context);
    repl.interpret("val test = sc.parallelize(Seq(Test(\"moon\", 33), Test(\"jobs\", 51), Test(\"gates\", 51), Test(\"park\", 34)))", context);
    if (isDataFrameSupported()) {
      repl.interpret("test.toDF.registerTempTable(\"test\")", context);
    } else {
      repl.interpret("test.registerTempTable(\"test\")", context);
    }

    InterpreterResult ret = sql.interpret("select name, age from test where age < 40", context);
    assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals(Type.TABLE, ret.message().get(0).getType());
    assertEquals("name\tage\nmoon\t33\npark\t34\n", ret.message().get(0).getData());

    ret = sql.interpret("select wrong syntax", context);
    assertEquals(InterpreterResult.Code.ERROR, ret.code());
    assertTrue(ret.message().get(0).getData().length() > 0);

    assertEquals(InterpreterResult.Code.SUCCESS, sql.interpret("select case when name==\"aa\" then name else name end from test", context).code());
  }

  @Test
  public void testStruct() {
    repl.interpret("case class Person(name:String, age:Int)", context);
    repl.interpret("case class People(group:String, person:Person)", context);
    repl.interpret(
        "val gr = sc.parallelize(Seq(People(\"g1\", Person(\"moon\",33)), People(\"g2\", Person(\"sun\",11))))",
        context);
    if (isDataFrameSupported()) {
      repl.interpret("gr.toDF.registerTempTable(\"gr\")", context);
    } else {
      repl.interpret("gr.registerTempTable(\"gr\")", context);
    }

    InterpreterResult ret = sql.interpret("select * from gr", context);
    assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
  }

  @Test
  public void test_null_value_in_row() {
    repl.interpret("import org.apache.spark.sql._", context);
    if (isDataFrameSupported()) {
      repl.interpret(
          "import org.apache.spark.sql.types.{StructType,StructField,StringType,IntegerType}",
          context);
    }
    repl.interpret(
        "def toInt(s:String): Any = {try { s.trim().toInt} catch {case e:Exception => null}}",
        context);
    repl.interpret(
        "val schema = StructType(Seq(StructField(\"name\", StringType, false),StructField(\"age\" , IntegerType, true),StructField(\"other\" , StringType, false)))",
        context);
    repl.interpret(
        "val csv = sc.parallelize(Seq((\"jobs, 51, apple\"), (\"gates, , microsoft\")))",
        context);
    repl.interpret(
        "val raw = csv.map(_.split(\",\")).map(p => Row(p(0),toInt(p(1)),p(2)))",
        context);
    if (isDataFrameSupported()) {
      repl.interpret("val people = z.sqlContext.createDataFrame(raw, schema)",
          context);
      repl.interpret("people.toDF.registerTempTable(\"people\")", context);
    } else {
      repl.interpret("val people = z.sqlContext.applySchema(raw, schema)",
          context);
      repl.interpret("people.registerTempTable(\"people\")", context);
    }

    InterpreterResult ret = sql.interpret(
        "select name, age from people where name = 'gates'", context);
    System.err.println("RET=" + ret.message());
    assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals(Type.TABLE, ret.message().get(0).getType());
    assertEquals("name\tage\ngates\tnull\n", ret.message().get(0).getData());
  }
}
