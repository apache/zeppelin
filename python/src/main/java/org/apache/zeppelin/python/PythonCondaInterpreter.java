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
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterResult.Type;
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

  private Pattern condaEnvListPattern = Pattern.compile("([^\\s]*)[\\s*]*\\s(.*)");
  private Pattern listEnvPattern = Pattern.compile("env\\s*list\\s?");
  private Pattern activatePattern = Pattern.compile("activate\\s*(.*)");
  private Pattern deactivatePattern = Pattern.compile("deactivate");
  private Pattern helpPattern = Pattern.compile("help");
  private Pattern infoPattern = Pattern.compile("info");

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
    Matcher activateMatcher = activatePattern.matcher(st);

    try {
      if (st == null || listEnvPattern.matcher(st).matches()) {
        runCondaEnvList(out, getCondaEnvs());
        return new InterpreterResult(Code.SUCCESS);
      } else if (activateMatcher.matches()) {
        String envName = activateMatcher.group(1);
        changePythonEnvironment(envName);
        restartPythonProcess();
        return new InterpreterResult(Code.SUCCESS,
            "\"" + envName + "\" activated");
      } else if (deactivatePattern.matcher(st).matches()) {
        changePythonEnvironment(null);
        restartPythonProcess();
        return new InterpreterResult(Code.SUCCESS, "Deactivated");
      } else if (helpPattern.matcher(st).matches()) {
        printCondaUsage(out);
        return new InterpreterResult(Code.SUCCESS);
      } else if (infoPattern.matcher(st).matches()) {
        String result = runCondaInfo();
        return new InterpreterResult(Code.SUCCESS, Type.TEXT, result);
      } else {
        return new InterpreterResult(Code.ERROR, "Not supported command: " + st);
      }
    } catch (RuntimeException | IOException | InterruptedException e) {
      throw new InterpreterException(e);
    }
  }

  private void changePythonEnvironment(String envName)
      throws IOException, InterruptedException {
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
    Interpreter p =
        getInterpreterInTheSameSessionByClassName(PythonInterpreter.class.getName());

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

  private HashMap getCondaEnvs() throws IOException, InterruptedException {
    HashMap envList = null;

    StringBuilder sb = createStringBuilder();
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
    return envList;
  }

  private void runCondaEnvList(InterpreterOutput out, HashMap<String, String> envList)
      throws IOException {
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
  }

  private String runCondaInfo() throws IOException, InterruptedException {
    StringBuilder out = createStringBuilder();
    int exit = runCommand(out, "conda", "info");
    if (exit != 0) {
      throw new RuntimeException("Failed to execute conda info. exited with " + exit);
    }

    return out.toString();
  }

  private void printCondaUsage(InterpreterOutput out) {
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
