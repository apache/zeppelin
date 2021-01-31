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

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.ZeppelinContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.KerberosInterpreter;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;

/**
 * Shell interpreter for Zeppelin.
 */
public class ShellInterpreter extends KerberosInterpreter {
  private static final Logger LOGGER = LoggerFactory.getLogger(ShellInterpreter.class);

  private static final String TIMEOUT_PROPERTY = "shell.command.timeout.millisecs";
  private static final String TIMEOUT_CHECK_INTERVAL_PROPERTY = "shell.command.timeout.check.interval";
  private static final String DEFAULT_TIMEOUT = "60000";
  private static final String DEFAULT_CHECK_INTERVAL = "10000";
  private static final String DIRECTORY_USER_HOME = "shell.working.directory.user.home";

  private final boolean isWindows = System.getProperty("os.name").startsWith("Windows");
  private final String shell = isWindows ? "cmd /c" : "bash -c";
  private ConcurrentHashMap<String, DefaultExecutor> executorMap;
  private ConcurrentHashMap<String, InterpreterContext> contextMap;
  private ScheduledExecutorService shellOutputCheckExecutor = Executors.newSingleThreadScheduledExecutor();

  public ShellInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    super.open();
    long timeoutThreshold = Long.parseLong(getProperty(TIMEOUT_PROPERTY, DEFAULT_TIMEOUT));
    long timeoutCheckInterval = Long.parseLong(getProperty(TIMEOUT_CHECK_INTERVAL_PROPERTY, DEFAULT_CHECK_INTERVAL));
    LOGGER.info("Command timeout property: {}", timeoutThreshold);
    executorMap = new ConcurrentHashMap<>();
    contextMap = new ConcurrentHashMap<>();

    shellOutputCheckExecutor.scheduleAtFixedRate(() -> {
      for (Map.Entry<String, DefaultExecutor> entry : executorMap.entrySet()) {
        String paragraphId = entry.getKey();
        DefaultExecutor executor = entry.getValue();
        InterpreterContext context = contextMap.get(paragraphId);
        if (context == null) {
          LOGGER.warn("No InterpreterContext associated with paragraph: {}", paragraphId);
          continue;
        }
        if ((System.currentTimeMillis() - context.out.getLastWriteTimestamp()) > timeoutThreshold) {
          LOGGER.info("No output for paragraph {} for the last {} milli-seconds, so kill it",
                  paragraphId, timeoutThreshold);
          executor.getWatchdog().stop();
        }
      }
    }, timeoutCheckInterval, timeoutCheckInterval, TimeUnit.MILLISECONDS);
  }

  @Override
  public void close() {
    super.close();
    for (String executorKey : executorMap.keySet()) {
      DefaultExecutor executor = executorMap.remove(executorKey);
      if (executor != null) {
        try {
          executor.getWatchdog().destroyProcess();
        } catch (Exception e){
          LOGGER.error("error destroying executor for paragraphId: " + executorKey, e);
        }
      }
    }
  }

  @Override
  protected boolean isInterpolate() {
    return Boolean.parseBoolean(getProperty("zeppelin.shell.interpolation", "false"));
  }

  @Override
  public ZeppelinContext getZeppelinContext() {
    return null;
  }

  @Override
  public InterpreterResult internalInterpret(String cmd,
                                             InterpreterContext context) {
    LOGGER.debug("Run shell command '{}'", cmd);

    CommandLine cmdLine = CommandLine.parse(shell);
    // the Windows CMD shell doesn't handle multiline statements,
    // they need to be delimited by '&&' instead
    if (isWindows) {
      String[] lines = StringUtils.split(cmd, "\n");
      cmd = StringUtils.join(lines, " && ");
    }
    cmdLine.addArgument(cmd, false);

    try {
      contextMap.put(context.getParagraphId(), context);

      DefaultExecutor executor = new DefaultExecutor();
      executor.setStreamHandler(new PumpStreamHandler(
          context.out, context.out));
      executor.setWatchdog(new ExecuteWatchdog(Long.MAX_VALUE));
      executorMap.put(context.getParagraphId(), executor);

      if (Boolean.valueOf(getProperty(DIRECTORY_USER_HOME))) {
        executor.setWorkingDirectory(new File(System.getProperty("user.home")));
      }

      int exitVal = executor.execute(cmdLine);
      LOGGER.info("Paragraph {} return with exit value: {}", context.getParagraphId(), exitVal);
      if (exitVal == 0) {
        return new InterpreterResult(Code.SUCCESS);
      } else {
        return new InterpreterResult(Code.ERROR);
      }
    } catch (ExecuteException e) {
      int exitValue = e.getExitValue();
      LOGGER.error("Can not run command: " + cmd, e);
      Code code = Code.ERROR;
      StringBuilder messageBuilder = new StringBuilder();
      if (exitValue == 143) {
        code = Code.INCOMPLETE;
        messageBuilder.append("Paragraph received a SIGTERM\n");
        LOGGER.info("The paragraph {} stopped executing: {}",
                context.getParagraphId(), messageBuilder.toString());
      }
      messageBuilder.append("ExitValue: " + exitValue);
      return new InterpreterResult(code, messageBuilder.toString());
    } catch (IOException e) {
      LOGGER.error("Can not run command: " + cmd, e);
      return new InterpreterResult(Code.ERROR, e.getMessage());
    } finally {
      executorMap.remove(context.getParagraphId());
      contextMap.remove(context.getParagraphId());
    }
  }

  @Override
  public void cancel(InterpreterContext context) {
    DefaultExecutor executor = executorMap.remove(context.getParagraphId());
    if (executor != null) {
      try {
        executor.getWatchdog().destroyProcess();
      } catch (Exception e){
        LOGGER.error("error destroying executor for paragraphId: " + context.getParagraphId(), e);
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
  protected boolean runKerberosLogin() {
    try {
      createSecureConfiguration();
      return true;
    } catch (Exception e) {
      LOGGER.error("Unable to run kinit for zeppelin", e);
    }
    return false;
  }

  public ConcurrentHashMap<String, DefaultExecutor> getExecutorMap() {
    return executorMap;
  }

  public void createSecureConfiguration() throws InterpreterException {
    Properties properties = getProperties();
    CommandLine cmdLine = CommandLine.parse(shell);
    cmdLine.addArgument("-c", false);
    String kinitCommand = String.format("kinit -k -t %s %s",
        properties.getProperty("zeppelin.shell.keytab.location"),
        properties.getProperty("zeppelin.shell.principal"));
    cmdLine.addArgument(kinitCommand, false);
    DefaultExecutor executor = new DefaultExecutor();
    try {
      executor.execute(cmdLine);
    } catch (Exception e) {
      LOGGER.error("Unable to run kinit for zeppelin user " + kinitCommand, e);
      throw new InterpreterException(e);
    }
  }

  @Override
  protected boolean isKerboseEnabled() {
    if (StringUtils.isNotBlank(getProperty("zeppelin.shell.auth.type")) && getProperty(
        "zeppelin.shell.auth.type").equalsIgnoreCase("kerberos")) {
      return true;
    }
    return false;
  }

}
