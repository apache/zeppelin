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

package org.apache.zeppelin.kotlin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.interpreter.util.InterpreterOutputStream;
import org.apache.zeppelin.kotlin.completion.KotlinCompleter;
import org.apache.zeppelin.kotlin.context.KotlinReceiver;
import org.apache.zeppelin.kotlin.reflect.KotlinVariableInfo;
import org.apache.zeppelin.scheduler.Job;

public class KotlinInterpreter extends Interpreter {

  private static Logger logger = LoggerFactory.getLogger(KotlinInterpreter.class);

  private InterpreterOutputStream out;
  private KotlinRepl interpreter;
  private KotlinReplBuilder builder;
  private KotlinCompleter completer;

  public KotlinInterpreter(Properties properties) {
    super(properties);
    builder = new KotlinReplBuilder();

    int maxResult = Integer.parseInt(
        properties.getProperty("zeppelin.kotlin.maxResult", "1000"));
    String imports = properties.getProperty("zeppelin.interpreter.localRepo", "");

    builder
        .executionContext(new KotlinReceiver())
        .maxResult(maxResult)
        .codeOnLoad("")
        .classPath(getImportClasspath(imports));
  }

  public KotlinReplBuilder getBuilder() {
    return builder;
  }

  @Override
  public void open() throws InterpreterException {
    interpreter = builder.build();

    completer = new KotlinCompleter(interpreter.getKotlinContext());
    out = new InterpreterOutputStream(logger);
  }

  @Override
  public void close() {

  }

  @Override
  public InterpreterResult interpret(String code,
                                     InterpreterContext context) throws InterpreterException{
    // saving job's running thread for cancelling
    Job<?> runningJob = getRunningJob(context.getParagraphId());
    if (runningJob != null) {
      runningJob.info().put("CURRENT_THREAD", Thread.currentThread());
    }

    return runWithOutput(code, context.out);
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    Job<?> runningJob = getRunningJob(context.getParagraphId());
    if (runningJob != null) {
      Map<String, Object> info = runningJob.info();
      Object object = info.get("CURRENT_THREAD");
      if (object instanceof Thread) {
        try {
          Thread t = (Thread) object;
          t.interrupt();
        } catch (Throwable t) {
          logger.error("Failed to cancel script: " + t, t);
        }
      }
    }
  }

  @Override
  public FormType getFormType() throws InterpreterException {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return 0;
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor,
      InterpreterContext interpreterContext) throws InterpreterException {
    return completer.completion(buf, cursor, interpreterContext);
  }

  public List<KotlinVariableInfo> getVariables() {
    return interpreter.getRuntimeVariables();
  }

  public List<Method> getMethods() {
    return interpreter.getMethods();
  }

  private Job<?> getRunningJob(String paragraphId) {
    Job foundJob = null;
    Collection<Job> jobsRunning = getScheduler().getAllJobs();
    for (Job job : jobsRunning) {
      if (job.getId().equals(paragraphId)) {
        foundJob = job;
      }
    }
    return foundJob;
  }

  private InterpreterResult runWithOutput(String code, InterpreterOutput out) {
    this.out.setInterpreterOutput(out);

    PrintStream oldOut = System.out;
    PrintStream newOut = (out != null) ? new PrintStream(out) : null;
    System.setOut(newOut);
    InterpreterResult res = interpreter.eval(code);
    System.setOut(oldOut);

    return res;
  }

  private List<String> getImportClasspath(String localRepo) {
    List<String> classpath = new ArrayList<>();
    if (localRepo.equals("")) {
      return classpath;
    }

    File repo = new File(localRepo);
    File[] files = repo.listFiles();
    if (files == null) {
      return classpath;
    }
    for (File file : files) {
      if (!file.isDirectory()) {
        classpath.add(file.getAbsolutePath());
      }
    }
    return classpath;
  }
}