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

import com.google.common.io.Files;
import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.integration.DownloadUtils;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class ZeppelinFlinkClusterTest extends AbstractTestRestApi {

  private static final Logger LOGGER = LoggerFactory.getLogger(ZeppelinFlinkClusterTest.class);
  private String flinkVersion;
  private String flinkHome;

  public ZeppelinFlinkClusterTest(String flinkVersion) throws Exception {
    this.flinkVersion = flinkVersion;
    LOGGER.info("Testing FlinkVersion: " + flinkVersion);
    this.flinkHome = DownloadUtils.downloadFlink(flinkVersion);
  }

  @BeforeClass
  public static void setUp() throws Exception {
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HELIUM_REGISTRY.getVarName(),
            "helium");
    AbstractTestRestApi.startUp(ZeppelinFlinkClusterTest.class.getSimpleName());
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  //@Test
  public void testResumeFromCheckpoint() throws Exception {

    Note note = null;
    try {
      // create new note
      note = TestUtils.getInstance(Notebook.class).createNote("note1", AuthenticationInfo.ANONYMOUS);

      // run p0 for %flink.conf
      String checkpointPath = Files.createTempDir().getAbsolutePath();
      Paragraph p0 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      StringBuilder builder = new StringBuilder("%flink.conf\n");
      builder.append("FLINK_HOME " + flinkHome + "\n");
      builder.append("flink.execution.mode local\n");
      builder.append("state.checkpoints.dir file://" + checkpointPath + "\n");
      builder.append("execution.checkpointing.externalized-checkpoint-retention RETAIN_ON_CANCELLATION");
      p0.setText(builder.toString());
      note.run(p0.getId(), true);
      assertEquals(Job.Status.FINISHED, p0.getStatus());

      // run p1 for creating flink table via scala
      Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      p1.setText("%flink " + getInitStreamScript(2000));
      note.run(p1.getId(), true);
      assertEquals(Job.Status.FINISHED, p0.getStatus());

      // run p2 for flink streaming sql
      Paragraph p2 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      p2.setText("%flink.ssql(type=single, template=<h1>Total: {0}</h1>, resumeFromLatestCheckpoint=true)\n" +
              "select count(1) from log;");
      note.run(p2.getId(), false);
      p2.waitUntilRunning();

      Thread.sleep(30 * 1000);
      TestUtils.getInstance(Notebook.class).getInterpreterSettingManager()
              .getInterpreterSettingByName("flink").close();
      assertTrue(p2.getConfig().toString(), p2.getConfig().get("latest_checkpoint_path").toString().contains(checkpointPath));

      // run it again
      note.run(p0.getId(), true);
      note.run(p1.getId(), true);
      note.run(p2.getId(), false);
      p2.waitUntilFinished();
      assertEquals(p2.getReturn().toString(), Job.Status.FINISHED, p2.getStatus());

    } catch (Exception e) {
      e.printStackTrace();
      throw e;
    } finally {
      if (null != note) {
        TestUtils.getInstance(Notebook.class).removeNote(note, AuthenticationInfo.ANONYMOUS);
      }
    }
  }

  //@Test
  public void testResumeFromInvalidCheckpoint() throws Exception {

    Note note = null;
    try {
      // create new note
      note = TestUtils.getInstance(Notebook.class).createNote("note2", AuthenticationInfo.ANONYMOUS);

      // run p0 for %flink.conf
      String checkpointPath = Files.createTempDir().getAbsolutePath();
      Paragraph p0 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      StringBuilder builder = new StringBuilder("%flink.conf\n");
      builder.append("FLINK_HOME " + flinkHome + "\n");
      builder.append("flink.execution.mode local\n");
      builder.append("state.checkpoints.dir file://" + checkpointPath + "\n");
      builder.append("execution.checkpointing.externalized-checkpoint-retention RETAIN_ON_CANCELLATION");
      p0.setText(builder.toString());
      note.run(p0.getId(), true);
      assertEquals(Job.Status.FINISHED, p0.getStatus());

      // run p1 for creating flink table via scala
      Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      p1.setText("%flink " + getInitStreamScript(500));
      note.run(p1.getId(), true);
      assertEquals(Job.Status.FINISHED, p0.getStatus());

      // run p2 for flink streaming sql
      Paragraph p2 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      p2.setText("%flink.ssql(type=single, template=<h1>Total: {0}</h1>, resumeFromLatestCheckpoint=true)\n" +
              "select count(1) from log;");
      p2.getConfig().put("latest_checkpoint_path", "file:///invalid_checkpoint");
      note.run(p2.getId(), false);
      p2.waitUntilFinished();
      assertEquals(p2.getReturn().toString(), Job.Status.ERROR, p2.getStatus());
      assertTrue(p2.getReturn().toString(), p2.getReturn().toString().contains("Cannot find checkpoint"));

      p2.setText("%flink.ssql(type=single, template=<h1>Total: {0}</h1>, resumeFromLatestCheckpoint=false)\n" +
              "select count(1) from log;");
      note.run(p2.getId(), false);
      p2.waitUntilFinished();
      assertEquals(p2.getReturn().toString(), Job.Status.FINISHED, p2.getStatus());
    } catch (Exception e) {
      e.printStackTrace();
      throw e;
    } finally {
      if (null != note) {
        TestUtils.getInstance(Notebook.class).removeNote(note, AuthenticationInfo.ANONYMOUS);
      }
    }
  }

  public static String getInitStreamScript(int sleep_interval) throws IOException {
    return IOUtils.toString(FlinkIntegrationTest.class.getResource("/init_stream.scala"))
            .replace("{{sleep_interval}}", sleep_interval + "");
  }
}
