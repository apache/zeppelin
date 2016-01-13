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
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.BufferedReader;
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

  private static final String TACHYON_MASTER_HOSTNAME = "tachyon.master.hostname";
  private static final String TACHYON_MASTER_PORT = "tachyon.master.port";

  private TfsShell tfs;

  private int totalCommands = 0;
  private int completedCommands = 0;

  private String tachyonMasterHostname;
  private String tachyonMasterPort;

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
      String[] args = splitAndRemoveEmpty(command, " ");
      int commandResuld = tfs.run(args);
      System.out.println();
      if (commandResuld != 0) {
        isSuccess = false;
        break;
      } else {
        completedCommands += 1;
      }
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
    ArrayList<String> voices = new ArrayList<String>();
    try {
      String[] keywords = getCompletionKeywords();
      for (String command : keywords) {
        if (command.startsWith(buf)) {
          voices.add(command);
        }
      }
    } catch (IOException e) {
      logger.error("Cannot create Tachyon completer", e);
    }
    return voices;
  }
  
  private String[] getCompletionKeywords() throws IOException {
    return new BufferedReader(new InputStreamReader(
        TachyonInterpreter.class.getResourceAsStream("/tachyon.keywords")))
            .readLine().split(",");
  }
      
}
