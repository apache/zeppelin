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


import org.apache.livy.test.framework.Cluster;
import org.apache.livy.test.framework.Cluster$;
import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LivyInterpreterIT {

  private static Logger LOGGER = LoggerFactory.getLogger(LivyInterpreterIT.class);
  private static Cluster cluster;
  private static Properties properties;

  @BeforeClass
  public static void setUp() {
    if (!checkPreCondition()) {
      return;
    }
    cluster = Cluster$.MODULE$.get();
    LOGGER.info("Starting livy at {}", cluster.livyEndpoint());
    properties = new Properties();
    properties.setProperty("zeppelin.livy.url", cluster.livyEndpoint());
    properties.setProperty("zeppelin.livy.session.create_timeout", "120");
    properties.setProperty("zeppelin.livy.spark.sql.maxResult", "100");
    properties.setProperty("zeppelin.livy.displayAppInfo", "false");
  }

  @AfterClass
  public static void tearDown() {
    if (cluster != null) {
      LOGGER.info("Shutting down livy at {}", cluster.livyEndpoint());
      cluster.cleanUp();
    }
  }

  public static boolean checkPreCondition() {
    if (System.getenv("LIVY_HOME") == null) {
      LOGGER.warn(("livy integration is skipped because LIVY_HOME is not set"));
      return false;
    }
    if (System.getenv("SPARK_HOME") == null) {
      LOGGER.warn(("livy integration is skipped because SPARK_HOME is not set"));
      return false;
    }
    return true;
  }


//  @Test
  public void testSparkInterpreterRDD() throws InterpreterException {
    if (!checkPreCondition()) {
      return;
    }
    InterpreterGroup interpreterGroup = new InterpreterGroup("group_1");
    interpreterGroup.put("session_1", new ArrayList<Interpreter>());
    final LivySparkInterpreter sparkInterpreter = new LivySparkInterpreter(properties);
    sparkInterpreter.setInterpreterGroup(interpreterGroup);
    interpreterGroup.get("session_1").add(sparkInterpreter);
    AuthenticationInfo authInfo = new AuthenticationInfo("user1");
    MyInterpreterOutputListener outputListener = new MyInterpreterOutputListener();
    InterpreterOutput output = new InterpreterOutput(outputListener);
    final InterpreterContext context = new InterpreterContext("noteId", "paragraphId", "livy.spark",
        "title", "text", authInfo, null, null, null, null, null, null, output);
    sparkInterpreter.open();

    try {
      // detect spark version
      InterpreterResult result = sparkInterpreter.interpret("sc.version", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(1, result.message().size());

      boolean isSpark2 = isSpark2(sparkInterpreter, context);

      // test RDD api
      result = sparkInterpreter.interpret("sc.parallelize(1 to 10).sum()", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(1, result.message().size());
      assertTrue(result.message().get(0).getData().contains("Double = 55.0"));

      // single line comment
      String singleLineComment = "println(1)// my comment";
      result = sparkInterpreter.interpret(singleLineComment, context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(1, result.message().size());

      // multiple line comment
      String multipleLineComment = "println(1)/* multiple \n" + "line \n" + "comment */";
      result = sparkInterpreter.interpret(multipleLineComment, context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(1, result.message().size());

      // multi-line string
      String multiLineString = "val str = \"\"\"multiple\n" +
          "line\"\"\"\n" +
          "println(str)";
      result = sparkInterpreter.interpret(multiLineString, context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(1, result.message().size());
      assertTrue(result.message().get(0).getData().contains("multiple\nline"));

      // case class
      String caseClassCode = "case class Person(id:Int, \n" +
          "name:String)\n" +
          "val p=Person(1, \"name_a\")";
      result = sparkInterpreter.interpret(caseClassCode, context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(1, result.message().size());
      assertTrue(result.message().get(0).getData().contains("p: Person = Person(1,name_a)"));

      // object class
      String objectClassCode = "object Person {}";
      result = sparkInterpreter.interpret(objectClassCode, context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(1, result.message().size());
      if (!isSpark2) {
        assertTrue(result.message().get(0).getData().contains("defined module Person"));
      } else {
        assertTrue(result.message().get(0).getData().contains("defined object Person"));
      }

      // html output
      String htmlCode = "println(\"%html <h1> hello </h1>\")";
      result = sparkInterpreter.interpret(htmlCode, context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(1, result.message().size());
      assertEquals(InterpreterResult.Type.HTML, result.message().get(0).getType());

      // error
      result = sparkInterpreter.interpret("println(a)", context);
      assertEquals(InterpreterResult.Code.ERROR, result.code());
      assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType());
      assertTrue(result.message().get(0).getData().contains("error: not found: value a"));

      // incomplete code
      result = sparkInterpreter.interpret("if(true){", context);
      assertEquals(InterpreterResult.Code.ERROR, result.code());
      assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType());
      assertTrue(result.message().get(0).getData().contains("incomplete statement"));

      // cancel
      if (sparkInterpreter.livyVersion.newerThanEquals(LivyVersion.LIVY_0_3_0)) {
        Thread cancelThread = new Thread() {
          @Override
          public void run() {
            // invoke cancel after 1 millisecond to wait job starting
            try {
              Thread.sleep(1);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
            sparkInterpreter.cancel(context);
          }
        };
        cancelThread.start();
        result = sparkInterpreter
            .interpret("sc.parallelize(1 to 10).foreach(e=>Thread.sleep(10*1000))", context);
        assertEquals(InterpreterResult.Code.ERROR, result.code());
        String message = result.message().get(0).getData();
        // 2 possibilities, sometimes livy doesn't return the real cancel exception
        assertTrue(message.contains("cancelled part of cancelled job group") ||
            message.contains("Job is cancelled"));
      }

    } finally {
      sparkInterpreter.close();
    }
  }


//  @Test
  public void testSparkInterpreterDataFrame() throws InterpreterException {
    if (!checkPreCondition()) {
      return;
    }
    InterpreterGroup interpreterGroup = new InterpreterGroup("group_1");
    interpreterGroup.put("session_1", new ArrayList<Interpreter>());
    LivySparkInterpreter sparkInterpreter = new LivySparkInterpreter(properties);
    sparkInterpreter.setInterpreterGroup(interpreterGroup);
    interpreterGroup.get("session_1").add(sparkInterpreter);
    AuthenticationInfo authInfo = new AuthenticationInfo("user1");
    MyInterpreterOutputListener outputListener = new MyInterpreterOutputListener();
    InterpreterOutput output = new InterpreterOutput(outputListener);
    InterpreterContext context = new InterpreterContext("noteId", "paragraphId", "livy.spark",
        "title", "text", authInfo, null, null, null, null, null, null, output);
    sparkInterpreter.open();

    LivySparkSQLInterpreter sqlInterpreter = new LivySparkSQLInterpreter(properties);
    interpreterGroup.get("session_1").add(sqlInterpreter);
    sqlInterpreter.setInterpreterGroup(interpreterGroup);
    sqlInterpreter.open();

    try {
      // detect spark version
      InterpreterResult result = sparkInterpreter.interpret("sc.version", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(1, result.message().size());

      boolean isSpark2 = isSpark2(sparkInterpreter, context);

      // test DataFrame api
      if (!isSpark2) {
        result = sparkInterpreter.interpret(
            "val df=sqlContext.createDataFrame(Seq((\"hello\",20))).toDF(\"col_1\", \"col_2\")\n"
                + "df.collect()", context);
        assertEquals(InterpreterResult.Code.SUCCESS, result.code());
        assertEquals(1, result.message().size());
        assertTrue(result.message().get(0).getData()
            .contains("Array[org.apache.spark.sql.Row] = Array([hello,20])"));
      } else {
        result = sparkInterpreter.interpret(
            "val df=spark.createDataFrame(Seq((\"hello\",20))).toDF(\"col_1\", \"col_2\")\n"
                + "df.collect()", context);
        assertEquals(InterpreterResult.Code.SUCCESS, result.code());
        assertEquals(1, result.message().size());
        assertTrue(result.message().get(0).getData()
            .contains("Array[org.apache.spark.sql.Row] = Array([hello,20])"));
      }
      sparkInterpreter.interpret("df.registerTempTable(\"df\")", context);
      // test LivySparkSQLInterpreter which share the same SparkContext with LivySparkInterpreter
      result = sqlInterpreter.interpret("select * from df where col_1='hello'", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(InterpreterResult.Type.TABLE, result.message().get(0).getType());
      assertEquals("col_1\tcol_2\nhello\t20", result.message().get(0).getData());
      // double quotes
      result = sqlInterpreter.interpret("select * from df where col_1=\"hello\"", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(InterpreterResult.Type.TABLE, result.message().get(0).getType());
      assertEquals("col_1\tcol_2\nhello\t20", result.message().get(0).getData());

      // only enable this test in spark2 as spark1 doesn't work for this case
      if (isSpark2) {
        result = sqlInterpreter.interpret("select * from df where col_1=\"he\\\"llo\" ", context);
        assertEquals(InterpreterResult.Code.SUCCESS, result.code());
        assertEquals(InterpreterResult.Type.TABLE, result.message().get(0).getType());
      }

      // single quotes inside attribute value
      result = sqlInterpreter.interpret("select * from df where col_1=\"he'llo\"", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(InterpreterResult.Type.TABLE, result.message().get(0).getType());

      // test sql with syntax error
      result = sqlInterpreter.interpret("select * from df2", context);
      assertEquals(InterpreterResult.Code.ERROR, result.code());
      assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType());

      if (!isSpark2) {
        assertTrue(result.message().get(0).getData().contains("Table not found"));
      } else {
        assertTrue(result.message().get(0).getData().contains("Table or view not found"));
      }
    } finally {
      sparkInterpreter.close();
      sqlInterpreter.close();
    }
  }

//  @Test
  public void testSparkSQLInterpreter() throws InterpreterException {
    if (!checkPreCondition()) {
      return;
    }
    InterpreterGroup interpreterGroup = new InterpreterGroup("group_1");
    interpreterGroup.put("session_1", new ArrayList<Interpreter>());
    LazyOpenInterpreter sparkInterpreter = new LazyOpenInterpreter(
        new LivySparkInterpreter(properties));
    sparkInterpreter.setInterpreterGroup(interpreterGroup);
    interpreterGroup.get("session_1").add(sparkInterpreter);
    LazyOpenInterpreter sqlInterpreter = new LazyOpenInterpreter(
        new LivySparkSQLInterpreter(properties));
    interpreterGroup.get("session_1").add(sqlInterpreter);
    sqlInterpreter.setInterpreterGroup(interpreterGroup);
    sqlInterpreter.open();

    try {
      AuthenticationInfo authInfo = new AuthenticationInfo("user1");
      MyInterpreterOutputListener outputListener = new MyInterpreterOutputListener();
      InterpreterOutput output = new InterpreterOutput(outputListener);
      InterpreterContext context = new InterpreterContext("noteId", "paragraphId", "livy.sql",
          "title", "text", authInfo, null, null, null, null, null, null, output);
      InterpreterResult result = sqlInterpreter.interpret("show tables", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(InterpreterResult.Type.TABLE, result.message().get(0).getType());
      assertTrue(result.message().get(0).getData().contains("tableName"));
      int r = sqlInterpreter.getProgress(context);
      assertTrue(r == 0);
    } finally {
      sqlInterpreter.close();
    }
  }


//  @Test
  public void testSparkSQLCancellation() throws InterpreterException {
    if (!checkPreCondition()) {
      return;
    }
    InterpreterGroup interpreterGroup = new InterpreterGroup("group_1");
    interpreterGroup.put("session_1", new ArrayList<Interpreter>());
    LivySparkInterpreter sparkInterpreter = new LivySparkInterpreter(properties);
    sparkInterpreter.setInterpreterGroup(interpreterGroup);
    interpreterGroup.get("session_1").add(sparkInterpreter);
    AuthenticationInfo authInfo = new AuthenticationInfo("user1");
    MyInterpreterOutputListener outputListener = new MyInterpreterOutputListener();
    InterpreterOutput output = new InterpreterOutput(outputListener);
    final InterpreterContext context = new InterpreterContext("noteId", "paragraphId", "livy.spark",
        "title", "text", authInfo, null, null, null, null, null, null, output);
    sparkInterpreter.open();

    final LivySparkSQLInterpreter sqlInterpreter = new LivySparkSQLInterpreter(properties);
    interpreterGroup.get("session_1").add(sqlInterpreter);
    sqlInterpreter.setInterpreterGroup(interpreterGroup);
    sqlInterpreter.open();

    try {
      // detect spark version
      InterpreterResult result = sparkInterpreter.interpret("sc.version", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(1, result.message().size());

      boolean isSpark2 = isSpark2(sparkInterpreter, context);

      // test DataFrame api
      if (!isSpark2) {
        result = sparkInterpreter.interpret(
            "val df=sqlContext.createDataFrame(Seq((\"hello\",20))).toDF(\"col_1\", \"col_2\")\n"
                + "df.collect()", context);
        assertEquals(InterpreterResult.Code.SUCCESS, result.code());
        assertEquals(1, result.message().size());
        assertTrue(result.message().get(0).getData()
            .contains("Array[org.apache.spark.sql.Row] = Array([hello,20])"));
      } else {
        result = sparkInterpreter.interpret(
            "val df=spark.createDataFrame(Seq((\"hello\",20))).toDF(\"col_1\", \"col_2\")\n"
                + "df.collect()", context);
        assertEquals(InterpreterResult.Code.SUCCESS, result.code());
        assertEquals(1, result.message().size());
        assertTrue(result.message().get(0).getData()
            .contains("Array[org.apache.spark.sql.Row] = Array([hello,20])"));
      }
      sparkInterpreter.interpret("df.registerTempTable(\"df\")", context);

      // cancel
      if (sqlInterpreter.getLivyVersion().newerThanEquals(LivyVersion.LIVY_0_3_0)) {
        Thread cancelThread = new Thread() {
          @Override
          public void run() {
            sqlInterpreter.cancel(context);
          }
        };
        cancelThread.start();
        //sleep so that cancelThread performs a cancel.
        try {
          Thread.sleep(1);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        result = sqlInterpreter
            .interpret("select count(1) from df", context);
        if (result.code().equals(InterpreterResult.Code.ERROR)) {
          String message = result.message().get(0).getData();
          // 2 possibilities, sometimes livy doesn't return the real cancel exception
          assertTrue(message.contains("cancelled part of cancelled job group") ||
              message.contains("Job is cancelled"));
        }
      }
    } catch (LivyException e) {
    } finally {
      sparkInterpreter.close();
      sqlInterpreter.close();
    }
  }

//  @Test
  public void testStringWithTruncation() throws InterpreterException {
    if (!checkPreCondition()) {
      return;
    }
    InterpreterGroup interpreterGroup = new InterpreterGroup("group_1");
    interpreterGroup.put("session_1", new ArrayList<Interpreter>());
    LivySparkInterpreter sparkInterpreter = new LivySparkInterpreter(properties);
    sparkInterpreter.setInterpreterGroup(interpreterGroup);
    interpreterGroup.get("session_1").add(sparkInterpreter);
    AuthenticationInfo authInfo = new AuthenticationInfo("user1");
    MyInterpreterOutputListener outputListener = new MyInterpreterOutputListener();
    InterpreterOutput output = new InterpreterOutput(outputListener);
    InterpreterContext context = new InterpreterContext("noteId", "paragraphId", "livy.spark",
        "title", "text", authInfo, null, null, null, null, null, null, output);
    sparkInterpreter.open();

    LivySparkSQLInterpreter sqlInterpreter = new LivySparkSQLInterpreter(properties);
    interpreterGroup.get("session_1").add(sqlInterpreter);
    sqlInterpreter.setInterpreterGroup(interpreterGroup);
    sqlInterpreter.open();

    try {
      // detect spark version
      InterpreterResult result = sparkInterpreter.interpret("sc.version", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(1, result.message().size());

      boolean isSpark2 = isSpark2(sparkInterpreter, context);

      // test DataFrame api
      if (!isSpark2) {
        result = sparkInterpreter.interpret(
            "val df=sqlContext.createDataFrame(Seq((\"12characters12characters\",20))).toDF(\"col_1\", \"col_2\")\n"
                + "df.collect()", context);
        assertEquals(InterpreterResult.Code.SUCCESS, result.code());
        assertEquals(1, result.message().size());
        assertTrue(result.message().get(0).getData()
            .contains("Array[org.apache.spark.sql.Row] = Array([12characters12characters,20])"));
      } else {
        result = sparkInterpreter.interpret(
            "val df=spark.createDataFrame(Seq((\"12characters12characters\",20))).toDF(\"col_1\", \"col_2\")\n"
                + "df.collect()", context);
        assertEquals(InterpreterResult.Code.SUCCESS, result.code());
        assertEquals(1, result.message().size());
        assertTrue(result.message().get(0).getData()
            .contains("Array[org.apache.spark.sql.Row] = Array([12characters12characters,20])"));
      }
      sparkInterpreter.interpret("df.registerTempTable(\"df\")", context);
      // test LivySparkSQLInterpreter which share the same SparkContext with LivySparkInterpreter
      result = sqlInterpreter.interpret("select * from df where col_1='12characters12characters'", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(InterpreterResult.Type.TABLE, result.message().get(0).getType());
      assertEquals("col_1\tcol_2\n12characters12cha...\t20", result.message().get(0).getData());
    } finally {
      sparkInterpreter.close();
      sqlInterpreter.close();
    }
  }


//  @Test
  public void testStringWithoutTruncation() throws InterpreterException {
    if (!checkPreCondition()) {
      return;
    }
    InterpreterGroup interpreterGroup = new InterpreterGroup("group_1");
    interpreterGroup.put("session_1", new ArrayList<Interpreter>());
    Properties newProps = new Properties();
    for (Object name: properties.keySet()) {
      newProps.put(name, properties.get(name));
    }
    newProps.put(LivySparkSQLInterpreter.ZEPPELIN_LIVY_SPARK_SQL_FIELD_TRUNCATE, "false");
    LivySparkInterpreter sparkInterpreter = new LivySparkInterpreter(newProps);
    sparkInterpreter.setInterpreterGroup(interpreterGroup);
    interpreterGroup.get("session_1").add(sparkInterpreter);
    AuthenticationInfo authInfo = new AuthenticationInfo("user1");
    MyInterpreterOutputListener outputListener = new MyInterpreterOutputListener();
    InterpreterOutput output = new InterpreterOutput(outputListener);
    InterpreterContext context = new InterpreterContext("noteId", "paragraphId", "livy.spark",
        "title", "text", authInfo, null, null, null, null, null, null, output);
    sparkInterpreter.open();

    LivySparkSQLInterpreter sqlInterpreter = new LivySparkSQLInterpreter(newProps);
    interpreterGroup.get("session_1").add(sqlInterpreter);
    sqlInterpreter.setInterpreterGroup(interpreterGroup);
    sqlInterpreter.open();

    try {
      // detect spark version
      InterpreterResult result = sparkInterpreter.interpret("sc.version", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(1, result.message().size());

      boolean isSpark2 = isSpark2(sparkInterpreter, context);

      // test DataFrame api
      if (!isSpark2) {
        result = sparkInterpreter.interpret(
            "val df=sqlContext.createDataFrame(Seq((\"12characters12characters\",20))).toDF(\"col_1\", \"col_2\")\n"
                + "df.collect()", context);
        assertEquals(InterpreterResult.Code.SUCCESS, result.code());
        assertEquals(1, result.message().size());
        assertTrue(result.message().get(0).getData()
            .contains("Array[org.apache.spark.sql.Row] = Array([12characters12characters,20])"));
      } else {
        result = sparkInterpreter.interpret(
            "val df=spark.createDataFrame(Seq((\"12characters12characters\",20))).toDF(\"col_1\", \"col_2\")\n"
                + "df.collect()", context);
        assertEquals(InterpreterResult.Code.SUCCESS, result.code());
        assertEquals(1, result.message().size());
        assertTrue(result.message().get(0).getData()
            .contains("Array[org.apache.spark.sql.Row] = Array([12characters12characters,20])"));
      }
      sparkInterpreter.interpret("df.registerTempTable(\"df\")", context);
      // test LivySparkSQLInterpreter which share the same SparkContext with LivySparkInterpreter
      result = sqlInterpreter.interpret("select * from df where col_1='12characters12characters'", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(InterpreterResult.Type.TABLE, result.message().get(0).getType());
      assertEquals("col_1\tcol_2\n12characters12characters\t20", result.message().get(0).getData());
    } finally {
      sparkInterpreter.close();
      sqlInterpreter.close();
    }
  }

  @Test
  public void testPySparkInterpreter() throws LivyException, InterpreterException {
    if (!checkPreCondition()) {
      return;
    }

    final LivyPySparkInterpreter pysparkInterpreter = new LivyPySparkInterpreter(properties);
    AuthenticationInfo authInfo = new AuthenticationInfo("user1");
    MyInterpreterOutputListener outputListener = new MyInterpreterOutputListener();
    InterpreterOutput output = new InterpreterOutput(outputListener);
    final InterpreterContext context = new InterpreterContext("noteId", "paragraphId", "livy.pyspark",
        "title", "text", authInfo, null, null, null, null, null, null, output);
    pysparkInterpreter.open();

    // test traceback msg
    try {
      pysparkInterpreter.getLivyVersion();
      // for livy version >=0.3 , input some erroneous spark code, check the shown result is more than one line
      InterpreterResult result = pysparkInterpreter.interpret("sc.parallelize(wrongSyntax(1, 2)).count()", context);
      assertEquals(InterpreterResult.Code.ERROR, result.code());
      assertTrue(result.message().get(0).getData().split("\n").length>1);
      assertTrue(result.message().get(0).getData().contains("Traceback"));
    } catch (APINotFoundException e) {
      // only livy 0.2 can throw this exception since it doesn't have /version endpoint
      // in livy 0.2, most error msg is encapsulated in evalue field, only print(a) in pyspark would return none-empty
      // traceback
      InterpreterResult result = pysparkInterpreter.interpret("print(a)", context);
      assertEquals(InterpreterResult.Code.ERROR, result.code());
      assertTrue(result.message().get(0).getData().split("\n").length>1);
      assertTrue(result.message().get(0).getData().contains("Traceback"));
    }

    // test utf-8 Encoding
    try {
      String utf8Str = "你你你你你你好";
      InterpreterResult result = pysparkInterpreter.interpret("print(\""+utf8Str+"\")", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertTrue(result.message().get(0).getData().contains(utf8Str));
    }catch (Exception e) {
      e.printStackTrace();
    }

    try {
      InterpreterResult result = pysparkInterpreter.interpret("sc.version", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(1, result.message().size());

      boolean isSpark2 = isSpark2(pysparkInterpreter, context);

      // test RDD api
      result = pysparkInterpreter.interpret("sc.range(1, 10).sum()", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(1, result.message().size());
      assertTrue(result.message().get(0).getData().contains("45"));

      // test DataFrame api
      if (!isSpark2) {
        pysparkInterpreter.interpret("from pyspark.sql import SQLContext\n"
            + "sqlContext = SQLContext(sc)", context);
        result = pysparkInterpreter.interpret("df=sqlContext.createDataFrame([(\"hello\",20)])\n"
            + "df.collect()", context);
        assertEquals(InterpreterResult.Code.SUCCESS, result.code());
        assertEquals(1, result.message().size());
        //python2 has u and python3 don't have u
        assertTrue(result.message().get(0).getData().contains("[Row(_1=u'hello', _2=20)]")
            || result.message().get(0).getData().contains("[Row(_1='hello', _2=20)]"));
      } else {
        result = pysparkInterpreter.interpret("df=spark.createDataFrame([(\"hello\",20)])\n"
            + "df.collect()", context);
        assertEquals(InterpreterResult.Code.SUCCESS, result.code());
        assertEquals(1, result.message().size());
        //python2 has u and python3 don't have u
        assertTrue(result.message().get(0).getData().contains("[Row(_1=u'hello', _2=20)]")
            || result.message().get(0).getData().contains("[Row(_1='hello', _2=20)]"));
      }

      // test magic api
      pysparkInterpreter.interpret("t = [{\"name\":\"userA\", \"role\":\"roleA\"},"
          + "{\"name\":\"userB\", \"role\":\"roleB\"}]", context);
      result = pysparkInterpreter.interpret("%table t", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(1, result.message().size());
      assertEquals(InterpreterResult.Type.TABLE, result.message().get(0).getType());
      assertTrue(result.message().get(0).getData().contains("userA"));

      // error
      result = pysparkInterpreter.interpret("print(a)", context);
      assertEquals(InterpreterResult.Code.ERROR, result.code());
      assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType());
      assertTrue(result.message().get(0).getData().contains("name 'a' is not defined"));

      // cancel
      if (pysparkInterpreter.livyVersion.newerThanEquals(LivyVersion.LIVY_0_3_0)) {
        Thread cancelThread = new Thread() {
          @Override
          public void run() {
            // invoke cancel after 1 millisecond to wait job starting
            try {
              Thread.sleep(1);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
            pysparkInterpreter.cancel(context);
          }
        };
        cancelThread.start();
        result = pysparkInterpreter
            .interpret("import time\n" +
                "sc.range(1, 10).foreach(lambda a: time.sleep(10))", context);
        assertEquals(InterpreterResult.Code.ERROR, result.code());
        String message = result.message().get(0).getData();
        // 2 possibilities, sometimes livy doesn't return the real cancel exception
        assertTrue(message.contains("cancelled part of cancelled job group") ||
            message.contains("Job is cancelled"));
      }
    } finally {
      pysparkInterpreter.close();
    }
  }

//  @Test
  public void testSparkInterpreterWithDisplayAppInfo() throws InterpreterException {
    if (!checkPreCondition()) {
      return;
    }
    InterpreterGroup interpreterGroup = new InterpreterGroup("group_1");
    interpreterGroup.put("session_1", new ArrayList<Interpreter>());
    Properties properties2 = new Properties(properties);
    properties2.put("zeppelin.livy.displayAppInfo", "true");
    // enable spark ui because it is disabled by livy integration test
    properties2.put("livy.spark.ui.enabled", "true");
    LivySparkInterpreter sparkInterpreter = new LivySparkInterpreter(properties2);
    sparkInterpreter.setInterpreterGroup(interpreterGroup);
    interpreterGroup.get("session_1").add(sparkInterpreter);
    AuthenticationInfo authInfo = new AuthenticationInfo("user1");
    MyInterpreterOutputListener outputListener = new MyInterpreterOutputListener();
    InterpreterOutput output = new InterpreterOutput(outputListener);
    InterpreterContext context = new InterpreterContext("noteId", "paragraphId", "livy.spark",
        "title", "text", authInfo, null, null, null, null, null, null, output);
    sparkInterpreter.open();

    try {
      InterpreterResult result = sparkInterpreter.interpret("sc.version", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(2, result.message().size());
      assertTrue(result.message().get(1).getData().contains("Spark Application Id"));

      // html output
      String htmlCode = "println(\"%html <h1> hello </h1>\")";
      result = sparkInterpreter.interpret(htmlCode, context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(2, result.message().size());
      assertEquals(InterpreterResult.Type.HTML, result.message().get(0).getType());

    } finally {
      sparkInterpreter.close();
    }
  }

//  @Test
  public void testSparkRInterpreter() throws LivyException, InterpreterException {
    if (!checkPreCondition()) {
      return;
    }

    final LivySparkRInterpreter sparkRInterpreter = new LivySparkRInterpreter(properties);
    try {
      sparkRInterpreter.getLivyVersion();
    } catch (APINotFoundException e) {
      // don't run sparkR test for livy 0.2 as there's some issues for livy 0.2
      return;
    }
    AuthenticationInfo authInfo = new AuthenticationInfo("user1");
    MyInterpreterOutputListener outputListener = new MyInterpreterOutputListener();
    InterpreterOutput output = new InterpreterOutput(outputListener);
    final InterpreterContext context = new InterpreterContext("noteId", "paragraphId", "livy.sparkr",
        "title", "text", authInfo, null, null, null, null, null, null, output);
    sparkRInterpreter.open();

    try {
      // only test it in livy newer than 0.2.0
      boolean isSpark2 = isSpark2(sparkRInterpreter, context);
      InterpreterResult result = null;
      // test DataFrame api
      if (isSpark2) {
        result = sparkRInterpreter.interpret("df <- as.DataFrame(faithful)\nhead(df)", context);
        assertEquals(InterpreterResult.Code.SUCCESS, result.code());
        assertEquals(1, result.message().size());
        assertTrue(result.message().get(0).getData().contains("eruptions waiting"));

        // cancel
        Thread cancelThread = new Thread() {
          @Override
          public void run() {
            // invoke cancel after 1 millisecond to wait job starting
            try {
              Thread.sleep(1);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
            sparkRInterpreter.cancel(context);
          }
        };
        cancelThread.start();
        result = sparkRInterpreter.interpret("df <- as.DataFrame(faithful)\n" +
            "df1 <- dapplyCollect(df, function(x) " +
            "{ Sys.sleep(10); x <- cbind(x, x$waiting * 60) })", context);
        assertEquals(InterpreterResult.Code.ERROR, result.code());
        String message = result.message().get(0).getData();
        // 2 possibilities, sometimes livy doesn't return the real cancel exception
        assertTrue(message.contains("cancelled part of cancelled job group") ||
            message.contains("Job is cancelled"));
      } else {
        result = sparkRInterpreter.interpret("df <- createDataFrame(sqlContext, faithful)" +
            "\nhead(df)", context);
        assertEquals(InterpreterResult.Code.SUCCESS, result.code());
        assertEquals(1, result.message().size());
        assertTrue(result.message().get(0).getData().contains("eruptions waiting"));
      }

      // error
      result = sparkRInterpreter.interpret("cat(a)", context);
      //TODO @zjffdu, it should be ERROR, it is due to bug of LIVY-313
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType());
      assertTrue(result.message().get(0).getData().contains("object 'a' not found"));
    } finally {
      sparkRInterpreter.close();
    }
  }

//  @Test
  public void testLivyTutorialNote() throws IOException, InterpreterException {
    if (!checkPreCondition()) {
      return;
    }
    InterpreterGroup interpreterGroup = new InterpreterGroup("group_1");
    interpreterGroup.put("session_1", new ArrayList<Interpreter>());
    LazyOpenInterpreter sparkInterpreter = new LazyOpenInterpreter(
        new LivySparkInterpreter(properties));
    sparkInterpreter.setInterpreterGroup(interpreterGroup);
    interpreterGroup.get("session_1").add(sparkInterpreter);
    LazyOpenInterpreter sqlInterpreter = new LazyOpenInterpreter(
        new LivySparkSQLInterpreter(properties));
    interpreterGroup.get("session_1").add(sqlInterpreter);
    sqlInterpreter.setInterpreterGroup(interpreterGroup);
    sqlInterpreter.open();

    try {
      AuthenticationInfo authInfo = new AuthenticationInfo("user1");
      MyInterpreterOutputListener outputListener = new MyInterpreterOutputListener();
      InterpreterOutput output = new InterpreterOutput(outputListener);
      InterpreterContext context = new InterpreterContext("noteId", "paragraphId", "livy.sql",
          "title", "text", authInfo, null, null, null, null, null, null, output);

      String p1 = IOUtils.toString(getClass().getResourceAsStream("/livy_tutorial_1.scala"));
      InterpreterResult result = sparkInterpreter.interpret(p1, context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());

      String p2 = IOUtils.toString(getClass().getResourceAsStream("/livy_tutorial_2.sql"));
      result = sqlInterpreter.interpret(p2, context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(InterpreterResult.Type.TABLE, result.message().get(0).getType());
    } finally {
      sparkInterpreter.close();
      sqlInterpreter.close();
    }
  }


  private boolean isSpark2(BaseLivyInterpreter interpreter, InterpreterContext context) {
    InterpreterResult result = null;
    if (interpreter instanceof LivySparkRInterpreter) {
      result = interpreter.interpret("sparkR.session()", context);
      // SparkRInterpreter would always return SUCCESS, it is due to bug of LIVY-313
      if (result.message().get(0).getData().contains("Error")) {
        return false;
      } else {
        return true;
      }
    } else {
      result = interpreter.interpret("spark", context);
      if (result.code() == InterpreterResult.Code.SUCCESS) {
        return true;
      } else {
        return false;
      }
    }
  }

  public static class MyInterpreterOutputListener implements InterpreterOutputListener {
    @Override
    public void onAppend(int index, InterpreterResultMessageOutput out, byte[] line) {
    }

    @Override
    public void onUpdate(int index, InterpreterResultMessageOutput out) {

    }

    @Override
    public void onUpdateAll(InterpreterOutput out) {

    }
  }
}
