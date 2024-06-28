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

import net.jodah.concurrentunit.Waiter;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationsRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationsResponse;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.zeppelin.test.DownloadUtils;
import org.apache.zeppelin.MiniZeppelinServer;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
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
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class SparkSubmitIntegrationTest {

  private static Logger LOGGER = LoggerFactory.getLogger(SparkSubmitIntegrationTest.class);

  private static MiniHadoopCluster hadoopCluster;
  private static InterpreterFactory interpreterFactory;
  private static InterpreterSettingManager interpreterSettingManager;

  private static String sparkHome;
  private static MiniZeppelinServer zepServer;

  @BeforeAll
  static void init() throws Exception {
    LOGGER.info("Testing Spark Version: " + DownloadUtils.DEFAULT_SPARK_VERSION);
    LOGGER.info("Testing Hadoop Version: " + DownloadUtils.DEFAULT_SPARK_HADOOP_VERSION);
    sparkHome = DownloadUtils.downloadSpark();
    hadoopCluster = new MiniHadoopCluster();
    hadoopCluster.start();

    zepServer = new MiniZeppelinServer(SparkSubmitIntegrationTest.class.getSimpleName());
    zepServer.addInterpreter("sh");
    zepServer.addInterpreter("spark-submit");
    zepServer.copyBinDir();
    zepServer.getZeppelinConfiguration().setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HELIUM_REGISTRY.getVarName(),
        "helium");
    zepServer.start();
    interpreterSettingManager = zepServer.getServiceLocator().getService(InterpreterSettingManager.class);
    interpreterFactory = zepServer.getServiceLocator().getService(InterpreterFactory.class);
    InterpreterSetting sparkSubmitInterpreterSetting = interpreterSettingManager.getInterpreterSettingByName("spark-submit");
    sparkSubmitInterpreterSetting.setProperty("SPARK_HOME", sparkHome);
    sparkSubmitInterpreterSetting.setProperty("HADOOP_CONF_DIR", hadoopCluster.getConfigPath());
    sparkSubmitInterpreterSetting.setProperty("YARN_CONF_DIR", hadoopCluster.getConfigPath());
  }

  @AfterAll
  public static void tearDown() throws Exception {
    zepServer.destroy();
    if (hadoopCluster != null) {
      hadoopCluster.stop();
    }
  }

  @Test
  void testLocalMode() throws InterpreterException, YarnException {
    try {
      // test SparkSubmitInterpreterSetting
      Interpreter sparkSubmitInterpreter = interpreterFactory.getInterpreter("spark-submit", new ExecutionContext("user1", "note1", "test"));

      InterpreterContext context = new InterpreterContext.Builder().setNoteId("note1").setParagraphId("paragraph_1").build();
      InterpreterResult interpreterResult =
              sparkSubmitInterpreter.interpret("--master local --class org.apache.spark.examples.SparkPi --deploy-mode client " +
              sparkHome + "/examples/jars/spark-examples_2.12-" + DownloadUtils.DEFAULT_SPARK_VERSION + ".jar", context);
      assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code(), interpreterResult.toString());

      // no yarn application launched
      GetApplicationsRequest request = GetApplicationsRequest.newInstance(EnumSet.of(YarnApplicationState.RUNNING));
      GetApplicationsResponse response = hadoopCluster.getYarnCluster().getResourceManager().getClientRMService().getApplications(request);
      assertEquals(0, response.getApplicationList().size());
    } finally {
      interpreterSettingManager.close();
    }
  }

  @Test
  void testYarnMode() throws InterpreterException, YarnException {
    try {
      // test SparkSubmitInterpreterSetting
      Interpreter sparkSubmitInterpreter = interpreterFactory.getInterpreter("spark-submit", new ExecutionContext("user1", "note1", "test"));

      InterpreterContext context = new InterpreterContext.Builder().setNoteId("note1").setParagraphId("paragraph_1").build();
      String yarnAppName = "yarn_example";
      InterpreterResult interpreterResult =
              sparkSubmitInterpreter.interpret("--master yarn --deploy-mode cluster --class org.apache.spark.examples.SparkPi " +
                      "--conf spark.app.name=" + yarnAppName + " --conf spark.driver.memory=512m " +
                      "--conf spark.executor.memory=512m " +
                      sparkHome + "/examples/jars/spark-examples_2.12-" + DownloadUtils.DEFAULT_SPARK_VERSION + ".jar", context);
      assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code(), interpreterResult.toString());

      GetApplicationsRequest request = GetApplicationsRequest.newInstance(EnumSet.of(YarnApplicationState.FINISHED));
      GetApplicationsResponse response = hadoopCluster.getYarnCluster().getResourceManager().getClientRMService().getApplications(request);
      assertTrue(response.getApplicationList().size() >= 1);

      List<ApplicationReport> apps = response.getApplicationList().stream()
              .filter(app -> app.getName().equals(yarnAppName))
              .collect(Collectors.toList());
      assertEquals(1, apps.size());
    } finally {
      interpreterSettingManager.close();
    }
  }

  @Test
  void testCancelSparkYarnApp() throws InterpreterException, YarnException, TimeoutException, InterruptedException {
    try {
      // test SparkSubmitInterpreterSetting
      Interpreter sparkSubmitInterpreter = interpreterFactory.getInterpreter("spark-submit", new ExecutionContext("user1", "note1", "test"));
      InterpreterContext context = new InterpreterContext.Builder().setNoteId("note1").setParagraphId("paragraph_1").build();

      final Waiter waiter = new Waiter();
      Thread thread = new Thread() {
        @Override
        public void run() {
          try {
            String yarnAppName = "yarn_cancel_example";
            InterpreterResult interpreterResult =
                    sparkSubmitInterpreter.interpret("--master yarn  --deploy-mode cluster --class org.apache.spark.examples.SparkPi " +
                            "--conf spark.app.name=" + yarnAppName + " --conf spark.driver.memory=512m " +
                            "--conf spark.executor.memory=512m " +
                            sparkHome + "/examples/jars/spark-examples_2.12-" + DownloadUtils.DEFAULT_SPARK_VERSION + ".jar", context);
            assertEquals(InterpreterResult.Code.INCOMPLETE, interpreterResult.code(), interpreterResult.toString());
            assertTrue(interpreterResult.toString().contains("Paragraph received a SIGTERM"), interpreterResult.toString());
          } catch (InterpreterException e) {
            waiter.fail("Should not throw exception\n" + ExceptionUtils.getStackTrace(e));
          }
          waiter.resume();
        }
      };
      thread.start();

      long start = System.currentTimeMillis();
      long threshold = 120 * 1000;
      while ((System.currentTimeMillis() - start) < threshold) {
        GetApplicationsRequest request = GetApplicationsRequest.newInstance(EnumSet.of(YarnApplicationState.RUNNING));
        GetApplicationsResponse response = hadoopCluster.getYarnCluster().getResourceManager().getClientRMService().getApplications(request);
        if (response.getApplicationList().size() >= 1) {
          break;
        }
        Thread.sleep(5 * 1000);
      }

      sparkSubmitInterpreter.cancel(context);
      waiter.await(10000);

    } finally {
      interpreterSettingManager.close();
    }
  }
}
