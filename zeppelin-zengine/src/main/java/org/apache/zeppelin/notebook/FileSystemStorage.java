package org.apache.zeppelin.notebook;

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

  // only do UserGroupInformation.loginUserFromKeytab one time, otherwise you will still get
  // your ticket expired.
  static {
    if (UserGroupInformation.isSecurityEnabled()) {
      ZeppelinConfiguration zConf = ZeppelinConfiguration.create();
      String keytab = zConf.getString(
          ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_KERBEROS_KEYTAB);
      String principal = zConf.getString(
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
    // disable checksum for local file system. because interpreter.json may be updated by
    // non-hadoop filesystem api
    // disable caching for file:// scheme to avoid getting LocalFS which does CRC checks
    this.hadoopConf.setBoolean("fs.file.impl.disable.cache", true);
    this.hadoopConf.set("fs.file.impl", RawLocalFileSystem.class.getName());
    this.isSecurityEnabled = UserGroupInformation.isSecurityEnabled();

    try {
      this.fs = FileSystem.get(new URI(path), this.hadoopConf);
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }
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
              LOGGER.warn("Unknown file: " + path.getPath());
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
