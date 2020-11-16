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

package org.apache.zeppelin.interpreter.integration;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for downloading spark/flink. This is used for spark/flink integration test.
 */
public class DownloadUtils {
  private static Logger LOGGER = LoggerFactory.getLogger(DownloadUtils.class);

  private static String downloadFolder = System.getProperty("user.home") + "/.cache";

  static {
    try {
      FileUtils.forceMkdir(new File(downloadFolder));
    } catch (IOException e) {
      throw new RuntimeException("Fail to create download folder: " + downloadFolder, e);
    }
  }

  public static String downloadSpark(String sparkVersion, String hadoopVersion) {
    String sparkDownloadFolder = downloadFolder + "/spark";
    File targetSparkHomeFolder =
            new File(sparkDownloadFolder + "/spark-" + sparkVersion + "-bin-hadoop" + hadoopVersion);
    if (targetSparkHomeFolder.exists()) {
      LOGGER.info("Skip to download spark as it is already downloaded.");
      return targetSparkHomeFolder.getAbsolutePath();
    }
    download("spark", sparkVersion, "-bin-hadoop" + hadoopVersion + ".tgz");
    return targetSparkHomeFolder.getAbsolutePath();
  }

  public static String downloadFlink(String version) {
    String flinkDownloadFolder = downloadFolder + "/flink";
    File targetFlinkHomeFolder = new File(flinkDownloadFolder + "/flink-" + version);
    if (targetFlinkHomeFolder.exists()) {
      LOGGER.info("Skip to download flink as it is already downloaded.");
      return targetFlinkHomeFolder.getAbsolutePath();
    }
    download("flink", version, "-bin-scala_2.11.tgz");
    // download other dependencies for running flink with yarn and hive
    try {
      runShellCommand(new String[]{"wget",
              "https://repo1.maven.org/maven2/org/apache/flink/flink-connector-hive_2.11/"
                      + version + "/flink-connector-hive_2.11-" + version + ".jar",
              "-P", targetFlinkHomeFolder + "/lib"});
      runShellCommand(new String[]{"wget",
              "https://repo1.maven.org/maven2/org/apache/flink/flink-hadoop-compatibility_2.11/"
                      + version + "/flink-hadoop-compatibility_2.11-" + version + ".jar",
              "-P", targetFlinkHomeFolder + "/lib"});
      runShellCommand(new String[]{"wget",
              "https://repo1.maven.org/maven2/org/apache/hive/hive-exec/2.3.4/hive-exec-2.3.4.jar",
              "-P", targetFlinkHomeFolder + "/lib"});
      runShellCommand(new String[]{"wget",
              "https://repo1.maven.org/maven2/org/apache/flink/flink-shaded-hadoop2-uber/2.7.5-1.8.1/flink-shaded-hadoop2-uber-2.7.5-1.8.1.jar",
              "-P", targetFlinkHomeFolder + "/lib"});
    } catch (Exception e) {
      throw new RuntimeException("Fail to download jar", e);
    }
    return targetFlinkHomeFolder.getAbsolutePath();
  }

  public static String downloadHadoop(String version) {
    String hadoopDownloadFolder = downloadFolder + "/hadoop";
    File targetHadoopHomeFolder = new File(hadoopDownloadFolder + "/hadoop-" + version);
    if (targetHadoopHomeFolder.exists()) {
      LOGGER.info("Skip to download hadoop as it is already downloaded.");
      return targetHadoopHomeFolder.getAbsolutePath();
    }
    download("hadoop", version, ".tar.gz", "hadoop/core");
    return targetHadoopHomeFolder.getAbsolutePath();
  }

  // Try mirrors first, if fails fallback to apache archive
  private static void download(String project, String version, String postFix, String projectPath) {
    String projectDownloadFolder = downloadFolder + "/" + project;
    try {
      String preferredMirror = IOUtils.toString(new URL("https://www.apache.org/dyn/closer.lua?preferred=true"), StandardCharsets.UTF_8);
      String filePath = projectDownloadFolder + "/" + project + "-" + version + postFix;
      
      String downloadURL = preferredMirror + "/" + projectPath + "/" + project + "-" + version + "/" + project + "-" + version + postFix;
      if (File.separator.equals("\\"))
      {
        File folder = new File(projectDownloadFolder);
        folder.mkdirs();
        String[] args1 = new String[]{"powershell","wget", downloadURL, "-OutFile", filePath}; 
        LOGGER.info("download exec: "+String.join(" ",args1));
        runShellCommand(args1);
      }
      else
        runShellCommand(new String[]{"wget", downloadURL, "-P", projectDownloadFolder});
      File downloadFile = new File(filePath);
      runShellCommand(new String[]{"tar", "-xvf", downloadFile.getAbsolutePath(), "-C", projectDownloadFolder});
    } catch (Exception e) {
      LOGGER.warn("Failed to download " + project + " from mirror site, fallback to use apache archive", e);
      String filePath = projectDownloadFolder + "/" + project + "-" + version + postFix;
      File downloadFile = new File(filePath);
      String downloadURL =
              "https://archive.apache.org/dist/" + projectPath + "/" + project +"-"
                      + version
                      + "/" + project + "-"
                      + version
                      + postFix;
      try {
        if (File.separator.equals("\\"))
        {
          String[] args1 = new String[]{"powershell","wget", downloadURL, "-OutFile", filePath}; 
          LOGGER.info("download exec: "+String.join(" ",args1));
          runShellCommand(args1);
        }
        else
          runShellCommand(new String[]{"wget", downloadURL, "-P", projectDownloadFolder});
        runShellCommand(
                new String[]{"tar", "-xvf", downloadFile.getAbsolutePath(), "-C", projectDownloadFolder});
      } catch (Exception ex) {
        throw new RuntimeException("Fail to download " + project + " " + version, ex);
      }
    }
  }

  private static void download(String project, String version, String postFix) {
    download(project, version, postFix, project);
  }

  private static void runShellCommand(String[] commands) throws IOException, InterruptedException {
    LOGGER.info("Starting shell commands: " + StringUtils.join(commands, " "));
    Process process = Runtime.getRuntime().exec(commands);
    StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream());
    StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream());
    errorGobbler.start();
    outputGobbler.start();
    if (process.waitFor() != 0) {
      throw new IOException("Fail to run shell commands: " + StringUtils.join(commands, " "));
    }
    LOGGER.info("Complete shell commands: " + StringUtils.join(commands, " "));
  }

  private static class StreamGobbler extends Thread {
    InputStream is;

    // reads everything from is until empty.
    StreamGobbler(InputStream is) {
      this.is = is;
    }

    @Override
    public void run() {
      try {
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        String line = null;
        long startTime = System.currentTimeMillis();
        while ((line = br.readLine()) != null) {
          // logging per 5 seconds
          if ((System.currentTimeMillis() - startTime) > 5000) {
            LOGGER.info(line);
            startTime = System.currentTimeMillis();
          }
        }
      } catch (IOException ioe) {
        LOGGER.warn("Fail to print shell output", ioe);
      }
    }
  }
}
