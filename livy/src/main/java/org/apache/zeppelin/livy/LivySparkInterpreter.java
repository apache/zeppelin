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

import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Livy Spark interpreter for Zeppelin.
 */
public class LivySparkInterpreter extends Interpreter {

  static {
    Interpreter.register(
        "livy",
        "spark",
        LivyHelper.class.getName(),
        new InterpreterPropertyBuilder()
            .build()
    );
  }

  private Map<String, Integer> userSessionMap;
  private LivyHelper livyHelper;
  private static final String EXECUTOR_KEY = "executor";
  Logger LOGGER = LoggerFactory.getLogger(LivySparkInterpreter.class);

  public LivySparkInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    userSessionMap = new HashMap<>();
    livyHelper = new LivyHelper();
  }

  @Override
  public void close() {
  }


  @Override
  public InterpreterResult interpret(String line, InterpreterContext context) {
    if (context.getAuthenticationInfo().getUser() == null) {

    }
    if (userSessionMap.get(context.getAuthenticationInfo().getUser()) == null) {
      userSessionMap.put(
          context.getAuthenticationInfo().getUser(),
          livyHelper.createSession(context.getAuthenticationInfo().getUser(), "spark"));
    }
    if (line == null || line.trim().length() == 0) {
      return new InterpreterResult(InterpreterResult.Code.SUCCESS);
    }

    DefaultExecutor executor = new DefaultExecutor();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
    executor.setStreamHandler(new PumpStreamHandler(context.out, errorStream));
    executor.setWatchdog(new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT));

    Job runningJob = getRunningJob(context.getParagraphId());
    Map<String, Object> info = runningJob.info();
    info.put(EXECUTOR_KEY, executor);

    return livyHelper.interpret(line.split("\n"), context, userSessionMap);
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
        LivyHelper.class.getName() + this.hashCode(), 10);
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
  public List<String> completion(String buf, int cursor) {
    return null;
  }
}
