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

package org.apache.zeppelin.util;

import com.google.gson.Gson;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.file.HDFSCommand;
import org.apache.zeppelin.file.HDFSStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;


/**
 * Class to create / move / delete / write to / read from HDFS filessytem
 */
public class HdfsSite {
  public static final String HDFS_URL = "hdfs.url";
  public static final String HDFS_USER = "hdfs.user";
  public static final String HDFS_MAXLENGTH = "hdfs.maxlength";

  private static Logger logger = LoggerFactory.getLogger(HdfsSite.class);
  public static final String HDFS_NOTEBOOK_DIR = "hdfs.notebook.dir";
  static final String NOTE_JSON = "note.json";
  static final String NOTE_JSON_TEMP = "_note.json";

  ZeppelinConfiguration conf;
  boolean enableWebHDFS = true;
  String hdfsUrl = null;
  String hdfsUser = null;
  int hdfsMaxLength = 0;
  HDFSCommand hdfsCmd = null;


  public HdfsSite(ZeppelinConfiguration conf) throws URISyntaxException {
    this.conf = conf;
    this.hdfsUrl = conf.getString(HDFS_URL, HDFS_URL, "http://localhost:50070/webhdfs/v1/");
    this.hdfsMaxLength = conf.getInt(HDFS_URL, HDFS_MAXLENGTH, 100000);
    this.hdfsUser = System.getenv("HADOOP_USER_NAME");
    if (this.hdfsUser == null) {
      this.hdfsUser = System.getenv("LOGNAME");
    }
    if (this.hdfsUser == null) {

      this.enableWebHDFS = false;
    } else {
      this.hdfsCmd = new HDFSCommand(hdfsUrl, hdfsUser, logger, this.hdfsMaxLength);
      this.enableWebHDFS = exists("/");
    }
  }

  public boolean exists(String path) {
    boolean ret = false;
    HDFSStatus.SingleFileStatus fileStatus;
    Gson gson = new Gson();
    try {
      String notebookStatus = this.hdfsCmd.runCommand(this.hdfsCmd.getFileStatus, path, null);
      fileStatus = gson.fromJson(notebookStatus, HDFSStatus.SingleFileStatus.class);
      ret = fileStatus.FileStatus.modificationTime > 0;
    } catch (Exception e) {
      logger.info("disabled webHDFS. Please check webhdfs configurations");
      ret = false;
    } finally {
      return ret;
    }
  }


  public String[] listFiles(String directory) throws IOException {
    List<String> hdfsNotebook = new ArrayList<String>();
    Gson gson = new Gson();
    String hdfsDirStatus;

    try {
      hdfsDirStatus = this.hdfsCmd.runCommand(this.hdfsCmd.listStatus, directory, null);
      if (hdfsDirStatus != null) {
        HDFSStatus.AllFileStatus allFiles = gson.fromJson(hdfsDirStatus,
            HDFSStatus.AllFileStatus.class);
        if (allFiles != null && allFiles.FileStatuses != null
            && allFiles.FileStatuses.FileStatus != null) {
          for (HDFSStatus.OneFileStatus fs : allFiles.FileStatuses.FileStatus) {
            if ("DIRECTORY".equals(fs.type) && fs.pathSuffix.startsWith("_") == false) {
              hdfsNotebook.add(fs.pathSuffix);
              logger.info("read a notebook from HDFS: " + fs.pathSuffix);
            }
          }
        }
      }
    } catch (Exception e) {
      logger.error("exception occurred during getting notebook from hdfs : ", e);
    }
    return hdfsNotebook.toArray(new String[0]);
  }

  public void delete(String path) throws IOException {
    logger.debug("remove : " + path);

    HDFSCommand.Arg recursive = this.hdfsCmd.new Arg("recursive", "true");
    HDFSCommand.Arg[] args = {recursive};

    try {
      this.hdfsCmd.runCommand(this.hdfsCmd.deleteFile, path, args);
    } catch (Exception e) {
      logger.error("Exception: ", e);
      throw new IOException(e.getCause());
    }
  }

  public void mkdirs(String path) throws IOException {

    try {
      this.hdfsCmd.runCommand(this.hdfsCmd.makeDirectory, path, null);
    } catch (Exception e) {
      logger.error("Exception: ", e);
      throw new IOException(e.getCause());
    }
  }


  public void writeFile(byte[] content, String path) throws IOException {
    try {
      HDFSCommand.Arg dest = this.hdfsCmd.new Arg("overwrite", "true");
      HDFSCommand.Arg[] createArgs = {dest};
      this.hdfsCmd.runCommand(this.hdfsCmd.createWriteFile, path, content, createArgs);
    } catch (Exception e) {
      logger.error("Exception: ", e);
      throw new IOException(e.getCause());
    }
  }

  public void rename(String oldPath, String newPath) throws IOException {
    try {
      HDFSCommand.Arg dest = this.hdfsCmd.new Arg("destination", newPath);
      HDFSCommand.Arg[] renameArgs = {dest};
      this.hdfsCmd.runCommand(this.hdfsCmd.renameFile, oldPath, renameArgs);
    } catch (Exception e) {
      logger.error("Exception: ", e);
      throw new IOException(e.getCause());
    } finally {
    }
  }

  public boolean isDirectory(String path) throws IOException {
    boolean ret = false;
    HDFSStatus.SingleFileStatus fileStatus;
    Gson gson = new Gson();
    try {
      String notebookStatus = this.hdfsCmd.runCommand(this.hdfsCmd.getFileStatus, path, null);
      fileStatus = gson.fromJson(notebookStatus, HDFSStatus.SingleFileStatus.class);
      ret = fileStatus.FileStatus.type.equals("DIRECTORY");
    } catch (Exception e) {
      logger.info("disabled webHDFS. Please check webhdfs configurations");
      ret = false;
    } finally {
      return ret;
    }
  }

  public byte[] readFile(String path) throws IOException {
    byte[] res = new byte[0];
    try {
      res = this.hdfsCmd.runCommand(this.hdfsCmd.openFile, path, null, null).getBytes();
    } catch (Exception e) {
      logger.error("Exception: ", e);
      throw new IOException(e.getCause());
    } finally {
      return res;
    }
  }
}
