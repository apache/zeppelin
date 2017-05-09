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
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.fs.*;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.file.HDFSCommand;
import org.apache.zeppelin.file.HDFSFileInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.apache.zeppelin.file.HDFSFileInterpreter.HDFS_MAXLENGTH;
import static org.apache.zeppelin.file.HDFSFileInterpreter.HDFS_URL;

/**
 * Class to create / move / delete / write to / read from HDFS filessytem
 */
public class HdfsSite {
  private static Logger logger = LoggerFactory.getLogger(HdfsSite.class);
  public static final String HDFS_NOTEBOOK_DIR = "hdfs.notebook.dir";
  static final String NOTE_JSON = "note.json";
  static final String NOTE_JSON_TEMP = "_note.json";

  ZeppelinConfiguration conf;
  boolean enableWebHDFS = true;
  String hdfsUrl = null;
  String hdfsUser = null;
  int hdfsMaxLength = 0;
  String hdfsNotebookDir = null;
  HDFSCommand hdfsCmd = null;


  public HdfsSite(ZeppelinConfiguration conf) throws URISyntaxException {
    this.conf = conf;
    this.hdfsUrl = conf.getString(HDFS_URL, HDFS_URL, "http://localhost:50070/webhdfs/v1/");
    this.hdfsMaxLength = conf.getInt(HDFS_URL, HDFS_MAXLENGTH, 100000);
    this.hdfsNotebookDir = removeProtocol(conf.getString(HDFS_URL, HDFS_NOTEBOOK_DIR, "/tmp"));
    this.hdfsUser = System.getenv("HADOOP_USER_NAME");
    if (this.hdfsUser == null) {
      this.hdfsUser = System.getenv("LOGNAME");
    }
    if (this.hdfsUser == null) {

      this.enableWebHDFS = false;
    } else {
      this.hdfsCmd = new HDFSCommand(hdfsUrl, hdfsUser, logger, this.hdfsMaxLength);
      this.enableWebHDFS = checkWebHDFS();
    }
  }

  public String removeProtocol(String hdfsUrl) {
    String newUrl = hdfsUrl.replaceAll("/$", "");
    if (newUrl.startsWith("hdfs://")) {
      return "/" + newUrl.replaceAll("^hdfs://", "").split("/", 2)[1];
    } else {
      return newUrl;
    }
  }

  private boolean checkWebHDFS() {
    boolean ret = true;

    HDFSFileInterpreter.OneFileStatus fileStatus;
    Gson gson = new Gson();
    try {
      String notebookStatus = this.hdfsCmd.runCommand(this.hdfsCmd.getFileStatus, "/", null);
      fileStatus = gson.fromJson(notebookStatus, HDFSFileInterpreter.OneFileStatus.class);
      long modificationTime = fileStatus.modificationTime;
    } catch (Exception e) {
      logger.info("disabled webHDFS. Please check webhdfs configurations");
      ret = false;
    } finally {
      return ret;
    }
  }


  public String[] listFiles() throws IOException {
    listFiles(this.hdfsNotebookDir);
  }

  public String[] listFiles(String directory) throws IOException {
    List<String> hdfsNotebook = new ArrayList<String>();
    Gson gson = new Gson();
    String hdfsDirStatus;

    try {
      sp
          hdfsDirStatus = this.hdfsCmd.runCommand(this.hdfsCmd.listStatus, directory, null);
      if (hdfsDirStatus != null) {
        HDFSFileInterpreter.AllFileStatus allFiles = gson.fromJson(hdfsDirStatus, HDFSFileInterpreter.AllFileStatus.class);
        if (allFiles != null && allFiles.FileStatuses != null
            && allFiles.FileStatuses.FileStatus != null) {
          for (HDFSFileInterpreter.OneFileStatus fs : allFiles.FileStatuses.FileStatus) {
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
    hdfsNotebook.toArray(new String[0]);
  }

  public void delete(String noteId) throws IOException {
    String noteDir = this.hdfsNotebookDir + "/" + noteId;
    logger.debug("remove noteDir: " + noteDir);

    HDFSCommand.Arg recursive = this.hdfsCmd.new Arg("recursive", "true");
    HDFSCommand.Arg[] args = {recursive};

    try {
      this.hdfsCmd.runCommand(this.hdfsCmd.deleteFile, noteDir, args);
    } catch (Exception e) {
      logger.error("Exception: ", e);
      throw new IOException(e.getCause());
    }
  }

  public void mkdirs(String noteId) throws IOException {
    String noteDir = this.hdfsNotebookDir + "/" + noteId;
    try {
      this.hdfsCmd.runCommand(this.hdfsCmd.makeDirectory, noteDir, null);
    } catch (Exception e) {
      logger.error("Exception: ", e);
      throw new IOException(e.getCause());
    }
  }

  private void uploadNoteToHDFS(String noteId) throws IOException {
    String localNotebook = super.getRootDir() + "/" + noteId + "/" + NOTE_JSON;
    FileObject localNote = super.getRootDir().resolveFile(noteId + "/" + NOTE_JSON);
    String noteDir = this.hdfsNotebookDir + "/" + noteId;
    String notebook = noteDir + "/" + NOTE_JSON;
    String newNotebook = noteDir + "/" + NOTE_JSON_TEMP;
    logger.debug("localNotebook: {}\tnotebook: {}", localNotebook, notebook);

    try {
      this.hdfsCmd.runCommand(this.hdfsCmd.makeDirectory, noteDir, null);
      this.hdfsCmd.runCommand(this.hdfsCmd.createWriteFile, newNotebook, localNote, null);
      this.hdfsCmd.runCommand(this.hdfsCmd.deleteFile, notebook, null);
      Arg dest = this.hdfsCmd.new Arg("destination", notebook);
      Arg[] renameArgs = {dest};
      this.hdfsCmd.runCommand(this.hdfsCmd.renameFile, newNotebook, renameArgs);
    } catch (Exception e) {
      logger.error("Exception: ", e);
      throw new IOException(e.getCause());
    }
  }


  /**
   * @param content data to write
   * @param path    absolute path without scheme
   * @throws IOException
   */
  public void writeFile(byte[] content, String noteId) throws IOException {
    FileSystem fs = null;
    FSDataOutputStream fout = null;
    try {
      fs = FileSystem.get(conf);
      fout = fs.create(path, true);
      fout.write(content);
    } finally {
      if (fout != null)
        fout.close();
      if (fs != null)
        fs.close();
    }
  }

  /**
   * @param oldPath file to rename
   * @param newPath new file name
   * @throws IOException
   */
  public void rename(Path oldPath, Path newPath) throws IOException {
    FileSystem fs = null;
    try {
      fs = FileSystem.get(conf);
      fs.rename(oldPath, newPath);
    } finally {
      if (fs != null)
        fs.close();
    }
  }

  /**
   * @param path
   * @return
   * @throws IOException
   */
  public boolean exists(Path path) throws IOException {
    FileSystem fs = null;
    try {
      fs = FileSystem.get(conf);
      return fs.exists(path);
    } finally {
      if (fs != null)
        fs.close();
    }
  }

  /**
   * @param path
   * @return
   * @throws IOException
   */
  public boolean isDirectory(Path path) throws IOException {
    FileSystem fs = null;
    try {
      fs = FileSystem.get(conf);
      return fs.isDirectory(path);
    } finally {
      if (fs != null)
        fs.close();
    }
  }

  /**
   * @param path
   * @return
   * @throws IOException
   */
  public byte[] readFile(Path path) throws IOException {
    FileSystem fs = null;
    BufferedReader br = null;
    FSDataInputStream in = null;
    try {
      fs = FileSystem.get(conf);
      long fileLen = fs.getFileStatus(path).getLen();
      byte[] toRead = new byte[(int) fileLen];
      in = fs.open(path);
      in.readFully(0, toRead);
      return toRead;
    } finally {
      if (in != null)
        in.close();
      if (br != null)
        br.close();
      if (fs != null)
        fs.close();
    }
  }
}
