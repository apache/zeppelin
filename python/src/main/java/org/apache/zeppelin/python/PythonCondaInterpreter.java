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

import java.io.*;
import java.util.HashMap;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conda support
 */
public class PythonCondaInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(PythonCondaInterpreter.class);
  public static final String ZEPPELIN_PYTHON = "zeppelin.python";
  public static final String CONDA_PYTHON_PATH = "/bin/python";
  public static final String DEFAULT_ZEPPELIN_PYTHON = "python";

  Pattern condaEnvListPattern = Pattern.compile("([^\\s]*)[\\s*]*\\s(.*)");
  Pattern listPattern = Pattern.compile("env\\s*list\\s?");
  Pattern activatePattern = Pattern.compile("activate\\s*(.*)");
  Pattern deactivatePattern = Pattern.compile("deactivate");
  Pattern helpPattern = Pattern.compile("help");

  public PythonCondaInterpreter(Properties property) {
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

    Matcher listMatcher = listPattern.matcher(st);
    Matcher activateMatcher = activatePattern.matcher(st);
    Matcher deactivateMatcher = deactivatePattern.matcher(st);
    Matcher helpMatcher = helpPattern.matcher(st);

    if (st == null || st.isEmpty() || listMatcher.matches()) {
      listEnv(out, getCondaEnvs());
      return new InterpreterResult(InterpreterResult.Code.SUCCESS);
    } else if (activateMatcher.matches()) {
      String envName = activateMatcher.group(1);
      changePythonEnvironment(envName);
      restartPythonProcess();
      return new InterpreterResult(InterpreterResult.Code.SUCCESS, "\"" + envName + "\" activated");
    } else if (deactivateMatcher.matches()) {
      changePythonEnvironment(null);
      restartPythonProcess();
      return new InterpreterResult(InterpreterResult.Code.SUCCESS, "Deactivated");
    } else if (helpMatcher.matches()) {
      printUsage(out);
      return new InterpreterResult(InterpreterResult.Code.SUCCESS);
    } else {
      return new InterpreterResult(InterpreterResult.Code.ERROR, "Not supported command: " + st);
    }
  }

  private void changePythonEnvironment(String envName) {
    PythonInterpreter python = getPythonInterpreter();
    String binPath = null;
    if (envName == null) {
      binPath = getProperty(ZEPPELIN_PYTHON);
      if (binPath == null) {
        binPath = DEFAULT_ZEPPELIN_PYTHON;
      }
    } else {
      HashMap<String, String> envList = getCondaEnvs();
      for (String name : envList.keySet()) {
        if (envName.equals(name)) {
          binPath = envList.get(name) + CONDA_PYTHON_PATH;
          break;
        }
      }
    }
    python.setPythonCommand(binPath);
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

  private HashMap getCondaEnvs() {
    HashMap envList = null;

    StringBuilder sb = createStringBuilder();
    try {
      int exit = runCommand(sb, "conda", "env", "list");
      if (exit == 0) {
        envList = new HashMap();
        String[] lines = sb.toString().split("\n");
        for (String s : lines) {
          if (s == null || s.isEmpty() || s.startsWith("#")) {
            continue;
          }
          Matcher match = condaEnvListPattern.matcher(s);

          if (!match.matches()) {
            continue;
          }
          envList.put(match.group(1), match.group(2));
        }
      }
    } catch (IOException | InterruptedException e) {
      throw new InterpreterException(e);
    }
    return envList;
  }

  private void listEnv(InterpreterOutput out, HashMap<String, String> envList) {
    try {
      out.setType(InterpreterResult.Type.HTML);
      out.write("<h4>Conda environments</h4>\n");
      // start table
      out.write("<div style=\"display:table\">\n");

      for (String name : envList.keySet()) {
        String path = envList.get(name);

        out.write(String.format("<div style=\"display:table-row\">" +
            "<div style=\"display:table-cell;width:150px\">%s</div>" +
            "<div style=\"display:table-cell;\">%s</div>" +
            "</div>\n",
          name, path));
      }
      // end table
      out.write("</div><br />\n");
      out.write("<small><code>%python.conda help</code> for the usage</small>\n");
    } catch  (IOException e) {
      throw new InterpreterException(e);
    }
  }


  private void printUsage(InterpreterOutput out) {
    try {
      out.setType(InterpreterResult.Type.HTML);
      out.writeResource("output_templates/conda_usage.html");
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
   * To make sure %python.conda paragraph and %python paragraph runs sequentially
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

  protected int runCommand(StringBuilder sb, String ... command)
      throws IOException, InterruptedException {
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.redirectErrorStream(true);
    Process process = builder.start();
    InputStream stdout = process.getInputStream();
    BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
    String line;
    while ((line = br.readLine()) != null) {
      sb.append(line);
      sb.append("\n");
    }
    int r = process.waitFor(); // Let the process finish.
    return r;
  }

  protected StringBuilder createStringBuilder() {
    return new StringBuilder();
  }
}
