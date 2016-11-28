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
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.shell.security.ShellSecurityImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shell interpreter for Zeppelin.
 */
public class ShellInterpreter extends Interpreter {
  private static final Logger LOGGER = LoggerFactory.getLogger(ShellInterpreter.class);
  private static final String TIMEOUT_PROPERTY = "shell.command.timeout.millisecs";
  private final boolean isWindows = System.getProperty("os.name").startsWith("Windows");
  private final String shell = isWindows ? "cmd /c" : "bash -c";
  ConcurrentHashMap<String, DefaultExecutor> executors;

  public ShellInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    LOGGER.info("Command timeout property: {}", getProperty(TIMEOUT_PROPERTY));
    executors = new ConcurrentHashMap<>();
    if (!StringUtils.isAnyEmpty(getProperty("zeppelin.shell.auth.type"))) {
      ShellSecurityImpl.createSecureConfiguration(getProperty(), shell);
    }
  }

  @Override
  public void close() {}


  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext contextInterpreter) {
    LOGGER.debug("Run shell command '" + cmd + "'");
    OutputStream outStream = new ByteArrayOutputStream();
    
    CommandLine cmdLine = CommandLine.parse(shell);
    // the Windows CMD shell doesn't handle multiline statements,
    // they need to be delimited by '&&' instead
    if (isWindows) {
      String[] lines = StringUtils.split(cmd, "\n");
      cmd = StringUtils.join(lines, " && ");
    }
    cmdLine.addArgument(cmd, false);

    try {
      DefaultExecutor executor = new DefaultExecutor();
      executor.setStreamHandler(new PumpStreamHandler(outStream, outStream));
      executor.setWatchdog(new ExecuteWatchdog(Long.valueOf(getProperty(TIMEOUT_PROPERTY))));
      executors.put(contextInterpreter.getParagraphId(), executor);
      int exitVal = executor.execute(cmdLine);
      LOGGER.info("Paragraph " + contextInterpreter.getParagraphId() 
        + " return with exit value: " + exitVal);
      return new InterpreterResult(Code.SUCCESS, outStream.toString());
    } catch (ExecuteException e) {
      int exitValue = e.getExitValue();
      LOGGER.error("Can not run " + cmd, e);
      Code code = Code.ERROR;
      String message = outStream.toString();
      if (exitValue == 143) {
        code = Code.INCOMPLETE;
        message += "Paragraph received a SIGTERM.\n";
        LOGGER.info("The paragraph " + contextInterpreter.getParagraphId() 
          + " stopped executing: " + message);
      }
      message += "ExitValue: " + exitValue;
      return new InterpreterResult(code, message);
    } catch (IOException e) {
      LOGGER.error("Can not run " + cmd, e);
      return new InterpreterResult(Code.ERROR, e.getMessage());
    } finally {
      executors.remove(contextInterpreter.getParagraphId());
    }
  }

  @Override
  public void cancel(InterpreterContext context) {
    DefaultExecutor executor = executors.remove(context.getParagraphId());
    if (executor != null) {
      executor.getWatchdog().destroyProcess();
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
  public List<InterpreterCompletion> completion(String buf, int cursor) {
    return null;
  }

}
