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

package org.apache.zeppelin.alluxio;

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

import alluxio.Configuration;
import alluxio.shell.AlluxioShell;

/**
 * Alluxio interpreter for Zeppelin.
 */
public class AlluxioInterpreter extends Interpreter {
  
  Logger logger = LoggerFactory.getLogger(AlluxioInterpreter.class);

  protected static final String ALLUXIO_MASTER_HOSTNAME = "alluxio.master.hostname";
  protected static final String ALLUXIO_MASTER_PORT = "alluxio.master.port";

  private AlluxioShell fs;

  private int totalCommands = 0;
  private int completedCommands = 0;

  private final String alluxioMasterHostname;
  private final String alluxioMasterPort;

  protected final List<String> keywords = Arrays.asList("cat", "chgrp",
          "chmod", "chown", "copyFromLocal", "copyToLocal", "count",
          "createLineage", "deleteLineage", "du", "fileInfo", "free",
          "getCapacityBytes", "getUsedBytes", "listLineages", "load",
          "loadMetadata", "location", "ls", "mkdir", "mount", "mv",
          "persist", "pin", "report", "rm", "setTtl", "tail", "touch",
          "unmount", "unpin", "unsetTtl");

  public AlluxioInterpreter(Properties property) {
    super(property);

    alluxioMasterHostname = property.getProperty(ALLUXIO_MASTER_HOSTNAME);
    alluxioMasterPort = property.getProperty(ALLUXIO_MASTER_PORT);
  }

  static {
    Interpreter.register("alluxio", "alluxio",
        AlluxioInterpreter.class.getName(),
        new InterpreterPropertyBuilder()
                .add(ALLUXIO_MASTER_HOSTNAME, "localhost", "Alluxio master hostname")
                .add(ALLUXIO_MASTER_PORT, "19998", "Alluxio master port")
                .build());
  }

  @Override
  public void open() {
    logger.info("Starting Alluxio shell to connect to " + alluxioMasterHostname +
      " on port " + alluxioMasterPort);

    System.setProperty(ALLUXIO_MASTER_HOSTNAME, alluxioMasterHostname);
    System.setProperty(ALLUXIO_MASTER_PORT, alluxioMasterPort);
    fs = new AlluxioShell(new Configuration());
  }

  @Override
  public void close() {
    logger.info("Closing Alluxio shell");

    try {
      fs.close();
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
        commandResuld = fs.run(args);
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
    sb.append("\n\t[cat <path>] - Prints the file's contents to the console.");
    sb.append("\n\t[chgrp [-R] <group> <path>] - Changes the group of a file or directory " +
            "specified by args. Specify -R to change the group recursively.");
    sb.append("\n\t[chmod -R <mode> <path>] - Changes the permission of a file or directory " +
            "specified by args. Specify -R to change the permission recursively.");
    sb.append("\n\t[chown -R <owner> <path>] - Changes the owner of a file or directory " +
            "specified by args. Specify -R to change the owner recursively.");
    sb.append("\n\t[copyFromLocal <src> <remoteDst>] - Copies a file or a directory from " +
            "local filesystem to Alluxio filesystem.");
    sb.append("\n\t[copyToLocal <src> <localDst>] - Copies a file or a directory from the " +
            "Alluxio filesystem to the local filesystem.");
    sb.append("\n\t[count <path>] - Displays the number of files and directories matching " +
            "the specified prefix.");
    sb.append("\n\t[createLineage <inputFile1,...> <outputFile1,...> " +
            "[<cmd_arg1> <cmd_arg2> ...]] - Creates a lineage.");
    sb.append("\n\t[deleteLineage <lineageId> <cascade(true|false)>] - Deletes a lineage. If " +
            "cascade is specified as true, dependent lineages will also be deleted.");
    sb.append("\n\t[du <path>] - Displays the size of the specified file or directory.");
    sb.append("\n\t[fileInfo <path>] - Displays all block info for the specified file.");
    sb.append("\n\t[free <file path|folder path>] - Removes the file or directory(recursively) " +
            "from Alluxio memory space.");
    sb.append("\n\t[getCapacityBytes] - Gets the capacity of the Alluxio file system.");
    sb.append("\n\t[getUsedBytes] - Gets number of bytes used in the Alluxio file system.");
    sb.append("\n\t[listLineages] - Lists all lineages.");
    sb.append("\n\t[load <path>] - Loads a file or directory in Alluxio space, makes it " +
            "resident in memory.");
    sb.append("\n\t[loadMetadata <path>] - Loads metadata for the given Alluxio path from the " +
            "under file system.");
    sb.append("\n\t[location <path>] - Displays the list of hosts storing the specified file.");
    sb.append("\n\t[ls [-R] <path>] - Displays information for all files and directories " +
            "directly under the specified path. Specify -R to display files and " +
            "directories recursively.");
    sb.append("\n\t[mkdir <path1> [path2] ... [pathn]] - Creates the specified directories, " +
            "including any parent directories that are required.");
    sb.append("\n\t[mount <alluxioPath> <ufsURI>] - Mounts a UFS path onto an Alluxio path.");
    sb.append("\n\t[mv <src> <dst>] - Renames a file or directory.");
    sb.append("\n\t[persist <alluxioPath>] - Persists a file or directory currently stored " +
            "only in Alluxio to the UnderFileSystem.");
    sb.append("\n\t[pin <path>] - Pins the given file or directory in memory (works " +
            "recursively for directories). Pinned files are never evicted from memory, unless " +
            "TTL is set.");
    sb.append("\n\t[report <path>] - Reports to the master that a file is lost.");
    sb.append("\n\t[rm [-R] <path>] - Removes the specified file. Specify -R to remove file or " +
            "directory recursively.");
    sb.append("\n\t[setTtl <path> <time to live(in milliseconds)>] - Sets a new TTL value for " +
            "the file at path.");
    sb.append("\n\t[tail <path>] - Prints the file's last 1KB of contents to the console.");
    sb.append("\n\t[touch <path>] - Creates a 0 byte file. The file will be written to the " +
            "under file system.");
    sb.append("\n\t[unmount <alluxioPath>] - Unmounts an Alluxio path.");
    sb.append("\n\t[unpin <path>] - Unpins the given file or folder from memory " +
            "(works recursively for a directory).");
    sb.append("\n\\t[unsetTtl <path>] - Unsets the TTL value for the given path.");
    sb.append("\n\t[unpin <path>] - Unpin the given file to allow Alluxio to evict " +
            "this file again. If the given path is a directory, it recursively unpins " +
            "all files contained and any new files created within this directory.");
    return sb.toString();
  }
}
