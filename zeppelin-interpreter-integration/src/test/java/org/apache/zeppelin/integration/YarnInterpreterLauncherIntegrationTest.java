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

import com.google.common.collect.Lists;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationsRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationsResponse;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.zeppelin.dep.Dependency;
import org.apache.zeppelin.interpreter.ExecutionContext;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class YarnInterpreterLauncherIntegrationTest {

  private static Logger LOGGER = LoggerFactory.getLogger(YarnInterpreterLauncherIntegrationTest.class);

  private static MiniHadoopCluster hadoopCluster;
  private static MiniZeppelin zeppelin;
  private static InterpreterFactory interpreterFactory;
  private static InterpreterSettingManager interpreterSettingManager;

  private String hadoopHome;

  @BeforeClass
  public static void setUp() throws IOException {
    Configuration conf = new Configuration();
    hadoopCluster = new MiniHadoopCluster(conf);
    hadoopCluster.start();

    zeppelin = new MiniZeppelin();
    zeppelin.start(YarnInterpreterLauncherIntegrationTest.class);
    interpreterFactory = zeppelin.getInterpreterFactory();
    interpreterSettingManager = zeppelin.getInterpreterSettingManager();
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
  public void testLaunchShellInYarn() throws YarnException, InterpreterException, InterruptedException {
    InterpreterSetting shellInterpreterSetting = interpreterSettingManager.getInterpreterSettingByName("sh");
    shellInterpreterSetting.setProperty("zeppelin.interpreter.launcher", "yarn");
    shellInterpreterSetting.setProperty("HADOOP_CONF_DIR", hadoopCluster.getConfigPath());

    Interpreter shellInterpreter = interpreterFactory.getInterpreter("sh", new ExecutionContext("user1", "note1", "sh"));

    InterpreterContext context = new InterpreterContext.Builder().setNoteId("note1").setParagraphId("paragraph_1").build();
    InterpreterResult interpreterResult = shellInterpreter.interpret("pwd", context);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertTrue(interpreterResult.toString(), interpreterResult.message().get(0).getData().contains("/usercache/"));

    Thread.sleep(1000);
    // 1 yarn application launched
    GetApplicationsRequest request = GetApplicationsRequest.newInstance(EnumSet.of(YarnApplicationState.RUNNING));
    GetApplicationsResponse response = hadoopCluster.getYarnCluster().getResourceManager().getClientRMService().getApplications(request);
    assertEquals(1, response.getApplicationList().size());

    interpreterSettingManager.close();
  }

  @Test
  public void testJdbcPython_YarnLauncher() throws InterpreterException, YarnException, InterruptedException {
    InterpreterSetting jdbcInterpreterSetting = interpreterSettingManager.getInterpreterSettingByName("jdbc");
    jdbcInterpreterSetting.setProperty("default.driver", "com.mysql.jdbc.Driver");
    jdbcInterpreterSetting.setProperty("default.url", "jdbc:mysql://localhost:3306/");
    jdbcInterpreterSetting.setProperty("default.user", "root");
    jdbcInterpreterSetting.setProperty("zeppelin.interpreter.launcher", "yarn");
    jdbcInterpreterSetting.setProperty("zeppelin.interpreter.yarn.resource.memory", "512");
    jdbcInterpreterSetting.setProperty("HADOOP_CONF_DIR", hadoopCluster.getConfigPath());

    Dependency dependency = new Dependency("mysql:mysql-connector-java:5.1.46");
    jdbcInterpreterSetting.setDependencies(Lists.newArrayList(dependency));
    interpreterSettingManager.restart(jdbcInterpreterSetting.getId());
    jdbcInterpreterSetting.waitForReady(60 * 1000);

    InterpreterSetting pythonInterpreterSetting = interpreterSettingManager.getInterpreterSettingByName("python");
    pythonInterpreterSetting.setProperty("zeppelin.interpreter.launcher", "yarn");
    pythonInterpreterSetting.setProperty("zeppelin.interpreter.yarn.resource.memory", "512");
    pythonInterpreterSetting.setProperty("HADOOP_CONF_DIR", hadoopCluster.getConfigPath());

    Interpreter jdbcInterpreter = interpreterFactory.getInterpreter("jdbc", new ExecutionContext("user1", "note1", "test"));
    assertNotNull("JdbcInterpreter is null", jdbcInterpreter);

    InterpreterContext context = new InterpreterContext.Builder()
            .setNoteId("note1")
            .setParagraphId("paragraph_1")
            .setAuthenticationInfo(AuthenticationInfo.ANONYMOUS)
            .build();
    InterpreterResult interpreterResult = jdbcInterpreter.interpret("show databases;", context);
    assertEquals(interpreterResult.toString(), InterpreterResult.Code.SUCCESS, interpreterResult.code());

    context.getLocalProperties().put("saveAs", "table_1");
    interpreterResult = jdbcInterpreter.interpret("SELECT 1 as c1, 2 as c2;", context);
    assertEquals(interpreterResult.toString(), InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(1, interpreterResult.message().size());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
    assertEquals("c1\tc2\n1\t2\n", interpreterResult.message().get(0).getData());

    // read table_1 from python interpreter
    Interpreter pythonInterpreter = interpreterFactory.getInterpreter("python", new ExecutionContext("user1", "note1", "test"));
    assertNotNull("PythonInterpreter is null", pythonInterpreter);

    context = new InterpreterContext.Builder()
            .setNoteId("note1")
            .setParagraphId("paragraph_1")
            .setAuthenticationInfo(AuthenticationInfo.ANONYMOUS)
            .build();
    interpreterResult = pythonInterpreter.interpret("df=z.getAsDataFrame('table_1')\nz.show(df)", context);
    assertEquals(interpreterResult.toString(), InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(1, interpreterResult.message().size());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
    assertEquals("c1\tc2\n1\t2\n", interpreterResult.message().get(0).getData());

    // 2 yarn application launched
    GetApplicationsRequest request = GetApplicationsRequest.newInstance(EnumSet.of(YarnApplicationState.RUNNING));
    GetApplicationsResponse response = hadoopCluster.getYarnCluster().getResourceManager().getClientRMService().getApplications(request);
    assertEquals(2, response.getApplicationList().size());

    interpreterSettingManager.close();

    // sleep for 5 seconds to make sure yarn apps are finished
    Thread.sleep(5* 1000);
    request = GetApplicationsRequest.newInstance(EnumSet.of(YarnApplicationState.RUNNING));
    response = hadoopCluster.getYarnCluster().getResourceManager().getClientRMService().getApplications(request);
    assertEquals(0, response.getApplicationList().size());
  }
}
