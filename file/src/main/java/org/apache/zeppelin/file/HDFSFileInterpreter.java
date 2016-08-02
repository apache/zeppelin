/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.file;

import java.text.SimpleDateFormat;
import java.util.*;

import com.google.gson.Gson;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;

/**
 * HDFS implementation of File interpreter for Zeppelin.
 *
 */
public class HDFSFileInterpreter extends FileInterpreter {
  static final String HDFS_URL = "hdfs.url";
  static final String HDFS_USER = "hdfs.user";
  static final String HDFS_MAXLENGTH = "hdfs.maxlength";

  Exception exceptionOnConnect = null;
  HDFSCommand cmd = null;
  Gson gson = null;

  public void prepare() {
    String userName = getProperty(HDFS_USER);
    String hdfsUrl = getProperty(HDFS_URL);
    int i = Integer.parseInt(getProperty(HDFS_MAXLENGTH));
    cmd = new HDFSCommand(hdfsUrl, userName, logger, i);
    gson = new Gson();
  }

  public HDFSFileInterpreter(Properties property){
    super(property);
    prepare();
  }

  /**
   * Status of one file
   *
   * matches returned JSON
   */
  public class OneFileStatus {
    public long accessTime;
    public int blockSize;
    public int childrenNum;
    public int fileId;
    public String group;
    public long length;
    public long modificationTime;
    public String owner;
    public String pathSuffix;
    public String permission;
    public int replication;
    public int storagePolicy;
    public String type;
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("\nAccessTime = " + accessTime);
      sb.append("\nBlockSize = " + blockSize);
      sb.append("\nChildrenNum = " + childrenNum);
      sb.append("\nFileId = " + fileId);
      sb.append("\nGroup = " + group);
      sb.append("\nLength = " + length);
      sb.append("\nModificationTime = " + modificationTime);
      sb.append("\nOwner = " + owner);
      sb.append("\nPathSuffix = " + pathSuffix);
      sb.append("\nPermission = " + permission);
      sb.append("\nReplication = " + replication);
      sb.append("\nStoragePolicy = " + storagePolicy);
      sb.append("\nType = " + type);
      return sb.toString();
    }
  }

  /**
   * Status of one file
   *
   * matches returned JSON
   */
  public class SingleFileStatus {
    public OneFileStatus FileStatus;
  }

  /**
   * Status of all files in a directory
   *
   * matches returned JSON
   */
  public class MultiFileStatus {
    public OneFileStatus[] FileStatus;
  }

  /**
   * Status of all files in a directory
   *
   * matches returned JSON
   */
  public class AllFileStatus {
    public MultiFileStatus FileStatuses;
  }

  // tests whether we're able to connect to HDFS

  private void testConnection() {
    try {
      if (isDirectory("/"))
        logger.info("Successfully created WebHDFS connection");
    } catch (Exception e) {
      logger.error("testConnection: Cannot open WebHDFS connection. Bad URL: " + "/", e);
      exceptionOnConnect = e;
    }
  }

  @Override
  public void open() {
    testConnection();
  }

  @Override
  public void close() {
  }

  private String listDir(String path) throws Exception {
    return cmd.runCommand(cmd.listStatus, path, null);
  }

  private String listPermission(OneFileStatus fs){
    StringBuilder sb = new StringBuilder();
    sb.append(fs.type.equalsIgnoreCase("Directory") ? 'd' : '-');
    int p = Integer.parseInt(fs.permission, 16);
    sb.append(((p & 0x400) == 0) ? '-' : 'r');
    sb.append(((p & 0x200) == 0) ? '-' : 'w');
    sb.append(((p & 0x100) == 0) ? '-' : 'x');
    sb.append(((p & 0x40)  == 0) ? '-' : 'r');
    sb.append(((p & 0x20)  == 0) ? '-' : 'w');
    sb.append(((p & 0x10)  == 0) ? '-' : 'x');
    sb.append(((p & 0x4)   == 0) ? '-' : 'r');
    sb.append(((p & 0x2)   == 0) ? '-' : 'w');
    sb.append(((p & 0x1)   == 0) ? '-' : 'x');
    return sb.toString();
  }
  private String listDate(OneFileStatus fs) {
    return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(fs.modificationTime));
  }
  private String ListOne(String path, OneFileStatus fs) {
    if (args.flags.contains(new Character('l'))) {
      StringBuilder sb = new StringBuilder();
      sb.append(listPermission(fs) + "\t");
      sb.append(((fs.replication == 0) ? "-" : fs.replication) + "\t ");
      sb.append(fs.owner + "\t");
      sb.append(fs.group + "\t");
      if (args.flags.contains(new Character('h'))){ //human readable
        sb.append(humanReadableByteCount(fs.length) + "\t\t");
      } else {
        sb.append(fs.length + "\t");
      }
      sb.append(listDate(fs) + "GMT\t");
      sb.append((path.length() == 1) ? path + fs.pathSuffix : path + '/' + fs.pathSuffix);
      return sb.toString();
    }
    return fs.pathSuffix;
  }

  private String humanReadableByteCount(long bytes) {
    int unit = 1024;
    if (bytes < unit) return bytes + " B";
    int exp = (int) (Math.log(bytes) / Math.log(unit));
    String pre = "KMGTPE".charAt(exp - 1) + "";
    return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
  }

  public String listFile(String filePath) {
    try {
      String str = cmd.runCommand(cmd.getFileStatus, filePath, null);
      SingleFileStatus sfs = gson.fromJson(str, SingleFileStatus.class);
      if (sfs != null) {
        return ListOne(filePath, sfs.FileStatus);
      }
    } catch (Exception e) {
      logger.error("listFile: " + filePath, e);
    }
    return "No such File or directory";
  }

  public String listAll(String path) {
    String all = "";
    if (exceptionOnConnect != null)
      return "Error connecting to provided endpoint.";
    try {
      //see if directory.
      if (isDirectory(path)) {
        String sfs = listDir(path);
        if (sfs != null) {
          AllFileStatus allFiles = gson.fromJson(sfs, AllFileStatus.class);

          if (allFiles != null &&
                  allFiles.FileStatuses != null &&
                  allFiles.FileStatuses.FileStatus != null)
          {
            for (OneFileStatus fs : allFiles.FileStatuses.FileStatus)
              all = all + ListOne(path, fs) + '\n';
          }
        }
        return all;
      } else {
        return listFile(path);
      }
    } catch (Exception e) {
      logger.error("listall: listDir " + path, e);
      throw new InterpreterException("Could not find file or directory:\t" + path);
    }
  }

  public boolean isDirectory(String path) {
    boolean ret = false;
    if (exceptionOnConnect != null)
      return ret;
    try {
      String str = cmd.runCommand(cmd.getFileStatus, path, null);
      SingleFileStatus sfs = gson.fromJson(str, SingleFileStatus.class);
      if (sfs != null)
        return sfs.FileStatus.type.equals("DIRECTORY");
    } catch (Exception e) {
      logger.error("IsDirectory: " + path, e);
      return false;
    }
    return ret;
  }


  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor) {
    logger.info("Completion request at position\t" + cursor + " in string " + buf);
    final List<InterpreterCompletion> suggestions = new ArrayList<>();
    if (StringUtils.isEmpty(buf)) {
      suggestions.add(new InterpreterCompletion("ls", "ls"));
      suggestions.add(new InterpreterCompletion("cd", "cd"));
      suggestions.add(new InterpreterCompletion("pwd", "pwd"));
      return suggestions;
    }

    //part of a command == no spaces
    if (buf.split(" ").length == 1){
      if ("cd".contains(buf)) suggestions.add(new InterpreterCompletion("cd", "cd"));
      if ("ls".contains(buf)) suggestions.add(new InterpreterCompletion("ls", "ls"));
      if ("pwd".contains(buf)) suggestions.add(new InterpreterCompletion("pwd", "pwd"));

      return suggestions;
    }


    // last word will contain the path we're working with.
    String lastToken = buf.substring(buf.lastIndexOf(" ") + 1);
    if (lastToken.startsWith("-")) { //flag not path
      return null;
    }

    String localPath = ""; //all things before the last '/'
    String unfinished = lastToken; //unfished filenames or directories
    if (lastToken.contains("/")) {
      localPath = lastToken.substring(0, lastToken.lastIndexOf('/') + 1);
      unfinished = lastToken.substring(lastToken.lastIndexOf('/') + 1);
    }
    String globalPath = getNewPath(localPath); //adjust for cwd

    if (isDirectory(globalPath)){
      try {
        String fileStatusString = listDir(globalPath);
        if (fileStatusString != null) {
          AllFileStatus allFiles = gson.fromJson(fileStatusString, AllFileStatus.class);

          if (allFiles != null &&
                  allFiles.FileStatuses != null &&
                  allFiles.FileStatuses.FileStatus != null)
          {
            for (OneFileStatus fs : allFiles.FileStatuses.FileStatus) {
              if (fs.pathSuffix.contains(unfinished)) {

                //only suggest the text after the last .
                String beforeLastPeriod = unfinished.substring(0, unfinished.lastIndexOf('.') + 1);
                //beforeLastPeriod should be the start of fs.pathSuffix, so take the end of it.
                String suggestedFinish = fs.pathSuffix.substring(beforeLastPeriod.length());
                suggestions.add(new InterpreterCompletion(suggestedFinish, suggestedFinish));
              }
            }
            return suggestions;
          }
        }
      } catch (Exception e) {
        logger.error("listall: listDir " + globalPath, e);
        return null;
      }
    } else {
      logger.info("path is not a directory.  No values suggested.");
    }

    //Error in string.
    return null;
  }
}
