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

import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.integration.DownloadUtils;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterManagedProcess;
import org.apache.zeppelin.util.Util;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SparkInterpreterLauncherTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(SparkInterpreterLauncher.class);

  private String sparkHome;
  private String zeppelinHome;

  @Before
  public void setUp() {
    for (final ZeppelinConfiguration.ConfVars confVar : ZeppelinConfiguration.ConfVars.values()) {
      System.clearProperty(confVar.getVarName());
    }

    sparkHome = DownloadUtils.downloadSpark("2.3.2");
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME.getVarName(),
            new File("..").getAbsolutePath());

    zeppelinHome = ZeppelinConfiguration.create().getZeppelinHome();
    LOGGER.info("ZEPPELIN_HOME: " + zeppelinHome);
  }

  @Test
  public void testConnectTimeOut() throws IOException {
    ZeppelinConfiguration zConf = new ZeppelinConfiguration();
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("SPARK_HOME", sparkHome);
    properties.setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT.getVarName(), "10000");
    InterpreterOption option = new InterpreterOption();
    option.setUserImpersonate(true);
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null, "user1", "intpGroupId", "groupId", "groupName", "name", 0, "host");
    InterpreterClient client = launcher.launch(context);
    assertTrue( client instanceof RemoteInterpreterManagedProcess);
    RemoteInterpreterManagedProcess interpreterProcess = (RemoteInterpreterManagedProcess) client;
    assertEquals("name", interpreterProcess.getInterpreterSettingName());
    assertEquals(zeppelinHome + "/interpreter/groupName", interpreterProcess.getInterpreterDir());
    assertEquals(zeppelinHome + "/local-repo/groupId", interpreterProcess.getLocalRepoDir());
    assertEquals(10000, interpreterProcess.getConnectTimeout());
    assertEquals(zConf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
    assertTrue(interpreterProcess.getEnv().size() >= 2);
    assertEquals(true, interpreterProcess.isUserImpersonated());
  }

  @Test
  public void testLocalMode() throws IOException {
    ZeppelinConfiguration zConf = new ZeppelinConfiguration();
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("SPARK_HOME", sparkHome);
    properties.setProperty("property_1", "value_1");
    properties.setProperty("master", "local[*]");
    properties.setProperty("spark.files", "file_1");
    properties.setProperty("spark.jars", "jar_1");

    InterpreterOption option = new InterpreterOption();
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null, "user1", "intpGroupId", "groupId", "spark", "spark", 0, "host");
    InterpreterClient client = launcher.launch(context);
    assertTrue( client instanceof RemoteInterpreterManagedProcess);
    RemoteInterpreterManagedProcess interpreterProcess = (RemoteInterpreterManagedProcess) client;
    assertEquals("spark", interpreterProcess.getInterpreterSettingName());
    assertTrue(interpreterProcess.getInterpreterDir().endsWith("/interpreter/spark"));
    assertTrue(interpreterProcess.getLocalRepoDir().endsWith("/local-repo/groupId"));
    assertEquals(zConf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
    assertTrue(interpreterProcess.getEnv().size() >= 2);
    assertEquals(sparkHome, interpreterProcess.getEnv().get("SPARK_HOME"));
    assertEquals(InterpreterLauncher.escapeSpecialCharacter(" --master local[*] --conf spark.files=file_1 --conf spark.jars=jar_1"),
            interpreterProcess.getEnv().get("ZEPPELIN_SPARK_CONF"));
  }

  @Test
  public void testYarnClientMode_1() throws IOException {
    ZeppelinConfiguration zConf = new ZeppelinConfiguration();
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("SPARK_HOME", sparkHome);
    properties.setProperty("property_1", "value_1");
    properties.setProperty("master", "yarn-client");
    properties.setProperty("spark.files", "file_1");
    properties.setProperty("spark.jars", "jar_1");

    InterpreterOption option = new InterpreterOption();
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null, "user1", "intpGroupId", "groupId", "spark", "spark", 0, "host");
    InterpreterClient client = launcher.launch(context);
    assertTrue( client instanceof RemoteInterpreterManagedProcess);
    RemoteInterpreterManagedProcess interpreterProcess = (RemoteInterpreterManagedProcess) client;
    assertEquals("spark", interpreterProcess.getInterpreterSettingName());
    assertTrue(interpreterProcess.getInterpreterDir().endsWith("/interpreter/spark"));
    assertTrue(interpreterProcess.getLocalRepoDir().endsWith("/local-repo/groupId"));
    assertEquals(zConf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
    assertTrue(interpreterProcess.getEnv().size() >= 2);
    assertEquals(sparkHome, interpreterProcess.getEnv().get("SPARK_HOME"));

    String sparkJars = "jar_1";
    String sparkrZip = sparkHome + "/R/lib/sparkr.zip#sparkr";
    String sparkFiles = "file_1";
    assertEquals(InterpreterLauncher.escapeSpecialCharacter(" --master yarn-client --conf spark.yarn.dist.archives=" + sparkrZip +
                    " --conf spark.files=" + sparkFiles + " --conf spark.jars=" + sparkJars +
                    " --conf spark.yarn.isPython=true"),
            interpreterProcess.getEnv().get("ZEPPELIN_SPARK_CONF"));
  }

  @Test
  public void testYarnClientMode_2() throws IOException {
    ZeppelinConfiguration zConf = new ZeppelinConfiguration();
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("SPARK_HOME", sparkHome);
    properties.setProperty("property_1", "value_1");
    properties.setProperty("master", "yarn");
    properties.setProperty("spark.submit.deployMode", "client");
    properties.setProperty("spark.files", "file_1");
    properties.setProperty("spark.jars", "jar_1");

    InterpreterOption option = new InterpreterOption();
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null, "user1", "intpGroupId", "groupId", "spark", "spark", 0, "host");
    InterpreterClient client = launcher.launch(context);
    assertTrue( client instanceof RemoteInterpreterManagedProcess);
    RemoteInterpreterManagedProcess interpreterProcess = (RemoteInterpreterManagedProcess) client;
    assertEquals("spark", interpreterProcess.getInterpreterSettingName());
    assertTrue(interpreterProcess.getInterpreterDir().endsWith("/interpreter/spark"));
    assertTrue(interpreterProcess.getLocalRepoDir().endsWith("/local-repo/groupId"));
    assertEquals(zConf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
    assertTrue(interpreterProcess.getEnv().size() >= 2);
    assertEquals(sparkHome, interpreterProcess.getEnv().get("SPARK_HOME"));

    String sparkJars = "jar_1";
    String sparkrZip = sparkHome + "/R/lib/sparkr.zip#sparkr";
    String sparkFiles = "file_1";
    assertEquals(InterpreterLauncher.escapeSpecialCharacter(" --master yarn --conf spark.yarn.dist.archives=" + sparkrZip +
                    " --conf spark.files=" + sparkFiles + " --conf spark.jars=" + sparkJars +
                    " --conf spark.submit.deployMode=client" +
                    " --conf spark.yarn.isPython=true"),
            interpreterProcess.getEnv().get("ZEPPELIN_SPARK_CONF"));
  }

  @Test
  public void testYarnClusterMode_1() throws IOException {
    ZeppelinConfiguration zConf = new ZeppelinConfiguration();
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("SPARK_HOME", sparkHome);
    properties.setProperty("property_1", "value_1");
    properties.setProperty("master", "yarn-cluster");
    properties.setProperty("spark.files", "file_1");
    properties.setProperty("spark.jars", "jar_1");

    InterpreterOption option = new InterpreterOption();
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null, "user1", "intpGroupId", "groupId", "spark", "spark", 0, "host");
    InterpreterClient client = launcher.launch(context);
    assertTrue( client instanceof RemoteInterpreterManagedProcess);
    RemoteInterpreterManagedProcess interpreterProcess = (RemoteInterpreterManagedProcess) client;
    assertEquals("spark", interpreterProcess.getInterpreterSettingName());
    assertTrue(interpreterProcess.getInterpreterDir().endsWith("/interpreter/spark"));
    assertTrue(interpreterProcess.getLocalRepoDir().endsWith("/local-repo/groupId"));
    assertEquals(zConf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
    assertTrue(interpreterProcess.getEnv().size() >= 3);
    assertEquals(sparkHome, interpreterProcess.getEnv().get("SPARK_HOME"));

    assertEquals("true", interpreterProcess.getEnv().get("ZEPPELIN_SPARK_YARN_CLUSTER"));
    String sparkJars = "jar_1," +
            zeppelinHome + "/interpreter/spark/scala-2.11/spark-scala-2.11-" + Util.getVersion() + ".jar";
    String sparkrZip = sparkHome + "/R/lib/sparkr.zip#sparkr";
    String sparkFiles = "file_1," + zeppelinHome + "/conf/log4j_yarn_cluster.properties";
    assertEquals(InterpreterLauncher.escapeSpecialCharacter(" --master yarn-cluster --conf spark.yarn.dist.archives=" + sparkrZip +
                    " --conf spark.yarn.maxAppAttempts=1" +
                    " --conf spark.files=" + sparkFiles + " --conf spark.jars=" + sparkJars +
                    " --conf spark.yarn.isPython=true" +
                    " --conf spark.yarn.submit.waitAppCompletion=false"),
            interpreterProcess.getEnv().get("ZEPPELIN_SPARK_CONF"));
  }

  @Test
  public void testYarnClusterMode_2() throws IOException {
    ZeppelinConfiguration zConf = new ZeppelinConfiguration();
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("SPARK_HOME", sparkHome);
    properties.setProperty("property_1", "value_1");
    properties.setProperty("master", "yarn");
    properties.setProperty("spark.submit.deployMode", "cluster");
    properties.setProperty("spark.files", "file_1");
    properties.setProperty("spark.jars", "jar_1");

    InterpreterOption option = new InterpreterOption();
    option.setUserImpersonate(true);
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null, "user1", "intpGroupId", "groupId", "spark", "spark", 0, "host");
    Path localRepoPath = Paths.get(zConf.getInterpreterLocalRepoPath(), context.getInterpreterSettingId());
    FileUtils.deleteDirectory(localRepoPath.toFile());
    Files.createDirectories(localRepoPath);
    Files.createFile(Paths.get(localRepoPath.toAbsolutePath().toString(), "test.jar"));

    InterpreterClient client = launcher.launch(context);
    assertTrue( client instanceof RemoteInterpreterManagedProcess);
    RemoteInterpreterManagedProcess interpreterProcess = (RemoteInterpreterManagedProcess) client;
    assertEquals("spark", interpreterProcess.getInterpreterSettingName());
    assertTrue(interpreterProcess.getInterpreterDir().endsWith("/interpreter/spark"));
    assertTrue(interpreterProcess.getLocalRepoDir().endsWith("/local-repo/groupId"));
    assertEquals(zConf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
    assertTrue(interpreterProcess.getEnv().size() >= 3);
    assertEquals(sparkHome, interpreterProcess.getEnv().get("SPARK_HOME"));
    assertEquals("true", interpreterProcess.getEnv().get("ZEPPELIN_SPARK_YARN_CLUSTER"));
    String sparkJars = "jar_1," +
            Paths.get(localRepoPath.toAbsolutePath().toString(), "test.jar").toString() + "," +
            zeppelinHome + "/interpreter/spark/scala-2.11/spark-scala-2.11-" + Util.getVersion() + ".jar";
    String sparkrZip = sparkHome + "/R/lib/sparkr.zip#sparkr";
    String sparkFiles = "file_1," + zeppelinHome + "/conf/log4j_yarn_cluster.properties";
    assertEquals(InterpreterLauncher.escapeSpecialCharacter(" --master yarn --conf spark.yarn.dist.archives=" + sparkrZip +
            " --conf spark.yarn.maxAppAttempts=1" +
            " --conf spark.files=" + sparkFiles + " --conf spark.jars=" + sparkJars +
            " --conf spark.submit.deployMode=cluster --conf spark.yarn.isPython=true" +
            " --conf spark.yarn.submit.waitAppCompletion=false --proxy-user user1"),
            interpreterProcess.getEnv().get("ZEPPELIN_SPARK_CONF"));
    Files.deleteIfExists(Paths.get(localRepoPath.toAbsolutePath().toString(), "test.jar"));
    FileUtils.deleteDirectory(localRepoPath.toFile());
  }

  @Test
  public void testYarnClusterMode_3() throws IOException {
    ZeppelinConfiguration zConf = new ZeppelinConfiguration();
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("SPARK_HOME", sparkHome);
    properties.setProperty("property_1", "value_1");
    properties.setProperty("master", "yarn");
    properties.setProperty("spark.submit.deployMode", "cluster");
    properties.setProperty("spark.files", "{}");
    properties.setProperty("spark.jars", "jar_1");

    InterpreterOption option = new InterpreterOption();
    option.setUserImpersonate(true);
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null, "user1", "intpGroupId", "groupId", "spark", "spark", 0, "host");
    Path localRepoPath = Paths.get(zConf.getInterpreterLocalRepoPath(), context.getInterpreterSettingId());
    FileUtils.deleteDirectory(localRepoPath.toFile());
    Files.createDirectories(localRepoPath);

    InterpreterClient client = launcher.launch(context);
    assertTrue( client instanceof RemoteInterpreterManagedProcess);
    RemoteInterpreterManagedProcess interpreterProcess = (RemoteInterpreterManagedProcess) client;
    assertEquals("spark", interpreterProcess.getInterpreterSettingName());
    assertTrue(interpreterProcess.getInterpreterDir().endsWith("/interpreter/spark"));
    assertTrue(interpreterProcess.getLocalRepoDir().endsWith("/local-repo/groupId"));
    assertEquals(zConf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
    assertTrue(interpreterProcess.getEnv().size() >= 3);
    assertEquals(sparkHome, interpreterProcess.getEnv().get("SPARK_HOME"));
    assertEquals("true", interpreterProcess.getEnv().get("ZEPPELIN_SPARK_YARN_CLUSTER"));

    String sparkJars = "jar_1," +
            zeppelinHome + "/interpreter/spark/scala-2.11/spark-scala-2.11-" + Util.getVersion() + ".jar";
    String sparkrZip = sparkHome + "/R/lib/sparkr.zip#sparkr";
    // escape special characters
    String sparkFiles = "{}," + zeppelinHome + "/conf/log4j_yarn_cluster.properties";
    assertEquals(InterpreterLauncher.escapeSpecialCharacter(" --master yarn --conf spark.yarn.dist.archives=" + sparkrZip +
                    " --conf spark.yarn.maxAppAttempts=1" +
                    " --conf spark.files=" + sparkFiles + " --conf spark.jars=" + sparkJars +
                    " --conf spark.submit.deployMode=cluster --conf spark.yarn.isPython=true" +
                    " --conf spark.yarn.submit.waitAppCompletion=false --proxy-user user1"),
            interpreterProcess.getEnv().get("ZEPPELIN_SPARK_CONF"));
    FileUtils.deleteDirectory(localRepoPath.toFile());
  }
}
