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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.server.ZeppelinServer;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test against spark cluster.
 * Spark cluster is started by CI server using testing/startSparkCluster.sh
 */
public class ZeppelinSparkClusterTest extends AbstractTestRestApi {
    AuthenticationInfo anonymous;

    @BeforeClass
    public static void init() throws Exception {
        AbstractTestRestApi.startUp(ZeppelinSparkClusterTest.class.getSimpleName());
    }

    @AfterClass
    public static void destroy() throws Exception {
        AbstractTestRestApi.shutDown();
    }

    @Before
    public void setUp() {
        anonymous = new AuthenticationInfo("anonymous");
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

    @Test
    public void scalaOutputTest() throws IOException {
        // create new note
        Note note = ZeppelinServer.notebook.createNote(anonymous);
        Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        Map config = p.getConfig();
        config.put("enabled", true);
        p.setConfig(config);
        p.setText("%spark import java.util.Date\n" +
            "import java.net.URL\n" +
            "println(\"hello\")\n"
        );
        p.setAuthenticationInfo(anonymous);
        note.run(p.getId());
        waitForFinish(p);
        assertEquals(Status.FINISHED, p.getStatus());
        assertEquals("import java.util.Date\n" +
            "import java.net.URL\n" +
            "hello\n", p.getResult().message().get(0).getData());
        ZeppelinServer.notebook.removeNote(note.getId(), anonymous);
    }



    @Test
    public void basicRDDTransformationAndActionTest() throws IOException {
        // create new note
        Note note = ZeppelinServer.notebook.createNote(anonymous);

        // run markdown paragraph, again
        Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        Map config = p.getConfig();
        config.put("enabled", true);
        p.setConfig(config);
        p.setText("%spark print(sc.parallelize(1 to 10).reduce(_ + _))");
        p.setAuthenticationInfo(anonymous);
        note.run(p.getId());
        waitForFinish(p);
        assertEquals(Status.FINISHED, p.getStatus());
        assertEquals("55", p.getResult().message().get(0).getData());
        ZeppelinServer.notebook.removeNote(note.getId(), anonymous);
    }

    @Test
    public void sparkSQLTest() throws IOException {
        // create new note
        Note note = ZeppelinServer.notebook.createNote(anonymous);
        int sparkVersion = getSparkVersionNumber(note);
        // DataFrame API is available from spark 1.3
        if (sparkVersion >= 13) {
            // test basic dataframe api
            Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
            Map config = p.getConfig();
            config.put("enabled", true);
            p.setConfig(config);
            p.setText("%spark val df=sqlContext.createDataFrame(Seq((\"hello\",20)))\n" +
                    "df.collect()");
            p.setAuthenticationInfo(anonymous);
            note.run(p.getId());
            waitForFinish(p);
            assertEquals(Status.FINISHED, p.getStatus());
            assertTrue(p.getResult().message().get(0).getData().contains(
                    "Array[org.apache.spark.sql.Row] = Array([hello,20])"));

            // test display DataFrame
            p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
            config = p.getConfig();
            config.put("enabled", true);
            p.setConfig(config);
            p.setText("%spark val df=sqlContext.createDataFrame(Seq((\"hello\",20)))\n" +
                    "z.show(df)");
            p.setAuthenticationInfo(anonymous);
            note.run(p.getId());
            waitForFinish(p);
            assertEquals(Status.FINISHED, p.getStatus());
            assertEquals(InterpreterResult.Type.TABLE, p.getResult().message().get(1).getType());
            assertEquals("_1\t_2\nhello\t20\n", p.getResult().message().get(1).getData());

            // test display DataSet
            if (sparkVersion >= 20) {
                p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
                config = p.getConfig();
                config.put("enabled", true);
                p.setConfig(config);
                p.setText("%spark val ds=spark.createDataset(Seq((\"hello\",20)))\n" +
                        "z.show(ds)");
                p.setAuthenticationInfo(anonymous);
                note.run(p.getId());
                waitForFinish(p);
                assertEquals(Status.FINISHED, p.getStatus());
                assertEquals(InterpreterResult.Type.TABLE, p.getResult().message().get(1).getType());
                assertEquals("_1\t_2\nhello\t20\n", p.getResult().message().get(1).getData());
            }
            ZeppelinServer.notebook.removeNote(note.getId(), anonymous);
        }
    }

    @Test
    public void sparkRTest() throws IOException, InterpreterException {
      // create new note
      Note note = ZeppelinServer.notebook.createNote(anonymous);
      int sparkVersion = getSparkVersionNumber(note);

      if (isSparkR() && sparkVersion >= 14) {   // sparkr supported from 1.4.0
        // restart spark interpreter
        List<InterpreterSetting> settings =
          ZeppelinServer.notebook.getBindedInterpreterSettings(note.getId());

        for (InterpreterSetting setting : settings) {
          if (setting.getName().equals("spark")) {
            ZeppelinServer.notebook.getInterpreterSettingManager().restart(setting.getId());
            break;
          }
        }

        String sqlContextName = "sqlContext";
        if (sparkVersion >= 20) {
          sqlContextName = "spark";
        }
        Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        Map config = p.getConfig();
        config.put("enabled", true);
        p.setConfig(config);
        p.setText("%r localDF <- data.frame(name=c(\"a\", \"b\", \"c\"), age=c(19, 23, 18))\n" +
            "df <- createDataFrame(" + sqlContextName + ", localDF)\n" +
            "count(df)"
        );
        p.setAuthenticationInfo(anonymous);
        note.run(p.getId());
        waitForFinish(p);
        System.err.println("sparkRTest=" + p.getResult().message().get(0).getData());
        assertEquals(Status.FINISHED, p.getStatus());
        assertEquals("[1] 3", p.getResult().message().get(0).getData().trim());
      }
      ZeppelinServer.notebook.removeNote(note.getId(), anonymous);
    }

    @Test
    public void pySparkTest() throws IOException {
        // create new note
        Note note = ZeppelinServer.notebook.createNote(anonymous);
        note.setName("note");
        int sparkVersion = getSparkVersionNumber(note);

        if (isPyspark() && sparkVersion >= 12) {   // pyspark supported from 1.2.1
            // run markdown paragraph, again
            Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
            Map config = p.getConfig();
            config.put("enabled", true);
            p.setConfig(config);
            p.setText("%pyspark print(sc.parallelize(range(1, 11)).reduce(lambda a, b: a + b))");
            p.setAuthenticationInfo(anonymous);
            note.run(p.getId());
            waitForFinish(p);
            assertEquals(Status.FINISHED, p.getStatus());
            assertEquals("55\n", p.getResult().message().get(0).getData());
            if (sparkVersion >= 13) {
                // run sqlContext test
                p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
                config = p.getConfig();
                config.put("enabled", true);
                p.setConfig(config);
                p.setText("%pyspark from pyspark.sql import Row\n" +
                        "df=sqlContext.createDataFrame([Row(id=1, age=20)])\n" +
                        "df.collect()");
                p.setAuthenticationInfo(anonymous);
                note.run(p.getId());
                waitForFinish(p);
                assertEquals(Status.FINISHED, p.getStatus());
                assertEquals("[Row(age=20, id=1)]\n", p.getResult().message().get(0).getData());

                // test display Dataframe
                p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
                config = p.getConfig();
                config.put("enabled", true);
                p.setConfig(config);
                p.setText("%pyspark from pyspark.sql import Row\n" +
                        "df=sqlContext.createDataFrame([Row(id=1, age=20)])\n" +
                        "z.show(df)");
                p.setAuthenticationInfo(anonymous);
                note.run(p.getId());
                waitForFinish(p);
                assertEquals(Status.FINISHED, p.getStatus());
                assertEquals(InterpreterResult.Type.TABLE, p.getResult().message().get(0).getType());
                // TODO (zjffdu), one more \n is appended, need to investigate why.
                assertEquals("age\tid\n20\t1\n", p.getResult().message().get(0).getData());

                // test udf
                p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
                config = p.getConfig();
                config.put("enabled", true);
                p.setConfig(config);
                p.setText("%pyspark sqlContext.udf.register(\"f1\", lambda x: len(x))\n" +
                       "sqlContext.sql(\"select f1(\\\"abc\\\") as len\").collect()");
                p.setAuthenticationInfo(anonymous);
                note.run(p.getId());
                waitForFinish(p);
                assertEquals(Status.FINISHED, p.getStatus());
                assertTrue("[Row(len=u'3')]\n".equals(p.getResult().message().get(0).getData()) ||
                    "[Row(len='3')]\n".equals(p.getResult().message().get(0).getData()));

                // test exception
                p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
                config = p.getConfig();
                config.put("enabled", true);
                p.setConfig(config);
                /**
                 %pyspark
                 a=1

                 print(a2)
                 */
                p.setText("%pyspark a=1\n\nprint(a2)");
                p.setAuthenticationInfo(anonymous);
                note.run(p.getId());
                waitForFinish(p);
                assertEquals(Status.ERROR, p.getStatus());
                assertTrue(p.getResult().message().get(0).getData()
                    .contains("Fail to execute line 3: print(a2)"));
                assertTrue(p.getResult().message().get(0).getData()
                    .contains("name 'a2' is not defined"));
            }
            if (sparkVersion >= 20) {
                // run SparkSession test
                p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
                config = p.getConfig();
                config.put("enabled", true);
                p.setConfig(config);
                p.setText("%pyspark from pyspark.sql import Row\n" +
                        "df=sqlContext.createDataFrame([Row(id=1, age=20)])\n" +
                        "df.collect()");
                p.setAuthenticationInfo(anonymous);
                note.run(p.getId());
                waitForFinish(p);
                assertEquals(Status.FINISHED, p.getStatus());
                assertEquals("[Row(age=20, id=1)]\n", p.getResult().message().get(0).getData());

                // test udf
                p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
                config = p.getConfig();
                config.put("enabled", true);
                p.setConfig(config);
                // use SQLContext to register UDF but use this UDF through SparkSession
                p.setText("%pyspark sqlContext.udf.register(\"f1\", lambda x: len(x))\n" +
                        "spark.sql(\"select f1(\\\"abc\\\") as len\").collect()");
                p.setAuthenticationInfo(anonymous);
                note.run(p.getId());
                waitForFinish(p);
                assertEquals(Status.FINISHED, p.getStatus());
                assertTrue("[Row(len=u'3')]\n".equals(p.getResult().message().get(0).getData()) ||
                    "[Row(len='3')]\n".equals(p.getResult().message().get(0).getData()));
            }
        }
        ZeppelinServer.notebook.removeNote(note.getId(), anonymous);
    }

    @Test
    public void pySparkAutoConvertOptionTest() throws IOException {
        // create new note
        Note note = ZeppelinServer.notebook.createNote(anonymous);
        note.setName("note");

        int sparkVersionNumber = getSparkVersionNumber(note);

        if (isPyspark() && sparkVersionNumber >= 14) {   // auto_convert enabled from spark 1.4
            // run markdown paragraph, again
            Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
            Map config = p.getConfig();
            config.put("enabled", true);
            p.setConfig(config);

            String sqlContextName = "sqlContext";
            if (sparkVersionNumber >= 20) {
                sqlContextName = "spark";
            }

            p.setText("%pyspark\nfrom pyspark.sql.functions import *\n"
                    + "print(" + sqlContextName + ".range(0, 10).withColumn('uniform', rand(seed=10) * 3.14).count())");
            p.setAuthenticationInfo(anonymous);
            note.run(p.getId());
            waitForFinish(p);
            assertEquals(Status.FINISHED, p.getStatus());
            assertEquals("10\n", p.getResult().message().get(0).getData());
        }
        ZeppelinServer.notebook.removeNote(note.getId(), anonymous);
    }

    @Test
    public void zRunTest() throws IOException {
        // create new note
        Note note = ZeppelinServer.notebook.createNote(anonymous);
        Paragraph p0 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        Map config0 = p0.getConfig();
        config0.put("enabled", true);
        p0.setConfig(config0);
        p0.setText("%spark z.run(1)");
        p0.setAuthenticationInfo(anonymous);
        Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        Map config1 = p1.getConfig();
        config1.put("enabled", true);
        p1.setConfig(config1);
        p1.setText("%spark val a=10");
        p1.setAuthenticationInfo(anonymous);
        Paragraph p2 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        Map config2 = p2.getConfig();
        config2.put("enabled", true);
        p2.setConfig(config2);
        p2.setText("%spark print(a)");
        p2.setAuthenticationInfo(anonymous);

        note.run(p0.getId());
        waitForFinish(p0);
        assertEquals(Status.FINISHED, p0.getStatus());

        // z.run is not blocking call. So p1 may not be finished when p0 is done.
        waitForFinish(p1);
        note.run(p2.getId());
        waitForFinish(p2);
        assertEquals(Status.FINISHED, p2.getStatus());
        assertEquals("10", p2.getResult().message().get(0).getData());

        Paragraph p3 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        Map config3 = p3.getConfig();
        config3.put("enabled", true);
        p3.setConfig(config3);
        p3.setText("%spark println(new java.util.Date())");
        p3.setAuthenticationInfo(anonymous);

        p0.setText(String.format("%%spark z.runNote(\"%s\")", note.getId()));
        note.run(p0.getId());
        waitForFinish(p0);
        waitForFinish(p1);
        waitForFinish(p2);
        waitForFinish(p3);

        assertEquals(Status.FINISHED, p3.getStatus());
        String p3result = p3.getResult().message().get(0).getData();
        assertNotEquals(null, p3result);
        assertNotEquals("", p3result);

        p0.setText(String.format("%%spark z.run(\"%s\", \"%s\")", note.getId(), p3.getId()));
        p3.setText("%%spark println(\"END\")");

        note.run(p0.getId());
        waitForFinish(p0);
        waitForFinish(p3);

        assertNotEquals(p3result, p3.getResult().message());

        ZeppelinServer.notebook.removeNote(note.getId(), anonymous);
    }

    @Test
    public void pySparkDepLoaderTest() throws IOException, InterpreterException {
        // create new note
        Note note = ZeppelinServer.notebook.createNote(anonymous);
        int sparkVersionNumber = getSparkVersionNumber(note);

        if (isPyspark() && sparkVersionNumber >= 14) {
            // restart spark interpreter
            List<InterpreterSetting> settings =
                    ZeppelinServer.notebook.getBindedInterpreterSettings(note.getId());

            for (InterpreterSetting setting : settings) {
                if (setting.getName().equals("spark")) {
                    ZeppelinServer.notebook.getInterpreterSettingManager().restart(setting.getId());
                    break;
                }
            }

            // load dep
            Paragraph p0 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
            Map config = p0.getConfig();
            config.put("enabled", true);
            p0.setConfig(config);
            p0.setText("%dep z.load(\"com.databricks:spark-csv_2.11:1.2.0\")");
            p0.setAuthenticationInfo(anonymous);
            note.run(p0.getId());
            waitForFinish(p0);
            assertEquals(Status.FINISHED, p0.getStatus());

            // write test csv file
            File tmpFile = File.createTempFile("test", "csv");
            FileUtils.write(tmpFile, "a,b\n1,2");

            // load data using libraries from dep loader
            Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
            p1.setConfig(config);

            String sqlContextName = "sqlContext";
            if (sparkVersionNumber >= 20) {
                sqlContextName = "spark";
            }
            p1.setText("%pyspark\n" +
                    "from pyspark.sql import SQLContext\n" +
                    "print(" + sqlContextName + ".read.format('com.databricks.spark.csv')" +
                    ".load('"+ tmpFile.getAbsolutePath() +"').count())");
            p1.setAuthenticationInfo(anonymous);
            note.run(p1.getId());

            waitForFinish(p1);
            assertEquals(Status.FINISHED, p1.getStatus());
            assertEquals("2\n", p1.getResult().message().get(0).getData());
        }
        ZeppelinServer.notebook.removeNote(note.getId(), anonymous);
    }

    /**
     * Get spark version number as a numerical value.
     * eg. 1.1.x => 11, 1.2.x => 12, 1.3.x => 13 ...
     */
    private int getSparkVersionNumber(Note note) {
        Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        note.setName("note");
        Map config = p.getConfig();
        config.put("enabled", true);
        p.setConfig(config);
        p.setText("%spark print(sc.version)");
        p.setAuthenticationInfo(anonymous);
        note.run(p.getId());
        waitForFinish(p);
        assertEquals(Status.FINISHED, p.getStatus());
        String sparkVersion = p.getResult().message().get(0).getData();
        System.out.println("Spark version detected " + sparkVersion);
        String[] split = sparkVersion.split("\\.");
        int version = Integer.parseInt(split[0]) * 10 + Integer.parseInt(split[1]);
        return version;
    }

    @Test
    public void testSparkZeppelinContextDynamicForms() throws IOException {
        Note note = ZeppelinServer.notebook.createNote(anonymous);
        Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        note.setName("note");
        Map config = p.getConfig();
        config.put("enabled", true);
        p.setConfig(config);
        String code = "%spark.spark println(z.textbox(\"my_input\", \"default_name\"))\n" +
            "println(z.select(\"my_select\", \"1\"," +
            "Seq((\"1\", \"select_1\"), (\"2\", \"select_2\"))))\n" +
            "val items=z.checkbox(\"my_checkbox\", Seq(\"2\"), " +
            "Seq((\"1\", \"check_1\"), (\"2\", \"check_2\")))\n" +
            "println(items(0))";
        p.setText(code);
        p.setAuthenticationInfo(anonymous);
        note.run(p.getId());
        waitForFinish(p);

        assertEquals(Status.FINISHED, p.getStatus());
        Iterator<String> formIter = p.settings.getForms().keySet().iterator();
        assert(formIter.next().equals("my_input"));
        assert(formIter.next().equals("my_select"));
        assert(formIter.next().equals("my_checkbox"));

        // check dynamic forms values
        String[] result = p.getResult().message().get(0).getData().split("\n");
        assertEquals(4, result.length);
        assertEquals("default_name", result[0]);
        assertEquals("1", result[1]);
        assertEquals("items: Seq[Object] = Buffer(2)", result[2]);
        assertEquals("2", result[3]);

        ZeppelinServer.notebook.removeNote(note.getId(), anonymous);
    }

    @Test
    public void testPySparkZeppelinContextDynamicForms() throws IOException {
        Note note = ZeppelinServer.notebook.createNote(anonymous);
        Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        note.setName("note");
        Map config = p.getConfig();
        config.put("enabled", true);
        p.setConfig(config);
        String code = "%spark.pyspark print(z.input('my_input', 'default_name'))\n" +
            "print(z.select('my_select', " +
            "[('1', 'select_1'), ('2', 'select_2')], defaultValue='1'))\n" +
            "items=z.checkbox('my_checkbox', " +
            "[('1', 'check_1'), ('2', 'check_2')], defaultChecked=['2'])\n" +
            "print(items[0])";
        p.setText(code);
        p.setAuthenticationInfo(anonymous);
        note.run(p.getId());
        waitForFinish(p);

        assertEquals(Status.FINISHED, p.getStatus());
        Iterator<String> formIter = p.settings.getForms().keySet().iterator();
        assert(formIter.next().equals("my_input"));
        assert(formIter.next().equals("my_select"));
        assert(formIter.next().equals("my_checkbox"));

        // check dynamic forms values
        String[] result = p.getResult().message().get(0).getData().split("\n");
        assertEquals(3, result.length);
        assertEquals("default_name", result[0]);
        assertEquals("1", result[1]);
        assertEquals("2", result[2]);

        ZeppelinServer.notebook.removeNote(note.getId(), anonymous);
    }

    @Test
    public void testConfInterpreter() throws IOException {
        Note note = ZeppelinServer.notebook.createNote(AuthenticationInfo.ANONYMOUS);
        Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        Map config = p.getConfig();
        config.put("enabled", true);
        p.setConfig(config);
        p.setText("%spark.conf spark.jars.packages\tcom.databricks:spark-csv_2.11:1.2.0");
        p.setAuthenticationInfo(anonymous);
        note.run(p.getId());
        waitForFinish(p);
        assertEquals(Status.FINISHED, p.getStatus());

        Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        p1.setConfig(config);
        p1.setText("%spark\nimport com.databricks.spark.csv._");
        p1.setAuthenticationInfo(anonymous);
        note.run(p1.getId());

        waitForFinish(p1);
        assertEquals(Status.FINISHED, p1.getStatus());

        ZeppelinServer.notebook.removeNote(note.getId(), anonymous);

    }
}
