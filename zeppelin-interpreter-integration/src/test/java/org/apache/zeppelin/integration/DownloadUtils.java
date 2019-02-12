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


  public static String downloadSpark(String version) {
    String sparkDownloadFolder = downloadFolder + "/spark";
    File targetSparkHomeFolder = new File(sparkDownloadFolder + "/spark-" + version + "-bin-hadoop2.6");
    if (targetSparkHomeFolder.exists()) {
      LOGGER.info("Skip to download spark as it is already downloaded.");
      return targetSparkHomeFolder.getAbsolutePath();
    }
    download("spark", version, "-bin-hadoop2.6.tgz");
    return targetSparkHomeFolder.getAbsolutePath();
  }

  public static String downloadFlink(String version) {
    String flinkDownloadFolder = downloadFolder + "/flink";
    File targetFlinkHomeFolder = new File(flinkDownloadFolder + "/flink-" + version);
    if (targetFlinkHomeFolder.exists()) {
      LOGGER.info("Skip to download flink as it is already downloaded.");
      return targetFlinkHomeFolder.getAbsolutePath();
    }
    download("flink", version, "-bin-hadoop27-scala_2.11.tgz");
    return targetFlinkHomeFolder.getAbsolutePath();
  }

  // Try mirrors first, if fails fallback to apache archive
  private static void download(String project, String version, String postFix) {
    String projectDownloadFolder = downloadFolder + "/" + project;
    try {
      String preferredMirror = IOUtils.toString(new URL("https://www.apache.org/dyn/closer.lua?preferred=true"));
      File downloadFile = new File(projectDownloadFolder + "/" + project + "-" + version + postFix);
      String downloadURL = preferredMirror + "/" + project + "/" + project + "-" + version + "/" + project + "-" + version + postFix;
      runShellCommand(new String[]{"wget", downloadURL, "-P", projectDownloadFolder});
      runShellCommand(new String[]{"tar", "-xvf", downloadFile.getAbsolutePath(), "-C", projectDownloadFolder});
    } catch (Exception e) {
      LOGGER.warn("Failed to download " + project + " from mirror site, fallback to use apache archive", e);
      File downloadFile = new File(projectDownloadFolder + "/" + project + "-" + version + postFix);
      String downloadURL =
              "https://archive.apache.org/dist/" + project + "/" + project +"-"
                      + version
                      + "/" + project + "-"
                      + version
                      + postFix;
      try {
        runShellCommand(new String[]{"wget", downloadURL, "-P", projectDownloadFolder});
        runShellCommand(
                new String[]{"tar", "-xvf", downloadFile.getAbsolutePath(), "-C", projectDownloadFolder});
      } catch (Exception ex) {
        throw new RuntimeException("Fail to download " + project + " " + version, ex);
      }
    }
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
