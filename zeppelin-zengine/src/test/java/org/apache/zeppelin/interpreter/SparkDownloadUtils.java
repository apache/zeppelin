package org.apache.zeppelin.interpreter;

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

public class SparkDownloadUtils {
  private static Logger LOGGER = LoggerFactory.getLogger(SparkDownloadUtils.class);

  private static String downloadFolder = System.getProperty("user.home") + "/.cache/spark";

  static {
    try {
      FileUtils.forceMkdir(new File(downloadFolder));
    } catch (IOException e) {
      throw new RuntimeException("Fail to create downloadFolder: " + downloadFolder, e);
    }
  }


  public static String downloadSpark(String version) {
    File targetSparkHomeFolder = new File(downloadFolder + "/spark-" + version + "-bin-hadoop2.6");
    if (targetSparkHomeFolder.exists()) {
      LOGGER.info("Skip to download spark as it is already downloaded.");
      return targetSparkHomeFolder.getAbsolutePath();
    }
    // Try mirrors a few times until one succeeds
    for (int i = 0; i < 3; i++) {
      try {
        String preferredMirror = IOUtils.toString(new URL("https://www.apache.org/dyn/closer.lua?preferred=true"));
        File downloadFile = new File(downloadFolder + "/spark-" + version + "-bin-hadoop2.6.tgz");
        String downloadURL = preferredMirror + "/spark/spark-" + version + "/spark-" + version + "-bin-hadoop2.6.tgz";;
        runShellCommand(new String[] {"wget", downloadURL, "-P", downloadFolder});
        runShellCommand(new String[]{"tar", "-xvf", downloadFile.getAbsolutePath(), "-C", downloadFolder});
        break;
      } catch (Exception e) {
        LOGGER.warn("Failed to download Spark", e);
      }
    }
    return targetSparkHomeFolder.getAbsolutePath();
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
        while ( (line = br.readLine()) != null) {
          // logging per 5 seconds
          if ((System.currentTimeMillis() - startTime) > 5000) {
            LOGGER.info(line);
            startTime = System.currentTimeMillis();
          }
        }
      } catch (IOException ioe) {
        ioe.printStackTrace();
      }
    }
  }
}
