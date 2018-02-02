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

import com.google.common.io.Files;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.InterpreterResult.Type;
import org.junit.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NewSparkSqlInterpreterTest {

  private static SparkSqlInterpreter sqlInterpreter;
  private static SparkInterpreter sparkInterpreter;
  private static InterpreterContext context;
  private static InterpreterGroup intpGroup;

  @BeforeClass
  public static void setUp() throws Exception {
    Properties p = new Properties();
    p.setProperty("spark.master", "local");
    p.setProperty("spark.app.name", "test");
    p.setProperty("zeppelin.spark.maxResult", "10");
    p.setProperty("zeppelin.spark.concurrentSQL", "false");
    p.setProperty("zeppelin.spark.sqlInterpreter.stacktrace", "false");
    p.setProperty("zeppelin.spark.useNew", "true");
    intpGroup = new InterpreterGroup();
    sparkInterpreter = new SparkInterpreter(p);
    sparkInterpreter.setInterpreterGroup(intpGroup);

    sqlInterpreter = new SparkSqlInterpreter(p);
    sqlInterpreter.setInterpreterGroup(intpGroup);
    intpGroup.put("session_1", new LinkedList<Interpreter>());
    intpGroup.get("session_1").add(sparkInterpreter);
    intpGroup.get("session_1").add(sqlInterpreter);

    sparkInterpreter.open();
    sqlInterpreter.open();

    context = new InterpreterContext("note", "id", null, "title", "text", new AuthenticationInfo(),
        new HashMap<String, Object>(), new GUI(), new GUI(),
        new AngularObjectRegistry(intpGroup.getId(), null),
        new LocalResourcePool("id"),
        new LinkedList<InterpreterContextRunner>(), new InterpreterOutput(null));
  }

  @AfterClass
  public static void tearDown() throws InterpreterException {
    sqlInterpreter.close();
    sparkInterpreter.close();
  }

  boolean isDataFrameSupported() {
    return sparkInterpreter.getSparkVersion().hasDataFrame();
  }

  @Test
  public void test() throws InterpreterException {
    sparkInterpreter.interpret("case class Test(name:String, age:Int)", context);
    sparkInterpreter.interpret("val test = sc.parallelize(Seq(Test(\"moon\", 33), Test(\"jobs\", 51), Test(\"gates\", 51), Test(\"park\", 34)))", context);
    if (isDataFrameSupported()) {
      sparkInterpreter.interpret("test.toDF.registerTempTable(\"test\")", context);
    } else {
      sparkInterpreter.interpret("test.registerTempTable(\"test\")", context);
    }

    InterpreterResult ret = sqlInterpreter.interpret("select name, age from test where age < 40", context);
    assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals(Type.TABLE, ret.message().get(0).getType());
    assertEquals("name\tage\nmoon\t33\npark\t34\n", ret.message().get(0).getData());

    ret = sqlInterpreter.interpret("select wrong syntax", context);
    assertEquals(InterpreterResult.Code.ERROR, ret.code());
    assertTrue(ret.message().get(0).getData().length() > 0);

    assertEquals(InterpreterResult.Code.SUCCESS, sqlInterpreter.interpret("select case when name='aa' then name else name end from test", context).code());
  }

  @Test
  public void testStruct() throws InterpreterException {
    sparkInterpreter.interpret("case class Person(name:String, age:Int)", context);
    sparkInterpreter.interpret("case class People(group:String, person:Person)", context);
    sparkInterpreter.interpret(
        "val gr = sc.parallelize(Seq(People(\"g1\", Person(\"moon\",33)), People(\"g2\", Person(\"sun\",11))))",
        context);
    if (isDataFrameSupported()) {
      sparkInterpreter.interpret("gr.toDF.registerTempTable(\"gr\")", context);
    } else {
      sparkInterpreter.interpret("gr.registerTempTable(\"gr\")", context);
    }

    InterpreterResult ret = sqlInterpreter.interpret("select * from gr", context);
    assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
  }

  public void test_null_value_in_row() throws InterpreterException {
    sparkInterpreter.interpret("import org.apache.spark.sql._", context);
    if (isDataFrameSupported()) {
      sparkInterpreter.interpret(
          "import org.apache.spark.sql.types.{StructType,StructField,StringType,IntegerType}",
          context);
    }
    sparkInterpreter.interpret(
        "def toInt(s:String): Any = {try { s.trim().toInt} catch {case e:Exception => null}}",
        context);
    sparkInterpreter.interpret(
        "val schema = StructType(Seq(StructField(\"name\", StringType, false),StructField(\"age\" , IntegerType, true),StructField(\"other\" , StringType, false)))",
        context);
    sparkInterpreter.interpret(
        "val csv = sc.parallelize(Seq((\"jobs, 51, apple\"), (\"gates, , microsoft\")))",
        context);
    sparkInterpreter.interpret(
        "val raw = csv.map(_.split(\",\")).map(p => Row(p(0),toInt(p(1)),p(2)))",
        context);
    if (isDataFrameSupported()) {
      sparkInterpreter.interpret("val people = sqlContext.createDataFrame(raw, schema)",
          context);
      sparkInterpreter.interpret("people.toDF.registerTempTable(\"people\")", context);
    } else {
      sparkInterpreter.interpret("val people = sqlContext.applySchema(raw, schema)",
          context);
      sparkInterpreter.interpret("people.registerTempTable(\"people\")", context);
    }

    InterpreterResult ret = sqlInterpreter.interpret(
        "select name, age from people where name = 'gates'", context);
    assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals(Type.TABLE, ret.message().get(0).getType());
    assertEquals("name\tage\ngates\tnull\n", ret.message().get(0).getData());
  }

  @Test
  public void testMaxResults() throws InterpreterException {
    sparkInterpreter.interpret("case class P(age:Int)", context);
    sparkInterpreter.interpret(
        "val gr = sc.parallelize(Seq(P(1),P(2),P(3),P(4),P(5),P(6),P(7),P(8),P(9),P(10),P(11)))",
        context);
    if (isDataFrameSupported()) {
      sparkInterpreter.interpret("gr.toDF.registerTempTable(\"gr\")", context);
    } else {
      sparkInterpreter.interpret("gr.registerTempTable(\"gr\")", context);
    }

    InterpreterResult ret = sqlInterpreter.interpret("select * from gr", context);
    assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
    assertTrue(ret.message().get(1).getData().contains("alert-warning"));
  }
}
