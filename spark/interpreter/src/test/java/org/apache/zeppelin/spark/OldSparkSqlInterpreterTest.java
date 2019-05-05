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
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Type;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.LinkedList;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class OldSparkSqlInterpreterTest {

  @ClassRule
  public static TemporaryFolder tmpDir = new TemporaryFolder();

  static SparkSqlInterpreter sql;
  static SparkInterpreter repl;
  static InterpreterContext context;
  static InterpreterGroup intpGroup;

  @BeforeClass
  public static void setUp() throws Exception {
    Properties p = new Properties();
    p.putAll(OldSparkInterpreterTest.getSparkTestProperties(tmpDir));
    p.setProperty("zeppelin.spark.maxResult", "10");
    p.setProperty("zeppelin.spark.concurrentSQL", "false");
    p.setProperty("zeppelin.spark.sql.stacktrace", "false");
    p.setProperty("zeppelin.spark.deprecatedMsg.show", "false");

    repl = new SparkInterpreter(p);
    intpGroup = new InterpreterGroup();
    repl.setInterpreterGroup(intpGroup);
    repl.open();
    OldSparkInterpreterTest.repl = repl;
    OldSparkInterpreterTest.intpGroup = intpGroup;

    sql = new SparkSqlInterpreter(p);

    intpGroup = new InterpreterGroup();
    intpGroup.put("note", new LinkedList<Interpreter>());
    intpGroup.get("note").add(repl);
    intpGroup.get("note").add(sql);
    sql.setInterpreterGroup(intpGroup);
    sql.open();

    context = InterpreterContext.builder()
        .setNoteId("noteId")
        .setParagraphId("paragraphId")
        .setParagraphTitle("title")
        .setAngularObjectRegistry(new AngularObjectRegistry(intpGroup.getId(), null))
        .setResourcePool(new LocalResourcePool("id"))
        .setInterpreterOut(new InterpreterOutput(null))
        .setIntpEventClient(mock(RemoteInterpreterEventClient.class))
        .build();
  }

  @AfterClass
  public static void tearDown() throws InterpreterException {
    sql.close();
    repl.close();
  }

  boolean isDataFrameSupported() {
    return OldSparkInterpreterTest.getSparkVersionNumber(repl) >= 13;
  }

  @Test
  public void test() throws InterpreterException {
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
  public void testStruct() throws InterpreterException {
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
  public void test_null_value_in_row() throws InterpreterException {
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
      repl.interpret("val people = sqlContext.createDataFrame(raw, schema)",
          context);
      repl.interpret("people.toDF.registerTempTable(\"people\")", context);
    } else {
      repl.interpret("val people = sqlContext.applySchema(raw, schema)",
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

  @Test
  public void testMaxResults() throws InterpreterException {
    repl.interpret("case class P(age:Int)", context);
    repl.interpret(
        "val gr = sc.parallelize(Seq(P(1),P(2),P(3),P(4),P(5),P(6),P(7),P(8),P(9),P(10),P(11)))",
        context);
    if (isDataFrameSupported()) {
      repl.interpret("gr.toDF.registerTempTable(\"gr\")", context);
    } else {
      repl.interpret("gr.registerTempTable(\"gr\")", context);
    }

    InterpreterResult ret = sql.interpret("select * from gr", context);
    assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
    assertTrue(ret.message().get(1).getData().contains("alert-warning"));
  }
}
