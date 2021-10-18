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
import org.apache.zeppelin.client.ClientConfig;
import org.apache.zeppelin.client.ExecuteResult;
import org.apache.zeppelin.client.websocket.SimpleMessageHandler;
import org.apache.zeppelin.client.Status;
import org.apache.zeppelin.client.ZSession;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.integration.DownloadUtils;
import org.apache.zeppelin.interpreter.lifecycle.TimeoutLifecycleManager;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.apache.zeppelin.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ZSessionIntegrationTest extends AbstractTestRestApi {

  private static Notebook notebook;
  private static String sparkHome;
  private static String flinkHome;

  private ClientConfig clientConfig = new ClientConfig("http://localhost:8080");


  @BeforeClass
  public static void setUp() throws Exception {
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HELIUM_REGISTRY.getVarName(),
            "helium");
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_ALLOWED_ORIGINS.getVarName(), "*");
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_SESSION_CHECK_INTERVAL.getVarName(), "5000");

    AbstractTestRestApi.startUp(ZSessionIntegrationTest.class.getSimpleName());
    ZeppelinConfiguration zConf = ZeppelinConfiguration.create();
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_CLASS.getVarName(), TimeoutLifecycleManager.class.getName());
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_TIMEOUT_CHECK_INTERVAL.getVarName(), "5000");
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_TIMEOUT_THRESHOLD.getVarName(), "10000");

    notebook = TestUtils.getInstance(Notebook.class);
    sparkHome = DownloadUtils.downloadSpark("2.4.4", "2.7");
    flinkHome = DownloadUtils.downloadFlink("1.12.4", "2.11");
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  @Test
  public void testZSession_Shell() throws Exception {
    ZSession session = ZSession.builder()
            .setClientConfig(clientConfig)
            .setInterpreter("sh")
            .build();

    try {
      session.start();
      assertNull(session.getWeburl());
      assertNotNull(session.getNoteId());

      Note note = notebook.getNote(session.getNoteId());
      assertEquals(2, note.getParagraphCount());
      assertTrue(note.getParagraph(0).getText(), note.getParagraph(0).getText().startsWith("%sh.conf"));

      ExecuteResult result = session.execute("pwd");
      assertEquals(result.toString(), Status.FINISHED, result.getStatus());
      assertEquals(1, result.getResults().size());
      assertEquals("TEXT", result.getResults().get(0).getType());

      result = session.execute("invalid_command");
      assertEquals(Status.ERROR, result.getStatus());
      assertEquals(2, result.getResults().size());
      assertEquals("TEXT", result.getResults().get(0).getType());
      assertTrue(result.getResults().get(0).getData(), result.getResults().get(0).getData().contains("command not found"));
      assertEquals("TEXT", result.getResults().get(1).getType());
      assertTrue(result.getResults().get(1).getData(), result.getResults().get(1).getData().contains("ExitValue"));

      assertEquals(4, note.getParagraphCount());
      assertEquals("%sh invalid_command", note.getParagraph(3).getText());

    } finally {
      session.stop();
    }
  }

  @Test
  public void testZSession_Shell_Submit() throws Exception {
    ZSession session = ZSession.builder()
            .setClientConfig(clientConfig)
            .setInterpreter("sh")
            .build();

    try {
      session.start();
      assertNull(session.getWeburl());
      assertNotNull(session.getNoteId());

      Note note = notebook.getNote(session.getNoteId());
      assertEquals(2, note.getParagraphCount());
      assertTrue(note.getParagraph(0).getText(), note.getParagraph(0).getText().startsWith("%sh.conf"));

      ExecuteResult result = session.submit("sleep 10\npwd");
      assertFalse("Status is: " + result.getStatus().toString(), result.getStatus().isCompleted());
      result = session.waitUntilFinished(result.getStatementId());
      assertEquals(result.toString(), Status.FINISHED, result.getStatus());
      assertEquals(1, result.getResults().size());
      assertEquals("TEXT", result.getResults().get(0).getType());

      result = session.submit("invalid_command");
      result = session.waitUntilFinished(result.getStatementId());
      assertEquals(Status.ERROR, result.getStatus());
      assertEquals(2, result.getResults().size());
      assertEquals("TEXT", result.getResults().get(0).getType());
      assertTrue(result.getResults().get(0).getData(), result.getResults().get(0).getData().contains("command not found"));
      assertEquals("TEXT", result.getResults().get(1).getType());
      assertTrue(result.getResults().get(1).getData(), result.getResults().get(1).getData().contains("ExitValue"));

      assertEquals(4, note.getParagraphCount());
      assertEquals("%sh invalid_command", note.getParagraph(3).getText());

    } finally {
      session.stop();
    }
  }

  @Test
  public void testZSession_Spark() throws Exception {
    Map<String, String> intpProperties = new HashMap<>();
    intpProperties.put("SPARK_HOME", sparkHome);
    intpProperties.put("spark.master", "local[*]");

    ZSession session = ZSession.builder()
            .setClientConfig(clientConfig)
            .setInterpreter("spark")
            .setIntpProperties(intpProperties)
            .build();

    try {
      session.start();
      assertNotNull(session.getWeburl());
      assertNotNull(session.getNoteId());

      // scala
      ExecuteResult result = session.execute("sc.version");
      assertEquals(result.toString(), Status.FINISHED, result.getStatus());
      assertEquals(1, result.getResults().size());
      assertEquals("TEXT", result.getResults().get(0).getType());
      assertTrue(result.getResults().get(0).getData(), result.getResults().get(0).getData().contains("2.4.4"));
      assertEquals(0, result.getJobUrls().size());

      // pyspark
      result = session.execute("pyspark", "df = spark.createDataFrame([(1,'a'),(2,'b')])\ndf.registerTempTable('df')\ndf.show()");
      assertEquals(Status.FINISHED, result.getStatus());
      assertEquals(1, result.getResults().size());
      assertEquals("TEXT", result.getResults().get(0).getType());
      assertEquals(
              "+---+---+\n" +
              "| _1| _2|\n" +
              "+---+---+\n" +
              "|  1|  a|\n" +
              "|  2|  b|\n" +
              "+---+---+", result.getResults().get(0).getData().trim());
      assertTrue(result.getJobUrls().size() > 0);

      // sparkr
      result = session.execute("r", "df <- as.DataFrame(faithful)\nhead(df)");
      assertEquals(Status.FINISHED, result.getStatus());
      assertEquals(1, result.getResults().size());
      assertEquals("TEXT", result.getResults().get(0).getType());
      assertTrue(result.getResults().get(0).getData(), result.getResults().get(0).getData().contains("eruptions waiting"));
      assertEquals(2, result.getJobUrls().size());

      // spark sql
      result = session.execute("sql", "select * from df");
      assertEquals(Status.FINISHED, result.getStatus());
      assertEquals(1, result.getResults().size());
      assertEquals("TABLE", result.getResults().get(0).getType());
      assertTrue(result.getResults().get(0).getData(), result.getResults().get(0).getData().contains("1\ta\n2\tb\n"));
      assertTrue(result.getJobUrls().size() > 0);

      // spark invalid sql
      result = session.execute("sql", "select * from unknown_table");
      assertEquals(Status.ERROR, result.getStatus());
      assertEquals(1, result.getResults().size());
      assertEquals("TEXT", result.getResults().get(0).getType());
      assertTrue(result.getResults().get(0).getData(), result.getResults().get(0).getData().contains("Table or view not found"));
      assertEquals(0, result.getJobUrls().size());

    } finally {
      session.stop();
    }
  }

  @Test
  public void testZSession_Spark_Submit() throws Exception {
    Map<String, String> intpProperties = new HashMap<>();
    intpProperties.put("SPARK_HOME", sparkHome);
    intpProperties.put("spark.master", "local[*]");

    ZSession session = ZSession.builder()
            .setClientConfig(clientConfig)
            .setInterpreter("spark")
            .setIntpProperties(intpProperties)
            .build();

    try {
      session.start();
      assertNotNull(session.getWeburl());
      assertNotNull(session.getNoteId());

      // scala
      ExecuteResult result = session.submit("sc.version");
      result = session.waitUntilFinished(result.getStatementId());
      assertEquals(result.toString(), Status.FINISHED, result.getStatus());
      assertEquals(1, result.getResults().size());
      assertEquals("TEXT", result.getResults().get(0).getType());
      assertTrue(result.getResults().get(0).getData(), result.getResults().get(0).getData().contains("2.4.4"));
      assertEquals(0, result.getJobUrls().size());

      // pyspark
      result = session.submit("pyspark", "df = spark.createDataFrame([(1,'a'),(2,'b')])\ndf.registerTempTable('df')\ndf.show()");
      result = session.waitUntilFinished(result.getStatementId());
      assertEquals(result.toString(), Status.FINISHED, result.getStatus());
      assertEquals(1, result.getResults().size());
      assertEquals("TEXT", result.getResults().get(0).getType());
      assertEquals(
              "+---+---+\n" +
              "| _1| _2|\n" +
              "+---+---+\n" +
              "|  1|  a|\n" +
              "|  2|  b|\n" +
              "+---+---+", result.getResults().get(0).getData().trim());
      assertTrue(result.getJobUrls().size() > 0);

      // sparkr
      result = session.submit("r", "df <- as.DataFrame(faithful)\nhead(df)");
      result = session.waitUntilFinished(result.getStatementId());
      assertEquals(Status.FINISHED, result.getStatus());
      assertEquals(1, result.getResults().size());
      assertEquals("TEXT", result.getResults().get(0).getType());
      assertTrue(result.getResults().get(0).getData(), result.getResults().get(0).getData().contains("eruptions waiting"));
      assertEquals(2, result.getJobUrls().size());

      // spark sql
      result = session.submit("sql", "select * from df");
      result = session.waitUntilFinished(result.getStatementId());
      assertEquals(Status.FINISHED, result.getStatus());
      assertEquals(1, result.getResults().size());
      assertEquals("TABLE", result.getResults().get(0).getType());
      assertTrue(result.getResults().get(0).getData(), result.getResults().get(0).getData().contains("1\ta\n2\tb\n"));
      assertTrue(result.getJobUrls().size() > 0);

      // spark invalid sql
      result = session.submit("sql", "select * from unknown_table");
      result = session.waitUntilFinished(result.getStatementId());
      assertEquals(Status.ERROR, result.getStatus());
      assertEquals(1, result.getResults().size());
      assertEquals("TEXT", result.getResults().get(0).getType());
      assertTrue(result.getResults().get(0).getData(), result.getResults().get(0).getData().contains("Table or view not found"));
      assertEquals(0, result.getJobUrls().size());

      // cancel
      result = session.submit("sc.range(1,100).map(e=>{Thread.sleep(1000);e}).collect()");
      assertFalse("Status is: " + result.getStatus().toString(), result.getStatus().isCompleted());
      result = session.waitUntilRunning(result.getStatementId());
      session.cancel(result.getStatementId());
      assertEquals(result.toString(), Status.RUNNING, result.getStatus());
      result = session.waitUntilFinished(result.getStatementId());
      assertEquals(result.toString(), Status.ABORT, result.getStatus());

    } finally {
      session.stop();
    }
  }

  @Test
  public void testZSession_Flink() throws Exception {
    Map<String, String> intpProperties = new HashMap<>();
    intpProperties.put("FLINK_HOME", flinkHome);

    ZSession session = ZSession.builder()
            .setClientConfig(clientConfig)
            .setInterpreter("flink")
            .setIntpProperties(intpProperties)
            .build();

    try {
      session.start();
      assertNotNull(session.getWeburl());
      assertNotNull(session.getNoteId());

      // scala
      ExecuteResult result = session.execute("val data = benv.fromElements(1, 2, 3)\ndata.collect()");
      assertEquals(result.toString(), Status.FINISHED, result.getStatus());
      assertEquals(1, result.getResults().size());
      assertEquals("TEXT", result.getResults().get(0).getType());
      assertTrue(result.getResults().get(0).getData(), result.getResults().get(0).getData().contains("1, 2, 3"));

      // sql
      result = session.execute(getInitStreamScript(200));
      assertEquals(result.toString(), Status.FINISHED, result.getStatus());
      Map<String, String> localProperties = new HashMap<>();
      localProperties.put("type", "update");
      localProperties.put("parallelism", "2");
      result = session.execute("ssql", localProperties, "select url, count(1) as pv from log group by url");
      assertEquals(result.toString(), Status.FINISHED, result.getStatus());

    } finally {
      session.stop();
    }
  }

  @Test
  public void testZSession_Flink_Submit() throws Exception {
    Map<String, String> intpProperties = new HashMap<>();
    intpProperties.put("FLINK_HOME", flinkHome);

    ZSession session = ZSession.builder()
            .setClientConfig(clientConfig)
            .setInterpreter("flink")
            .setIntpProperties(intpProperties)
            .build();

    try {
      session.start(new SimpleMessageHandler());
      assertNotNull(session.getWeburl());
      assertNotNull(session.getNoteId());

      // scala
      ExecuteResult result = session.submit("val data = benv.fromElements(1, 2, 3)\ndata.collect()");
      result = session.waitUntilFinished(result.getStatementId());
      assertEquals(result.toString(), Status.FINISHED, result.getStatus());
      assertEquals(1, result.getResults().size());
      assertEquals("TEXT", result.getResults().get(0).getType());
      assertTrue(result.getResults().get(0).getData(), result.getResults().get(0).getData().contains("1, 2, 3"));

      // sql
      result = session.submit(getInitStreamScript(200));
      result = session.waitUntilFinished(result.getStatementId());
      assertEquals(result.toString(), Status.FINISHED, result.getStatus());
      Map<String, String> localProperties = new HashMap<>();
      localProperties.put("type", "update");
      result = session.submit("ssql", localProperties, "select url, count(1) as pv from log group by url");
      assertFalse("Status is: " + result.getStatus().toString(), result.getStatus().isCompleted());
      result = session.waitUntilFinished(result.getStatementId());
      assertEquals(result.toString(), Status.FINISHED, result.getStatus());

      // cancel
      result = session.submit("ssql", localProperties, "select url, count(1) as pv from log group by url");
      assertFalse("Status is: " + result.getStatus().toString(), result.getStatus().isCompleted());
      result = session.waitUntilRunning(result.getStatementId());
      session.cancel(result.getStatementId());
      assertEquals(result.toString(), Status.RUNNING, result.getStatus());
      result = session.waitUntilFinished(result.getStatementId());
      assertEquals(result.toString(), Status.ABORT, result.getStatus());
    } finally {
      session.stop();
    }
  }

  @Test
  public void testZSession_Python() throws Exception {
    Map<String, String> intpProperties = new HashMap<>();
    intpProperties.put("zeppelin.python.gatewayserver_address", "127.0.0.1");

    ZSession session = ZSession.builder()
            .setClientConfig(clientConfig)
            .setInterpreter("python")
            .setIntpProperties(intpProperties)
            .build();

    try {
      session.start(new SimpleMessageHandler());
      assertNull(session.getWeburl());
      assertNotNull(session.getNoteId());

      // python
//      ExecuteResult result = session.execute("1+1");
//      assertEquals(result.toString(), Status.FINISHED, result.getStatus());
//      assertEquals(1, result.getResults().size());
//      assertEquals("TEXT", result.getResults().get(0).getType());
//      assertTrue(result.getResults().get(0).getData(), result.getResults().get(0).getData().contains("2"));
//
//      // python
//      result = session.execute("1/0");
//      assertEquals(result.toString(), Status.ERROR, result.getStatus());
//      assertEquals(1, result.getResults().size());
//      assertEquals("TEXT", result.getResults().get(0).getType());
//      assertTrue(result.getResults().get(0).getData(), result.getResults().get(0).getData().contains("ZeroDivisionError"));

      // for loop
      ExecuteResult result = session.execute("import time\n" +
                               "for i in range(1,10):\n" +
                               "\tprint(i)\n" +
                               "\ttime.sleep(1)");
      assertEquals(result.toString(), Status.FINISHED, result.getStatus());
      assertEquals(1, result.getResults().size());
      assertEquals("TEXT", result.getResults().get(0).getType());

      Map<String, String> localProperties = new HashMap<>();
      localProperties.put("key 1", "hello world"); // contains whitespace
      localProperties.put("key,2", "a,b"); // contains comma
      result = session.execute("1+1", localProperties);
      assertEquals(result.toString(), Status.FINISHED, result.getStatus());
      assertEquals(1, result.getResults().size());
      assertEquals("TEXT", result.getResults().get(0).getType());
    } finally {
      session.stop();
    }
  }

  @Test
  public void testZSessionCleanup() throws Exception {
    Map<String, String> intpProperties = new HashMap<>();
    intpProperties.put("zeppelin.python.gatewayserver_address", "127.0.0.1");

    ZSession session = ZSession.builder()
            .setClientConfig(clientConfig)
            .setInterpreter("python")
            .setIntpProperties(intpProperties)
            .build();

    try {
      session.start(new SimpleMessageHandler());
      assertNull(session.getWeburl());
      assertNotNull(session.getNoteId());

      assertTrue(notebook.getAllNotes().size() > 0);

      Thread.sleep(30 * 1000);
      assertEquals(0, notebook.getAllNotes().size());

      try {
        session.execute("1/0");
        fail("Should fail to execute code after session is stopped");
      } catch (Exception e) {
        e.printStackTrace();
      }
    } finally {
      try {
        session.stop();
        fail("Should fail to stop session after it is stopped");
      } catch (Exception e) {
        e.printStackTrace();
        assertTrue(e.getMessage().contains("No such session"));
      }
    }
  }

  //@Test
  public void testZSession_Jdbc() throws Exception {

    Map<String, String> intpProperties = new HashMap<>();
    intpProperties.put("default.driver", "com.mysql.jdbc.Driver");
    intpProperties.put("default.url", "jdbc:mysql://localhost:3306/");
    intpProperties.put("default.user", "root");

    ZSession session = ZSession.builder()
            .setClientConfig(clientConfig)
            .setInterpreter("jdbc")
            .setIntpProperties(intpProperties)
            .build();

    try {
      session.start();
      assertEquals("", session.getWeburl());
      assertNotNull(session.getNoteId());

      // show databases
      ExecuteResult result = session.execute("show databases");
      assertEquals(result.toString(), Status.FINISHED, result.getStatus());
      assertEquals(1, result.getResults().size());
      assertEquals("TABLE", result.getResults().get(0).getType());
      assertTrue(result.getResults().get(0).getData(), result.getResults().get(0).getData().contains("Database"));

      // select statement
      result = session.execute("SELECT 1 as c1, 2 as c2");
      assertEquals(result.toString(), Status.FINISHED, result.getStatus());
      assertEquals(1, result.getResults().size());
      assertEquals("TABLE", result.getResults().get(0).getType());
      assertEquals("c1\tc2\n1\t2\n", result.getResults().get(0).getData());

    } finally {
      session.stop();
    }
  }

  //@Test
  public void testZSession_Jdbc_Submit() throws Exception {

    Map<String, String> intpProperties = new HashMap<>();
    intpProperties.put("default.driver", "com.mysql.jdbc.Driver");
    intpProperties.put("default.url", "jdbc:mysql://localhost:3306/");
    intpProperties.put("default.user", "root");

    ZSession session = ZSession.builder()
            .setClientConfig(clientConfig)
            .setInterpreter("jdbc")
            .setIntpProperties(intpProperties)
            .build();

    try {
      session.start();
      assertEquals("", session.getWeburl());
      assertNotNull(session.getNoteId());

      // show databases
      ExecuteResult result = session.submit("show databases");
      result = session.waitUntilFinished(result.getStatementId());
      assertEquals(result.toString(), Status.FINISHED, result.getStatus());
      assertEquals(1, result.getResults().size());
      assertEquals("TABLE", result.getResults().get(0).getType());
      assertTrue(result.getResults().get(0).getData(), result.getResults().get(0).getData().contains("Database"));

      // select statement
      result = session.submit("SELECT 1 as c1, 2 as c2");
      result = session.waitUntilFinished(result.getStatementId());
      assertEquals(result.toString(), Status.FINISHED, result.getStatus());
      assertEquals(1, result.getResults().size());
      assertEquals("TABLE", result.getResults().get(0).getType());
      assertEquals("c1\tc2\n1\t2\n", result.getResults().get(0).getData());

    } finally {
      session.stop();
    }
  }

  public static String getInitStreamScript(int sleep_interval) throws IOException {
    return IOUtils.toString(ZSessionIntegrationTest.class.getResource("/init_stream.scala"))
            .replace("{{sleep_interval}}", sleep_interval + "");
  }
}
