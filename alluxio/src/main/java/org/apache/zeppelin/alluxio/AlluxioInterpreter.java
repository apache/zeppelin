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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import alluxio.cli.fs.FileSystemShell;
import alluxio.cli.fs.FileSystemShellUtils;
import alluxio.client.file.FileSystem;

import org.apache.zeppelin.completer.CompletionType;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;

/**
 * Alluxio interpreter for Zeppelin.
 */
public class AlluxioInterpreter extends Interpreter {

  Logger logger = LoggerFactory.getLogger(AlluxioInterpreter.class);

  protected static final String ALLUXIO_MASTER_HOSTNAME = "alluxio.master.hostname";
  protected static final String ALLUXIO_MASTER_PORT = "alluxio.master.port";

  private FileSystemShell fs;

  private int totalCommands = 0;
  private int completedCommands = 0;

  private final String alluxioMasterHostname;
  private final String alluxioMasterPort;
  protected Set<String> keywords;

  public AlluxioInterpreter(Properties property) {
    super(property);

    alluxioMasterHostname = property.getProperty(ALLUXIO_MASTER_HOSTNAME);
    alluxioMasterPort = property.getProperty(ALLUXIO_MASTER_PORT);
  }

  @Override
  public void open() {
    logger.info("Starting Alluxio shell to connect to " + alluxioMasterHostname +
        " on port " + alluxioMasterPort);

    System.setProperty(ALLUXIO_MASTER_HOSTNAME, alluxioMasterHostname);
    System.setProperty(ALLUXIO_MASTER_PORT, alluxioMasterPort);
    fs = new FileSystemShell();
    keywords = FileSystemShellUtils.loadCommands(FileSystem.Factory.get()).keySet();
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
      int commandResult = 1;
      String[] args = splitAndRemoveEmpty(command, " ");
      commandResult = fs.run(args);
      if (commandResult != 0) {
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
    ArrayList<String> result = new ArrayList<>();
    for (String voice : voices) {
      if (!voice.trim().isEmpty()) {
        result.add(voice);
      }
    }
    return result.toArray(new String[result.size()]);
  }

  private String[] splitAndRemoveEmpty(String[] sts, String splitSeparator) {
    ArrayList<String> result = new ArrayList<>();
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
  public List<InterpreterCompletion> completion(String buf, int cursor,
      InterpreterContext interpreterContext) {
    String[] words = splitAndRemoveEmpty(splitAndRemoveEmpty(buf, "\n"), " ");
    String lastWord = "";
    if (words.length > 0) {
      lastWord = words[ words.length - 1 ];
    }

    List<InterpreterCompletion>  voices = new LinkedList<>();
    for (String command : keywords) {
      if (command.startsWith(lastWord)) {
        voices.add(new InterpreterCompletion(command, command, CompletionType.command.name()));
      }
    }
    return voices;
  }
}
