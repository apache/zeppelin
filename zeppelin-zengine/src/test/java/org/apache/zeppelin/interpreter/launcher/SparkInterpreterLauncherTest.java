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

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.test.DownloadUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.remote.ExecRemoteInterpreterProcess;
import org.apache.zeppelin.util.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class SparkInterpreterLauncherTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(SparkInterpreterLauncher.class);

  private String sparkHome;
  private String zeppelinHome;
  private ZeppelinConfiguration zConf;

  @BeforeEach
  public void setUp() {
    sparkHome = DownloadUtils.downloadSpark();
    zConf = ZeppelinConfiguration.load();
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME.getVarName(),
        new File("..").getAbsolutePath());
    zeppelinHome = zConf.getZeppelinHome();
    LOGGER.info("ZEPPELIN_HOME: " + zeppelinHome);
  }

  @Test
  void testConnectTimeOut() throws IOException {
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("SPARK_HOME", sparkHome);
    properties.setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT.getVarName(), "10000");
    InterpreterOption option = new InterpreterOption();
    option.setUserImpersonate(true);
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null, "user1", "intpGroupId", "groupId", "groupName", "name", 0, "host");
    InterpreterClient client = launcher.launch(context);
    assertTrue(client instanceof ExecRemoteInterpreterProcess);
    try (ExecRemoteInterpreterProcess interpreterProcess = (ExecRemoteInterpreterProcess) client) {
      assertEquals("name", interpreterProcess.getInterpreterSettingName());
      assertEquals(zeppelinHome + "/interpreter/groupName", interpreterProcess.getInterpreterDir());
      assertEquals(zeppelinHome + "/local-repo/groupId", interpreterProcess.getLocalRepoDir());
      assertEquals(10000, interpreterProcess.getConnectTimeout());
      assertEquals(zConf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
      assertTrue(interpreterProcess.getEnv().size() >= 2);
      assertEquals(true, interpreterProcess.isUserImpersonated());
    }
  }

  @Test
  void testLocalMode() throws IOException {
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("SPARK_HOME", sparkHome);
    properties.setProperty("ENV_1", "");
    properties.setProperty("property_1", "value_1");
    properties.setProperty("spark.master", "local[*]");
    properties.setProperty("spark.files", "file_1");
    properties.setProperty("spark.jars", "jar_1");

    InterpreterOption option = new InterpreterOption();
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null, "user1", "intpGroupId", "groupId", "spark", "spark", 0, "host");
    InterpreterClient client = launcher.launch(context);
    assertTrue( client instanceof ExecRemoteInterpreterProcess);
    try (ExecRemoteInterpreterProcess interpreterProcess = (ExecRemoteInterpreterProcess) client) {
      assertEquals("spark", interpreterProcess.getInterpreterSettingName());
      assertTrue(interpreterProcess.getInterpreterDir().endsWith("/interpreter/spark"));
      assertTrue(interpreterProcess.getLocalRepoDir().endsWith("/local-repo/groupId"));
      assertEquals(zConf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
      assertTrue(interpreterProcess.getEnv().size() >= 2);
      assertEquals(sparkHome, interpreterProcess.getEnv().get("SPARK_HOME"));
      assertFalse(interpreterProcess.getEnv().containsKey("ENV_1"));
      String expected = "--conf|spark.files=file_1" +
        "|--conf|spark.jars=jar_1|--conf|spark.app.name=intpGroupId|--conf|spark.master=local[*]";
      assertTrue(CollectionUtils.isEqualCollection(Arrays.asList(expected.split("\\|")),
        Arrays.asList(interpreterProcess.getEnv().get("ZEPPELIN_SPARK_CONF").split("\\|"))));
    }
  }

  @Test
  void testYarnClientMode_1() throws IOException {
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("SPARK_HOME", sparkHome);
    properties.setProperty("property_1", "value_1");
    properties.setProperty("spark.master", "yarn-client");
    properties.setProperty("spark.files", "file_1");
    properties.setProperty("spark.jars", "jar_1");

    InterpreterOption option = new InterpreterOption();
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null, "user1", "intpGroupId", "groupId", "spark", "spark", 0, "host");
    InterpreterClient client = launcher.launch(context);
    assertTrue( client instanceof ExecRemoteInterpreterProcess);
    try (ExecRemoteInterpreterProcess interpreterProcess = (ExecRemoteInterpreterProcess) client) {
      assertEquals("spark", interpreterProcess.getInterpreterSettingName());
      assertTrue(interpreterProcess.getInterpreterDir().endsWith("/interpreter/spark"));
      assertTrue(interpreterProcess.getLocalRepoDir().endsWith("/local-repo/groupId"));
      assertEquals(zConf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
      assertTrue(interpreterProcess.getEnv().size() >= 2);
      assertEquals(sparkHome, interpreterProcess.getEnv().get("SPARK_HOME"));

      String sparkJars = "jar_1";
      String sparkrZip = sparkHome + "/R/lib/sparkr.zip#sparkr";
      String sparkFiles = "file_1";
      String expected = "--conf|spark.yarn.dist.archives=" + sparkrZip +
        "|--conf|spark.files=" + sparkFiles + "|--conf|spark.jars=" + sparkJars +
        "|--conf|spark.yarn.isPython=true|--conf|spark.app.name=intpGroupId|--conf|spark.master=yarn-client";
      assertTrue(CollectionUtils.isEqualCollection(Arrays.asList(expected.split("\\|")),
        Arrays.asList(interpreterProcess.getEnv().get("ZEPPELIN_SPARK_CONF").split("\\|"))));
    }
  }

  @Test
  void testYarnClientMode_2() throws IOException {
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("SPARK_HOME", sparkHome);
    properties.setProperty("property_1", "value_1");
    properties.setProperty("spark.master", "yarn");
    properties.setProperty("spark.submit.deployMode", "client");
    properties.setProperty("spark.files", "file_1");
    properties.setProperty("spark.jars", "jar_1");

    InterpreterOption option = new InterpreterOption();
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null, "user1", "intpGroupId", "groupId", "spark", "spark", 0, "host");
    InterpreterClient client = launcher.launch(context);
    assertTrue( client instanceof ExecRemoteInterpreterProcess);
    try (ExecRemoteInterpreterProcess interpreterProcess = (ExecRemoteInterpreterProcess) client) {
      assertEquals("spark", interpreterProcess.getInterpreterSettingName());
      assertTrue(interpreterProcess.getInterpreterDir().endsWith("/interpreter/spark"));
      assertTrue(interpreterProcess.getLocalRepoDir().endsWith("/local-repo/groupId"));
      assertEquals(zConf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
      assertTrue(interpreterProcess.getEnv().size() >= 2);
      assertEquals(sparkHome, interpreterProcess.getEnv().get("SPARK_HOME"));

      String sparkJars = "jar_1";
      String sparkrZip = sparkHome + "/R/lib/sparkr.zip#sparkr";
      String sparkFiles = "file_1";
      String expected = "--conf|spark.yarn.dist.archives=" + sparkrZip +
        "|--conf|spark.files=" + sparkFiles + "|--conf|spark.jars=" + sparkJars +
        "|--conf|spark.submit.deployMode=client" +
        "|--conf|spark.yarn.isPython=true|--conf|spark.app.name=intpGroupId|--conf|spark.master=yarn";
      assertTrue(CollectionUtils.isEqualCollection(Arrays.asList(expected.split("\\|")),
        Arrays.asList(interpreterProcess.getEnv().get("ZEPPELIN_SPARK_CONF").split("\\|"))));
    }
  }

  @Test
  void testYarnClusterMode_1() throws IOException {
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("SPARK_HOME", sparkHome);
    properties.setProperty("property_1", "value_1");
    properties.setProperty("spark.master", "yarn-cluster");
    properties.setProperty("spark.files", "file_1");
    properties.setProperty("spark.jars", "jar_1");

    InterpreterOption option = new InterpreterOption();
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null, "user1", "intpGroupId", "groupId", "spark", "spark", 0, "host");
    InterpreterClient client = launcher.launch(context);
    assertTrue( client instanceof ExecRemoteInterpreterProcess);
    try (ExecRemoteInterpreterProcess interpreterProcess = (ExecRemoteInterpreterProcess) client) {
      assertEquals("spark", interpreterProcess.getInterpreterSettingName());
      assertTrue(interpreterProcess.getInterpreterDir().endsWith("/interpreter/spark"));
      assertTrue(interpreterProcess.getLocalRepoDir().endsWith("/local-repo/groupId"));
      assertEquals(zConf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
      assertTrue(interpreterProcess.getEnv().size() >= 3);
      assertEquals(sparkHome, interpreterProcess.getEnv().get("SPARK_HOME"));

      assertEquals("true", interpreterProcess.getEnv().get("ZEPPELIN_SPARK_YARN_CLUSTER"));
      String sparkJars = "jar_1," +
        zeppelinHome + "/interpreter/spark/scala-2.12/spark-scala-2.12-" + Util.getVersion()
        + ".jar," +
              zeppelinHome + "/interpreter/zeppelin-interpreter-shaded-" + Util.getVersion() + ".jar";
      String sparkrZip = sparkHome + "/R/lib/sparkr.zip#sparkr";
      String sparkFiles = "file_1," + zeppelinHome + "/conf/log4j_yarn_cluster.properties";
      String expected = "--conf|spark.yarn.dist.archives=" + sparkrZip +
                      "|--conf|spark.yarn.maxAppAttempts=1" +
                      "|--conf|spark.files=" + sparkFiles +
                      "|--conf|spark.jars=" + sparkJars +
                      "|--conf|spark.yarn.isPython=true" +
                      "|--conf|spark.yarn.submit.waitAppCompletion=false" +
                      "|--conf|spark.app.name=intpGroupId" +
                      "|--conf|spark.master=yarn-cluster";
      assertTrue(CollectionUtils.isEqualCollection(Arrays.asList(expected.split("\\|")),
        Arrays.asList(interpreterProcess.getEnv().get("ZEPPELIN_SPARK_CONF").split("\\|"))));
    }
  }

  @Test
  void testYarnClusterMode_2() throws IOException {
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("SPARK_HOME", sparkHome);
    properties.setProperty("property_1", "value_1");
    properties.setProperty("spark.master", "yarn");
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
    assertTrue(client instanceof ExecRemoteInterpreterProcess);
    try (ExecRemoteInterpreterProcess interpreterProcess = (ExecRemoteInterpreterProcess) client) {
      assertEquals("spark", interpreterProcess.getInterpreterSettingName());
      assertTrue(interpreterProcess.getInterpreterDir().endsWith("/interpreter/spark"));
      assertTrue(interpreterProcess.getLocalRepoDir().endsWith("/local-repo/groupId"));
      assertEquals(zConf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
      assertTrue(interpreterProcess.getEnv().size() >= 3);
      assertEquals(sparkHome, interpreterProcess.getEnv().get("SPARK_HOME"));
      assertEquals("true", interpreterProcess.getEnv().get("ZEPPELIN_SPARK_YARN_CLUSTER"));
      String sparkJars = "jar_1," +
              Paths.get(localRepoPath.toAbsolutePath().toString(), "test.jar").toString() + "," +
        zeppelinHome + "/interpreter/spark/scala-2.12/spark-scala-2.12-" + Util.getVersion()
        + ".jar," +
              zeppelinHome + "/interpreter/zeppelin-interpreter-shaded-" + Util.getVersion() + ".jar";
      String sparkrZip = sparkHome + "/R/lib/sparkr.zip#sparkr";
      String sparkFiles = "file_1," + zeppelinHome + "/conf/log4j_yarn_cluster.properties";
      String expected = "--proxy-user|user1|--conf|spark.yarn.dist.archives=" + sparkrZip +
              "|--conf|spark.yarn.isPython=true|--conf|spark.app.name=intpGroupId" +
              "|--conf|spark.yarn.maxAppAttempts=1" +
              "|--conf|spark.master=yarn" +
              "|--conf|spark.files=" + sparkFiles + "|--conf|spark.jars=" + sparkJars +
              "|--conf|spark.submit.deployMode=cluster" +
              "|--conf|spark.yarn.submit.waitAppCompletion=false";
      assertTrue(CollectionUtils.isEqualCollection(Arrays.asList(expected.split("\\|")),
        Arrays.asList(interpreterProcess.getEnv().get("ZEPPELIN_SPARK_CONF").split("\\|"))));
      assertTrue(interpreterProcess.getEnv().get("ZEPPELIN_SPARK_CONF").startsWith("--proxy-user|user1"));
    }
    Files.deleteIfExists(Paths.get(localRepoPath.toAbsolutePath().toString(), "test.jar"));
    FileUtils.deleteDirectory(localRepoPath.toFile());
  }

  @Test
  void testYarnClusterMode_3() throws IOException {
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("SPARK_HOME", sparkHome);
    properties.setProperty("property_1", "value_1");
    properties.setProperty("spark.master", "yarn");
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
    assertTrue(client instanceof ExecRemoteInterpreterProcess);
    try (ExecRemoteInterpreterProcess interpreterProcess = (ExecRemoteInterpreterProcess) client) {
      assertEquals("spark", interpreterProcess.getInterpreterSettingName());
      assertTrue(interpreterProcess.getInterpreterDir().endsWith("/interpreter/spark"));
      assertTrue(interpreterProcess.getLocalRepoDir().endsWith("/local-repo/groupId"));
      assertEquals(zConf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
      assertTrue(interpreterProcess.getEnv().size() >= 3);
      assertEquals(sparkHome, interpreterProcess.getEnv().get("SPARK_HOME"));
      assertEquals("true", interpreterProcess.getEnv().get("ZEPPELIN_SPARK_YARN_CLUSTER"));

      String sparkJars = "jar_1," +
        zeppelinHome + "/interpreter/spark/scala-2.12/spark-scala-2.12-" + Util.getVersion()
        + ".jar," +
              zeppelinHome + "/interpreter/zeppelin-interpreter-shaded-" + Util.getVersion() + ".jar";
      String sparkrZip = sparkHome + "/R/lib/sparkr.zip#sparkr";
      // escape special characters
      String sparkFiles = "{}," + zeppelinHome + "/conf/log4j_yarn_cluster.properties";
      String expected = "--proxy-user|user1" +
                      "|--conf|spark.yarn.dist.archives=" + sparkrZip +
                      "|--conf|spark.yarn.isPython=true" +
                      "|--conf|spark.app.name=intpGroupId" +
                      "|--conf|spark.yarn.maxAppAttempts=1" +
                      "|--conf|spark.master=yarn" +
                      "|--conf|spark.files=" + sparkFiles +
                      "|--conf|spark.jars=" + sparkJars +
                      "|--conf|spark.submit.deployMode=cluster" +
                      "|--conf|spark.yarn.submit.waitAppCompletion=false";
      assertTrue(CollectionUtils.isEqualCollection(Arrays.asList(expected.split("\\|")),
        Arrays.asList(interpreterProcess.getEnv().get("ZEPPELIN_SPARK_CONF").split("\\|"))));
      assertTrue(interpreterProcess.getEnv().get("ZEPPELIN_SPARK_CONF").startsWith("--proxy-user|user1"));
    }
    FileUtils.deleteDirectory(localRepoPath.toFile());
  }

  @Test
  void testDetectSparkScalaVersionDirectStreamCapture() throws Exception {
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    
    // Use reflection to access private method
    Method detectSparkScalaVersionMethod = SparkInterpreterLauncher.class.getDeclaredMethod(
        "detectSparkScalaVersion", String.class, Map.class);
    detectSparkScalaVersionMethod.setAccessible(true);
    
    Map<String, String> env = new HashMap<>();
    
    // Call the method
    String scalaVersion = (String) detectSparkScalaVersionMethod.invoke(launcher, sparkHome, env);
    
    // Verify we got a valid result
    assertTrue(scalaVersion.equals("2.12") || scalaVersion.equals("2.13"), 
        "Expected scala version 2.12 or 2.13 but got: " + scalaVersion);
  }
  
  @Test
  void testDetectSparkScalaVersionByReplClassWithNonExistentDirectory() throws Exception {
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    
    // Use reflection to access private method
    Method detectMethod = SparkInterpreterLauncher.class.getDeclaredMethod(
        "detectSparkScalaVersionByReplClass", String.class);
    detectMethod.setAccessible(true);
    
    // Test with non-existent directory
    String nonExistentSparkHome = "/tmp/non-existent-spark-home-" + System.currentTimeMillis();
    
    try {
      detectMethod.invoke(launcher, nonExistentSparkHome);
      fail("Expected IOException for non-existent directory");
    } catch (Exception e) {
      Throwable cause = e.getCause();
      assertTrue(cause instanceof IOException, "Expected IOException but got: " + cause.getClass());
      assertTrue(cause.getMessage().contains("does not exist"), 
          "Error message should mention directory does not exist: " + cause.getMessage());
      assertTrue(cause.getMessage().contains("SPARK_HOME"), 
          "Error message should mention SPARK_HOME: " + cause.getMessage());
    }
  }

  @Test
  void testDetectSparkScalaVersionByReplClassWithFileInsteadOfDirectory() throws Exception {
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    
    // Use reflection to access private method
    Method detectMethod = SparkInterpreterLauncher.class.getDeclaredMethod(
        "detectSparkScalaVersionByReplClass", String.class);
    detectMethod.setAccessible(true);
    
    // Create a temporary file (not directory)
    File tempFile = File.createTempFile("spark-test", ".tmp");
    tempFile.deleteOnExit();
    
    // Create a fake SPARK_HOME that points to a parent directory
    String fakeSparkHome = tempFile.getParent() + "/" + tempFile.getName().replace(".tmp", "");
    
    // Rename temp file to simulate jars path as a file
    File jarsFile = new File(fakeSparkHome + "/jars");
    jarsFile.getParentFile().mkdirs();
    tempFile.renameTo(jarsFile);
    jarsFile.deleteOnExit();
    
    try {
      detectMethod.invoke(launcher, fakeSparkHome);
      fail("Expected IOException for file instead of directory");
    } catch (Exception e) {
      Throwable cause = e.getCause();
      assertTrue(cause instanceof IOException, "Expected IOException but got: " + cause.getClass());
      assertTrue(cause.getMessage().contains("not a directory"), 
          "Error message should mention not a directory: " + cause.getMessage());
    } finally {
      jarsFile.delete();
    }
  }

  @Test
  void testDetectSparkScalaVersionByReplClassWithValidDirectory() throws Exception {
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    
    // Use reflection to access private method
    Method detectMethod = SparkInterpreterLauncher.class.getDeclaredMethod(
        "detectSparkScalaVersionByReplClass", String.class);
    detectMethod.setAccessible(true);
    
    // Create a temporary directory structure
    Path tempSparkHome = Files.createTempDirectory("spark-test");
    Path jarsDir = tempSparkHome.resolve("jars");
    Files.createDirectories(jarsDir);
    
    // Create a fake spark-repl jar
    Path sparkReplJar = jarsDir.resolve("spark-repl_2.12-3.0.0.jar");
    Files.createFile(sparkReplJar);
    
    try {
      String scalaVersion = (String) detectMethod.invoke(launcher, tempSparkHome.toString());
      assertEquals("2.12", scalaVersion, "Should detect Scala 2.12");
    } finally {
      // Clean up
      Files.deleteIfExists(sparkReplJar);
      Files.deleteIfExists(jarsDir);
      Files.deleteIfExists(tempSparkHome);
    }
  }

  @Test
  void testDetectSparkScalaVersionByReplClassWithEmptyDirectory() throws Exception {
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    
    // Use reflection to access private method
    Method detectMethod = SparkInterpreterLauncher.class.getDeclaredMethod(
        "detectSparkScalaVersionByReplClass", String.class);
    detectMethod.setAccessible(true);
    
    // Create a temporary directory structure with empty jars directory
    Path tempSparkHome = Files.createTempDirectory("spark-test");
    Path jarsDir = tempSparkHome.resolve("jars");
    Files.createDirectories(jarsDir);
    
    try {
      detectMethod.invoke(launcher, tempSparkHome.toString());
      fail("Expected Exception for no spark-repl jar");
    } catch (Exception e) {
      Throwable cause = e.getCause();
      assertTrue(cause.getMessage().contains("No spark-repl jar found"), 
          "Error message should mention no spark-repl jar found: " + cause.getMessage());
    } finally {
      // Clean up
      Files.deleteIfExists(jarsDir);
      Files.deleteIfExists(tempSparkHome);
    }
  }

  @Test
  void testDetectSparkScalaVersionByReplClassWithMultipleJars() throws Exception {
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    
    // Use reflection to access private method
    Method detectMethod = SparkInterpreterLauncher.class.getDeclaredMethod(
        "detectSparkScalaVersionByReplClass", String.class);
    detectMethod.setAccessible(true);
    
    // Create a temporary directory structure with multiple spark-repl jars
    Path tempSparkHome = Files.createTempDirectory("spark-test");
    Path jarsDir = tempSparkHome.resolve("jars");
    Files.createDirectories(jarsDir);
    
    // Create multiple spark-repl jars
    Path sparkReplJar1 = jarsDir.resolve("spark-repl_2.12-3.0.0.jar");
    Path sparkReplJar2 = jarsDir.resolve("spark-repl_2.13-3.1.0.jar");
    Files.createFile(sparkReplJar1);
    Files.createFile(sparkReplJar2);
    
    try {
      detectMethod.invoke(launcher, tempSparkHome.toString());
      fail("Expected Exception for multiple spark-repl jars");
    } catch (Exception e) {
      Throwable cause = e.getCause();
      assertTrue(cause.getMessage().contains("Multiple spark-repl jar found"), 
          "Error message should mention multiple spark-repl jars found: " + cause.getMessage());
    } finally {
      // Clean up
      Files.deleteIfExists(sparkReplJar1);
      Files.deleteIfExists(sparkReplJar2);
      Files.deleteIfExists(jarsDir);
      Files.deleteIfExists(tempSparkHome);
    }
  }

  @Test
  void testDetectSparkScalaVersionByReplClassWithScala213() throws Exception {
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    
    // Use reflection to access private method
    Method detectMethod = SparkInterpreterLauncher.class.getDeclaredMethod(
        "detectSparkScalaVersionByReplClass", String.class);
    detectMethod.setAccessible(true);
    
    // Create a temporary directory structure
    Path tempSparkHome = Files.createTempDirectory("spark-test");
    Path jarsDir = tempSparkHome.resolve("jars");
    Files.createDirectories(jarsDir);
    
    // Create a fake spark-repl jar for Scala 2.13
    Path sparkReplJar = jarsDir.resolve("spark-repl_2.13-3.2.0.jar");
    Files.createFile(sparkReplJar);
    
    try {
      String scalaVersion = (String) detectMethod.invoke(launcher, tempSparkHome.toString());
      assertEquals("2.13", scalaVersion, "Should detect Scala 2.13");
    } finally {
      // Clean up
      Files.deleteIfExists(sparkReplJar);
      Files.deleteIfExists(jarsDir);
      Files.deleteIfExists(tempSparkHome);
    }
  }

  @Test
  void testDetectSparkScalaVersionByReplClassWithUnsupportedScalaVersion() throws Exception {
    SparkInterpreterLauncher launcher = new SparkInterpreterLauncher(zConf, null);
    
    // Use reflection to access private method
    Method detectMethod = SparkInterpreterLauncher.class.getDeclaredMethod(
        "detectSparkScalaVersionByReplClass", String.class);
    detectMethod.setAccessible(true);
    
    // Create a temporary directory structure
    Path tempSparkHome = Files.createTempDirectory("spark-test");
    Path jarsDir = tempSparkHome.resolve("jars");
    Files.createDirectories(jarsDir);
    
    // Create a fake spark-repl jar with unsupported Scala version
    Path sparkReplJar = jarsDir.resolve("spark-repl_2.11-2.4.0.jar");
    Files.createFile(sparkReplJar);
    
    try {
      detectMethod.invoke(launcher, tempSparkHome.toString());
      fail("Expected Exception for unsupported Scala version");
    } catch (Exception e) {
      Throwable cause = e.getCause();
      assertTrue(cause.getMessage().contains("Can not detect the scala version by spark-repl"), 
          "Error message should mention cannot detect scala version: " + cause.getMessage());
    } finally {
      // Clean up
      Files.deleteIfExists(sparkReplJar);
      Files.deleteIfExists(jarsDir);
      Files.deleteIfExists(tempSparkHome);
    }
  }
}
