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

package org.apache.zeppelin.test;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.tongfei.progressbar.DelegatingProgressBarConsumer;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility class for downloading spark/flink/livy. This is used for spark/flink integration test.
 */
public class DownloadUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(DownloadUtils.class);

  public static final String APACHE_MIRROR_ENV_KEY = "APACHE_MIRROR";
  public static final String PROGRESS_BAR_UPDATE_INTERVAL_ENV_KEY = "PROGRESS_BAR_UPDATE_INTERVAL";

  private static final String MIRROR_URL;
  private static final String ARCHIVE_URL = "https://archive.apache.org/dist/";
  private static final int PROGRESS_BAR_UPDATE_INTERVAL;

  private static String downloadFolder = System.getProperty("user.home") + "/.cache";
  public static final String DEFAULT_SPARK_VERSION = "3.5.6";
  public static final String DEFAULT_SPARK_HADOOP_VERSION = "3";


  private DownloadUtils() {
    throw new IllegalStateException("Utility class");
  }

  static {
    try {
      FileUtils.forceMkdir(new File(downloadFolder));
      String envUrl = System.getenv(APACHE_MIRROR_ENV_KEY);
      if (StringUtils.isNotBlank(envUrl)) {
        MIRROR_URL = envUrl;
      } else {
        MIRROR_URL =
            IOUtils.toString(new URL("https://www.apache.org/dyn/closer.lua?preferred=true"),
                StandardCharsets.UTF_8);
      }
      String envProgressUpdateInterval = System.getenv(PROGRESS_BAR_UPDATE_INTERVAL_ENV_KEY);
      if (StringUtils.isNotBlank(envProgressUpdateInterval)) {
        PROGRESS_BAR_UPDATE_INTERVAL = Integer.valueOf(envProgressUpdateInterval);
      } else {
        PROGRESS_BAR_UPDATE_INTERVAL = 1000;
      }
    } catch (IOException e) {
      throw new RuntimeException("Fail to create download folder: " + downloadFolder, e);
    }
  }

  /**
   * Download Spark with default versions
   *
   * @return home of Spark installation
   */
  public static String downloadSpark() {
    return downloadSpark(DEFAULT_SPARK_VERSION, DEFAULT_SPARK_HADOOP_VERSION, null);
  }

  /**
   * Download of a Spark distribution with a specific Hadoop version
   *
   * @param sparkVersion
   * @param hadoopVersion
   * @return home of Spark installation
   */
  public static String downloadSpark(String sparkVersion, String hadoopVersion) {
    return downloadSpark(sparkVersion, hadoopVersion, null);
  }

  /**
   * Download of a Spark distribution with a Hadoop and Scala version
   *
   * @param sparkVersion
   * @param hadoopVersion
   * @param scalaVersion
   * @return home of Spark installation
   */
  public static String downloadSpark(String sparkVersion, String hadoopVersion,
      String scalaVersion) {
    File sparkFolder = new File(downloadFolder, "spark");
    final File targetSparkHomeFolder;
    if (StringUtils.isNotBlank(scalaVersion)) {
      targetSparkHomeFolder = new File(sparkFolder,
          "spark-" + sparkVersion + "-bin-hadoop" + hadoopVersion + "-scala" + scalaVersion);
    } else {
      targetSparkHomeFolder = new File(sparkFolder,
          "spark-" + sparkVersion + "-bin-hadoop" + hadoopVersion);
    }

    return downloadSpark(sparkVersion, hadoopVersion, scalaVersion, targetSparkHomeFolder);
  }

  /**
   * Download of a Spark distribution
   *
   * @param sparkVersion
   * @param hadoopVersion
   * @param targetSparkHomeFolder - where should the spark archive be extracted
   * @return home of Spark installation
   */
  public static String downloadSpark(String sparkVersion, String hadoopVersion, String scalaVersion,
      File targetSparkHomeFolder) {
    File sparkFolder = new File(downloadFolder, "spark");
    sparkFolder.mkdir();
    final String sparkVersionLog;
    if (StringUtils.isBlank(scalaVersion)) {
      sparkVersionLog = "Spark " + sparkVersion + "-" + hadoopVersion;
    } else {
      sparkVersionLog = "Spark " + sparkVersion + "-" + hadoopVersion + "-" + scalaVersion;
    }
    if (targetSparkHomeFolder.exists()) {
      LOGGER.info("Skip to download {} as it is already downloaded.", sparkVersionLog);
      return targetSparkHomeFolder.getAbsolutePath();
    }
    final File sparkTarGZ;
    if (StringUtils.isBlank(scalaVersion)) {
      sparkTarGZ =
          new File(sparkFolder, "spark-" + sparkVersion + "-bin-hadoop" + hadoopVersion + ".tgz");
    } else {
      sparkTarGZ = new File(sparkFolder, "spark-" + sparkVersion + "-bin-hadoop" + hadoopVersion
          + "-scala" + scalaVersion + ".tgz");
    }

    try {
      URL mirrorURL = new URL(MIRROR_URL +
          generateSparkDownloadURL(sparkVersion, hadoopVersion, scalaVersion));
      URL archiveURL = new URL(ARCHIVE_URL +
          generateSparkDownloadURL(sparkVersion, hadoopVersion, scalaVersion));
      LOGGER.info("Download {}", sparkVersionLog);
      download(new DownloadRequest(mirrorURL, archiveURL), sparkTarGZ);
      ProgressBarBuilder pbb = new ProgressBarBuilder()
          .setTaskName("Unarchiv")
          .setUnit("MiB", 1048576) // setting the progress bar to use MiB as the unit
          .setStyle(ProgressBarStyle.ASCII)
          .setUpdateIntervalMillis(PROGRESS_BAR_UPDATE_INTERVAL)
          .setConsumer(new DelegatingProgressBarConsumer(LOGGER::info));
      try (
          InputStream fis = Files.newInputStream(sparkTarGZ.toPath());
          InputStream pbis = ProgressBar.wrap(fis, pbb);
          InputStream bis = new BufferedInputStream(pbis);
          InputStream gzis = new GzipCompressorInputStream(bis);
          ArchiveInputStream<TarArchiveEntry> o = new TarArchiveInputStream(gzis)) {
        LOGGER.info("Unarchive {} to {}", sparkVersionLog, targetSparkHomeFolder);
        unarchive(o, targetSparkHomeFolder, 1);
        LOGGER.info("Unarchive {} done", sparkVersionLog);
      }
    } catch (IOException e) {
      throw new RuntimeException("Unable to download spark", e);
    }
    return targetSparkHomeFolder.getAbsolutePath();
  }

  public static void download(String url, int retries, File dst) throws IOException {
    download(new URL(url), retries, dst);
  }

  public static void download(DownloadRequest downloadRequest, File dst) throws IOException {
    if (dst.exists()) {
      LOGGER.info("Skip Download of {}, because it exists", dst);
    } else {
      boolean urlDownload = download(downloadRequest.getUrl(), downloadRequest.getRetries(), dst);
      if (urlDownload) {
        LOGGER.info("Download successfully");
        return;
      }
      Optional<URL> alternativeURL = downloadRequest.getAlternativeUrl();
      if (alternativeURL.isPresent()) {
        urlDownload = download(alternativeURL.get(), downloadRequest.getRetries(), dst);
        if (urlDownload) {
          LOGGER.info("Download from alternative successfully");
          return;
        }
      }
      throw new IOException("Unable to download from " + downloadRequest.getUrl());
    }
  }

  private static boolean download(URL url, int retries, File dst) {
    int retry = 0;
    while (retry < retries) {
      try {
        HttpURLConnection httpConnection = (HttpURLConnection) (url.openConnection());
        long completeFileSize = httpConnection.getContentLength();
        ProgressBarBuilder pbb = new ProgressBarBuilder()
            .setTaskName("Download " + dst.getName())
            .setUnit("MiB", 1048576) // setting the progress bar to use MiB as the unit
            .setStyle(ProgressBarStyle.ASCII)
            .setUpdateIntervalMillis(PROGRESS_BAR_UPDATE_INTERVAL)
            .setInitialMax(completeFileSize)
            .setConsumer(new DelegatingProgressBarConsumer(LOGGER::info));
        try (
            OutputStream fileOS = Files.newOutputStream(dst.toPath());
            InputStream is = url.openStream();
            InputStream pbis = ProgressBar.wrap(is, pbb);
            InputStream bis = new BufferedInputStream(pbis)) {
          IOUtils.copyLarge(bis, fileOS);
          return true;
        }
      } catch (IOException e) {
        LOGGER.info("Unable to download from {}", url, e);
        ++retry;
      }
    }
    return false;
  }

  /**
   * @param livyVersion
   * @param targetLivyHomeFolder
   * @return livyHome
   */
  public static String downloadLivy(String livyVersion, String scalaVersion,
      File targetLivyHomeFolder) {
    File livyDownloadFolder = new File(downloadFolder, "livy");
    livyDownloadFolder.mkdir();
    final String livyLog = StringUtils.isBlank(scalaVersion) ? "Livy " + livyVersion : "Livy "
        + livyVersion + "_" + scalaVersion;
    if (targetLivyHomeFolder.exists()) {
      LOGGER.info("Skip to download {} as it is already downloaded.", livyLog);
      return targetLivyHomeFolder.getAbsolutePath();
    }
    final File livyZip;
    if (StringUtils.isBlank(scalaVersion)) {
      // e.g. apache-livy-0.7.1-incubating-bin.zip
      livyZip = new File(livyDownloadFolder, "apache-livy-" + livyVersion + "-bin.zip");
    } else {
      // e.g apache-livy-0.8.0-incubating_2.12-bin.zip
      livyZip = new File(livyDownloadFolder, "apache-livy-" + livyVersion + "_" + scalaVersion + "-bin.zip");
    }

    try {
      URL mirrorURL = new URL(MIRROR_URL + generateLivyDownloadUrl(livyVersion, scalaVersion));
      URL archiveURL = new URL(ARCHIVE_URL + generateLivyDownloadUrl(livyVersion, scalaVersion));
      LOGGER.info("Download {}", livyLog);
      download(new DownloadRequest(mirrorURL, archiveURL), livyZip);
      LOGGER.info("Unzip {} to {}", livyLog, targetLivyHomeFolder);
      ProgressBarBuilder pbb = new ProgressBarBuilder()
          .setTaskName("Unarchiv Livy")
          .setUnit("MiB", 1048576) // setting the progress bar to use MiB as the unit
          .setStyle(ProgressBarStyle.ASCII)
          .setUpdateIntervalMillis(PROGRESS_BAR_UPDATE_INTERVAL)
          .setConsumer(new DelegatingProgressBarConsumer(LOGGER::info));
      try (InputStream fis = Files.newInputStream(livyZip.toPath());
          InputStream pbis = ProgressBar.wrap(fis, pbb);
          InputStream bis = new BufferedInputStream(pbis);
          ZipInputStream zis = new ZipInputStream(bis)) {
        unzip(zis, targetLivyHomeFolder, 1);
      }
      LOGGER.info("Unzip {} done", livyLog);
      // Create logs directory
      File logs = new File(targetLivyHomeFolder, "logs");
      logs.mkdir();
    } catch (MalformedURLException e) {
      LOGGER.error("invalid URL", e);
    } catch (IOException e) {
      throw new RuntimeException("Unable to download livy", e);
    }
    return targetLivyHomeFolder.getAbsolutePath();
  }

  /**
   * @param livyVersion
   * @return return livyHome
   * @throws IOException
   */
  public static String downloadLivy(String livyVersion) {
    File livyDownloadFolder = new File(downloadFolder, "livy");
    File targetLivyHomeFolder = new File(livyDownloadFolder, "livy-" + livyVersion);
    return downloadLivy(livyVersion, null, targetLivyHomeFolder);
  }

  public static String downloadLivy(String livyVersion, String scalaVersion) {
    File livyDownloadFolder = new File(downloadFolder, "livy");
    File targetLivyHomeFolder =
        new File(livyDownloadFolder, "livy-" + livyVersion + "_" + scalaVersion);
    return downloadLivy(livyVersion, scalaVersion, targetLivyHomeFolder);
  }

  private static File newFile(File destinationDir, ZipEntry zipEntry, int strip)
      throws IOException {
    String filename = zipEntry.getName();
    for (int i = 0; i < strip; ++i) {
      if (filename.contains(File.separator)) {
        filename = filename.substring(filename.indexOf(File.separator) + 1);
      }
    }
    File destFile = new File(destinationDir, filename);
    String destDirPath = destinationDir.getCanonicalPath();
    String destFilePath = destFile.getCanonicalPath();

    if (!destFilePath.startsWith(destDirPath + File.separator)) {
      throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
    }

    return destFile;
  }

  private static File newFile(File destDir, ArchiveEntry archiveEntry, int strip)
      throws IOException {
    String filename = archiveEntry.getName();
    for (int i = 0; i < strip; ++i) {
      if (filename.contains(File.separator)) {
        filename = filename.substring(filename.indexOf(File.separator) + 1);
      }
    }
    File destFile = new File(destDir, filename);
    String destDirPath = destDir.getCanonicalPath();
    String destFilePath = destFile.getCanonicalPath();

    if (!destFilePath.startsWith(destDirPath + File.separator)) {
      throw new IOException("Entry is outside of the target dir: " + archiveEntry.getName());
    }

    return destFile;
  }

  private static void unarchive(ArchiveInputStream<? extends ArchiveEntry> ais, File destDir,
      int strip) throws IOException {
    byte[] buffer = new byte[1024];
    ArchiveEntry archiveEntry = ais.getNextEntry();
    while (archiveEntry != null) {
      File newFile;
      try {
        newFile = newFile(destDir, archiveEntry, strip);
      } catch (IOException e) {
        LOGGER.info("Skip {}", archiveEntry.getName());
        archiveEntry = ais.getNextEntry();
        continue;
      }
      if (archiveEntry.isDirectory()) {
        if (!newFile.isDirectory() && !newFile.mkdirs()) {
          throw new IOException("Failed to create directory " + newFile);
        }
      } else {
        File parent = newFile.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
          throw new IOException("Failed to create directory " + parent);
        }

        // write file content
        try (FileOutputStream fos = new FileOutputStream(newFile)) {
          int len;
          while ((len = ais.read(buffer)) > 0) {
            fos.write(buffer, 0, len);
          }
        }
        // Change permissions and metadata
        if (newFile.getParentFile().getName().contains("bin")
            && !newFile.setExecutable(true, false)) {
          LOGGER.info("Setting file {} to executable failed", newFile);
        }
        if (!newFile.setLastModified(archiveEntry.getLastModifiedDate().getTime())) {
          LOGGER.info("Setting last modified date to file {} failed", newFile);
        }
      }
      archiveEntry = ais.getNextEntry();
    }
  }

  private static void unzip(ZipInputStream zis, File destDir, int strip) throws IOException {
    byte[] buffer = new byte[1024];
    ZipEntry zipEntry = zis.getNextEntry();
    while (zipEntry != null) {
      File newFile;
      try {
        newFile = newFile(destDir, zipEntry, strip);
      } catch (IOException e) {
        LOGGER.info("Skip {}", zipEntry.getName());
        zipEntry = zis.getNextEntry();
        continue;
      }
      if (zipEntry.isDirectory()) {
        if (!newFile.isDirectory() && !newFile.mkdirs()) {
          throw new IOException("Failed to create directory " + newFile);
        }
      } else {
        File parent = newFile.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
          throw new IOException("Failed to create directory " + parent);
        }

        // write file content
        try (FileOutputStream fos = new FileOutputStream(newFile)) {
          int len;
          while ((len = zis.read(buffer)) > 0) {
            fos.write(buffer, 0, len);
          }
        }
        // Change permissions and metadata
        if (newFile.getParentFile().getName().contains("bin")
            && !newFile.setExecutable(true, false)) {
          LOGGER.info("Setting file {} to executable failed", newFile);
        }
        if (!newFile.setLastModified(zipEntry.getLastModifiedTime().toMillis())) {
          LOGGER.info("Setting last modified date to file {} failed", newFile);
        }
      }
      zipEntry = zis.getNextEntry();
    }
    zis.closeEntry();
  }

  public static String downloadFlink(String flinkVersion, String scalaVersion) {
    File flinkDownloadFolder = new File(downloadFolder, "flink");
    flinkDownloadFolder.mkdir();
    File targetFlinkHomeFolder = new File(flinkDownloadFolder, "flink-" + flinkVersion);
    if (targetFlinkHomeFolder.exists()) {
      LOGGER.info("Skip to download Flink {}_{} as it is already downloaded.", flinkVersion,
          scalaVersion);
      return targetFlinkHomeFolder.getAbsolutePath();
    }
    File flinkTGZ = new File(flinkDownloadFolder,
        "flink-" + flinkVersion + "-bin-scala_" + scalaVersion + ".tgz");
    try {
      URL mirrorURL = new URL(MIRROR_URL + generateDownloadURL(
              "flink", flinkVersion, "-bin-scala_" + scalaVersion + ".tgz", "flink"));
      URL archiveURL = new URL(ARCHIVE_URL + generateDownloadURL(
          "flink", flinkVersion, "-bin-scala_" + scalaVersion + ".tgz", "flink"));
      LOGGER.info("Download Flink {}_{}", flinkVersion, scalaVersion);
      download(new DownloadRequest(mirrorURL, archiveURL), flinkTGZ);
      ProgressBarBuilder pbb = new ProgressBarBuilder()
          .setTaskName("Unarchiv Flink")
          .setUnit("MiB", 1048576) // setting the progress bar to use MiB as the unit
          .setStyle(ProgressBarStyle.ASCII)
          .setUpdateIntervalMillis(1000)
          .setConsumer(new DelegatingProgressBarConsumer(LOGGER::info));
      try (
          InputStream fis = Files.newInputStream(flinkTGZ.toPath());
          InputStream pbis = ProgressBar.wrap(fis, pbb);
          InputStream bis = new BufferedInputStream(pbis);
          InputStream gzis = new GzipCompressorInputStream(bis);
          ArchiveInputStream<TarArchiveEntry> o = new TarArchiveInputStream(gzis)) {
        LOGGER.info("Unarchive Flink {}_{} to {}", flinkVersion, scalaVersion,
            targetFlinkHomeFolder);
        unarchive(o, targetFlinkHomeFolder, 1);
        LOGGER.info("Unarchive Flink done");
      }
    } catch (IOException e) {
      throw new RuntimeException("Unable to download flink", e);
    }


    // download other dependencies for running flink with yarn and hive
    try {
      download("https://repo1.maven.org/maven2/org/apache/flink/flink-connector-hive_"
          + scalaVersion + "/"
                      + flinkVersion + "/flink-connector-hive_" + scalaVersion + "-" + flinkVersion + ".jar",
          3, new File(targetFlinkHomeFolder, "lib" + File.separator + "flink-connector-hive_"
              + scalaVersion + "-" + flinkVersion + ".jar"));
      download("https://repo1.maven.org/maven2/org/apache/flink/flink-hadoop-compatibility_" + scalaVersion + "/"
                      + flinkVersion + "/flink-hadoop-compatibility_" + scalaVersion + "-" + flinkVersion + ".jar",
          3, new File(targetFlinkHomeFolder, "lib" + File.separator + "flink-hadoop-compatibility_"
              + scalaVersion + "-" + flinkVersion + ".jar"));
      download("https://repo1.maven.org/maven2/org/apache/hive/hive-exec/2.3.7/hive-exec-2.3.7.jar",
          3, new File(targetFlinkHomeFolder, "lib" + File.separator + "hive-exec-2.3.4.jar"));
      download(
          "https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-client-api/3.3.6/hadoop-client-api-3.3.6.jar",
          3, new File(targetFlinkHomeFolder,
              "lib" + File.separator + "hadoop-client-api-3.3.6.jar"));
      download(
          "https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-client-runtime/3.3.6/hadoop-client-runtime-3.3.6.jar",
          3, new File(targetFlinkHomeFolder,
              "lib" + File.separator + "hadoop-client-runtime-3.3.6.jar"));
      download("https://repo1.maven.org/maven2/org/apache/flink/flink-table-api-scala_"
          + scalaVersion + "/"
                      + flinkVersion + "/flink-table-api-scala_" + scalaVersion + "-" + flinkVersion + ".jar",
          3, new File(targetFlinkHomeFolder, "lib" + File.separator + "flink-table-api-scala_"
              + scalaVersion + "-" + flinkVersion + ".jar"));
      download("https://repo1.maven.org/maven2/org/apache/flink/flink-table-api-scala-bridge_"
          + scalaVersion + "/"
                      + flinkVersion + "/flink-table-api-scala-bridge_" + scalaVersion + "-" + flinkVersion + ".jar",
          3, new File(targetFlinkHomeFolder, "lib" + File.separator
              + "flink-table-api-scala-bridge_" + scalaVersion + "-" + flinkVersion + ".jar"));

      String jarName = "flink-table-planner_" + scalaVersion + "-" + flinkVersion + ".jar";
      mvFile(
          targetFlinkHomeFolder + File.separator + "opt" + File.separator + jarName,
          targetFlinkHomeFolder + File.separator + "lib" + File.separator + jarName);
      jarName = "flink-table-planner-loader-" + flinkVersion + ".jar";
      mvFile(
          targetFlinkHomeFolder + File.separator + "lib" + File.separator + jarName,
          targetFlinkHomeFolder + File.separator + "opt" + File.separator + jarName);
      if (SemanticVersion.of(flinkVersion).equalsOrNewerThan(SemanticVersion.of("1.16.0"))) {
        jarName = "flink-sql-client-" + flinkVersion + ".jar";
        mvFile(targetFlinkHomeFolder + File.separator + "opt" + File.separator + jarName,
            targetFlinkHomeFolder + File.separator + "lib" + File.separator + jarName);
      }
    } catch (Exception e) {
      throw new RuntimeException("Fail to download jar", e);
    }
    return targetFlinkHomeFolder.getAbsolutePath();
  }

  private static void mvFile(String srcPath, String dstPath) throws IOException {
    Path src = Paths.get(srcPath);
    Path dst = Paths.get(dstPath);
    if (src.toFile().exists()) {
      if (dst.toFile().exists()) {
        LOGGER.warn("{} does exits - replacing", dstPath);
        FileUtils.deleteQuietly(dst.toFile());
      }
      LOGGER.info("Copy file {} to {}", src, dst);
      Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
    } else {
      LOGGER.warn("{} does not exits - skipping", srcPath);
    }
  }

  public static String downloadHadoop(String version) {
    File hadoopDownloadFolder = new File(downloadFolder, "hadoop");
    hadoopDownloadFolder.mkdir();
    File targetHadoopHomeFolder = new File(hadoopDownloadFolder, "hadoop-" + version);
    if (targetHadoopHomeFolder.exists()) {
      LOGGER.info("Skip to download Hadoop {} as it is already downloaded.", version);
      return targetHadoopHomeFolder.getAbsolutePath();
    }
    File hadoopTGZ = new File(hadoopDownloadFolder, "hadoop-" + version + ".tar.gz");
    try {
      URL mirrorURL = new URL(MIRROR_URL + generateDownloadURL(
          "hadoop", version, ".tar.gz", "hadoop/core"));
      URL archiveURL = new URL(ARCHIVE_URL + generateDownloadURL(
          "hadoop", version, ".tar.gz", "hadoop/core"));
      LOGGER.info("Download Hadoop {}", version);
      download(new DownloadRequest(mirrorURL, archiveURL), hadoopTGZ);
      ProgressBarBuilder pbb = new ProgressBarBuilder()
          .setTaskName("Unarchiv")
          .setUnit("MiB", 1048576) // setting the progress bar to use MiB as the unit
          .setStyle(ProgressBarStyle.ASCII)
          .setUpdateIntervalMillis(1000)
          .setConsumer(new DelegatingProgressBarConsumer(LOGGER::info));
      try (
          InputStream fis = Files.newInputStream(hadoopTGZ.toPath());
          InputStream pbis = ProgressBar.wrap(fis, pbb);
          InputStream bis = new BufferedInputStream(pbis);
          InputStream gzis = new GzipCompressorInputStream(bis);
          ArchiveInputStream<TarArchiveEntry> o = new TarArchiveInputStream(gzis)) {
        LOGGER.info("Unarchive Hadoop {} to {}", version, targetHadoopHomeFolder);
        unarchive(o, targetHadoopHomeFolder, 1);
        LOGGER.info("Unarchive Hadoop {} done", version);
      }
    } catch (IOException e) {
      throw new RuntimeException("Unable to download hadoop");
    }
    return targetHadoopHomeFolder.getAbsolutePath();
  }

  private static String generateDownloadURL(String project, String version, String postFix,
      String projectPath) {
    return projectPath + "/" + project + "-" + version + "/" + project + "-" + version
        + postFix;
  }

  private static String generateSparkDownloadURL(String sparkVersion, String hadoopVersion,
      String scalaVersion) {
    final String url;
    String sparkVersionFolder = "spark/spark-" + sparkVersion;
    if (StringUtils.isNotBlank(hadoopVersion)) {
      if (StringUtils.isNotBlank(scalaVersion)) {
        // spark-3.4.0-bin-hadoop3-scala2.13.tgz
        url = sparkVersionFolder + "/spark-" + sparkVersion + "-bin-hadoop" + hadoopVersion
            + "-scala" + scalaVersion + ".tgz";
      } else {
        url =
            sparkVersionFolder + "/spark-" + sparkVersion + "-bin-hadoop" + hadoopVersion + ".tgz";
      }
    } else {
      url = sparkVersionFolder + "/spark-" + sparkVersion + "-bin-without-hadoop.tgz";
    }
    return url;
  }

  private static String generateLivyDownloadUrl(String livyVersion, String scalaVersion) {
    SemanticVersion livy = SemanticVersion.of(livyVersion.replace("incubating", ""));
    if (livy.equalsOrNewerThan(SemanticVersion.of("0.8.0"))) {
      return "incubator/livy/" + livyVersion + "/apache-livy-" + livyVersion + "_" + scalaVersion
          + "-bin.zip";
    }
    return "incubator/livy/" + livyVersion + "/apache-livy-" + livyVersion + "-bin.zip";
  }

  /**
   * Download of a HBase distribution
   *
   * @param version HBase version
   * @return home of HBase installation
   */
  public static String downloadHBase(String version) {
    File hbaseDownloadFolder = new File(downloadFolder, "hbase");
    hbaseDownloadFolder.mkdir();
    File targetHBaseHomeFolder = new File(hbaseDownloadFolder, "hbase-" + version);
    if (targetHBaseHomeFolder.exists()) {
      LOGGER.info("Skip to download HBase {} as it is already downloaded.", version);
      return targetHBaseHomeFolder.getAbsolutePath();
    }
    File hbaseTGZ = new File(hbaseDownloadFolder, "hbase-" + version + ".tar.gz");
    try {
      URL mirrorURL = new URL(MIRROR_URL + generateHBaseDownloadUrl(version));
      URL archiveURL = new URL(ARCHIVE_URL + generateHBaseDownloadUrl(version));
      LOGGER.info("Download HBase {}", version);
      download(new DownloadRequest(mirrorURL, archiveURL), hbaseTGZ);
      ProgressBarBuilder pbb = new ProgressBarBuilder()
          .setTaskName("Unarchive")
          .setUnit("MiB", 1048576) // setting the progress bar to use MiB as the unit
          .setStyle(ProgressBarStyle.ASCII)
          .setUpdateIntervalMillis(1000)
          .setConsumer(new DelegatingProgressBarConsumer(LOGGER::info));
      try (
          InputStream fis = Files.newInputStream(hbaseTGZ.toPath());
          InputStream pbis = ProgressBar.wrap(fis, pbb);
          InputStream bis = new BufferedInputStream(pbis);
          InputStream gzis = new GzipCompressorInputStream(bis);
          ArchiveInputStream<TarArchiveEntry> o = new TarArchiveInputStream(gzis)) {
        LOGGER.info("Unarchive HBase {} to {}", version, targetHBaseHomeFolder);
        unarchive(o, targetHBaseHomeFolder, 1);
        LOGGER.info("Unarchive HBase {} done", version);
      }
    } catch (IOException e) {
      throw new RuntimeException("Unable to download HBase");
    }
    return targetHBaseHomeFolder.getAbsolutePath();
  }

  private static String generateHBaseDownloadUrl(String hbaseVersion) {
    return "hbase/" + hbaseVersion + "/hbase-" + hbaseVersion + "-bin.tar.gz";
  }
}
