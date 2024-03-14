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

  private static final String MIRROR_URL = "https://www.apache.org/dyn/closer.lua?preferred=true";
  private static final String ARCHIVE_URL = "https://archive.apache.org/dist/";

  private static String downloadFolder = System.getProperty("user.home") + "/.cache";
  public static final String DEFAULT_SPARK_VERSION = "3.4.2";
  public static final String DEFAULT_SPARK_HADOOP_VERSION = "3";

  private DownloadUtils() {
    throw new IllegalStateException("Utility class");
  }

  static {
    try {
      FileUtils.forceMkdir(new File(downloadFolder));
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
    return downloadSpark(DEFAULT_SPARK_VERSION, DEFAULT_SPARK_HADOOP_VERSION);
  }

  /**
   * Download of a Spark distribution
   *
   * @param sparkVersion
   * @param hadoopVersion
   * @return home of Spark installation
   */
  public static String downloadSpark(String sparkVersion, String hadoopVersion) {
    File sparkFolder = new File(downloadFolder, "spark");
    File targetSparkHomeFolder =
        new File(sparkFolder, "spark-" + sparkVersion + "-bin-hadoop" + hadoopVersion);
    return downloadSpark(sparkVersion, hadoopVersion, targetSparkHomeFolder);
  }

  /**
   * Download of a Spark distribution
   *
   * @param sparkVersion
   * @param hadoopVersion
   * @param targetSparkHomeFolder - where should the spark archive be extracted
   * @return home of Spark installation
   */
  public static String downloadSpark(String sparkVersion, String hadoopVersion,
      File targetSparkHomeFolder) {
    File sparkFolder = new File(downloadFolder, "spark");
    sparkFolder.mkdir();
    if (targetSparkHomeFolder.exists()) {
      LOGGER.info("Skip to download Spark {}-{} as it is already downloaded.", sparkVersion,
          hadoopVersion);
      return targetSparkHomeFolder.getAbsolutePath();
    }
    File sparkTarGZ =
        new File(sparkFolder, "spark-" + sparkVersion + "-bin-hadoop" + hadoopVersion + ".tgz");
    try {
      URL mirrorURL = new URL(
          IOUtils.toString(new URL(MIRROR_URL), StandardCharsets.UTF_8) + generateDownloadURL(
              "spark", sparkVersion, "-bin-hadoop" + hadoopVersion + ".tgz", "spark"));
      URL archiveURL = new URL(ARCHIVE_URL + generateDownloadURL(
              "spark", sparkVersion, "-bin-hadoop" + hadoopVersion + ".tgz", "spark"));
      LOGGER.info("Download Spark {}-{}", sparkVersion, hadoopVersion);
      download(new DownloadRequest(mirrorURL, archiveURL), sparkTarGZ);
      ProgressBarBuilder pbb = new ProgressBarBuilder()
          .setTaskName("Unarchiv")
          .setUnit("MiB", 1048576) // setting the progress bar to use MiB as the unit
          .setStyle(ProgressBarStyle.ASCII)
          .setUpdateIntervalMillis(1000)
          .setConsumer(new DelegatingProgressBarConsumer(LOGGER::info));
      try (
          InputStream fis = Files.newInputStream(sparkTarGZ.toPath());
          InputStream pbis = ProgressBar.wrap(fis, pbb);
          InputStream bis = new BufferedInputStream(pbis);
          InputStream gzis = new GzipCompressorInputStream(bis);
          ArchiveInputStream<TarArchiveEntry> o = new TarArchiveInputStream(gzis)) {
        LOGGER.info("Unarchive Spark {}-{} to {}", sparkVersion, hadoopVersion,
            targetSparkHomeFolder);
        unarchive(o, targetSparkHomeFolder, 1);
        LOGGER.info("Unarchive Spark {}-{} done", sparkVersion, hadoopVersion);
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
            .setUpdateIntervalMillis(1000)
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
  public static String downloadLivy(String livyVersion, File targetLivyHomeFolder) {
    File livyDownloadFolder = new File(downloadFolder, "livy");
    livyDownloadFolder.mkdir();
    if (targetLivyHomeFolder.exists()) {
      LOGGER.info("Skip to download Livy {} as it is already downloaded.", livyVersion);
      return targetLivyHomeFolder.getAbsolutePath();
    }
    File livyZip = new File(livyDownloadFolder, "livy-" + livyVersion + ".zip");
    try {
      URL mirrorURL = new URL(
          IOUtils.toString(new URL(MIRROR_URL), StandardCharsets.UTF_8) + "incubator/livy/"
              + livyVersion
              + "/apache-livy-" + livyVersion + "-bin.zip");
      URL archiveURL = new URL("https://archive.apache.org/dist/incubator/livy/" + livyVersion
          + "/apache-livy-" + livyVersion + "-bin.zip");
      LOGGER.info("Download Livy {}", livyVersion);
      download(new DownloadRequest(mirrorURL, archiveURL), livyZip);
      LOGGER.info("Unzip Livy {} to {}", livyVersion, targetLivyHomeFolder);
      ProgressBarBuilder pbb = new ProgressBarBuilder()
          .setTaskName("Unarchiv Livy")
          .setUnit("MiB", 1048576) // setting the progress bar to use MiB as the unit
          .setStyle(ProgressBarStyle.ASCII)
          .setUpdateIntervalMillis(1000)
          .setConsumer(new DelegatingProgressBarConsumer(LOGGER::info));
      try (InputStream fis = Files.newInputStream(livyZip.toPath());
          InputStream pbis = ProgressBar.wrap(fis, pbb);
          InputStream bis = new BufferedInputStream(pbis);
          ZipInputStream zis = new ZipInputStream(bis)) {
        unzip(zis, targetLivyHomeFolder, 1);
      }
      LOGGER.info("Unzip Livy {} done", livyVersion);
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
    return downloadLivy(livyVersion, targetLivyHomeFolder);
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
        // fix for Windows-created archives
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
        // fix for Windows-created archives
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
      URL mirrorURL = new URL(
          IOUtils.toString(new URL(MIRROR_URL), StandardCharsets.UTF_8) + generateDownloadURL(
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
      download("https://repo1.maven.org/maven2/org/apache/hive/hive-exec/2.3.4/hive-exec-2.3.4.jar",
          3, new File(targetFlinkHomeFolder, "lib" + File.separator + "hive-exec-2.3.4.jar"));
      download(
          "https://repo1.maven.org/maven2/org/apache/flink/flink-shaded-hadoop2-uber/2.7.5-1.8.1/flink-shaded-hadoop2-uber-2.7.5-1.8.1.jar",
          3, new File(targetFlinkHomeFolder,
              "lib" + File.separator + "flink-shaded-hadoop2-uber-2.7.5-1.8.1.jar"));
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
      URL mirrorURL = new URL(
          IOUtils.toString(new URL(MIRROR_URL), StandardCharsets.UTF_8) + generateDownloadURL(
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
}
