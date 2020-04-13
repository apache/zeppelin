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

package org.apache.zeppelin.mongodb;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MongoDB interpreter. It uses the mongo shell to interpret the commands.
 */
public class MongoDbInterpreter extends Interpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(MongoDbInterpreter.class);

  private String shellExtension = StringUtils.EMPTY;

  private static final int SIGTERM_CODE = 143;

  private long commandTimeout = 60000;

  private String dbAddress;

  private int maxConcurrency = 10;

  private Map<String, Executor> runningProcesses =  new HashMap<>();

  public MongoDbInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    shellExtension = new Scanner(MongoDbInterpreter.class.getResourceAsStream("/shell_extension.js"), "UTF-8")
            .useDelimiter("\\A").next();

    commandTimeout = Long.parseLong(getProperty("mongo.shell.command.timeout"));
    maxConcurrency = Integer.parseInt(getProperty("mongo.interpreter.concurrency.max"));

    dbAddress = getProperty("mongo.server.host") + ":" + getProperty("mongo.server.port");

    prepareShellExtension();
  }

  @Override
  public void close() {
    runningProcesses.clear();
    runningProcesses = null;
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public InterpreterResult interpret(String script, InterpreterContext context) {
    LOGGER.debug("Run MongoDB script: {}", script);

    if (StringUtils.isEmpty(script)) {
      return new InterpreterResult(Code.SUCCESS);
    }

    String paragraphId = context.getParagraphId();
    // Write script in a temporary file
    // The script is enriched with extensions
    final File scriptFile = new File(getScriptFileName(paragraphId));
    try {
      FileUtils.write(scriptFile, shellExtension + script);
    }
    catch (IOException e) {
      LOGGER.error("Can not write script in temp file", e);
      return new InterpreterResult(Code.ERROR, e.getMessage());
    }

    InterpreterResult result = new InterpreterResult(InterpreterResult.Code.SUCCESS);

    final DefaultExecutor executor = new DefaultExecutor();
    final ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

    executor.setStreamHandler(new PumpStreamHandler(context.out, errorStream));
    executor.setWatchdog(new ExecuteWatchdog(commandTimeout));

    final CommandLine cmdLine = CommandLine.parse(getProperty("mongo.shell.path"));
    cmdLine.addArgument("--quiet", false);
    cmdLine.addArgument(dbAddress, false);
    cmdLine.addArgument(scriptFile.getAbsolutePath(), false);

    try {
      executor.execute(cmdLine);
      runningProcesses.put(paragraphId, executor);
    }
    catch (ExecuteException e) {
      LOGGER.error("Can not run script in paragraph {}", paragraphId, e);

      final int exitValue = e.getExitValue();
      Code code = Code.ERROR;
      String msg = errorStream.toString();

      if (exitValue == SIGTERM_CODE) {
        code = Code.INCOMPLETE;
        msg = msg + "Paragraph received a SIGTERM.\n";
        LOGGER.info("The paragraph {} stopped executing: {}", paragraphId, msg);
      }

      msg += "ExitValue: " + exitValue;
      result = new InterpreterResult(code, msg);
    }
    catch (IOException e) {
      LOGGER.error("Can not run script in paragraph {}", paragraphId, e);
      result = new InterpreterResult(Code.ERROR, e.getMessage());
    }
    finally {
      FileUtils.deleteQuietly(scriptFile);
      stopProcess(paragraphId);
    }

    return result;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public void cancel(InterpreterContext context) {
    stopProcess(context.getParagraphId());
    FileUtils.deleteQuietly(new File(getScriptFileName(context.getParagraphId())));
  }

  @Override
  public Scheduler getScheduler() {
    LOGGER.info("maxConcurrency is {}", maxConcurrency);
    return SchedulerFactory.singleton().createOrGetParallelScheduler(MongoDbInterpreter.class.getName() + this.hashCode(), maxConcurrency);
  }

  private String getScriptFileName(String paragraphId) {
    return String.format("%s%s.js",getScriptDir(),paragraphId);
  }

  private String getScriptDir(){
    String tmpProperty = System.getProperty("java.io.tmpdir");
    if (!tmpProperty.endsWith(File.separator)){
      tmpProperty += File.separator;
    }

    return tmpProperty + "zeppelin-mongo-scripts" + File.separator;
  }

  private void stopProcess(String paragraphId) {
    if (runningProcesses.containsKey(paragraphId)) {
      final Executor executor = runningProcesses.get(paragraphId);
      final ExecuteWatchdog watchdog = executor.getWatchdog();
      watchdog.destroyProcess();
      runningProcesses.remove(paragraphId);
    }
  }

  /**
   * use placeholders to replace properties
   */
  private void prepareShellExtension(){
    shellExtension = shellExtension.replace("TABLE_LIMIT_PLACEHOLDER", getProperty("mongo.shell.command.table.limit"))
            .replace("TARGET_DB_PLACEHOLDER", getProperty("mongo.server.database"))
            .replace("USER_NAME_PLACEHOLDER", getProperty("mongo.server.username"))
            .replace("PASSWORD_PLACEHOLDER", getProperty("mongo.server.password"))
            .replace("AUTH_DB_PLACEHOLDER", getProperty("mongo.server.authenticationDatabase"));
  }
}