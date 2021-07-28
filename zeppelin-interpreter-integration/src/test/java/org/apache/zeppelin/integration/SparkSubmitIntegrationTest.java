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
import org.apache.zeppelin.interpreter.ExecutionContext;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.integration.DownloadUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SparkSubmitIntegrationTest {

  private static Logger LOGGER = LoggerFactory.getLogger(SparkSubmitIntegrationTest.class);

  private static MiniHadoopCluster hadoopCluster;
  private static MiniZeppelin zeppelin;
  private static InterpreterFactory interpreterFactory;
  private static InterpreterSettingManager interpreterSettingManager;

  private static String sparkHome;

  @BeforeClass
  public static void setUp() throws IOException {
    String sparkVersion = "2.4.7";
    String hadoopVersion = "2.7";
    LOGGER.info("Testing Spark Version: " + sparkVersion);
    LOGGER.info("Testing Hadoop Version: " + hadoopVersion);
    sparkHome = DownloadUtils.downloadSpark(sparkVersion, hadoopVersion);

    hadoopCluster = new MiniHadoopCluster();
    hadoopCluster.start();

    zeppelin = new MiniZeppelin();
    zeppelin.start(SparkIntegrationTest.class);
    interpreterFactory = zeppelin.getInterpreterFactory();
    interpreterSettingManager = zeppelin.getInterpreterSettingManager();

    InterpreterSetting sparkSubmitInterpreterSetting =
            interpreterSettingManager.getInterpreterSettingByName("spark-submit");
    sparkSubmitInterpreterSetting.setProperty("SPARK_HOME", sparkHome);
    sparkSubmitInterpreterSetting.setProperty("HADOOP_CONF_DIR", hadoopCluster.getConfigPath());
    sparkSubmitInterpreterSetting.setProperty("YARN_CONF_DIR", hadoopCluster.getConfigPath());
  }

  @AfterClass
  public static void tearDown() throws IOException {
    if (zeppelin != null) {
      zeppelin.stop();
    }
    if (hadoopCluster != null) {
      hadoopCluster.stop();
    }
  }

  @Test
  public void testLocalMode() throws InterpreterException, YarnException {
    try {
      // test SparkSubmitInterpreterSetting
      Interpreter sparkSubmitInterpreter = interpreterFactory.getInterpreter("spark-submit", new ExecutionContext("user1", "note1", "test"));

      InterpreterContext context = new InterpreterContext.Builder().setNoteId("note1").setParagraphId("paragraph_1").build();
      InterpreterResult interpreterResult =
              sparkSubmitInterpreter.interpret("--master yarn-cluster --class org.apache.spark.examples.SparkPi " +
              sparkHome + "/examples/jars/spark-examples_2.11-2.4.7.jar", context);
      assertEquals(interpreterResult.toString(), InterpreterResult.Code.SUCCESS, interpreterResult.code());

      // no yarn application launched
      GetApplicationsRequest request = GetApplicationsRequest.newInstance(EnumSet.of(YarnApplicationState.RUNNING));
      GetApplicationsResponse response = hadoopCluster.getYarnCluster().getResourceManager().getClientRMService().getApplications(request);
      assertEquals(0, response.getApplicationList().size());
    } finally {
      interpreterSettingManager.close();
    }
  }

  @Test
  public void testYarnMode() throws InterpreterException, YarnException {
    try {
      // test SparkSubmitInterpreterSetting
      Interpreter sparkSubmitInterpreter = interpreterFactory.getInterpreter("spark-submit", new ExecutionContext("user1", "note1", "test"));

      InterpreterContext context = new InterpreterContext.Builder().setNoteId("note1").setParagraphId("paragraph_1").build();
      String yarnAppName = "yarn_example";
      InterpreterResult interpreterResult =
              sparkSubmitInterpreter.interpret("--master yarn-cluster --class org.apache.spark.examples.SparkPi " +
                      "--conf spark.app.name=" + yarnAppName + " --conf spark.driver.memory=512m " +
                      "--conf spark.executor.memory=512m " +
                      sparkHome + "/examples/jars/spark-examples_2.11-2.4.7.jar", context);
      assertEquals(interpreterResult.toString(), InterpreterResult.Code.SUCCESS, interpreterResult.code());

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
  public void testCancelSparkYarnApp() throws InterpreterException, YarnException, TimeoutException, InterruptedException {
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
                    sparkSubmitInterpreter.interpret("--master yarn-cluster --class org.apache.spark.examples.SparkPi " +
                            "--conf spark.app.name=" + yarnAppName + " --conf spark.driver.memory=512m " +
                            "--conf spark.executor.memory=512m " +
                            sparkHome + "/examples/jars/spark-examples_2.11-2.4.7.jar", context);
            assertEquals(interpreterResult.toString(), InterpreterResult.Code.INCOMPLETE, interpreterResult.code());
            assertTrue(interpreterResult.toString(), interpreterResult.toString().contains("Paragraph received a SIGTERM"));
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
