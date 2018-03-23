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

package org.apache.zeppelin.storage;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RawLocalFileSystem;
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
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;


/**
 * Hadoop FileSystem wrapper. Support both secure and no-secure mode
 */
public class FileSystemStorage {

  private static Logger LOGGER = LoggerFactory.getLogger(FileSystemStorage.class);

  private ZeppelinConfiguration zConf;
  private Configuration hadoopConf;
  private boolean isSecurityEnabled = false;
  private FileSystem fs;

  public FileSystemStorage(ZeppelinConfiguration zConf, String path) throws IOException {
    this.zConf = zConf;
    this.hadoopConf = new Configuration();
    // disable checksum for local file system. because interpreter.json may be updated by
    // non-hadoop filesystem api
    this.hadoopConf.set("fs.file.impl", RawLocalFileSystem.class.getName());
    this.isSecurityEnabled = UserGroupInformation.isSecurityEnabled();

    if (isSecurityEnabled) {
      String keytab = zConf.getString(
          ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_KERBEROS_KEYTAB);
      String principal = zConf.getString(
          ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_KERBEROS_PRINCIPAL);
      if (StringUtils.isBlank(keytab) || StringUtils.isBlank(principal)) {
        throw new IOException("keytab and principal can not be empty, keytab: " + keytab
            + ", principal: " + principal);
      }
      UserGroupInformation.loginUserFromKeytab(principal, keytab);
    }

    this.fs = new Path(path).getFileSystem(hadoopConf);

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
          LOGGER.info("Create dir {} in hdfs", dir.toString());
        }
        if (fs.isFile(dir)) {
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
        LOGGER.debug("Read from file: " + file);
        ByteArrayOutputStream noteBytes = new ByteArrayOutputStream();
        IOUtils.copyBytes(fs.open(file), noteBytes, hadoopConf);
        return new String(noteBytes.toString(
            zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_ENCODING)));
      }
    });
  }

  public void writeFile(final String content, final Path file, boolean writeTempFileFirst)
      throws IOException {
    callHdfsOperation(new HdfsOperation<Void>() {
      @Override
      public Void call() throws IOException {
        InputStream in = new ByteArrayInputStream(content.getBytes(
            zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_ENCODING)));
        Path tmpFile = new Path(file.toString() + ".tmp");
        IOUtils.copyBytes(in, fs.create(tmpFile), hadoopConf);
        fs.delete(file, true);
        fs.rename(tmpFile, file);
        return null;
      }
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
