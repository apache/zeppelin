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

package org.apache.zeppelin.livy;

import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
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

import java.util.*;

import static org.apache.zeppelin.livy.RequestHelper.executeHTTP;

/**
 * Livy interpreter for Zeppelin.
 */
public class LivyInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(LivyInterpreter.class);
  private static final String EXECUTOR_KEY = "executor";
  int commandTimeOut = 600000;


  static final String DEFAULT_URL = "http://localhost:8998";
  static final String DEFAULT_USER = "livy";

  private Map<String, Integer> userSessionMap;

  static {
    Interpreter.register(
        "livy",
        "livy",
        LivyInterpreter.class.getName(),
        new InterpreterPropertyBuilder()
            .add("zeppelin.livy.url", DEFAULT_URL, "The URL for Livy Server.")
            .add("zeppelin.livy.user", DEFAULT_USER, "The livy user")
            .build()
    );
  }

  public LivyInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    userSessionMap = new HashMap<>();
  }

  @Override
  public void close() {
  }


  @Override
  public InterpreterResult interpret(String line, InterpreterContext context) {
    if (userSessionMap.get(context.getAuthenticationInfo().getUser()) == null) {
      userSessionMap.put(context.getAuthenticationInfo().getUser(), createSession(context.getAuthenticationInfo().getUser()));
    }
    if (line == null || line.trim().length() == 0) {
      return new InterpreterResult(Code.SUCCESS);
    }
    return interpret(line.split("\n"), context);
  }

  private Integer createSession(String user) {
    try {
      String json = executeHTTP(DEFAULT_URL + "/sessions", "POST", "{\"kind\": \"pyspark\", \"proxyUser\": \"" + user + "\"}");
    } catch (Exception e) {
      e.printStackTrace();
    }
    return 0;
  }

  public InterpreterResult interpret(String[] lines, InterpreterContext context) {
    synchronized (this) {
      InterpreterResult res = interpretInput(lines, context);
      return res;
    }
  }

  public InterpreterResult interpretInput(String[] lines, InterpreterContext context) {
//    logger.debug("Run livy command '" + cmd + "'");
//    CommandLine cmdLine = CommandLine.parse("bash");
//    cmdLine.addArgument("-c", false);
//    cmdLine.addArgument(cmd, false);
//    DefaultExecutor executor = new DefaultExecutor();
//    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//    ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
//    executor.setStreamHandler(new PumpStreamHandler(contextInterpreter.out, errorStream));
//    executor.setWatchdog(new ExecuteWatchdog(commandTimeOut));
//
//    Job runningJob = getRunningJob(contextInterpreter.getParagraphId());
//    Map<String, Object> info = runningJob.info();
//    info.put(EXECUTOR_KEY, executor);
//    try {
//      int exitVal = executor.execute(cmdLine);
//      logger.info("Paragraph " + contextInterpreter.getParagraphId()
//          + "return with exit value: " + exitVal);
//      return new InterpreterResult(InterpreterResult.Code.SUCCESS, null);
//    } catch (ExecuteException e) {
//      int exitValue = e.getExitValue();
//      logger.error("Can not run " + cmd, e);
//      Code code = Code.ERROR;
//      String msg = errorStream.toString();
//      if (exitValue == 143) {
//        code = Code.INCOMPLETE;
//        msg = msg + "Paragraph received a SIGTERM.\n";
//        logger.info("The paragraph " + contextInterpreter.getParagraphId()
//            + " stopped executing: " + msg);
//      }
//      msg += "ExitValue: " + exitValue;
//      return new InterpreterResult(code, msg);
//    } catch (IOException e) {
//      logger.error("Can not run " + cmd, e);
//      return new InterpreterResult(Code.ERROR, e.getMessage());
//    }


    String[] linesToRun = new String[lines.length + 1];
    for (int i = 0; i < lines.length; i++) {
      linesToRun[i] = lines[i];
    }
    linesToRun[lines.length] = "print(\"\")";

    String incomplete = "";
    Code r = null;

    for (int l = 0; l < linesToRun.length; l++) {
      String s = linesToRun[l];
      // check if next line starts with "." (but not ".." or "./") it is treated as an invocation
      if (l + 1 < linesToRun.length) {
        String nextLine = linesToRun[l + 1].trim();
        if (nextLine.startsWith(".") && !nextLine.startsWith("..") && !nextLine.startsWith("./")) {
          incomplete += s + "\n";
          continue;
        }
      }

      executeHTTP()
//      incomplete + s;
//      return new InterpreterResult(Code.ERROR, InterpreterUtils.getMostRelevantMessage(e));
    }
    if (r == Code.INCOMPLETE) {
      return new InterpreterResult(r, "Incomplete expression");
    } else {
      return new InterpreterResult(Code.SUCCESS);
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
        LivyInterpreter.class.getName() + this.hashCode(), 10);
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return null;
  }

}
