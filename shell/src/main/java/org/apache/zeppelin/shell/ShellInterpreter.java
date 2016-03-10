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

package org.apache.zeppelin.shell;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shell interpreter for Zeppelin.
 */
public class ShellInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(ShellInterpreter.class);
  private static final String EXECUTOR_KEY = "executor";
  public static final String SHELL_COMMAND_TIMEOUT = "shell.command.timeout.millisecs";
  public static final String DEFAULT_COMMAND_TIMEOUT = "600000";
  int commandTimeOut;
  private static final boolean isWindows = System
          .getProperty("os.name")
          .startsWith("Windows");
  final String shell = isWindows ? "cmd /c" : "bash -c";

  static {
    Interpreter.register(
            "sh",
            "sh",
            ShellInterpreter.class.getName(),
            new InterpreterPropertyBuilder()
              .add(
                SHELL_COMMAND_TIMEOUT,
                DEFAULT_COMMAND_TIMEOUT,
                "Shell command time out in millisecs. Default = 600000")
              .build()
    );
  }

  public ShellInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    logger.info("Command timeout is set as:", SHELL_COMMAND_TIMEOUT);

    commandTimeOut = Integer.valueOf(getProperty(SHELL_COMMAND_TIMEOUT));
  }

  @Override
  public void close() {}


  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext contextInterpreter) {
    logger.debug("Run shell command '" + cmd + "'");
    CommandLine cmdLine = CommandLine.parse(shell);
    // the Windows CMD shell doesn't handle multiline statements,
    // they need to be delimited by '&&' instead
    if (isWindows) {
      String[] lines = StringUtils.split(cmd, "\n");
      cmd = StringUtils.join(lines, " && ");
    }
    cmdLine.addArgument(cmd, false);
    DefaultExecutor executor = new DefaultExecutor();
    ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
    executor.setStreamHandler(new PumpStreamHandler(contextInterpreter.out, errorStream));
    executor.setWatchdog(new ExecuteWatchdog(commandTimeOut));

    Job runningJob = getRunningJob(contextInterpreter.getParagraphId());
    Map<String, Object> info = runningJob.info();
    info.put(EXECUTOR_KEY, executor);
    try {
      int exitVal = executor.execute(cmdLine);
      logger.info("Paragraph " + contextInterpreter.getParagraphId()
          + "return with exit value: " + exitVal);
      return new InterpreterResult(InterpreterResult.Code.SUCCESS, null);
    } catch (ExecuteException e) {
      int exitValue = e.getExitValue();
      logger.error("Can not run " + cmd, e);
      Code code = Code.ERROR;
      String msg = errorStream.toString();
      if (exitValue == 143) {
        code = Code.INCOMPLETE;
        msg = msg + "Paragraph received a SIGTERM.\n";
        logger.info("The paragraph " + contextInterpreter.getParagraphId()
            + " stopped executing: " + msg);
      }
      msg += "ExitValue: " + exitValue;
      return new InterpreterResult(code, msg);
    } catch (IOException e) {
      logger.error("Can not run " + cmd, e);
      return new InterpreterResult(Code.ERROR, e.getMessage());
    }
  }

  private Job getRunningJob(String paragraphId) {
    Job foundJob = null;
    Collection<Job> jobsRunning = getScheduler().getJobsRunning();
    for (Job job : jobsRunning) {
      if (job.getId().equals(paragraphId)) {
        foundJob = job;
      }
    }
    return foundJob;
  }

  @Override
  public void cancel(InterpreterContext context) {
    Job runningJob = getRunningJob(context.getParagraphId());
    if (runningJob != null) {
      Map<String, Object> info = runningJob.info();
      Object object = info.get(EXECUTOR_KEY);
      if (object != null) {
        Executor executor = (Executor) object;
        ExecuteWatchdog watchdog = executor.getWatchdog();
        watchdog.destroyProcess();
      }
    }
  }
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
    return SchedulerFactory.singleton().createOrGetParallelScheduler(
        ShellInterpreter.class.getName() + this.hashCode(), 10);
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return null;
  }

}
