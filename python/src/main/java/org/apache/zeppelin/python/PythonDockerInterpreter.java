/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*  http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.zeppelin.python;

import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.scheduler.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helps run python interpreter on a docker container
 */
public class PythonDockerInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(PythonDockerInterpreter.class);
  Pattern activatePattern = Pattern.compile("activate\\s*(.*)");
  Pattern deactivatePattern = Pattern.compile("deactivate");
  Pattern helpPattern = Pattern.compile("help");

  public PythonDockerInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {

  }

  @Override
  public void close() {

  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    InterpreterOutput out = context.out;

    Matcher activateMatcher = activatePattern.matcher(st);
    Matcher deactivateMatcher = deactivatePattern.matcher(st);
    Matcher helpMatcher = helpPattern.matcher(st);

    if (st == null || st.isEmpty() || helpMatcher.matches()) {
      printUsage(out);
      return new InterpreterResult(InterpreterResult.Code.SUCCESS);
    } else if (activateMatcher.matches()) {
      String image = activateMatcher.group(1);
      pull(out, image);
      setPythonCommand("docker run -i --rm " + image + " python -iu");
      restartPythonProcess();
      out.clear();
      return new InterpreterResult(InterpreterResult.Code.SUCCESS, "\"" + image + "\" activated");
    } else if (deactivateMatcher.matches()) {
      setPythonCommand(null);
      restartPythonProcess();
      return new InterpreterResult(InterpreterResult.Code.SUCCESS, "Deactivated");
    } else {
      return new InterpreterResult(InterpreterResult.Code.ERROR, "Not supported command: " + st);
    }
  }

  public void setPythonCommand(String cmd) {
    PythonInterpreter python = getPythonInterpreter();
    python.setPythonCommand(cmd);
  }

  private void printUsage(InterpreterOutput out) {
    try {
      out.setType(InterpreterResult.Type.HTML);
      out.writeResource("output_templates/docker_usage.html");
    } catch (IOException e) {
      logger.error("Can't print usage", e);
    }
  }

  @Override
  public void cancel(InterpreterContext context) {

  }

  @Override
  public FormType getFormType() {
    return FormType.NONE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  /**
   * Use python interpreter's scheduler.
   * To make sure %python.docker paragraph and %python paragraph runs sequentially
   */
  @Override
  public Scheduler getScheduler() {
    PythonInterpreter pythonInterpreter = getPythonInterpreter();
    if (pythonInterpreter != null) {
      return pythonInterpreter.getScheduler();
    } else {
      return null;
    }
  }

  private void restartPythonProcess() {
    PythonInterpreter python = getPythonInterpreter();
    python.close();
    python.open();
  }

  protected PythonInterpreter getPythonInterpreter() {
    LazyOpenInterpreter lazy = null;
    PythonInterpreter python = null;
    Interpreter p = getInterpreterInTheSameSessionByClassName(PythonInterpreter.class.getName());

    while (p instanceof WrappedInterpreter) {
      if (p instanceof LazyOpenInterpreter) {
        lazy = (LazyOpenInterpreter) p;
      }
      p = ((WrappedInterpreter) p).getInnerInterpreter();
    }
    python = (PythonInterpreter) p;

    if (lazy != null) {
      lazy.open();
    }
    return python;
  }

  public boolean pull(InterpreterOutput out, String image) {
    int exit = 0;
    try {
      exit = runCommand(out, "docker", "pull", image);
    } catch (IOException | InterruptedException e) {
      logger.error(e.getMessage(), e);
      throw new InterpreterException(e);
    }
    return exit == 0;
  }

  protected int runCommand(InterpreterOutput out, String... command)
      throws IOException, InterruptedException {
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.redirectErrorStream(true);
    Process process = builder.start();
    InputStream stdout = process.getInputStream();
    BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
    String line;
    while ((line = br.readLine()) != null) {
      out.write(line + "\n");
    }
    int r = process.waitFor(); // Let the process finish.
    return r;
  }
}
