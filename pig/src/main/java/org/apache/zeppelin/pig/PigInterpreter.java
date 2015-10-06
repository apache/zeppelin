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

package org.apache.zeppelin.pig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pig interpreter for Zeppelin.
 *
 * @author abajwa-hw
 *
 */
public class PigInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(PigInterpreter.class);
  int commandTimeOut = 600000;

  static final String PIG_START_ARGS = "pig.start.args";
  static final String DEFAULT_START_ARGS = "-useHCatalog -exectype tez";

  static {
    Interpreter.register(
      "pig",
      "pig",
      PigInterpreter.class.getName(),
      new InterpreterPropertyBuilder()
        .add(PIG_START_ARGS, DEFAULT_START_ARGS, "Starting arguments")
        .build()
    );
  }

  public PigInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {}

  @Override
  public void close() {}


  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext contextInterpreter) {

    CommandLine cmdLine = CommandLine.parse("pig");

    // add any arguments specified
    String startArgs = getProperty((PIG_START_ARGS).trim());
    if (startArgs.length() > 0){
      logger.info("Start arguments passed to pig: " + startArgs);
      List<String> argList = Arrays.asList(startArgs.split("\\s+"));
      for (String arg : argList) {
        cmdLine.addArgument(arg, false);
      }
    }

    logger.info("Run pig command '" + cmd + "'");
    long start = System.currentTimeMillis();
    cmdLine.addArgument("-e", false);
    cmdLine.addArgument(cmd, false);
    DefaultExecutor executor = new DefaultExecutor();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    executor.setStreamHandler(new PumpStreamHandler(outputStream));

    executor.setWatchdog(new ExecuteWatchdog(commandTimeOut));
    try {
      int exitValue = executor.execute(cmdLine);
      return new InterpreterResult(InterpreterResult.Code.SUCCESS, outputStream.toString());
    } catch (ExecuteException e) {
      logger.error("Can not run " + cmd, e);
      return new InterpreterResult(Code.ERROR, e.getMessage());
    } catch (IOException e) {
      logger.error("Can not run " + cmd, e);
      return new InterpreterResult(Code.ERROR, e.getMessage());
    }
  }

  @Override
  public void cancel(InterpreterContext context) {}

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler(
        PigInterpreter.class.getName() + this.hashCode());
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return null;
  }

}

