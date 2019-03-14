/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.submarine.hadoop;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.thrift.ParagraphInfo;
import org.apache.zeppelin.submarine.commons.SubmarineConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hadoop FileSystem wrapper. Support both secure and no-secure mode
 */
public class HdfsClient {
  private static Logger LOGGER = LoggerFactory.getLogger(HdfsClient.class);

  private ZeppelinConfiguration zConf = ZeppelinConfiguration.create();
  private Configuration hadoopConf;
  private boolean isSecurityEnabled;
  private FileSystem fs;

  private static Pattern REPL_PATTERN =
      Pattern.compile("(\\s*)%([\\w\\.]+)(\\(.*?\\))?.*", Pattern.DOTALL);

  public HdfsClient(Properties properties) {
    String krb5conf = properties.getProperty(SubmarineConstants.SUBMARINE_HADOOP_KRB5_CONF, "");
    if (!StringUtils.isEmpty(krb5conf)) {
      System.setProperty("java.security.krb5.conf", krb5conf);
    }

    this.hadoopConf = new Configuration();
    // disable checksum for local file system. because interpreter.json may be updated by
    // non-hadoop filesystem api
    // disable caching for file:// scheme to avoid getting LocalFS which does CRC checks
    // this.hadoopConf.setBoolean("fs.file.impl.disable.cache", true);
    this.hadoopConf.set("fs.file.impl", "org.apache.hadoop.hdfs.DistributedFileSystem");
    // UserGroupInformation.setConfiguration(hadoopConf);
    this.isSecurityEnabled = UserGroupInformation.isSecurityEnabled();

    if (isSecurityEnabled) {
      String keytab = properties.getProperty(
          SubmarineConstants.SUBMARINE_HADOOP_KEYTAB, "");
      String principal = properties.getProperty(
          SubmarineConstants.SUBMARINE_HADOOP_PRINCIPAL, "");

      ZeppelinConfiguration zConf = ZeppelinConfiguration.create();
      if (StringUtils.isEmpty(keytab)) {
        keytab = zConf.getString(
            ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_KERBEROS_KEYTAB);
      }
      if (StringUtils.isEmpty(principal)) {
        principal = zConf.getString(
            ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_KERBEROS_PRINCIPAL);
      }
      if (StringUtils.isBlank(keytab) || StringUtils.isBlank(principal)) {
        throw new RuntimeException("keytab and principal can not be empty, keytab: " + keytab
            + ", principal: " + principal);
      }
      try {
        UserGroupInformation.loginUserFromKeytab(principal, keytab);
      } catch (IOException e) {
        throw new RuntimeException("Fail to login via keytab:" + keytab +
        ", principal:" + principal, e);
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
      }
    }

    try {
      this.fs = FileSystem.get(new URI("/"), this.hadoopConf);
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (URISyntaxException e) {
      LOGGER.error(e.getMessage(), e);
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

  // recursive search path, (list folder in sub folder on demand, instead of load all
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

  public void writeFile(final String content, final Path file)
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

  private synchronized <T> T callHdfsOperation(final HdfsOperation<T> func) throws IOException {
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

  public String parseText(String text) {
    String script = "", intpText = "";

    // parse text to get interpreter component
    if (text != null) {
      Matcher matcher = REPL_PATTERN.matcher(text);
      if (matcher.matches()) {
        String headingSpace = matcher.group(1);
        intpText = matcher.group(2);

        if (matcher.groupCount() == 3 && matcher.group(3) != null) {
          String localPropertiesText = matcher.group(3);
          String[] splits = localPropertiesText.substring(1, localPropertiesText.length() - 1)
              .split(",");
          for (String split : splits) {
            String[] kv = split.split("=");
            if (StringUtils.isBlank(split) || kv.length == 0) {
              continue;
            }
            if (kv.length > 2) {
              throw new RuntimeException("Invalid paragraph properties format: " + split);
            }
          }
          script = text.substring(headingSpace.length() + intpText.length() +
              localPropertiesText.length() + 1).trim();
        } else {
          script = text.substring(headingSpace.length() + intpText.length() + 1).trim();
        }
      } else {
        script = text.trim();
      }
    }

    return script;
  }

  public String saveParagraphToFiles(String noteId, List<ParagraphInfo> paragraphInfos,
                                     String dirName, Properties properties)
      throws Exception {
    StringBuffer outputMsg = new StringBuffer();

    String hdfsUploadPath = properties.getProperty(
        SubmarineConstants.SUBMARINE_ALGORITHM_HDFS_PATH, "");

    HashMap<String, StringBuffer> mapParagraph = new HashMap<>();
    for (int i = 0; i < paragraphInfos.size(); i++) {
      ParagraphInfo paragraph = paragraphInfos.get(i);
      String paragraphTitle = paragraph.getParagraphTitle();
      if (org.apache.commons.lang.StringUtils.isEmpty(paragraphTitle)) {
        String message = "WARN: The title of the [" + i
            + "] paragraph is empty and was not submitted to HDFS.\n";
        LOGGER.warn(message);
        outputMsg.append(message);
        continue;
      }
      if (!mapParagraph.containsKey(paragraphTitle)) {
        StringBuffer mergeScript = new StringBuffer();
        mapParagraph.put(paragraphTitle, mergeScript);
      }
      StringBuffer mergeScript = mapParagraph.get(paragraphTitle);
      String parapraphText = paragraph.getParagraphText();
      String text = parseText(parapraphText);
      mergeScript.append(text + "\n\n");
    }

    // Clear all files in the local noteId directory
    if (!org.apache.commons.lang3.StringUtils.isEmpty(dirName)) {
      String noteDir = dirName + "/" + noteId;
      File fileNoteDir = new File(noteDir);
      if (fileNoteDir.exists()) {
        fileNoteDir.delete();
      }
      fileNoteDir.mkdirs();
    }

    // Clear all files in the noteid directory in HDFS
    if (!org.apache.commons.lang3.StringUtils.isEmpty(hdfsUploadPath)) {
      Path hdfsPath = new Path(hdfsUploadPath + "/" + noteId);
      try {
        if (exists(hdfsPath)) {
          delete(hdfsPath);
          tryMkDir(hdfsPath);
        }
      } catch (IOException e) {
        LOGGER.error(e.getMessage(), e);
        throw new Exception(e);
      }
    }

    for (Map.Entry<String, StringBuffer> entry : mapParagraph.entrySet()) {
      try {
        String fileName = entry.getKey();
        String fileContext = entry.getValue().toString();
        String paragraphFile = dirName + "/" + noteId + "/" + fileName;

        // save to local file
        if (!org.apache.commons.lang3.StringUtils.isEmpty(dirName)) {
          File fileParagraph = new File(paragraphFile);
          if (!fileParagraph.exists()) {
            fileParagraph.createNewFile();
          }
          FileWriter writer = new FileWriter(paragraphFile);
          writer.write(fileContext);
          writer.close();
        }

        // save to hdfs
        if (!StringUtils.isEmpty(hdfsUploadPath)) {
          String fileDir = hdfsUploadPath + "/" + noteId + "/" + fileName;
          // upload algorithm file
          LOGGER.info("Commit algorithm to HDFS: {}", fileDir);
          Path filePath = new Path(fileDir);
          writeFile(fileContext, filePath);
        }
      } catch (IOException e) {
        LOGGER.error(e.getMessage(), e);
        throw new Exception(e);
      }
    }

    return outputMsg.toString();
  }
}
