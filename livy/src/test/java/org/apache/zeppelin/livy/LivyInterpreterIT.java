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


import com.cloudera.livy.test.framework.Cluster;
import com.cloudera.livy.test.framework.Cluster$;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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
    properties.setProperty("zeppelin.livy.create.session.timeout", "120");
    properties.setProperty("zeppelin.livy.spark.sql.maxResult", "100");
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

  @Test
  public void testSparkInterpreterRDD() {
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
        "title", "text", authInfo, null, null, null, null, null, output);
    sparkInterpreter.open();

    try {
      InterpreterResult result = sparkInterpreter.interpret("sc.version", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(1, result.message().size());
      assertTrue(result.message().get(0).getData().contains("1.5.2"));

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
      assertTrue(result.message().get(0).getData().contains("defined module Person"));

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
    } finally {
      sparkInterpreter.close();
    }
  }

  @Test
  public void testSparkInterpreterDataFrame() {
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
        "title", "text", authInfo, null, null, null, null, null, output);
    sparkInterpreter.open();

    LivySparkSQLInterpreter sqlInterpreter = new LivySparkSQLInterpreter(properties);
    interpreterGroup.get("session_1").add(sqlInterpreter);
    sqlInterpreter.setInterpreterGroup(interpreterGroup);
    sqlInterpreter.open();

    try {
      // test DataFrame api
      sparkInterpreter.interpret("val sqlContext = new org.apache.spark.sql.SQLContext(sc)\n"
          + "import sqlContext.implicits._", context);
      InterpreterResult result = sparkInterpreter.interpret(
          "val df=sqlContext.createDataFrame(Seq((\"hello\",20))).toDF(\"col_1\", \"col_2\")\n"
          + "df.collect()", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(1, result.message().size());
      assertTrue(result.message().get(0).getData()
          .contains("Array[org.apache.spark.sql.Row] = Array([hello,20])"));
      sparkInterpreter.interpret("df.registerTempTable(\"df\")", context);

      // test LivySparkSQLInterpreter which share the same SparkContext with LivySparkInterpreter
      result = sqlInterpreter.interpret("select * from df where col_1='hello'", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(InterpreterResult.Type.TABLE, result.message().get(0).getType());
      // TODO(zjffdu), \t at the end of each line is not necessary,
      // it is a bug of LivySparkSQLInterpreter
      assertEquals("col_1\tcol_2\t\nhello\t20\t\n", result.message().get(0).getData());
      // double quotes
      result = sqlInterpreter.interpret("select * from df where col_1=\"hello\"", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(InterpreterResult.Type.TABLE, result.message().get(0).getType());
      assertEquals("col_1\tcol_2\t\nhello\t20\t\n", result.message().get(0).getData());
      // double quotes inside attribute value
      // TODO(zjffdu). This test case would fail on spark-1.5, would uncomment it when upgrading to
      // livy-0.3 and spark-1.6
      // result = sqlInterpreter.interpret("select * from df where col_1=\"he\\\"llo\" ", context);
      // assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      // assertEquals(InterpreterResult.Type.TABLE, result.message().get(0).getType());

      // single quotes inside attribute value
      result = sqlInterpreter.interpret("select * from df where col_1=\"he'llo\"", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(InterpreterResult.Type.TABLE, result.message().get(0).getType());

      // test sql with syntax error
      result = sqlInterpreter.interpret("select * from df2", context);
      assertEquals(InterpreterResult.Code.ERROR, result.code());
      assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType());
      assertTrue(result.message().get(0).getData().contains("Table Not Found"));
    } finally {
      sparkInterpreter.close();
      sqlInterpreter.close();
    }
  }

  @Test
  public void testSparkSQLInterpreter() {
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
          "title", "text", authInfo, null, null, null, null, null, output);
      InterpreterResult result = sqlInterpreter.interpret("show tables", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(InterpreterResult.Type.TABLE, result.message().get(0).getType());
      assertTrue(result.message().get(0).getData().contains("tableName"));
    } finally {
      sqlInterpreter.close();
    }
  }

  @Test
  public void testPySparkInterpreter() {
    if (!checkPreCondition()) {
      return;
    }

    LivyPySparkInterpreter pysparkInterpreter = new LivyPySparkInterpreter(properties);
    AuthenticationInfo authInfo = new AuthenticationInfo("user1");
    MyInterpreterOutputListener outputListener = new MyInterpreterOutputListener();
    InterpreterOutput output = new InterpreterOutput(outputListener);
    InterpreterContext context = new InterpreterContext("noteId", "paragraphId", "livy.pyspark",
        "title", "text", authInfo, null, null, null, null, null, output);
    pysparkInterpreter.open();

    try {
      InterpreterResult result = pysparkInterpreter.interpret("sc.version", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(1, result.message().size());
      assertTrue(result.message().get(0).getData().contains("1.5.2"));

      // test RDD api
      result = pysparkInterpreter.interpret("sc.range(1, 10).sum()", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(1, result.message().size());
      assertTrue(result.message().get(0).getData().contains("45"));

      // test DataFrame api
      pysparkInterpreter.interpret("from pyspark.sql import SQLContext\n"
          + "sqlContext = SQLContext(sc)", context);
      result = pysparkInterpreter.interpret("df=sqlContext.createDataFrame([(\"hello\",20)])\n"
          + "df.collect()", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertEquals(1, result.message().size());
      assertTrue(result.message().get(0).getData().contains("[Row(_1=u'hello', _2=20)]"));

      // error
      result = pysparkInterpreter.interpret("print(a)", context);
      assertEquals(InterpreterResult.Code.ERROR, result.code());
      assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType());
      assertTrue(result.message().get(0).getData().contains("name 'a' is not defined"));
    } finally {
      pysparkInterpreter.close();
    }
  }

  @Test
  public void testSparkRInterpreter() {
    if (!checkPreCondition()) {
      return;
    }
    // TODO(zjffdu),  Livy's SparkRIntepreter has some issue, do it after livy-0.3 release.
  }

  @Test
  public void testLivyTutorialNote() throws IOException {
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
          "title", "text", authInfo, null, null, null, null, null, output);

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
