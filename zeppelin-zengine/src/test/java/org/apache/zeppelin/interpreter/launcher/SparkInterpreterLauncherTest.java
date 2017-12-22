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

package org.apache.zeppelin.interpreter.launcher;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterManagedProcess;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SparkInterpreterLauncherTest {

  @Test
  public void testLocalMode() throws IOException {
    ZeppelinConfiguration zConf = new ZeppelinConfiguration();
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("SPARK_HOME", "/user/spark");
    properties.setProperty("property_1", "value_1");
    properties.setProperty("master", "local[*]");
    properties.setProperty("spark.files", "file_1");
    properties.setProperty("spark.jars", "jar_1");

    InterpreterOption option = new InterpreterOption();
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null, "user1", "intpGroupId", "groupId", "spark", "spark");
    InterpreterClient client = launcher.launch(context);
    assertTrue( client instanceof RemoteInterpreterManagedProcess);
    RemoteInterpreterManagedProcess interpreterProcess = (RemoteInterpreterManagedProcess) client;
    assertEquals("spark", interpreterProcess.getInterpreterSettingName());
    assertEquals(".//interpreter/spark", interpreterProcess.getInterpreterDir());
    assertEquals(".//local-repo/groupId", interpreterProcess.getLocalRepoDir());
    assertEquals(zConf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
    assertEquals(2, interpreterProcess.getEnv().size());
    assertEquals("/user/spark", interpreterProcess.getEnv().get("SPARK_HOME"));
    assertEquals(" --master local[*] --conf spark.files='file_1' --conf spark.jars='jar_1'", interpreterProcess.getEnv().get("ZEPPELIN_SPARK_CONF"));
  }

  @Test
  public void testYarnClientMode_1() throws IOException {
    ZeppelinConfiguration zConf = new ZeppelinConfiguration();
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("SPARK_HOME", "/user/spark");
    properties.setProperty("property_1", "value_1");
    properties.setProperty("master", "yarn-client");
    properties.setProperty("spark.files", "file_1");
    properties.setProperty("spark.jars", "jar_1");

    InterpreterOption option = new InterpreterOption();
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null, "user1", "intpGroupId", "groupId", "spark", "spark");
    InterpreterClient client = launcher.launch(context);
    assertTrue( client instanceof RemoteInterpreterManagedProcess);
    RemoteInterpreterManagedProcess interpreterProcess = (RemoteInterpreterManagedProcess) client;
    assertEquals("spark", interpreterProcess.getInterpreterSettingName());
    assertEquals(".//interpreter/spark", interpreterProcess.getInterpreterDir());
    assertEquals(".//local-repo/groupId", interpreterProcess.getLocalRepoDir());
    assertEquals(zConf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
    assertEquals(2, interpreterProcess.getEnv().size());
    assertEquals("/user/spark", interpreterProcess.getEnv().get("SPARK_HOME"));
    assertEquals(" --master yarn-client --conf spark.files='file_1' --conf spark.jars='jar_1' --conf spark.yarn.isPython=true", interpreterProcess.getEnv().get("ZEPPELIN_SPARK_CONF"));
  }

  @Test
  public void testYarnClientMode_2() throws IOException {
    ZeppelinConfiguration zConf = new ZeppelinConfiguration();
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("SPARK_HOME", "/user/spark");
    properties.setProperty("property_1", "value_1");
    properties.setProperty("master", "yarn");
    properties.setProperty("spark.submit.deployMode", "client");
    properties.setProperty("spark.files", "file_1");
    properties.setProperty("spark.jars", "jar_1");

    InterpreterOption option = new InterpreterOption();
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null, "user1", "intpGroupId", "groupId", "spark", "spark");
    InterpreterClient client = launcher.launch(context);
    assertTrue( client instanceof RemoteInterpreterManagedProcess);
    RemoteInterpreterManagedProcess interpreterProcess = (RemoteInterpreterManagedProcess) client;
    assertEquals("spark", interpreterProcess.getInterpreterSettingName());
    assertEquals(".//interpreter/spark", interpreterProcess.getInterpreterDir());
    assertEquals(".//local-repo/groupId", interpreterProcess.getLocalRepoDir());
    assertEquals(zConf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
    assertEquals(2, interpreterProcess.getEnv().size());
    assertEquals("/user/spark", interpreterProcess.getEnv().get("SPARK_HOME"));
    assertEquals(" --master yarn --conf spark.files='file_1' --conf spark.jars='jar_1' --conf spark.submit.deployMode='client' --conf spark.yarn.isPython=true", interpreterProcess.getEnv().get("ZEPPELIN_SPARK_CONF"));
  }

  @Test
  public void testYarnClusterMode_1() throws IOException {
    ZeppelinConfiguration zConf = new ZeppelinConfiguration();
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("SPARK_HOME", "/user/spark");
    properties.setProperty("property_1", "value_1");
    properties.setProperty("master", "yarn-cluster");
    properties.setProperty("spark.files", "file_1");
    properties.setProperty("spark.jars", "jar_1");

    InterpreterOption option = new InterpreterOption();
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null, "user1", "intpGroupId", "groupId", "spark", "spark");
    InterpreterClient client = launcher.launch(context);
    assertTrue( client instanceof RemoteInterpreterManagedProcess);
    RemoteInterpreterManagedProcess interpreterProcess = (RemoteInterpreterManagedProcess) client;
    assertEquals("spark", interpreterProcess.getInterpreterSettingName());
    assertEquals(".//interpreter/spark", interpreterProcess.getInterpreterDir());
    assertEquals(".//local-repo/groupId", interpreterProcess.getLocalRepoDir());
    assertEquals(zConf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
    assertEquals(3, interpreterProcess.getEnv().size());
    assertEquals("/user/spark", interpreterProcess.getEnv().get("SPARK_HOME"));
    assertEquals("true", interpreterProcess.getEnv().get("ZEPPELIN_SPARK_YARN_CLUSTER"));
    assertEquals(" --master yarn-cluster --files .//conf/log4j_yarn_cluster.properties --conf spark.files='file_1' --conf spark.jars='jar_1' --conf spark.yarn.isPython=true", interpreterProcess.getEnv().get("ZEPPELIN_SPARK_CONF"));
  }

  @Test
  public void testYarnClusterMode_2() throws IOException {
    ZeppelinConfiguration zConf = new ZeppelinConfiguration();
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("SPARK_HOME", "/user/spark");
    properties.setProperty("property_1", "value_1");
    properties.setProperty("master", "yarn");
    properties.setProperty("spark.submit.deployMode", "cluster");
    properties.setProperty("spark.files", "file_1");
    properties.setProperty("spark.jars", "jar_1");

    InterpreterOption option = new InterpreterOption();
    option.setUserImpersonate(true);
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null, "user1", "intpGroupId", "groupId", "spark", "spark");
    InterpreterClient client = launcher.launch(context);
    assertTrue( client instanceof RemoteInterpreterManagedProcess);
    RemoteInterpreterManagedProcess interpreterProcess = (RemoteInterpreterManagedProcess) client;
    assertEquals("spark", interpreterProcess.getInterpreterSettingName());
    assertEquals(".//interpreter/spark", interpreterProcess.getInterpreterDir());
    assertEquals(".//local-repo/groupId", interpreterProcess.getLocalRepoDir());
    assertEquals(zConf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
    assertEquals(3, interpreterProcess.getEnv().size());
    assertEquals("/user/spark", interpreterProcess.getEnv().get("SPARK_HOME"));
    assertEquals("true", interpreterProcess.getEnv().get("ZEPPELIN_SPARK_YARN_CLUSTER"));
    assertEquals(" --master yarn --files .//conf/log4j_yarn_cluster.properties --conf spark.files='file_1' --conf spark.jars='jar_1' --conf spark.submit.deployMode='cluster' --conf spark.yarn.isPython=true --proxy-user user1", interpreterProcess.getEnv().get("ZEPPELIN_SPARK_CONF"));
  }
}
