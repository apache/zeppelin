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
package org.apache.zeppelin.integration;

import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.display.ui.CheckBox;
import org.apache.zeppelin.display.ui.Select;
import org.apache.zeppelin.display.ui.TextBox;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterNotFoundException;
import org.apache.zeppelin.interpreter.InterpreterProperty;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.integration.DownloadUtils;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;


/**
 * Test against spark cluster.
 */
public abstract class ZeppelinSparkClusterTest extends AbstractTestRestApi {

  private static final Logger LOGGER = LoggerFactory.getLogger(ZeppelinSparkClusterTest.class);
  public static final String SPARK_MASTER_PROPERTY_NAME = "spark.master";

  //This is for only run setupSparkInterpreter one time for each spark version, otherwise
  //each test method will run setupSparkInterpreter which will cost a long time and may cause a
  //ci timeout.
  //TODO(zjffdu) remove this after we upgrade it to junit 4.13 (ZEPPELIN-3341)
  private static Set<String> verifiedSparkVersions = new HashSet<>();


  private String sparkVersion;
  private String sparkHome;
  private String hadoopVersion;
  private AuthenticationInfo anonymous = new AuthenticationInfo("anonymous");

  public ZeppelinSparkClusterTest(String sparkVersion, String hadoopVersion) throws Exception {
    this.sparkVersion = sparkVersion;
    LOGGER.info("Testing SparkVersion: " + sparkVersion);
    this.sparkHome = DownloadUtils.downloadSpark(sparkVersion, hadoopVersion);
    this.hadoopVersion = hadoopVersion;
    if (!verifiedSparkVersions.contains(sparkVersion)) {
      verifiedSparkVersions.add(sparkVersion);
      setupSparkInterpreter(sparkHome);
      verifySparkVersionNumber();
    }
  }

  private boolean isHadoopVersionMatch() {
    String version = org.apache.hadoop.util.VersionInfo.getVersion();
    String majorVersion = version.split("\\.")[0];
    return majorVersion.equals(hadoopVersion.split("\\.")[0]);
  }

  public void setupSparkInterpreter(String sparkHome) throws InterpreterException {
    InterpreterSetting sparkIntpSetting = TestUtils.getInstance(Notebook.class).getInterpreterSettingManager()
        .getInterpreterSettingByName("spark");

    Map<String, InterpreterProperty> sparkProperties =
        (Map<String, InterpreterProperty>) sparkIntpSetting.getProperties();
    LOG.info("SPARK HOME detected " + sparkHome);
    String masterEnv = System.getenv("SPARK_MASTER");
    sparkProperties.put(SPARK_MASTER_PROPERTY_NAME,
        new InterpreterProperty(SPARK_MASTER_PROPERTY_NAME, masterEnv == null ? "local[2]" : masterEnv));
    sparkProperties.put("SPARK_HOME", new InterpreterProperty("SPARK_HOME", sparkHome));
    sparkProperties.put("spark.cores.max",
        new InterpreterProperty("spark.cores.max", "2"));
    sparkProperties.put("zeppelin.spark.useHiveContext",
        new InterpreterProperty("zeppelin.spark.useHiveContext", "false"));
    sparkProperties.put("zeppelin.pyspark.useIPython",
            new InterpreterProperty("zeppelin.pyspark.useIPython", "false"));
    sparkProperties.put("zeppelin.spark.useNew",
            new InterpreterProperty("zeppelin.spark.useNew", "true"));
    sparkProperties.put("spark.serializer",
            new InterpreterProperty("spark.serializer", "org.apache.spark.serializer.KryoSerializer"));
    sparkProperties.put("zeppelin.spark.scala.color",
            new InterpreterProperty("zeppelin.spark.scala.color", "false"));
    sparkProperties.put("zeppelin.spark.deprecatedMsg.show",
            new InterpreterProperty("zeppelin.spark.deprecatedMsg.show", "false"));
    TestUtils.getInstance(Notebook.class).getInterpreterSettingManager().restart(sparkIntpSetting.getId());
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
    assumeTrue("Hadoop version mismatch, skip test", isHadoopVersionMatch());

    String noteId = null;
    try {
      // create new note
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
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

          // check spark weburl in zeppelin-server side
          InterpreterSettingManager interpreterSettingManager = TestUtils.getInstance(InterpreterSettingManager.class);
          InterpreterSetting sparkInterpreterSetting = interpreterSettingManager.getByName("spark");
          assertEquals(1, sparkInterpreterSetting.getAllInterpreterGroups().size());
          assertNotNull(sparkInterpreterSetting.getAllInterpreterGroups().get(0).getWebUrl());

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
          List<InterpreterCompletion> completions = note.completion(p.getId(), "sc.", 2, AuthenticationInfo.ANONYMOUS);
          assertTrue(completions.size() > 0);

          // test cancel
          p.setText("%spark sc.range(1,10).map(e=>{Thread.sleep(1000); e}).collect()");
          note.run(p.getId(), false);
          waitForRunning(p);
          p.abort();
          waitForFinish(p);
          assertEquals(Status.ABORT, p.getStatus());
          return null;
        });
    } finally {
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  public void basicRDDTransformationAndActionTest() throws IOException {
    assumeTrue("Hadoop version mismatch, skip test", isHadoopVersionMatch());

    String noteId = null;

    try {
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          Paragraph p = note.addNewParagraph(anonymous);
          p.setText("%spark print(sc.parallelize(1 to 10).reduce(_ + _))");
          note.run(p.getId(), true);
          assertEquals(Status.FINISHED, p.getStatus());
          assertEquals("55", p.getReturn().message().get(0).getData());
          return null;
        });
    } finally {
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  public void sparkReadJSONTest() throws IOException {
    assumeTrue("Hadoop version mismatch, skip test", isHadoopVersionMatch());

    String noteId = null;
    try {
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          Paragraph p = note.addNewParagraph(anonymous);
          File tmpJsonFile = File.createTempFile("test", ".json");
          FileWriter jsonFileWriter = new FileWriter(tmpJsonFile);
          IOUtils.copy(new StringReader("{\"metadata\": { \"key\": 84896, \"value\": 54 }}\n"),
                  jsonFileWriter);
          jsonFileWriter.close();
          if (isSpark2() || isSpark3()) {
            p.setText("%spark spark.read.json(\"file://" + tmpJsonFile.getAbsolutePath() + "\")");
          } else {
            p.setText("%spark sqlContext.read.json(\"file://" + tmpJsonFile.getAbsolutePath() + "\")");
          }
          note.run(p.getId(), true);
          assertEquals(Status.FINISHED, p.getStatus());
          if (isSpark2() || isSpark3()) {
            assertTrue(p.getReturn().message().get(0).getData().contains(
                    "org.apache.spark.sql.DataFrame = [metadata: struct<key: bigint, value: bigint>]"));
          } else {
            assertTrue(p.getReturn().message().get(0).getData().contains(
                    "org.apache.spark.sql.DataFrame = [metadata: struct<key:bigint,value:bigint>]"));
          }
          return null;
        });
    } finally {
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  public void sparkReadCSVTest() throws IOException {
    assumeTrue("Hadoop version mismatch, skip test", isHadoopVersionMatch());

    String noteId = null;
    try {
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          Paragraph p = note.addNewParagraph(anonymous);
          File tmpCSVFile = File.createTempFile("test", ".csv");
          FileWriter csvFileWriter = new FileWriter(tmpCSVFile);
          IOUtils.copy(new StringReader("84896,54"), csvFileWriter);
          csvFileWriter.close();
          p.setText("%spark spark.read.csv(\"file://" + tmpCSVFile.getAbsolutePath() + "\")");
          note.run(p.getId(), true);
          assertEquals(Status.FINISHED, p.getStatus());
          assertTrue(p.getReturn().message().get(0).getData().contains(
                  "org.apache.spark.sql.DataFrame = [_c0: string, _c1: string]\n"));
          return null;
        });
    } finally {
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  public void sparkSQLTest() throws IOException {
    assumeTrue("Hadoop version mismatch, skip test", isHadoopVersionMatch());

    String noteId = null;
    try {
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          // test basic dataframe api
          Paragraph p = note.addNewParagraph(anonymous);
          if (isSpark2() || isSpark3()) {
            p.setText("%spark val df=spark.createDataFrame(Seq((\"hello\",20)))" +
                    ".toDF(\"name\", \"age\")\n" +
                    "df.collect()");
          } else {
            p.setText("%spark val df=sqlContext.createDataFrame(Seq((\"hello\",20)))" +
                    ".toDF(\"name\", \"age\")\n" +
                    "df.collect()");
          }
          note.run(p.getId(), true);
          assertEquals(Status.FINISHED, p.getStatus());
          assertTrue(p.getReturn().message().get(0).getData().contains(
                  "Array[org.apache.spark.sql.Row] = Array([hello,20])"));

          // test display DataFrame
          p = note.addNewParagraph(anonymous);
          if (isSpark2() || isSpark3()) {
            p.setText("%spark val df=spark.createDataFrame(Seq((\"hello\",20)))" +
                    ".toDF(\"name\", \"age\")\n" +
                    "df.createOrReplaceTempView(\"test_table\")\n" +
                    "z.show(df)");
          } else {
            p.setText("%spark val df=sqlContext.createDataFrame(Seq((\"hello\",20)))" +
                    ".toDF(\"name\", \"age\")\n" +
                    "df.registerTempTable(\"test_table\")\n" +
                    "z.show(df)");
          }
          note.run(p.getId(), true);
          assertEquals(Status.FINISHED, p.getStatus());
          assertEquals(InterpreterResult.Type.TABLE, p.getReturn().message().get(0).getType());
          assertEquals("name\tage\nhello\t20\n", p.getReturn().message().get(0).getData());

          // run sql and save it into resource pool
          p = note.addNewParagraph(anonymous);
          p.setText("%spark.sql(saveAs=table_result) select * from test_table");
          note.run(p.getId(), true);
          assertEquals(Status.FINISHED, p.getStatus());
          assertEquals(InterpreterResult.Type.TABLE, p.getReturn().message().get(0).getType());
          assertEquals("name\tage\nhello\t20\n", p.getReturn().message().get(0).getData());

          // get resource from spark
          p = note.addNewParagraph(anonymous);
          p.setText("%spark val df=z.getAsDataFrame(\"table_result\")\nz.show(df)");
          note.run(p.getId(), true);
          assertEquals(Status.FINISHED, p.getStatus());
          assertEquals(InterpreterResult.Type.TABLE, p.getReturn().message().get(0).getType());
          assertEquals("name\tage\nhello\t20\n", p.getReturn().message().get(0).getData());

          // get resource from pyspark
          p = note.addNewParagraph(anonymous);
          p.setText("%spark.pyspark df=z.getAsDataFrame('table_result')\nz.show(df)");
          note.run(p.getId(), true);
          assertEquals(Status.FINISHED, p.getStatus());
          assertEquals(InterpreterResult.Type.TABLE, p.getReturn().message().get(0).getType());
          assertEquals("name\tage\nhello\t20\n", p.getReturn().message().get(0).getData());

          // get resource from ipyspark
          p = note.addNewParagraph(anonymous);
          p.setText("%spark.ipyspark df=z.getAsDataFrame('table_result')\nz.show(df)");
          note.run(p.getId(), true);
          assertEquals(Status.FINISHED, p.getStatus());
          assertEquals(InterpreterResult.Type.TABLE, p.getReturn().message().get(0).getType());
          assertEquals("name\tage\nhello\t20\n", p.getReturn().message().get(0).getData());

          // get resource from sparkr
          p = note.addNewParagraph(anonymous);
          p.setText("%spark.r df=z.getAsDataFrame('table_result')\ndf");
          note.run(p.getId(), true);
          assertEquals(Status.FINISHED, p.getStatus());
          assertEquals(InterpreterResult.Type.TEXT, p.getReturn().message().get(0).getType());
          assertTrue(p.getReturn().toString(),
                  p.getReturn().message().get(0).getData().contains("name age\n1 hello  20"));

          // test display DataSet
          if (isSpark2() || isSpark3()) {
            p = note.addNewParagraph(anonymous);
            p.setText("%spark val ds=spark.createDataset(Seq((\"hello\",20)))\n" +
                "z.show(ds)");
            note.run(p.getId(), true);
            assertEquals(Status.FINISHED, p.getStatus());
            assertEquals(InterpreterResult.Type.TABLE, p.getReturn().message().get(0).getType());
            assertEquals("_1\t_2\nhello\t20\n", p.getReturn().message().get(0).getData());
          }
          return null;
        });
    } finally {
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  public void sparkRTest() throws IOException {
    assumeTrue("Hadoop version mismatch, skip test", isHadoopVersionMatch());

    String noteId = null;
    try {
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          Paragraph p = note.addNewParagraph(anonymous);

          if (isSpark3()) {
            p.setText("%spark.r localDF <- data.frame(name=c(\"a\", \"b\", \"c\"), age=c(19, 23, 18))\n" +
                    "df <- createDataFrame(localDF)\n" +
                    "count(df)"
            );
          } else {
            String sqlContextName = "sqlContext";
            if (isSpark2() || isSpark3()) {
              sqlContextName = "spark";
            }
            p.setText("%spark.r localDF <- data.frame(name=c(\"a\", \"b\", \"c\"), age=c(19, 23, 18))\n" +
                    "df <- createDataFrame(" + sqlContextName + ", localDF)\n" +
                    "count(df)"
            );
          }

          note.run(p.getId(), true);
          assertEquals(Status.FINISHED, p.getStatus());
          assertEquals("[1] 3", p.getReturn().message().get(0).getData().trim());
          return null;
        });
    } finally {
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  public void pySparkTest() throws IOException {
    assumeTrue("Hadoop version mismatch, skip test", isHadoopVersionMatch());

    // create new note
    String noteId = null;
    try {
        noteId = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
        TestUtils.getInstance(Notebook.class).processNote(noteId,
                note -> {

          // run markdown paragraph, again
          Paragraph p = note.addNewParagraph(anonymous);
          p.setText("%spark.pyspark sc.parallelize(range(1, 11)).reduce(lambda a, b: a + b)");
          note.run(p.getId(), true);
          assertEquals(Status.FINISHED, p.getStatus());
          assertEquals("55\n", p.getReturn().message().get(0).getData());

          // simple form via local properties
          p = note.addNewParagraph(anonymous);
          p.setText("%spark.pyspark(form=simple) print('name_' + '${name=abc}')");
          note.run(p.getId(), true);
          assertEquals(Status.FINISHED, p.getStatus());
          assertEquals("name_abc\n", p.getReturn().message().get(0).getData());

          // test code completion
          String code = "%spark.pyspark spark.";
          List<InterpreterCompletion> completions = note.completion(p.getId(), code, code.length(), AuthenticationInfo.ANONYMOUS);
          assertTrue(completions.size() > 0);

          if (isSpark2()){
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
          } else {
            // run SparkSession test
            p = note.addNewParagraph(anonymous);
            p.setText("%pyspark from pyspark.sql import Row\n" +
                    "df=sqlContext.createDataFrame([Row(id=1, age=20)])\n" +
                    "df.collect()");
            note.run(p.getId(), true);
            assertEquals(Status.FINISHED, p.getStatus());
            assertEquals("[Row(id=1, age=20)]\n", p.getReturn().message().get(0).getData());

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
          return null;
        });
    } finally {
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  public void zRunTest() throws IOException, InterruptedException {
    assumeTrue("Hadoop version mismatch, skip test", isHadoopVersionMatch());

    String noteId = null;
    String note2Id = null;
    try {
      // create new note
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      note2Id = TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
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
          // Sleep 1 second to ensure p3 start running
          try {
            Thread.sleep(1000);
          } catch (InterruptedException e) {
            fail();
          }
          waitForFinish(p3);
          assertEquals(Status.FINISHED, p3.getStatus());
          assertEquals("END\n", p3.getReturn().message().get(0).getData());

          // run paragraph in note2 via paragraph in note1
          String noteId2 = TestUtils.getInstance(Notebook.class).createNote("note2", anonymous);
          TestUtils.getInstance(Notebook.class).processNote(noteId2,
            note2 -> {
              Paragraph p20 = note2.addNewParagraph(anonymous);
              p20.setText("%spark val a = 1");
              Paragraph p21 = note2.addNewParagraph(anonymous);
              p21.setText("%spark print(a)");

              // run p20 of note2 via paragraph in note1
              p0.setText(String.format("%%spark.pyspark z.run(\"%s\", \"%s\")", note2.getId(), p20.getId()));
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
              return null;
            });
          return noteId2;
        });
    } finally {
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
      if (null != note2Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note2Id, anonymous);
      }
    }
  }

  private void setupPythonIsolatedPerNote() {
    InterpreterSetting pythonIntpSetting = TestUtils.getInstance(Notebook.class).getInterpreterSettingManager()
            .getInterpreterSettingByName("python");
    pythonIntpSetting.getOption().setPerNote("isolated");
  }

  private void resetPythonIsolatedPerNote() {
    InterpreterSetting pythonIntpSetting = TestUtils.getInstance(Notebook.class).getInterpreterSettingManager()
            .getInterpreterSettingByName("python");
    pythonIntpSetting.getOption().setPerNote("shared");
  }

  @Test
  public void testRunNotebookInterpreter_RunSucceed() throws IOException {
    assumeTrue("Hadoop version mismatch, skip test", isHadoopVersionMatch());

    String note1Id = null;
    String note2Id = null;
    try {
      setupPythonIsolatedPerNote();
      // create new note: note1
      note1Id = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      // add one paragraph to note1
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
              note -> {
                Paragraph p0 = note.addNewParagraph(anonymous);
                p0.setText("%python(form=simple) a='hello,${name}'\nprint(a)");
                return null;
              });

      // create new note: note2
      note2Id = TestUtils.getInstance(Notebook.class).createNote("note2", anonymous);
      // Run paragraph(%run <note1Id>) in note2
      final String finalNote1Id = note1Id;
      TestUtils.getInstance(Notebook.class).processNote(note2Id,
              note -> {
                Paragraph p0 = note.addNewParagraph(anonymous);
                p0.setText("%run " + finalNote1Id + " name=zeppelin");
                p0.run();
                InterpreterResult result = p0.getReturn();
                assertEquals(InterpreterResult.Code.SUCCESS, result.code());
                assertEquals(1, result.message().size());
                assertEquals(result.toString(), InterpreterResult.Type.TEXT, result.message().get(0).getType());
                assertEquals(result.toString(), "hello,zeppelin\n", result.message().get(0).getData());

                Paragraph p1 = note.addNewParagraph(anonymous);
                p1.setText("%python print(a)");
                p1.run();
                result = p1.getReturn();
                assertEquals(InterpreterResult.Code.SUCCESS, result.code());
                assertEquals(1, result.message().size());
                assertEquals(result.toString(), InterpreterResult.Type.TEXT, result.message().get(0).getType());
                assertEquals(result.toString(), "hello,zeppelin\n", result.message().get(0).getData());
                return null;
              });
    } finally {
      if (null != note1Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note1Id, anonymous);
      }
      if (null != note2Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note2Id, anonymous);
      }
      resetPythonIsolatedPerNote();
    }
  }

  @Test
  public void testRunNotebookInterpreter_RunMultipleNotes() throws IOException {
    assumeTrue("Hadoop version mismatch, skip test", isHadoopVersionMatch());

    String note1Id = null;
    String note2Id = null;
    String note3Id = null;
    try {
      setupPythonIsolatedPerNote();
      // create new note: note1
      note1Id = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      // add one paragraph to note1
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
              note -> {
                Paragraph p0 = note.addNewParagraph(anonymous);
                p0.setText("%python(form=simple) a='hello,${name}'\nprint(a)");
                return null;
              });

      note2Id = TestUtils.getInstance(Notebook.class).createNote("note2", anonymous);
      // add one paragraph to note1
      TestUtils.getInstance(Notebook.class).processNote(note2Id,
              note -> {
                Paragraph p0 = note.addNewParagraph(anonymous);
                p0.setText("%python(form=simple) b='hello,${name}'\nprint(b)");
                return null;
              });

      // create new note: note3
      note3Id = TestUtils.getInstance(Notebook.class).createNote("note3", anonymous);
      // Run paragraph(%run <note1Id>\n<note2Id>) in note3
      final String finalNote1Id = note1Id;
      final String finalNote2Id = note2Id;
      TestUtils.getInstance(Notebook.class).processNote(note3Id,
              note -> {
                Paragraph p0 = note.addNewParagraph(anonymous);
                p0.setText("%run " + finalNote1Id + " name=apache\n" +
                        finalNote2Id + " name=zeppelin");
                p0.run();
                InterpreterResult result = p0.getReturn();
                assertEquals(InterpreterResult.Code.SUCCESS, result.code());
                assertEquals(2, result.message().size());
                assertEquals(result.toString(), InterpreterResult.Type.TEXT, result.message().get(0).getType());
                assertEquals(result.toString(), "hello,apache\n", result.message().get(0).getData());
                assertEquals(result.toString(), InterpreterResult.Type.TEXT, result.message().get(1).getType());
                assertEquals(result.toString(), "hello,zeppelin\n", result.message().get(1).getData());

                Paragraph p1 = note.addNewParagraph(anonymous);
                p1.setText("%python print(a)\nprint(b)");
                p1.run();
                result = p1.getReturn();
                assertEquals(InterpreterResult.Code.SUCCESS, result.code());
                assertEquals(1, result.message().size());
                assertEquals(result.toString(), InterpreterResult.Type.TEXT, result.message().get(0).getType());
                assertEquals(result.toString(), "hello,apache\nhello,zeppelin\n", result.message().get(0).getData());
                return null;
              });
    } finally {
      if (null != note1Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note1Id, anonymous);
      }
      if (null != note2Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note2Id, anonymous);
      }
      if (null != note3Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note3Id, anonymous);
      }
      resetPythonIsolatedPerNote();
    }
  }

  @Test
  public void testRunNotebookInterpreter_RunError() throws IOException {
    assumeTrue("Hadoop version mismatch, skip test", isHadoopVersionMatch());

    String note1Id = null;
    String note2Id = null;
    try {
      setupPythonIsolatedPerNote();
      // create new note: note1
      note1Id = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      // add one paragraph to note1
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
              note -> {
                Paragraph p0 = note.addNewParagraph(anonymous);
                // invalid code
                p0.setText("%python invalid_code");
                return null;
              });

      // create new note: note2
      note2Id = TestUtils.getInstance(Notebook.class).createNote("note2", anonymous);
      // Run paragraph(%run <note1Id>) in note2
      final String finalNote1Id = note1Id;
      TestUtils.getInstance(Notebook.class).processNote(note2Id,
              note -> {
                Paragraph p0 = note.addNewParagraph(anonymous);
                p0.setText("%run " + finalNote1Id);
                p0.run();
                InterpreterResult result = p0.getReturn();
                assertEquals(InterpreterResult.Code.ERROR, result.code());
                assertEquals(1, result.message().size());
                assertTrue(result.toString(), result.message().get(0).getData().contains("name 'invalid_code' is not defined"));
                return null;
              });
    } finally {
      if (null != note1Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note1Id, anonymous);
      }
      if (null != note2Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note2Id, anonymous);
      }
      resetPythonIsolatedPerNote();
    }
  }

  @Test
  public void testRunNotebookInterpreter_InvalidSyntax() throws IOException {
    assumeTrue("Hadoop version mismatch, skip test", isHadoopVersionMatch());

    String note1Id = null;
    String note2Id = null;
    try {
      setupPythonIsolatedPerNote();
      // create new note: note1
      note1Id = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      // add one paragraph to note1
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
              note -> {
                Paragraph p0 = note.addNewParagraph(anonymous);
                p0.setText("%python print('hello,zeppelin')");
                return null;
              });

      // create new note: note2
      note2Id = TestUtils.getInstance(Notebook.class).createNote("note2", anonymous);
      // Run paragraph(%run <note1Id>) in note2
      final String finalNote1Id = note1Id;
      TestUtils.getInstance(Notebook.class).processNote(note2Id,
              note -> {
                // invalid noteId
                Paragraph p0 = note.addNewParagraph(anonymous);
                p0.setText("%run invalid_noteId");
                p0.run();
                InterpreterResult result = p0.getReturn();
                assertEquals(InterpreterResult.Code.ERROR, result.code());
                assertEquals(1, result.message().size());
                assertEquals(result.toString(), "Note `invalid_noteId` doesn't exist", result.message().get(0).getData());

                // run current note itself
                Paragraph p1 = note.addNewParagraph(anonymous);
                p1.setText("%run " + note.getId());
                p1.run();
                result = p1.getReturn();
                assertEquals(InterpreterResult.Code.ERROR, result.code());
                assertEquals(1, result.message().size());
                assertEquals(result.toString(), "Run current note itself in %run is not allowed", result.message().get(0).getData());

                // invalid format of params
                
                return null;
              });
    } finally {
      if (null != note1Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note1Id, anonymous);
      }
      if (null != note2Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note2Id, anonymous);
      }
      resetPythonIsolatedPerNote();
    }
  }

  @Test
  public void testZeppelinContextResource() throws IOException {
    assumeTrue("Hadoop version mismatch, skip test", isHadoopVersionMatch());

    String noteId = null;
    try {
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          Paragraph p1 = note.addNewParagraph(anonymous);
          p1.setText("%spark z.put(\"var_1\", \"hello world\")");

          Paragraph p2 = note.addNewParagraph(anonymous);
          p2.setText("%spark println(z.get(\"var_1\"))");

          Paragraph p3 = note.addNewParagraph(anonymous);
          p3.setText("%spark.pyspark print(z.get(\"var_1\"))");

          Paragraph p4 = note.addNewParagraph(anonymous);
          p4.setText("%spark.r z.get(\"var_1\")");

          // resources across interpreter processes (via DistributedResourcePool)
          Paragraph p5 = note.addNewParagraph(anonymous);
          p5.setText("%python print(z.get('var_1'))");

          note.run(p1.getId(), true);
          note.run(p2.getId(), true);
          note.run(p3.getId(), true);
          note.run(p4.getId(), true);
          note.run(p5.getId(), true);

          assertEquals(Status.FINISHED, p1.getStatus());
          assertEquals(Status.FINISHED, p2.getStatus());
          assertEquals("hello world\n", p2.getReturn().message().get(0).getData());
          assertEquals(Status.FINISHED, p3.getStatus());
          assertEquals("hello world\n", p3.getReturn().message().get(0).getData());
          assertEquals(Status.FINISHED, p4.getStatus());
          assertTrue(p4.getReturn().toString(),
                  p4.getReturn().message().get(0).getData().contains("hello world"));
          assertEquals(Status.FINISHED, p5.getStatus());
          assertEquals("hello world\n", p5.getReturn().message().get(0).getData());
          return null;
        });
    } finally {
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  public void testZeppelinContextHook() throws IOException {
    assumeTrue("Hadoop version mismatch, skip test", isHadoopVersionMatch());

    String noteId = null;
    String note2Id = null;
    try {
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      note2Id = TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
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

          String note2Tmp = TestUtils.getInstance(Notebook.class).createNote("note2", anonymous);
          TestUtils.getInstance(Notebook.class).processNote(note2Tmp,
            note2 -> {
              Paragraph p3 = note2.addNewParagraph(anonymous);
              p3.setText("%python print(6)");
              note2.run(p3.getId(), true);
              assertEquals("1\n6\n2\n", p3.getReturn().message().get(0).getData());
              return null;
            });
          return note2Tmp;
        });
    } finally {
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
      if (null != note2Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note2Id, anonymous);
      }
    }
  }

  private void verifySparkVersionNumber() throws IOException {
    String noteId = null;
    try {
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          Paragraph p = note.addNewParagraph(anonymous);

          p.setText("%spark print(sc.version)");
          note.run(p.getId());
          waitForFinish(p);
          assertEquals(Status.FINISHED, p.getStatus());
          assertEquals(sparkVersion, p.getReturn().message().get(0).getData());

          p.setText("%spark.pyspark sc.version");
          note.run(p.getId());
          waitForFinish(p);
          assertEquals(Status.FINISHED, p.getStatus());
          assertTrue(p.getReturn().toString(),
                  p.getReturn().message().get(0).getData().contains(sparkVersion));
          return null;
        });
    } finally {
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  private boolean isSpark2() {
    return sparkVersion.startsWith("2.");
  }

  private boolean isSpark3() {
    return sparkVersion.startsWith("3.");
  }

  @Test
  public void testSparkZeppelinContextDynamicForms() throws IOException {
    assumeTrue("Hadoop version mismatch, skip test", isHadoopVersionMatch());

    String noteId = null;
    try {
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          Paragraph p = note.addNewParagraph(anonymous);
          String code = "%spark println(z.textbox(\"my_input\", \"default_name\"))\n" +
              "println(z.password(\"my_pwd\"))\n" +
              "println(z.select(\"my_select\", \"1\"," +
              "Seq((\"1\", \"select_1\"), (\"2\", \"select_2\"))))\n" +
              "val items=z.checkbox(\"my_checkbox\", " +
              "Seq((\"1\", \"check_1\"), (\"2\", \"check_2\")), Seq(\"2\"))\n" +
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
          return null;
        });
    } finally {
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  public void testPySparkZeppelinContextDynamicForms() throws IOException {
    assumeTrue("Hadoop version mismatch, skip test", isHadoopVersionMatch());

    String noteId = null;
    try {
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
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
      return null;
        });
    } finally {
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  public void testAngularObjects() throws IOException, InterpreterNotFoundException {
    assumeTrue("Hadoop version mismatch, skip test", isHadoopVersionMatch());

    String noteId = null;
    try {
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          Paragraph p1 = note.addNewParagraph(anonymous);

          // add local angular object
          p1.setText("%spark z.angularBind(\"name\", \"world\")");
          note.run(p1.getId(), true);
          assertEquals(Status.FINISHED, p1.getStatus());
          // angular object is saved to InterpreterGroup's AngularObjectRegistry
          List<AngularObject> angularObjects;
          try {
            angularObjects = p1.getBindedInterpreter().getInterpreterGroup()
                    .getAngularObjectRegistry().getAll(note.getId(), null);

            assertEquals(1, angularObjects.size());
            assertEquals("name", angularObjects.get(0).getName());
            assertEquals("world", angularObjects.get(0).get());
          } catch (InterpreterNotFoundException e) {
            fail();
          }
          // angular object is saved to note as well.
          try {
            angularObjects = note.getAngularObjects(p1.getBindedInterpreter().getInterpreterGroup().getId());

            assertEquals(1, angularObjects.size());
            assertEquals("name", angularObjects.get(0).getName());
            assertEquals("world", angularObjects.get(0).get());
          } catch (InterpreterNotFoundException e) {
            fail();
          }

          // remove local angular object
          Paragraph p2 = note.addNewParagraph(anonymous);
          p2.setText("%spark z.angularUnbind(\"name\")");
          note.run(p2.getId(), true);
          assertEquals(Status.FINISHED, p2.getStatus());
          try {
            angularObjects = p1.getBindedInterpreter().getInterpreterGroup().getAngularObjectRegistry()
                    .getAll(note.getId(), null);
            assertEquals(0, angularObjects.size());
          } catch (InterpreterNotFoundException e) {
            fail();
          }


          try {
            angularObjects = note.getAngularObjects(p1.getBindedInterpreter().getInterpreterGroup().getId());
            assertEquals(0, angularObjects.size());
          } catch (InterpreterNotFoundException e) {
            fail();
          }


          // add global angular object
          Paragraph p3 = note.addNewParagraph(anonymous);
          p3.setText("%spark z.angularBindGlobal(\"name2\", \"world2\")");
          note.run(p3.getId(), true);
          assertEquals(Status.FINISHED, p3.getStatus());
          List<AngularObject> globalAngularObjects;
          try {
            globalAngularObjects = p3.getBindedInterpreter().getInterpreterGroup()
                    .getAngularObjectRegistry().getAll(null, null);
            assertEquals(1, globalAngularObjects.size());
            assertEquals("name2", globalAngularObjects.get(0).getName());
            assertEquals("world2", globalAngularObjects.get(0).get());
          } catch (InterpreterNotFoundException e) {
            fail();
          }


          // global angular object is not saved to note
          try {
            angularObjects = note.getAngularObjects(p1.getBindedInterpreter().getInterpreterGroup().getId());
            assertEquals(0, angularObjects.size());
          } catch (InterpreterNotFoundException e) {
            fail();
          }


          // remove global angular object
          Paragraph p4 = note.addNewParagraph(anonymous);
          p4.setText("%spark z.angularUnbindGlobal(\"name2\")");
          note.run(p4.getId(), true);
          assertEquals(Status.FINISHED, p4.getStatus());
          try {
            globalAngularObjects = p4.getBindedInterpreter().getInterpreterGroup()
                    .getAngularObjectRegistry().getAll(note.getId(), null);
            assertEquals(0, globalAngularObjects.size());
          } catch (InterpreterNotFoundException e) {
            fail();
          }


          // global angular object is not saved to note
          try {
            angularObjects = note.getAngularObjects(p1.getBindedInterpreter().getInterpreterGroup().getId());
            assertEquals(0, angularObjects.size());
          } catch (InterpreterNotFoundException e) {
            fail();
          }

          return null;
        });
    } finally {
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  public void testScalaNoteDynamicForms() throws IOException {
    assumeTrue("Hadoop version mismatch, skip test", isHadoopVersionMatch());

    String noteId = null;
    try {
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          Paragraph p1 = note.addNewParagraph(anonymous);

          // create TextBox
          p1.setText("%spark z.noteTextbox(\"name\", \"world\")");
          note.run(p1.getId(), true);
          assertEquals(Status.FINISHED, p1.getStatus());
          Input input = p1.getNote().getNoteForms().get("name");
          assertTrue(input instanceof TextBox);
          TextBox inputTextBox = (TextBox) input;
          assertEquals("name", inputTextBox.getDisplayName());
          assertEquals("world", inputTextBox.getDefaultValue());
          assertEquals("world", p1.getNote().getNoteParams().get("name"));

          Paragraph p2 = note.addNewParagraph(anonymous);
          p2.setText("%md hello $${name}");
          note.run(p2.getId(), true);
          assertEquals(Status.FINISHED, p2.getStatus());
          assertTrue(p2.getReturn().toString(), p2.getReturn().toString().contains("hello world"));

          // create Select
          p1.setText("%spark z.noteSelect(\"language\", Seq((\"java\" -> \"JAVA\"), (\"scala\" -> \"SCALA\")), \"java\")");
          note.run(p1.getId(), true);
          assertEquals(Status.FINISHED, p1.getStatus());
          input = p1.getNote().getNoteForms().get("language");
          assertTrue(input instanceof Select);
          Select select = (Select) input;
          assertEquals("language", select.getDisplayName());
          assertEquals("java", select.getDefaultValue());
          assertEquals("java", p1.getNote().getNoteParams().get("language"));

          p2 = note.addNewParagraph(anonymous);
          p2.setText("%md hello $${language}");
          note.run(p2.getId(), true);
          assertEquals(Status.FINISHED, p2.getStatus());
          assertTrue(p2.getReturn().toString(), p2.getReturn().toString().contains("hello java"));

          // create Checkbox
          p1.setText("%spark z.noteCheckbox(\"languages\", Seq((\"java\" -> \"JAVA\"), (\"scala\" -> \"SCALA\")), Seq(\"java\", \"scala\"))");
          note.run(p1.getId(), true);
          assertEquals(Status.FINISHED, p1.getStatus());
          input = p1.getNote().getNoteForms().get("languages");
          assertTrue(input instanceof CheckBox);
          CheckBox checkbox = (CheckBox) input;
          assertEquals("languages", checkbox.getDisplayName());
          assertArrayEquals(new Object[]{"java", "scala"}, checkbox.getDefaultValue());
          assertEquals(Arrays.asList("java", "scala"), p1.getNote().getNoteParams().get("languages"));

          p2 = note.addNewParagraph(anonymous);
          p2.setText("%md hello $${checkbox:languages}");
          note.run(p2.getId(), true);
          assertEquals(Status.FINISHED, p2.getStatus());
          assertTrue(p2.getReturn().toString(), p2.getReturn().toString().contains("hello java,scala"));
          return null;
        });
    } finally {
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  public void testPythonNoteDynamicForms() throws IOException {
    assumeTrue("Hadoop version mismatch, skip test", isHadoopVersionMatch());

    String noteId = null;
    try {
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          Paragraph p1 = note.addNewParagraph(anonymous);

          // create TextBox
          p1.setText("%spark.pyspark z.noteTextbox(\"name\", \"world\")");
          note.run(p1.getId(), true);
          assertEquals(Status.FINISHED, p1.getStatus());
          Input input = p1.getNote().getNoteForms().get("name");
          assertTrue(input instanceof TextBox);
          TextBox inputTextBox = (TextBox) input;
          assertEquals("name", inputTextBox.getDisplayName());
          assertEquals("world", inputTextBox.getDefaultValue());
          assertEquals("world", p1.getNote().getNoteParams().get("name"));

          Paragraph p2 = note.addNewParagraph(anonymous);
          p2.setText("%md hello $${name}");
          note.run(p2.getId(), true);
          assertEquals(Status.FINISHED, p2.getStatus());
          assertTrue(p2.getReturn().toString(), p2.getReturn().toString().contains("hello world"));

          // create Select
          p1.setText("%spark.pyspark z.noteSelect('language', [('java', 'JAVA'), ('scala', 'SCALA')], 'java')");
          note.run(p1.getId(), true);
          assertEquals(Status.FINISHED, p1.getStatus());
          input = p1.getNote().getNoteForms().get("language");
          assertTrue(input instanceof Select);
          Select select = (Select) input;
          assertEquals("language", select.getDisplayName());
          assertEquals("java", select.getDefaultValue());
          assertEquals("java", p1.getNote().getNoteParams().get("language"));

          p2 = note.addNewParagraph(anonymous);
          p2.setText("%md hello $${language}");
          note.run(p2.getId(), true);
          assertEquals(Status.FINISHED, p2.getStatus());
          assertTrue(p2.getReturn().toString(), p2.getReturn().toString().contains("hello java"));

          // create Checkbox
          p1.setText("%spark.pyspark z.noteCheckbox('languages', [('java', 'JAVA'), ('scala', 'SCALA')], ['java', 'scala'])");
          note.run(p1.getId(), true);
          assertEquals(Status.FINISHED, p1.getStatus());
          input = p1.getNote().getNoteForms().get("languages");
          assertTrue(input instanceof CheckBox);
          CheckBox checkbox = (CheckBox) input;
          assertEquals("languages", checkbox.getDisplayName());
          assertArrayEquals(new Object[]{"java", "scala"}, checkbox.getDefaultValue());
          assertEquals(Arrays.asList("java", "scala"), p1.getNote().getNoteParams().get("languages"));

          p2 = note.addNewParagraph(anonymous);
          p2.setText("%md hello $${checkbox:languages}");
          note.run(p2.getId(), true);
          assertEquals(Status.FINISHED, p2.getStatus());
          assertTrue(p2.getReturn().toString(), p2.getReturn().toString().contains("hello java,scala"));
          return null;
        });
    } finally {
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  public void testRNoteDynamicForms() throws IOException {
    assumeTrue("Hadoop version mismatch, skip test", isHadoopVersionMatch());

    String noteId = null;
    try {
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          Paragraph p1 = note.addNewParagraph(anonymous);

          // create TextBox
          p1.setText("%spark.r z.noteTextbox(\"name\", \"world\")");
          note.run(p1.getId(), true);
          assertEquals(Status.FINISHED, p1.getStatus());
          Input input = p1.getNote().getNoteForms().get("name");
          assertTrue(input instanceof TextBox);
          TextBox inputTextBox = (TextBox) input;
          assertEquals("name", inputTextBox.getDisplayName());
          assertEquals("world", inputTextBox.getDefaultValue());
          assertEquals("world", p1.getNote().getNoteParams().get("name"));

          Paragraph p2 = note.addNewParagraph(anonymous);
          p2.setText("%md hello $${name}");
          note.run(p2.getId(), true);
          assertEquals(Status.FINISHED, p2.getStatus());
          assertTrue(p2.getReturn().toString(), p2.getReturn().toString().contains("hello world"));
          return null;
        });
    } finally {
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  public void testConfInterpreter() throws IOException {
    assumeTrue("Hadoop version mismatch, skip test", isHadoopVersionMatch());

    String noteId = null;
    try {
      TestUtils.getInstance(Notebook.class).getInterpreterSettingManager().close();
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          Paragraph p = note.addNewParagraph(anonymous);
          p.setText("%spark.conf spark.jars.packages\tcom.databricks:spark-csv_2.11:1.2.0");
          note.run(p.getId(), true);
          assertEquals(Status.FINISHED, p.getStatus());

          Paragraph p1 = note.addNewParagraph(anonymous);
          p1.setText("%spark\nimport com.databricks.spark.csv._");
          note.run(p1.getId(), true);
          assertEquals(Status.FINISHED, p1.getStatus());

          // test pyspark imports path
          Paragraph p2 = note.addNewParagraph(anonymous);
          p2.setText("%spark.pyspark\nimport sys\nsys.path");
          note.run(p2.getId(), true);
          assertEquals(Status.FINISHED, p2.getStatus());
          assertTrue(p2.getReturn().toString().contains("databricks_spark"));

          Paragraph p3 = note.addNewParagraph(anonymous);
          p3.setText("%spark.ipyspark\nimport sys\nsys.path");
          note.run(p3.getId(), true);
          assertEquals(Status.FINISHED, p3.getStatus());
          assertTrue(p3.getReturn().toString().contains("databricks_spark"));
          return null;
        });
    } finally {
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  public void testFailtoLaunchSpark() throws IOException {
    assumeTrue("Hadoop version mismatch, skip test", isHadoopVersionMatch());

    String noteId = null;
    try {
      TestUtils.getInstance(Notebook.class).getInterpreterSettingManager().close();
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          Paragraph p = note.addNewParagraph(anonymous);
          p.setText("%spark.conf SPARK_HOME invalid_spark_home");
          note.run(p.getId(), true);
          assertEquals(Status.FINISHED, p.getStatus());

          Paragraph p1 = note.addNewParagraph(anonymous);
          p1.setText("%spark\nsc.version");
          note.run(p1.getId(), true);
          assertEquals(Status.ERROR, p1.getStatus());
          assertTrue("Actual error message: " + p1.getReturn().message().get(0).getData(),
                  p1.getReturn().message().get(0).getData().contains("No such file or directory"));

          // run it again, and get the same error
          note.run(p1.getId(), true);
          assertEquals(Status.ERROR, p1.getStatus());
          assertTrue("Actual error message: " + p1.getReturn().message().get(0).getData(),
                  p1.getReturn().message().get(0).getData().contains("No such file or directory"));
          return null;
        });
    } finally {
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
      // reset SPARK_HOME, otherwise it will cause the following test fail
      InterpreterSetting sparkIntpSetting = TestUtils.getInstance(Notebook.class).getInterpreterSettingManager()
              .getInterpreterSettingByName("spark");
      Map<String, InterpreterProperty> sparkProperties =
              (Map<String, InterpreterProperty>) sparkIntpSetting.getProperties();
      sparkProperties.put("SPARK_HOME", new InterpreterProperty("SPARK_HOME", sparkHome));
      sparkIntpSetting.close();
    }
  }
}
