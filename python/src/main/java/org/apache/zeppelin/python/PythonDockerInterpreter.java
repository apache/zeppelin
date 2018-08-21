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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.scheduler.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Helps run python interpreter on a docker container */
public class PythonDockerInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(PythonDockerInterpreter.class);
  Pattern activatePattern = Pattern.compile("activate\\s*(.*)");
  Pattern deactivatePattern = Pattern.compile("deactivate");
  Pattern helpPattern = Pattern.compile("help");
  private File zeppelinHome;
  private PythonInterpreter pythonInterpreter;

  public PythonDockerInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() throws InterpreterException {
    if (System.getenv("ZEPPELIN_HOME") != null) {
      zeppelinHome = new File(System.getenv("ZEPPELIN_HOME"));
    } else {
      zeppelinHome = Paths.get("..").toAbsolutePath().toFile();
    }
    this.pythonInterpreter = getInterpreterInTheSameSessionByClassName(PythonInterpreter.class);
  }

  @Override
  public void close() {}

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context)
      throws InterpreterException {
    File pythonWorkDir = pythonInterpreter.getPythonWorkDir();
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

      // mount pythonscript dir
      String mountPythonScript = "-v " + pythonWorkDir.getAbsolutePath() + ":/_python_workdir ";

      // mount zeppelin dir
      String mountPy4j = "-v " + zeppelinHome.getAbsolutePath() + ":/_zeppelin ";

      // set PYTHONPATH
      String pythonPath = ".:/_python_workdir/py4j-src-0.10.7.zip:/_python_workdir";

      setPythonCommand(
          "docker run -i --rm "
              + mountPythonScript
              + mountPy4j
              + "-e PYTHONPATH=\""
              + pythonPath
              + "\" "
              + image
              + " "
              + pythonInterpreter.getPythonExec()
              + " "
              + "/_python_workdir/zeppelin_python.py");
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

  public void setPythonCommand(String cmd) throws InterpreterException {
    pythonInterpreter.setPythonExec(cmd);
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
  public void cancel(InterpreterContext context) {}

  @Override
  public FormType getFormType() {
    return FormType.NONE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  /**
   * Use python interpreter's scheduler. To make sure %python.docker paragraph and %python paragraph
   * runs sequentially
   */
  @Override
  public Scheduler getScheduler() {
    if (pythonInterpreter != null) {
      return pythonInterpreter.getScheduler();
    } else {
      return null;
    }
  }

  private void restartPythonProcess() throws InterpreterException {
    if (pythonInterpreter != null) {
      pythonInterpreter.close();
      pythonInterpreter.open();
    }
  }

  public boolean pull(InterpreterOutput out, String image) throws InterpreterException {
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
