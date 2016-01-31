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

package org.apache.zeppelin.tachyon;

import java.io.IOException;
import java.io.PrintStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tachyon.conf.TachyonConf;
import tachyon.shell.TfsShell;

/**
 * Tachyon interpreter for Zeppelin.
 */
public class TachyonInterpreter extends Interpreter {
  
  Logger logger = LoggerFactory.getLogger(TachyonInterpreter.class);

  protected static final String TACHYON_MASTER_HOSTNAME = "tachyon.master.hostname";
  protected static final String TACHYON_MASTER_PORT = "tachyon.master.port";

  private TfsShell tfs;

  private int totalCommands = 0;
  private int completedCommands = 0;

  private final String tachyonMasterHostname;
  private final String tachyonMasterPort;

  protected final List<String> keywords = Arrays.asList("cat", "copyFromLocal",
          "copyToLocal", "count", "du", "fileinfo", "free", "getUsedBytes",
          "getCapacityBytes", "load", "loadMetadata", "location", "ls", "lsr",
          "mkdir", "mount", "mv", "pin", "report", "request", "rm", "rmr",
          "setTTL", "unsetTTL", "tail", "touch", "unmount", "unpin");

  public TachyonInterpreter(Properties property) {
    super(property);

    tachyonMasterHostname = property.getProperty(TACHYON_MASTER_HOSTNAME);
    tachyonMasterPort = property.getProperty(TACHYON_MASTER_PORT);
  }

  static {
    Interpreter.register("tachyon", "tachyon",
        TachyonInterpreter.class.getName(),
        new InterpreterPropertyBuilder()
                .add(TACHYON_MASTER_HOSTNAME, "localhost", "Tachyon master hostname")
                .add(TACHYON_MASTER_PORT, "19998", "Tachyon master port")
                .build());
  }

  @Override
  public void open() {
    logger.info("Starting Tachyon shell to connect to " + tachyonMasterHostname +
      " on port " + tachyonMasterPort);

    System.setProperty(TACHYON_MASTER_HOSTNAME, tachyonMasterHostname);
    System.setProperty(TACHYON_MASTER_PORT, tachyonMasterPort);
    tfs = new TfsShell(new TachyonConf());
  }

  @Override
  public void close() {
    logger.info("Closing Tachyon shell");

    try {
      tfs.close();
    } catch (IOException e) {
      logger.error("Cannot close connection", e);
    }
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    String[] lines = splitAndRemoveEmpty(st, "\n");
    return interpret(lines, context);
  }
  
  private InterpreterResult interpret(String[] commands, InterpreterContext context) {
    boolean isSuccess = true;
    totalCommands = commands.length;
    completedCommands = 0;
    
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(baos);
    PrintStream old = System.out;
    
    System.setOut(ps);
    
    for (String command : commands) {
      int commandResuld = 1;
      String[] args = splitAndRemoveEmpty(command, " ");
      if (args.length > 0 && args[0].equals("help")) {
        System.out.println(getCommandList());
      } else {
        commandResuld = tfs.run(args);
      }
      if (commandResuld != 0) {
        isSuccess = false;
        break;
      } else {
        completedCommands += 1;
      }
      System.out.println();
    }

    System.out.flush();
    System.setOut(old);
    
    if (isSuccess) {
      return new InterpreterResult(Code.SUCCESS, baos.toString());
    } else {
      return new InterpreterResult(Code.ERROR, baos.toString());
    }
  }
  
  private String[] splitAndRemoveEmpty(String st, String splitSeparator) {
    String[] voices = st.split(splitSeparator);
    ArrayList<String> result = new ArrayList<String>();
    for (String voice : voices) {
      if (!voice.trim().isEmpty()) {
        result.add(voice);
      }
    }
    return result.toArray(new String[result.size()]);
  }

  private String[] splitAndRemoveEmpty(String[] sts, String splitSeparator) {
    ArrayList<String> result = new ArrayList<String>();
    for (String st : sts) {
      result.addAll(Arrays.asList(splitAndRemoveEmpty(st, splitSeparator)));
    }
    return result.toArray(new String[result.size()]);
  }

  @Override
  public void cancel(InterpreterContext context) { }

  @Override
  public FormType getFormType() {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return completedCommands * 100 / totalCommands;
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    String[] words = splitAndRemoveEmpty(splitAndRemoveEmpty(buf, "\n"), " ");
    String lastWord = "";
    if (words.length > 0) {
      lastWord = words[ words.length - 1 ];
    }
    ArrayList<String> voices = new ArrayList<String>();
    for (String command : keywords) {
      if (command.startsWith(lastWord)) {
        voices.add(command);
      }
    }
    return voices;
  }

  private String getCommandList() {
    StringBuilder sb = new StringBuilder();
    sb.append("Commands list:");
    sb.append("\n\t[help] - List all available commands.");
    sb.append("\n\t[cat <path>] - Print the content of the file to the console.");
    sb.append("\n\t[copyFromLocal <src> <remoteDst>] - Copy the specified file specified " +
            "by \"source path\" to the path specified by \"remote path\". " +
            "This command will fail if \"remote path\" already exists.");
    sb.append("\n\t[copyToLocal <src> <localDst>] - Copy the specified file from the path " +
            "specified by \"remote source\" to a local destination.");
    sb.append("\n\t[count <path>] - Display the number of folders and files matching " +
            "the specified prefix in \"path\".");
    sb.append("\n\t[du <path>] - Display the size of a file or a directory specified " +
            "by the input path.");
    sb.append("\n\t[fileinfo <path>] - Print the information of the blocks of a specified file.");
    sb.append("\n\t[free <file path|folder path>] - Free a file or all files under a " +
            "directory from Tachyon. If the file/directory is also in under storage, " +
            "it will still be available there.");
    sb.append("\n\t[getUsedBytes] - Get number of bytes used in the TachyonFS.");
    sb.append("\n\t[getCapacityBytes] - Get the capacity of the TachyonFS.");
    sb.append("\n\t[load <path>] - Load the data of a file or a directory from under " +
            "storage into Tachyon.");
    sb.append("\n\t[loadMetadata <path>] - Load the metadata of a file or a directory " +
            "from under storage into Tachyon.");
    sb.append("\n\t[location <path>] - Display a list of hosts that have the file data.");
    sb.append("\n\t[ls <path>] - List all the files and directories directly under the " +
            "given path with information such as size.");
    sb.append("\n\t[lsr <path>] - Recursively list all the files and directories under " +
            "the given path with information such as size.");
    sb.append("\n\t[mkdir <path>] - Create a directory under the given path, along with " +
            "any necessary parent directories. This command will fail if the given " +
            "path already exists.");
    sb.append("\n\t[mount <tachyonPath> <ufsURI>] - Mount the underlying file system " +
            "path \"uri\" into the Tachyon namespace as \"path\". The \"path\" is assumed " +
            "not to exist and is created by the operation. No data or metadata is loaded " +
            "from under storage into Tachyon. After a path is mounted, operations on objects " +
            "under the mounted path are mirror to the mounted under storage.");
    sb.append("\n\t[mv <src> <dst>] - Move a file or directory specified by \"source\" " +
            "to a new location \"destination\". This command will fail if " +
            "\"destination\" already exists.");
    sb.append("\n\t[pin <path>] - Pin the given file to avoid evicting it from memory. " +
            "If the given path is a directory, it recursively pins all the files contained " +
            "and any new files created within this directory.");
    sb.append("\n\t[report <path>] - Report to the master that a file is lost.");
    sb.append("\n\t[request <tachyonaddress> <dependencyId>] - Request the file for " +
            "a given dependency ID.");
    sb.append("\n\t[rm <path>] - Remove a file. This command will fail if the given " +
            "path is a directory rather than a file.");
    sb.append("\n\t[rmr <path>] - Remove a file, or a directory with all the files and " +
            "sub-directories that this directory contains.");
    sb.append("\n\t[tail <path>] - Print the last 1KB of the specified file to the console.");
    sb.append("\n\t[touch <path>] - Create a 0-byte file at the specified location.");
    sb.append("\n\t[unmount <tachyonPath>] - Unmount the underlying file system path " +
            "mounted in the Tachyon namespace as \"path\". Tachyon objects under \"path\" " +
            "are removed from Tachyon, but they still exist in the previously " +
            "mounted under storage.");
    sb.append("\n\t[unpin <path>] - Unpin the given file to allow Tachyon to evict " +
            "this file again. If the given path is a directory, it recursively unpins " +
            "all files contained and any new files created within this directory.");
    return sb.toString();
  }
}
