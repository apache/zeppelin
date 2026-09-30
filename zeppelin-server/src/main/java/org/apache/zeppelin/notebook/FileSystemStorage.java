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

package org.apache.zeppelin.notebook;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RawLocalFileSystem;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;


/**
 * Hadoop FileSystem wrapper. Support both secure and no-secure mode
 */
public class FileSystemStorage {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemStorage.class);
  private static final String S3A = "s3a";
  private static final String FS_DEFAULTFS = "fs.defaultFS";
  static final String TMP_SUFFIX = ".tmp";
  static final String BACKUP_SUFFIX = ".bak";

  // only do UserGroupInformation.loginUserFromKeytab one time, otherwise you will still get
  // your ticket expired.
  static {
    if (UserGroupInformation.isSecurityEnabled()) {
      String keytab = ZeppelinConfiguration.getStaticString(
          ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_KERBEROS_KEYTAB);
      String principal = ZeppelinConfiguration.getStaticString(
          ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_KERBEROS_PRINCIPAL);
      if (StringUtils.isBlank(keytab) || StringUtils.isBlank(principal)) {
        throw new RuntimeException("keytab and principal can not be empty, keytab: " + keytab
            + ", principal: " + principal);
      }
      try {
        UserGroupInformation.loginUserFromKeytab(principal, keytab);
      } catch (IOException e) {
        throw new RuntimeException("Fail to login via keytab:" + keytab +
            ", principal:" + principal, e);
      }
    }
  }

  private ZeppelinConfiguration zConf;
  private Configuration hadoopConf;
  private boolean isSecurityEnabled;
  private FileSystem fs;

  public FileSystemStorage(ZeppelinConfiguration zConf, String path) throws IOException {
    this.zConf = zConf;
    this.hadoopConf = new Configuration();
    URI zepConfigURI;
    URI defaultFSURI;

    try {
      zepConfigURI = new URI(path);
    } catch (URISyntaxException e) {
      LOGGER.error("Failed to get Zeppelin config URI");
      throw new IOException(e);
    }
    // disable checksum for local file system. because interpreter.json may be updated by
    // non-hadoop filesystem api
    // disable caching for file:// scheme to avoid getting LocalFS which does CRC checks.

    this.hadoopConf.setBoolean("fs.file.impl.disable.cache", true);
    String defaultFS = this.hadoopConf.get(FS_DEFAULTFS);
    try {
      defaultFSURI = new URI(defaultFS);
    } catch (URISyntaxException e) {
      LOGGER.error("Failed to get defaultFS URI");
      throw new IOException(e);
    }

    // to check whether underlying fileSystemStorage is S3A or not
    if (!isS3AFileSystem(defaultFSURI, zepConfigURI)) {
      this.hadoopConf.set("fs.file.impl", RawLocalFileSystem.class.getName());
    }

    this.isSecurityEnabled = UserGroupInformation.isSecurityEnabled();

    this.fs = FileSystem.get(zepConfigURI, this.hadoopConf);
  }

  public boolean isS3AFileSystem(URI defaultFSURI, URI zepConfigURI) {
    return defaultFSURI.getScheme().equals(S3A)
      || (StringUtils.isNotEmpty(zepConfigURI.getScheme())
      && zepConfigURI.getScheme().equals(S3A));
  }

  public FileSystem getFs() {
    return fs;
  }

  public Path makeQualified(Path path) {
    return fs.makeQualified(path);
  }

  public boolean exists(final Path path) throws IOException {
    return callHdfsOperation(new HdfsOperation<Boolean>() {

      @Override
      public Boolean call() throws IOException {
        return fs.exists(path);
      }
    });
  }

  public void tryMkDir(final Path dir) throws IOException {
    callHdfsOperation(new HdfsOperation<Void>() {
      @Override
      public Void call() throws IOException {
        if (!fs.exists(dir)) {
          fs.mkdirs(dir);
          LOGGER.info("Create dir {} in hdfs", dir);
        }
        if (fs.getFileStatus(dir).isFile()) {
          throw new IOException(dir.toString() + " is file instead of directory, please remove " +
              "it or specify another directory");
        }
        fs.mkdirs(dir);
        return null;
      }
    });
  }

  public List<Path> list(final Path path) throws IOException {
    return callHdfsOperation(new HdfsOperation<List<Path>>() {
      @Override
      public List<Path> call() throws IOException {
        List<Path> paths = new ArrayList<>();
        for (FileStatus status : fs.globStatus(path)) {
          paths.add(status.getPath());
        }
        return paths;
      }
    });
  }

  // recursive search path, (TODO zjffdu, list folder in sub folder on demand, instead of load all
  // data when zeppelin server start)
  public List<Path> listAll(final Path path) throws IOException {
    return callHdfsOperation(new HdfsOperation<List<Path>>() {
      @Override
      public List<Path> call() throws IOException {
        List<Path> paths = new ArrayList<>();
        collectNoteFiles(path, paths);
        return paths;
      }

      private void collectNoteFiles(Path folder, List<Path> noteFiles) throws IOException {
        FileStatus[] paths = fs.listStatus(folder);
        for (FileStatus path : paths) {
          if (path.isDirectory()) {
            collectNoteFiles(path.getPath(), noteFiles);
          } else {
            if (path.getPath().getName().endsWith(".zpln")) {
              noteFiles.add(path.getPath());
            } else {
              LOGGER.warn("Unknown file: {}", path.getPath());
            }
          }
        }
      }
    });
  }

  public boolean delete(final Path path) throws IOException {
    return callHdfsOperation(new HdfsOperation<Boolean>() {
      @Override
      public Boolean call() throws IOException {
        return fs.delete(path, true);
      }
    });
  }

  public String readFile(final Path file) throws IOException {
    return callHdfsOperation(new HdfsOperation<String>() {
      @Override
      public String call() throws IOException {
        LOGGER.debug("Read from file: {}", file);
        ByteArrayOutputStream noteBytes = new ByteArrayOutputStream();
        IOUtils.copyBytes(fs.open(file), noteBytes, hadoopConf);
        return noteBytes.toString(zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_ENCODING));
      }
    });
  }

  public void writeFile(final String content, final Path file, boolean writeTempFileFirst)
      throws IOException {
      writeFile(content, file, writeTempFileFirst, null);
  }

  public void writeFile(final String content, final Path file, boolean writeTempFileFirst, Set<PosixFilePermission> permissions)
      throws IOException {
    FsPermission fsPermission;
    if (permissions == null || permissions.isEmpty()) {
      fsPermission = FsPermission.getFileDefault();
    } else {
      // FsPermission expects a 10-character string because of the leading
      // directory indicator, i.e. "drwx------". The JDK toString method returns
      // a 9-character string, so prepend a leading character.
      fsPermission = FsPermission.valueOf("-" + PosixFilePermissions.toString(permissions));
    }
    callHdfsOperation(new HdfsOperation<Void>() {
      @Override
      public Void call() throws IOException {
        InputStream in = new ByteArrayInputStream(content.getBytes(
            zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_ENCODING)));
        Path tmpFile = new Path(file.toString() + TMP_SUFFIX);
        IOUtils.copyBytes(in, fs.create(tmpFile), hadoopConf);
        fs.setPermission(tmpFile, fsPermission);
        replaceFile(tmpFile, file);
        return null;
      }
    });
  }

  /**
   * Replaces file with tmpFile without deleting the original first. The original is renamed to
   * a backup and deleted only after tmpFile is in place, so an interrupted write always leaves a
   * complete copy behind.
   */
  private void replaceFile(Path tmpFile, Path file) throws IOException {
    Path backupFile = new Path(file.toString() + BACKUP_SUFFIX);
    boolean hasOriginal = fs.exists(file);
    if (hasOriginal) {
      // A backup next to an existing file is left over from an earlier write and is older
      // than the file itself.
      fs.delete(backupFile, false);
      if (!fs.rename(file, backupFile)) {
        throw new IOException("Fail to back up " + file + " to " + backupFile);
      }
    }
    if (!fs.rename(tmpFile, file)) {
      if (hasOriginal) {
        if (fs.rename(backupFile, file)) {
          // The original is back in place, so the temp file is no longer needed. Leaving it
          // would let it be mistaken for an interrupted write later.
          fs.delete(tmpFile, false);
        } else {
          LOGGER.error("Fail to restore {} from {}, please restore it manually",
              file, backupFile);
        }
      }
      throw new IOException("Fail to rename " + tmpFile + " to " + file);
    }
    if (hasOriginal && !fs.delete(backupFile, false)) {
      LOGGER.warn("Fail to delete backup file {}", backupFile);
    }
  }

  /**
   * Restores files under dir (recursively) whose last {@link #writeFile} was interrupted.
   * A file is restored only when it is missing and both its temp and backup files exist:
   * from the temp file if isComplete accepts its content, otherwise from the backup file.
   * A single leftover file is only logged. Leftover files next to an existing file are kept.
   *
   * @param dir folder to scan recursively
   * @param targetSuffix suffix of the files to restore, e.g. ".zpln"
   * @param isComplete given the file to restore and the content of its temp file, tells whether
   *                   that content is complete
   * @return the restored files
   */
  public List<Path> recoverInterruptedWrites(final Path dir, final String targetSuffix,
      final BiPredicate<Path, String> isComplete) throws IOException {
    return callHdfsOperation(new HdfsOperation<List<Path>>() {
      @Override
      public List<Path> call() throws IOException {
        List<Path> recovered = new ArrayList<>();
        if (!fs.exists(dir)) {
          return recovered;
        }
        Set<Path> missingFiles = new LinkedHashSet<>();
        collectMissingFiles(dir, targetSuffix, missingFiles);
        for (Path file : missingFiles) {
          if (recoverFile(file, isComplete)) {
            recovered.add(file);
          }
        }
        return recovered;
      }
    });
  }

  private void collectMissingFiles(Path folder, String targetSuffix, Set<Path> missingFiles)
      throws IOException {
    for (FileStatus status : fs.listStatus(folder)) {
      Path path = status.getPath();
      if (status.isDirectory()) {
        collectMissingFiles(path, targetSuffix, missingFiles);
        continue;
      }
      for (String suffix : new String[] {TMP_SUFFIX, BACKUP_SUFFIX}) {
        if (path.getName().endsWith(targetSuffix + suffix)) {
          String pathString = path.toString();
          Path file = new Path(pathString.substring(0, pathString.length() - suffix.length()));
          if (!fs.exists(file)) {
            missingFiles.add(file);
          }
        }
      }
    }
  }

  private boolean recoverFile(Path file, BiPredicate<Path, String> isComplete)
      throws IOException {
    Path tmpFile = new Path(file.toString() + TMP_SUFFIX);
    Path backupFile = new Path(file.toString() + BACKUP_SUFFIX);
    // writeFile leaves both files only when it stops between its two renames. A single leftover
    // may belong to a file that was deleted or moved on purpose, so it is not restored.
    if (!fs.exists(tmpFile) || !fs.exists(backupFile)) {
      LOGGER.warn("Found {} without {}, not restoring it automatically. "
          + "Rename it manually if it should be restored.",
          fs.exists(tmpFile) ? tmpFile : backupFile, file);
      return false;
    }
    // The temp file is newer than the backup, but it may be incomplete if the write stopped
    // while it was being written.
    if (isComplete.test(file, readContent(tmpFile)) && fs.rename(tmpFile, file)) {
      fs.delete(backupFile, false);
      LOGGER.warn("Recovered {} from {}", file, tmpFile);
      return true;
    }
    if (fs.rename(backupFile, file)) {
      LOGGER.warn("Recovered {} from {}", file, backupFile);
      return true;
    }
    LOGGER.error("Fail to recover {}, please check {} and {} manually",
        file, tmpFile, backupFile);
    return false;
  }

  private String readContent(Path file) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    IOUtils.copyBytes(fs.open(file), bytes, hadoopConf);
    return bytes.toString(zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_ENCODING));
  }

  public void move(Path src, Path dest) throws IOException {
    callHdfsOperation(() -> {
      fs.rename(src, dest);
      return null;
    });
  }

  private interface HdfsOperation<T> {
    T call() throws IOException;
  }

  public synchronized <T> T callHdfsOperation(final HdfsOperation<T> func) throws IOException {
    if (isSecurityEnabled) {
      try {
        return UserGroupInformation.getCurrentUser().doAs(new PrivilegedExceptionAction<T>() {
          @Override
          public T run() throws Exception {
            return func.call();
          }
        });
      } catch (InterruptedException e) {
        throw new IOException(e);
      }
    } else {
      return func.call();
    }
  }

}
