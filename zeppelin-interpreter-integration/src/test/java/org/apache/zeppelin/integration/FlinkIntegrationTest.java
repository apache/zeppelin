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

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationsRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationsResponse;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.zeppelin.test.DownloadUtils;
import org.apache.zeppelin.MiniZeppelinServer;
import org.apache.zeppelin.interpreter.ExecutionContext;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.EnumSet;

public abstract class FlinkIntegrationTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(FlinkIntegrationTest.class);

  private static MiniHadoopCluster hadoopCluster;
  private static MiniZeppelinServer zepServer;
  private static InterpreterFactory interpreterFactory;
  private static InterpreterSettingManager interpreterSettingManager;

  private String flinkVersion;
  private String hadoopHome;
  private String flinkHome;

  public void download(String flinkVersion, String scalaVersion) throws IOException {
    LOGGER.info("Testing FlinkVersion: " + flinkVersion);
    LOGGER.info("Testing ScalaVersion: " + scalaVersion);
    this.flinkVersion = flinkVersion;
    this.flinkHome = DownloadUtils.downloadFlink(flinkVersion, scalaVersion);
    this.hadoopHome = DownloadUtils.downloadHadoop("3.3.6");
  }

  @BeforeAll
  public static void setUp() throws Exception {
    Configuration conf = new Configuration();
    conf.setBoolean(YarnConfiguration.YARN_MINICLUSTER_FIXED_PORTS, true);
    conf.set(MiniDFSCluster.HDFS_MINIDFS_BASEDIR, "target/hadoop-minicluster");
    hadoopCluster = new MiniHadoopCluster(conf);
    hadoopCluster.start();

    zepServer = new MiniZeppelinServer(FlinkIntegrationTest.class.getSimpleName());
    zepServer.addInterpreter("flink");
    zepServer.addInterpreter("flink-cmd");
    zepServer.copyLogProperties();
    zepServer.copyBinDir();
    zepServer.addLauncher("YarnInterpreterLauncher");
    zepServer.addLauncher("FlinkInterpreterLauncher");
    zepServer.start();
  }

  @AfterAll
  public static void tearDown() throws Exception {
    zepServer.destroy();
    if (hadoopCluster != null) {
      hadoopCluster.stop();
    }
  }

  @BeforeEach
  void setup() {
    interpreterSettingManager = zepServer.getService(InterpreterSettingManager.class);
    interpreterFactory = new InterpreterFactory(interpreterSettingManager);
  }

  private void testInterpreterBasics() throws IOException, InterpreterException {
    // test FlinkInterpreter
    Interpreter flinkInterpreter = interpreterFactory.getInterpreter("flink", new ExecutionContext("user1", "note1", "flink"));

    InterpreterContext context = new InterpreterContext.Builder().setNoteId("note1").setParagraphId("paragraph_1").build();
    InterpreterResult interpreterResult = flinkInterpreter.interpret("1+1", context);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code(), interpreterResult.toString());
    assertTrue(interpreterResult.message().get(0).getData().contains("2"));

    interpreterResult = flinkInterpreter.interpret("val data = benv.fromElements(1, 2, 3)\ndata.collect()", context);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code(), interpreterResult.toString());
    assertTrue(interpreterResult.message().get(0).getData().contains("1, 2, 3"));

    interpreterResult = flinkInterpreter.interpret("val data = senv.fromElements(1, 2, 3)\ndata.print()", context);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code(), interpreterResult.toString());

    // check spark weburl in zeppelin-server side
    InterpreterSetting flinkInterpreterSetting = interpreterSettingManager.getByName("flink");
    assertEquals(1, flinkInterpreterSetting.getAllInterpreterGroups().size());
    assertNotNull(flinkInterpreterSetting.getAllInterpreterGroups().get(0).getWebUrl());
  }

  @Test
  public void testFlinkCmd() throws InterpreterException {
    InterpreterSetting flinkCmdInterpreterSetting = interpreterSettingManager.getInterpreterSettingByName("flink-cmd");
    flinkCmdInterpreterSetting.setProperty("FLINK_HOME", flinkHome);

    Interpreter flinkCmdInterpreter = interpreterFactory.getInterpreter("flink-cmd", new ExecutionContext("user1", "note1", "flink"));
    InterpreterContext context = new InterpreterContext.Builder().setNoteId("note1").setParagraphId("paragraph_1").build();
    InterpreterResult interpreterResult = flinkCmdInterpreter.interpret("info -c org.apache.flink.streaming.examples.wordcount.WordCount " + flinkHome + "/examples/streaming/WordCount.jar", context);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
  }

  @Test
  public void testLocalMode() throws IOException, YarnException, InterpreterException {
    InterpreterSetting flinkInterpreterSetting = interpreterSettingManager.getInterpreterSettingByName("flink");
    flinkInterpreterSetting.setProperty("FLINK_HOME", flinkHome);
    flinkInterpreterSetting.setProperty("ZEPPELIN_CONF_DIR", zepServer.getZeppelinConfiguration().getConfDir());
    flinkInterpreterSetting.setProperty("flink.execution.mode", "local");

    testInterpreterBasics();

    // no yarn application launched
    GetApplicationsRequest request = GetApplicationsRequest.newInstance(EnumSet.of(YarnApplicationState.RUNNING));
    GetApplicationsResponse response = hadoopCluster.getYarnCluster().getResourceManager().getClientRMService().getApplications(request);
    assertEquals(0, response.getApplicationList().size());

    interpreterSettingManager.close();
  }

  @Test
  public void testYarnMode() throws IOException, InterpreterException, YarnException {
    InterpreterSetting flinkInterpreterSetting = interpreterSettingManager.getInterpreterSettingByName("flink");
    flinkInterpreterSetting.setProperty("HADOOP_CONF_DIR", hadoopCluster.getConfigPath());
    flinkInterpreterSetting.setProperty("FLINK_HOME", flinkHome);
    flinkInterpreterSetting.setProperty("PATH", hadoopHome + "/bin:" + System.getenv("PATH"));
    flinkInterpreterSetting.setProperty("ZEPPELIN_CONF_DIR", zepServer.getZeppelinConfiguration().getConfDir());
    flinkInterpreterSetting.setProperty("flink.execution.mode", "yarn");
    flinkInterpreterSetting.setProperty("zeppelin.flink.run.asLoginUser", "false");

    testInterpreterBasics();

    // 1 yarn application launched
    GetApplicationsRequest request = GetApplicationsRequest.newInstance(EnumSet.of(YarnApplicationState.RUNNING));
    GetApplicationsResponse response = hadoopCluster.getYarnCluster().getResourceManager().getClientRMService().getApplications(request);
    assertEquals(1, response.getApplicationList().size());

    interpreterSettingManager.close();
  }

  @Test
  public void testYarnApplicationMode() throws IOException, InterpreterException, YarnException {
    if (flinkVersion.startsWith("1.10")) {
      LOGGER.info("Skip yarn application mode test for flink 1.10");
      return;
    }
    InterpreterSetting flinkInterpreterSetting = interpreterSettingManager.getInterpreterSettingByName("flink");
    flinkInterpreterSetting.setProperty("HADOOP_CONF_DIR", hadoopCluster.getConfigPath());
    flinkInterpreterSetting.setProperty("FLINK_HOME", flinkHome);
    flinkInterpreterSetting.setProperty("PATH", hadoopHome + "/bin:" + System.getenv("PATH"));
    flinkInterpreterSetting.setProperty("ZEPPELIN_CONF_DIR", zepServer.getZeppelinConfiguration().getConfDir());
    flinkInterpreterSetting.setProperty("flink.execution.mode", "yarn-application");
    // parameters with whitespace
    flinkInterpreterSetting.setProperty("flink.yarn.appName", "hello flink");
    flinkInterpreterSetting.setProperty("zeppelin.flink.run.asLoginUser", "false");

    testInterpreterBasics();

    // 1 yarn application launched
    GetApplicationsRequest request = GetApplicationsRequest.newInstance(EnumSet.of(YarnApplicationState.RUNNING));
    GetApplicationsResponse response = hadoopCluster.getYarnCluster().getResourceManager().getClientRMService().getApplications(request);
    assertEquals(1, response.getApplicationList().size());
    assertEquals("hello flink", response.getApplicationList().get(0).getName());

    interpreterSettingManager.close();
  }
}
