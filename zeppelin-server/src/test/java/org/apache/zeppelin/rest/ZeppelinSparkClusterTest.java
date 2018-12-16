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
package org.apache.zeppelin.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.notebook.Notebook;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterNotFoundException;
import org.apache.zeppelin.interpreter.InterpreterProperty;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.SparkDownloadUtils;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.server.ZeppelinServer;
import org.apache.zeppelin.user.AuthenticationInfo;

/**
 * Test against spark cluster.
 */
@RunWith(value = Parameterized.class)
public class ZeppelinSparkClusterTest extends AbstractTestRestApi {

  private static final Logger LOGGER = LoggerFactory.getLogger(ZeppelinSparkClusterTest.class);

  //This is for only run setupSparkInterpreter one time for each spark version, otherwise
  //each test method will run setupSparkInterpreter which will cost a long time and may cause travis
  //ci timeout.
  //TODO(zjffdu) remove this after we upgrade it to junit 4.13 (ZEPPELIN-3341)
  private static Set<String> verifiedSparkVersions = new HashSet<>();


  private String sparkVersion;
  private AuthenticationInfo anonymous = new AuthenticationInfo("anonymous");

  public ZeppelinSparkClusterTest(String sparkVersion) throws Exception {
    this.sparkVersion = sparkVersion;
    LOGGER.info("Testing SparkVersion: " + sparkVersion);
    String sparkHome = SparkDownloadUtils.downloadSpark(sparkVersion);
    if (!verifiedSparkVersions.contains(sparkVersion)) {
      verifiedSparkVersions.add(sparkVersion);
      setupSparkInterpreter(sparkHome);
      verifySparkVersionNumber();
    }
  }

  @Parameterized.Parameters
  public static List<Object[]> data() {
    return Arrays.asList(new Object[][]{
            {"2.4.0"},
            {"2.3.2"},
            {"2.2.1"},
            {"2.1.2"},
            {"2.0.2"},
            {"1.6.3"}
    });
  }

  public void setupSparkInterpreter(String sparkHome) throws InterpreterException {
    InterpreterSetting sparkIntpSetting = Notebook.getInstance().getInterpreterSettingManager()
        .getInterpreterSettingByName("spark");

    Map<String, InterpreterProperty> sparkProperties =
        (Map<String, InterpreterProperty>) sparkIntpSetting.getProperties();
    LOG.info("SPARK HOME detected " + sparkHome);
    if (System.getenv("SPARK_MASTER") != null) {
      sparkProperties.put("master",
          new InterpreterProperty("master", System.getenv("SPARK_MASTER")));
    } else {
      sparkProperties.put("master", new InterpreterProperty("master", "local[2]"));
    }
    sparkProperties.put("SPARK_HOME", new InterpreterProperty("SPARK_HOME", sparkHome));
    sparkProperties.put("spark.master", new InterpreterProperty("spark.master", "local[2]"));
    sparkProperties.put("spark.cores.max",
        new InterpreterProperty("spark.cores.max", "2"));
    sparkProperties.put("zeppelin.spark.useHiveContext",
        new InterpreterProperty("zeppelin.spark.useHiveContext", "false"));
    sparkProperties.put("zeppelin.pyspark.useIPython",
            new InterpreterProperty("zeppelin.pyspark.useIPython", "false"));
    sparkProperties.put("zeppelin.spark.useNew",
            new InterpreterProperty("zeppelin.spark.useNew", "true"));
    sparkProperties.put("zeppelin.spark.test",
            new InterpreterProperty("zeppelin.spark.test", "true"));
    sparkProperties.put("spark.serializer",
            new InterpreterProperty("spark.serializer", "org.apache.spark.serializer.KryoSerializer"));
    Notebook.getInstance().getInterpreterSettingManager().restart(sparkIntpSetting.getId());
  }

  @BeforeClass
  public static void setUp() throws Exception {
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HELIUM_REGISTRY.getVarName(),
            "helium");
    AbstractTestRestApi.startUp(ZeppelinSparkClusterTest.class.getSimpleName());
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  private void waitForFinish(Paragraph p) {
    while (p.getStatus() != Status.FINISHED
            && p.getStatus() != Status.ERROR
            && p.getStatus() != Status.ABORT) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        LOG.error("Exception in WebDriverManager while getWebDriver ", e);
      }
    }
  }

  private void waitForRunning(Paragraph p) {
    while (p.getStatus() != Status.RUNNING) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        LOG.error("Exception in WebDriverManager while getWebDriver ", e);
      }
    }
  }

  @Test
  public void scalaOutputTest() throws IOException, InterruptedException {
    // create new note
    Note note = Notebook.getInstance().createNote("note1", anonymous);
    Paragraph p = note.addNewParagraph(anonymous);
    p.setText("%spark import java.util.Date\n" +
        "import java.net.URL\n" +
        "println(\"hello\")\n"
    );
    note.run(p.getId(), true);
    assertEquals(Status.FINISHED, p.getStatus());
    assertEquals("hello\n" +
        "import java.util.Date\n" +
        "import java.net.URL\n",
        p.getReturn().message().get(0).getData());

    p.setText("%spark invalid_code");
    note.run(p.getId(), true);
    assertEquals(Status.ERROR, p.getStatus());
    assertTrue(p.getReturn().message().get(0).getData().contains("error: "));

    // test local properties
    p.setText("%spark(p1=v1,p2=v2) print(z.getInterpreterContext().getLocalProperties().size())");
    note.run(p.getId(), true);
    assertEquals(Status.FINISHED, p.getStatus());
    assertEquals("2", p.getReturn().message().get(0).getData());

    // test code completion
    List<InterpreterCompletion> completions = note.completion(p.getId(), "sc.", 2);
    assertTrue(completions.size() > 0);

    // test cancel
    p.setText("%spark sc.range(1,10).map(e=>{Thread.sleep(1000); e}).collect()");
    note.run(p.getId(), false);
    waitForRunning(p);
    p.abort();
    waitForFinish(p);
    assertEquals(Status.ABORT, p.getStatus());

    Notebook.getInstance().removeNote(note.getId(), anonymous);
  }

  @Test
  public void basicRDDTransformationAndActionTest() throws IOException {
    Note note = Notebook.getInstance().createNote("note1", anonymous);
    Paragraph p = note.addNewParagraph(anonymous);
    p.setText("%spark print(sc.parallelize(1 to 10).reduce(_ + _))");
    note.run(p.getId(), true);
    assertEquals(Status.FINISHED, p.getStatus());
    assertEquals("55", p.getReturn().message().get(0).getData());

    Notebook.getInstance().removeNote(note.getId(), anonymous);
  }

  @Test
  public void sparkSQLTest() throws IOException {
    Note note = Notebook.getInstance().createNote("note1", anonymous);
    // test basic dataframe api
    Paragraph p = note.addNewParagraph(anonymous);
    p.setText("%spark val df=sqlContext.createDataFrame(Seq((\"hello\",20)))\n" +
        "df.collect()");
    note.run(p.getId(), true);
    assertEquals(Status.FINISHED, p.getStatus());
    assertTrue(p.getReturn().message().get(0).getData().contains(
        "Array[org.apache.spark.sql.Row] = Array([hello,20])"));

    // test display DataFrame
    p = note.addNewParagraph(anonymous);
    p.setText("%spark val df=sqlContext.createDataFrame(Seq((\"hello\",20)))\n" +
        "z.show(df)");
    note.run(p.getId(), true);
    assertEquals(Status.FINISHED, p.getStatus());
    assertEquals(InterpreterResult.Type.TABLE, p.getReturn().message().get(0).getType());
    assertEquals("_1\t_2\nhello\t20\n", p.getReturn().message().get(0).getData());

    // test display DataSet
    if (isSpark2()) {
      p = note.addNewParagraph(anonymous);
      p.setText("%spark val ds=spark.createDataset(Seq((\"hello\",20)))\n" +
          "z.show(ds)");
      note.run(p.getId(), true);
      assertEquals(Status.FINISHED, p.getStatus());
      assertEquals(InterpreterResult.Type.TABLE, p.getReturn().message().get(0).getType());
      assertEquals("_1\t_2\nhello\t20\n", p.getReturn().message().get(0).getData());
    }

    Notebook.getInstance().removeNote(note.getId(), anonymous);
  }

  @Test
  public void sparkRTest() throws IOException {
    Note note = Notebook.getInstance().createNote("note1", anonymous);

    String sqlContextName = "sqlContext";
    if (isSpark2()) {
      sqlContextName = "spark";
    }
    Paragraph p = note.addNewParagraph(anonymous);
    p.setText("%spark.r localDF <- data.frame(name=c(\"a\", \"b\", \"c\"), age=c(19, 23, 18))\n" +
        "df <- createDataFrame(" + sqlContextName + ", localDF)\n" +
        "count(df)"
    );
    note.run(p.getId(), true);
    assertEquals(Status.FINISHED, p.getStatus());
    assertEquals("[1] 3", p.getReturn().message().get(0).getData().trim());

    Notebook.getInstance().removeNote(note.getId(), anonymous);
  }

  // @Test
  public void pySparkTest() throws IOException {
    // create new note
    Note note = Notebook.getInstance().createNote("note1", anonymous);

    // run markdown paragraph, again
    Paragraph p = note.addNewParagraph(anonymous);
    p.setText("%spark.pyspark sc.parallelize(range(1, 11)).reduce(lambda a, b: a + b)");
    note.run(p.getId(), true);
    assertEquals(Status.FINISHED, p.getStatus());
    assertEquals("55\n", p.getReturn().message().get(0).getData());
    if (!isSpark2()) {
      // run sqlContext test
      p = note.addNewParagraph(anonymous);
      p.setText("%pyspark from pyspark.sql import Row\n" +
          "df=sqlContext.createDataFrame([Row(id=1, age=20)])\n" +
          "df.collect()");
      note.run(p.getId(), true);
      assertEquals(Status.FINISHED, p.getStatus());
      assertEquals("[Row(age=20, id=1)]\n", p.getReturn().message().get(0).getData());

      // test display Dataframe
      p = note.addNewParagraph(anonymous);
      p.setText("%pyspark from pyspark.sql import Row\n" +
          "df=sqlContext.createDataFrame([Row(id=1, age=20)])\n" +
          "z.show(df)");
      note.run(p.getId(), true);
      waitForFinish(p);
      assertEquals(Status.FINISHED, p.getStatus());
      assertEquals(InterpreterResult.Type.TABLE, p.getReturn().message().get(0).getType());
      // TODO(zjffdu), one more \n is appended, need to investigate why.
      assertEquals("age\tid\n20\t1\n", p.getReturn().message().get(0).getData());

      // test udf
      p = note.addNewParagraph(anonymous);
      p.setText("%pyspark sqlContext.udf.register(\"f1\", lambda x: len(x))\n" +
          "sqlContext.sql(\"select f1(\\\"abc\\\") as len\").collect()");
      note.run(p.getId(), true);
      assertEquals(Status.FINISHED, p.getStatus());
      assertTrue("[Row(len=u'3')]\n".equals(p.getReturn().message().get(0).getData()) ||
          "[Row(len='3')]\n".equals(p.getReturn().message().get(0).getData()));

      // test exception
      p = note.addNewParagraph(anonymous);
      /*
       %pyspark
       a=1

       print(a2)
       */
      p.setText("%pyspark a=1\n\nprint(a2)");
      note.run(p.getId(), true);
      assertEquals(Status.ERROR, p.getStatus());
      assertTrue(p.getReturn().message().get(0).getData()
          .contains("Fail to execute line 3: print(a2)"));
      assertTrue(p.getReturn().message().get(0).getData()
          .contains("name 'a2' is not defined"));
    } else {
      // run SparkSession test
      p = note.addNewParagraph(anonymous);
      p.setText("%pyspark from pyspark.sql import Row\n" +
          "df=sqlContext.createDataFrame([Row(id=1, age=20)])\n" +
          "df.collect()");
      note.run(p.getId(), true);
      assertEquals(Status.FINISHED, p.getStatus());
      assertEquals("[Row(age=20, id=1)]\n", p.getReturn().message().get(0).getData());

      // test udf
      p = note.addNewParagraph(anonymous);
      // use SQLContext to register UDF but use this UDF through SparkSession
      p.setText("%pyspark sqlContext.udf.register(\"f1\", lambda x: len(x))\n" +
          "spark.sql(\"select f1(\\\"abc\\\") as len\").collect()");
      note.run(p.getId(), true);
      assertEquals(Status.FINISHED, p.getStatus());
      assertTrue("[Row(len=u'3')]\n".equals(p.getReturn().message().get(0).getData()) ||
          "[Row(len='3')]\n".equals(p.getReturn().message().get(0).getData()));
    }

    Notebook.getInstance().removeNote(note.getId(), anonymous);
  }

  @Test
  public void zRunTest() throws IOException {
    // create new note
    Note note = Notebook.getInstance().createNote("note1", anonymous);
    Paragraph p0 = note.addNewParagraph(anonymous);
    // z.run(paragraphIndex)
    p0.setText("%spark z.run(1)");
    Paragraph p1 = note.addNewParagraph(anonymous);
    p1.setText("%spark val a=10");
    Paragraph p2 = note.addNewParagraph(anonymous);
    p2.setText("%spark print(a)");

    note.run(p0.getId(), true);
    assertEquals(Status.FINISHED, p0.getStatus());

    // z.run is not blocking call. So p1 may not be finished when p0 is done.
    waitForFinish(p1);
    assertEquals(Status.FINISHED, p1.getStatus());
    note.run(p2.getId(), true);
    assertEquals(Status.FINISHED, p2.getStatus());
    assertEquals("10", p2.getReturn().message().get(0).getData());

    Paragraph p3 = note.addNewParagraph(anonymous);
    p3.setText("%spark println(new java.util.Date())");

    // run current Node, z.runNote(noteId)
    p0.setText(String.format("%%spark z.runNote(\"%s\")", note.getId()));
    note.run(p0.getId());
    waitForFinish(p0);
    waitForFinish(p1);
    waitForFinish(p2);
    waitForFinish(p3);

    assertEquals(Status.FINISHED, p3.getStatus());
    String p3result = p3.getReturn().message().get(0).getData();
    assertTrue(p3result.length() > 0);

    // z.run(noteId, paragraphId)
    p0.setText(String.format("%%spark z.run(\"%s\", \"%s\")", note.getId(), p3.getId()));
    p3.setText("%spark println(\"END\")");

    note.run(p0.getId(), true);
    waitForFinish(p3);
    assertEquals(Status.FINISHED, p3.getStatus());
    assertEquals("END\n", p3.getReturn().message().get(0).getData());

    // run paragraph in note2 via paragraph in note1
    Note note2 = Notebook.getInstance().createNote("note2", anonymous);
    Paragraph p20 = note2.addNewParagraph(anonymous);
    p20.setText("%spark val a = 1");
    Paragraph p21 = note2.addNewParagraph(anonymous);
    p21.setText("%spark print(a)");

    // run p20 of note2 via paragraph in note1
    p0.setText(String.format("%%spark z.run(\"%s\", \"%s\")", note2.getId(), p20.getId()));
    note.run(p0.getId(), true);
    waitForFinish(p20);
    assertEquals(Status.FINISHED, p20.getStatus());
    assertEquals(Status.READY, p21.getStatus());

    p0.setText(String.format("%%spark z.runNote(\"%s\")", note2.getId()));
    note.run(p0.getId(), true);
    waitForFinish(p20);
    waitForFinish(p21);
    assertEquals(Status.FINISHED, p20.getStatus());
    assertEquals(Status.FINISHED, p21.getStatus());
    assertEquals("1", p21.getReturn().message().get(0).getData());

    Notebook.getInstance().removeNote(note.getId(), anonymous);
    Notebook.getInstance().removeNote(note2.getId(), anonymous);
  }

  @Test
  public void testZeppelinContextResource() throws IOException {
    Note note = Notebook.getInstance().createNote("note1", anonymous);

    Paragraph p1 = note.addNewParagraph(anonymous);
    p1.setText("%spark z.put(\"var_1\", \"hello world\")");

    Paragraph p2 = note.addNewParagraph(anonymous);
    p2.setText("%spark println(z.get(\"var_1\"))");

    Paragraph p3 = note.addNewParagraph(anonymous);
    p3.setText("%spark.pyspark print(z.get(\"var_1\"))");

    // resources across interpreter processes (via DistributedResourcePool)
    Paragraph p4 = note.addNewParagraph(anonymous);
    p4.setText("%python print(z.get('var_1'))");

    note.run(p1.getId(), true);
    note.run(p2.getId(), true);
    note.run(p3.getId(), true);
    note.run(p4.getId(), true);

    assertEquals(Status.FINISHED, p1.getStatus());
    assertEquals(Status.FINISHED, p2.getStatus());
    assertEquals("hello world\n", p2.getReturn().message().get(0).getData());
    assertEquals(Status.FINISHED, p3.getStatus());
    assertEquals("hello world\n", p3.getReturn().message().get(0).getData());
    assertEquals(Status.FINISHED, p4.getStatus());
    assertEquals("hello world\n", p4.getReturn().message().get(0).getData());

    Notebook.getInstance().removeNote(note.getId(), anonymous);
  }

  @Test
  public void testZeppelinContextHook() throws IOException {
    Note note = Notebook.getInstance().createNote("note1", anonymous);

    // register global hook & note1 hook
    Paragraph p1 = note.addNewParagraph(anonymous);
    p1.setText("%python from __future__ import print_function\n" +
        "z.registerHook('pre_exec', 'print(1)')\n" +
        "z.registerHook('post_exec', 'print(2)')\n" +
        "z.registerNoteHook('pre_exec', 'print(3)', '" + note.getId() + "')\n" +
        "z.registerNoteHook('post_exec', 'print(4)', '" + note.getId() + "')\n");

    Paragraph p2 = note.addNewParagraph(anonymous);
    p2.setText("%python print(5)");

    note.run(p1.getId(), true);
    note.run(p2.getId(), true);

    assertEquals(Status.FINISHED, p1.getStatus());
    assertEquals(Status.FINISHED, p2.getStatus());
    assertEquals("1\n3\n5\n4\n2\n", p2.getReturn().message().get(0).getData());

    Note note2 = Notebook.getInstance().createNote("note2", anonymous);
    Paragraph p3 = note2.addNewParagraph(anonymous);
    p3.setText("%python print(6)");
    note2.run(p3.getId(), true);
    assertEquals("1\n6\n2\n", p3.getReturn().message().get(0).getData());

    Notebook.getInstance().removeNote(note.getId(), anonymous);
    Notebook.getInstance().removeNote(note2.getId(), anonymous);
  }

  @Test
  public void pySparkDepLoaderTest() throws IOException {
    Note note = Notebook.getInstance().createNote("note1", anonymous);

    // restart spark interpreter to make dep loader work
    Notebook.getInstance().getInterpreterSettingManager().close();

    // load dep
    Paragraph p0 = note.addNewParagraph(anonymous);
    p0.setText("%dep z.load(\"com.databricks:spark-csv_2.11:1.2.0\")");
    note.run(p0.getId(), true);
    assertEquals(Status.FINISHED, p0.getStatus());

    // write test csv file
    File tmpFile = File.createTempFile("test", "csv");
    FileUtils.write(tmpFile, "a,b\n1,2");

    // load data using libraries from dep loader
    Paragraph p1 = note.addNewParagraph(anonymous);

    String sqlContextName = "sqlContext";
    if (isSpark2()) {
      sqlContextName = "spark";
    }
    p1.setText("%pyspark\n" +
        "from pyspark.sql import SQLContext\n" +
        "print(" + sqlContextName + ".read.format('com.databricks.spark.csv')" +
        ".load('file://" + tmpFile.getAbsolutePath() + "').count())");
    note.run(p1.getId(), true);

    assertEquals(Status.FINISHED, p1.getStatus());
    assertEquals("2\n", p1.getReturn().message().get(0).getData());

    Notebook.getInstance().removeNote(note.getId(), anonymous);
  }

  private void verifySparkVersionNumber() throws IOException {
    Note note = Notebook.getInstance().createNote("note1", anonymous);
    Paragraph p = note.addNewParagraph(anonymous);

    p.setText("%spark print(sc.version)");
    note.run(p.getId());
    waitForFinish(p);
    assertEquals(Status.FINISHED, p.getStatus());
    assertEquals(sparkVersion, p.getReturn().message().get(0).getData());

    Notebook.getInstance().removeNote(note.getId(), anonymous);
  }

  private int toIntSparkVersion(String sparkVersion) {
    String[] split = sparkVersion.split("\\.");
    int version = Integer.parseInt(split[0]) * 10 + Integer.parseInt(split[1]);
    return version;
  }

  private boolean isSpark2() {
    return toIntSparkVersion(sparkVersion) >= 20;
  }

  @Test
  public void testSparkZeppelinContextDynamicForms() throws IOException {
    Note note = Notebook.getInstance().createNote("note1", anonymous);
    Paragraph p = note.addNewParagraph(anonymous);
    String code = "%spark.spark println(z.textbox(\"my_input\", \"default_name\"))\n" +
        "println(z.password(\"my_pwd\"))\n" +
        "println(z.select(\"my_select\", \"1\"," +
        "Seq((\"1\", \"select_1\"), (\"2\", \"select_2\"))))\n" +
        "val items=z.checkbox(\"my_checkbox\", Seq(\"2\"), " +
        "Seq((\"1\", \"check_1\"), (\"2\", \"check_2\")))\n" +
        "println(items(0))";
    p.setText(code);
    note.run(p.getId());
    waitForFinish(p);

    assertEquals(Status.FINISHED, p.getStatus());
    Iterator<String> formIter = p.settings.getForms().keySet().iterator();
    assertEquals("my_input", formIter.next());
    assertEquals("my_pwd", formIter.next());
    assertEquals("my_select", formIter.next());
    assertEquals("my_checkbox", formIter.next());

    // check dynamic forms values
    String[] result = p.getReturn().message().get(0).getData().split("\n");
    assertEquals(5, result.length);
    assertEquals("default_name", result[0]);
    assertEquals("null", result[1]);
    assertEquals("1", result[2]);
    assertEquals("2", result[3]);
    assertEquals("items: Seq[Any] = Buffer(2)", result[4]);

    Notebook.getInstance().removeNote(note.getId(), anonymous);
  }

  @Test
  public void testPySparkZeppelinContextDynamicForms() throws IOException {
    Note note = Notebook.getInstance().createNote("note1", anonymous);
    Paragraph p = note.addNewParagraph(anonymous);
    String code = "%spark.pyspark print(z.input('my_input', 'default_name'))\n" +
        "print(z.password('my_pwd'))\n" +
        "print(z.select('my_select', " +
        "[('1', 'select_1'), ('2', 'select_2')], defaultValue='1'))\n" +
        "items=z.checkbox('my_checkbox', " +
        "[('1', 'check_1'), ('2', 'check_2')], defaultChecked=['2'])\n" +
        "print(items[0])";
    p.setText(code);
    note.run(p.getId(), true);

    assertEquals(Status.FINISHED, p.getStatus());
    Iterator<String> formIter = p.settings.getForms().keySet().iterator();
    assertEquals("my_input", formIter.next());
    assertEquals("my_pwd", formIter.next());
    assertEquals("my_select", formIter.next());
    assertEquals("my_checkbox", formIter.next());

    // check dynamic forms values
    String[] result = p.getReturn().message().get(0).getData().split("\n");
    assertEquals(4, result.length);
    assertEquals("default_name", result[0]);
    assertEquals("None", result[1]);
    assertEquals("1", result[2]);
    assertEquals("2", result[3]);

    Notebook.getInstance().removeNote(note.getId(), anonymous);
  }

  @Test
  public void testAngularObjects() throws IOException, InterpreterNotFoundException {
    Note note = Notebook.getInstance().createNote("note1", anonymous);
    Paragraph p1 = note.addNewParagraph(anonymous);

    // add local angular object
    p1.setText("%spark z.angularBind(\"name\", \"world\")");
    note.run(p1.getId(), true);
    assertEquals(Status.FINISHED, p1.getStatus());
    List<AngularObject> angularObjects = p1.getBindedInterpreter().getInterpreterGroup()
            .getAngularObjectRegistry().getAll(note.getId(), null);
    assertEquals(1, angularObjects.size());
    assertEquals("name", angularObjects.get(0).getName());
    assertEquals("world", angularObjects.get(0).get());

    // remove local angular object
    Paragraph p2 = note.addNewParagraph(anonymous);
    p2.setText("%spark z.angularUnbind(\"name\")");
    note.run(p2.getId(), true);
    assertEquals(Status.FINISHED, p2.getStatus());
    angularObjects = p1.getBindedInterpreter().getInterpreterGroup().getAngularObjectRegistry()
            .getAll(note.getId(), null);
    assertEquals(0, angularObjects.size());

    // add global angular object
    Paragraph p3 = note.addNewParagraph(anonymous);
    p3.setText("%spark z.angularBindGlobal(\"name2\", \"world2\")");
    note.run(p3.getId(), true);
    assertEquals(Status.FINISHED, p3.getStatus());
    List<AngularObject> globalAngularObjects = p3.getBindedInterpreter().getInterpreterGroup()
            .getAngularObjectRegistry().getAll(null, null);
    assertEquals(1, globalAngularObjects.size());
    assertEquals("name2", globalAngularObjects.get(0).getName());
    assertEquals("world2", globalAngularObjects.get(0).get());

    // remove global angular object
    Paragraph p4 = note.addNewParagraph(anonymous);
    p4.setText("%spark z.angularUnbindGlobal(\"name2\")");
    note.run(p4.getId(), true);
    assertEquals(Status.FINISHED, p4.getStatus());
    globalAngularObjects = p4.getBindedInterpreter().getInterpreterGroup()
            .getAngularObjectRegistry().getAll(note.getId(), null);
    assertEquals(0, globalAngularObjects.size());

    Notebook.getInstance().removeNote(note.getId(), anonymous);
  }

  @Test
  public void testConfInterpreter() throws IOException {
    Notebook.getInstance().getInterpreterSettingManager().close();
    Note note = Notebook.getInstance().createNote("note1", anonymous);
    Paragraph p = note.addNewParagraph(anonymous);
    p.setText("%spark.conf spark.jars.packages\tcom.databricks:spark-csv_2.11:1.2.0");
    note.run(p.getId(), true);
    assertEquals(Status.FINISHED, p.getStatus());

    Paragraph p1 = note.addNewParagraph(anonymous);
    p1.setText("%spark\nimport com.databricks.spark.csv._");
    note.run(p1.getId(), true);
    assertEquals(Status.FINISHED, p1.getStatus());

    Notebook.getInstance().removeNote(note.getId(), anonymous);
  }
}
