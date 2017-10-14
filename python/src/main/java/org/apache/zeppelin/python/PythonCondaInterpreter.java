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

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterResult.Type;
import org.apache.zeppelin.scheduler.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
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

  public static final Pattern PATTERN_OUTPUT_ENV_LIST = Pattern.compile("([^\\s]*)[\\s*]*\\s(.*)");
  public static final Pattern PATTERN_COMMAND_ENV_LIST = Pattern.compile("env\\s*list\\s?");
  public static final Pattern PATTERN_COMMAND_ENV = Pattern.compile("env\\s*(.*)");
  public static final Pattern PATTERN_COMMAND_LIST = Pattern.compile("list");
  public static final Pattern PATTERN_COMMAND_CREATE = Pattern.compile("create\\s*(.*)");
  public static final Pattern PATTERN_COMMAND_ACTIVATE = Pattern.compile("activate\\s*(.*)");
  public static final Pattern PATTERN_COMMAND_DEACTIVATE = Pattern.compile("deactivate");
  public static final Pattern PATTERN_COMMAND_INSTALL = Pattern.compile("install\\s*(.*)");
  public static final Pattern PATTERN_COMMAND_UNINSTALL = Pattern.compile("uninstall\\s*(.*)");
  public static final Pattern PATTERN_COMMAND_HELP = Pattern.compile("help");
  public static final Pattern PATTERN_COMMAND_INFO = Pattern.compile("info");

  private String currentCondaEnvName = StringUtils.EMPTY;

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
  public InterpreterResult interpret(String st, InterpreterContext context)
      throws InterpreterException {
    InterpreterOutput out = context.out;
    Matcher activateMatcher = PATTERN_COMMAND_ACTIVATE.matcher(st);
    Matcher createMatcher = PATTERN_COMMAND_CREATE.matcher(st);
    Matcher installMatcher = PATTERN_COMMAND_INSTALL.matcher(st);
    Matcher uninstallMatcher = PATTERN_COMMAND_UNINSTALL.matcher(st);
    Matcher envMatcher = PATTERN_COMMAND_ENV.matcher(st);

    try {
      if (PATTERN_COMMAND_ENV_LIST.matcher(st).matches()) {
        String result = runCondaEnvList();
        return new InterpreterResult(Code.SUCCESS, Type.HTML, result);
      } else if (envMatcher.matches()) {
        // `envMatcher` should be used after `listEnvMatcher`
        String result = runCondaEnv(getRestArgsFromMatcher(envMatcher));
        return new InterpreterResult(Code.SUCCESS, Type.HTML, result);
      } else if (PATTERN_COMMAND_LIST.matcher(st).matches()) {
        String result = runCondaList();
        return new InterpreterResult(Code.SUCCESS, Type.HTML, result);
      } else if (createMatcher.matches()) {
        String result = runCondaCreate(getRestArgsFromMatcher(createMatcher));
        return new InterpreterResult(Code.SUCCESS, Type.HTML, result);
      } else if (activateMatcher.matches()) {
        String envName = activateMatcher.group(1).trim();
        return runCondaActivate(envName);
      } else if (PATTERN_COMMAND_DEACTIVATE.matcher(st).matches()) {
        return runCondaDeactivate();
      } else if (installMatcher.matches()) {
        String result = runCondaInstall(getRestArgsFromMatcher(installMatcher));
        return new InterpreterResult(Code.SUCCESS, Type.HTML, result);
      } else if (uninstallMatcher.matches()) {
        String result = runCondaUninstall(getRestArgsFromMatcher(uninstallMatcher));
        return new InterpreterResult(Code.SUCCESS, Type.HTML, result);
      } else if (st == null || PATTERN_COMMAND_HELP.matcher(st).matches()) {
        runCondaHelp(out);
        return new InterpreterResult(Code.SUCCESS);
      } else if (PATTERN_COMMAND_INFO.matcher(st).matches()) {
        String result = runCondaInfo();
        return new InterpreterResult(Code.SUCCESS, Type.HTML, result);
      } else {
        return new InterpreterResult(Code.ERROR, "Not supported command: " + st);
      }
    } catch (RuntimeException | IOException | InterruptedException e) {
      throw new InterpreterException(e);
    }
  }

  public String getCurrentCondaEnvName() {
    return currentCondaEnvName;
  }

  public void setCurrentCondaEnvName(String currentCondaEnvName) {
    if (currentCondaEnvName == null) {
      currentCondaEnvName = StringUtils.EMPTY;
    }
    this.currentCondaEnvName = currentCondaEnvName;
  }

  private void changePythonEnvironment(String envName)
      throws IOException, InterruptedException, InterpreterException {
    PythonInterpreter python = getPythonInterpreter();
    String binPath = null;
    if (envName == null) {
      binPath = getProperty(ZEPPELIN_PYTHON);
      if (binPath == null) {
        binPath = DEFAULT_ZEPPELIN_PYTHON;
      }
    } else {
      Map<String, String> envList = getCondaEnvs();
      for (String name : envList.keySet()) {
        if (envName.equals(name)) {
          binPath = envList.get(name) + CONDA_PYTHON_PATH;
          break;
        }
      }
    }
    setCurrentCondaEnvName(envName);
    python.setPythonCommand(binPath);
  }

  private void restartPythonProcess() throws InterpreterException {
    PythonInterpreter python = getPythonInterpreter();
    python.close();
    python.open();
  }

  protected PythonInterpreter getPythonInterpreter() throws InterpreterException {
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

  public static String runCondaCommandForTextOutput(String title, List<String> commands)
      throws IOException, InterruptedException {

    String result = runCommand(commands);
    return wrapCondaBasicOutputStyle(title, result);
  }

  private String runCondaCommandForTableOutput(String title, List<String> commands)
      throws IOException, InterruptedException {

    StringBuilder sb = new StringBuilder();
    String result = runCommand(commands);

    // use table output for pretty output
    Map<String, String> envPerName = parseCondaCommonStdout(result);
    return wrapCondaTableOutputStyle(title, envPerName);
  }

  protected Map<String, String> getCondaEnvs()
      throws IOException, InterruptedException {
    String result = runCommand("conda", "env", "list");
    Map<String, String> envList = parseCondaCommonStdout(result);
    return envList;
  }

  private String runCondaEnvList() throws IOException, InterruptedException {
    return wrapCondaTableOutputStyle("Environment List", getCondaEnvs());
  }

  private String runCondaEnv(List<String> restArgs)
      throws IOException, InterruptedException {

    restArgs.add(0, "conda");
    restArgs.add(1, "env");
    restArgs.add(3, "--yes"); // --yes should be inserted after command

    return runCondaCommandForTextOutput(null, restArgs);
  }

  private InterpreterResult runCondaActivate(String envName)
      throws IOException, InterruptedException, InterpreterException {

    if (null == envName || envName.isEmpty()) {
      return new InterpreterResult(Code.ERROR, "Env name should be specified");
    }

    changePythonEnvironment(envName);
    restartPythonProcess();

    return new InterpreterResult(Code.SUCCESS, "'" + envName + "' is activated");
  }

  private InterpreterResult runCondaDeactivate()
      throws IOException, InterruptedException, InterpreterException {

    changePythonEnvironment(null);
    restartPythonProcess();
    return new InterpreterResult(Code.SUCCESS, "Deactivated");
  }

  private String runCondaList() throws IOException, InterruptedException {
    List<String> commands = new ArrayList<String>();
    commands.add(0, "conda");
    commands.add(1, "list");
    if (!getCurrentCondaEnvName().isEmpty()) {
      commands.add(2, "-n");
      commands.add(3, getCurrentCondaEnvName());
    }

    return runCondaCommandForTableOutput("Installed Package List", commands);
  }

  private void runCondaHelp(InterpreterOutput out) {
    try {
      out.setType(InterpreterResult.Type.HTML);
      out.writeResource("output_templates/conda_usage.html");
    } catch (IOException e) {
      logger.error("Can't print usage", e);
    }
  }

  private String runCondaInfo() throws IOException, InterruptedException {
    List<String> commands = new ArrayList<String>();
    commands.add("conda");
    commands.add("info");

    return runCondaCommandForTextOutput("Conda Information", commands);
  }

  private String runCondaCreate(List<String> restArgs)
      throws IOException, InterruptedException {
    restArgs.add(0, "conda");
    restArgs.add(1, "create");
    restArgs.add(2, "--yes");

    return runCondaCommandForTextOutput("Environment Creation", restArgs);
  }

  private String runCondaInstall(List<String> restArgs)
      throws IOException, InterruptedException {

    restArgs.add(0, "conda");
    restArgs.add(1, "install");
    restArgs.add(2, "--yes");
    if (!getCurrentCondaEnvName().isEmpty()) {
      restArgs.add(3, "-n");
      restArgs.add(4, getCurrentCondaEnvName());
    }

    return runCondaCommandForTextOutput("Package Installation", restArgs);
  }

  private String runCondaUninstall(List<String> restArgs)
      throws IOException, InterruptedException {

    restArgs.add(0, "conda");
    restArgs.add(1, "uninstall");
    restArgs.add(2, "--yes");
    if (!getCurrentCondaEnvName().isEmpty()) {
      restArgs.add(3, "-n");
      restArgs.add(4, getCurrentCondaEnvName());
    }

    return runCondaCommandForTextOutput("Package Uninstallation", restArgs);
  }

  public static String wrapCondaBasicOutputStyle(String title, String content) {
    StringBuilder sb = new StringBuilder();
    if (null != title && !title.isEmpty()) {
      sb.append("<h4>").append(title).append("</h4>\n")
          .append("</div><br />\n");
    }
    sb.append("<div style=\"white-space:pre-wrap;\">\n")
        .append(content)
        .append("</div>");

    return sb.toString();
  }

  public static String wrapCondaTableOutputStyle(String title, Map<String, String> kv) {
    StringBuilder sb = new StringBuilder();

    if (null != title && !title.isEmpty()) {
      sb.append("<h4>").append(title).append("</h4>\n");
    }

    sb.append("<div style=\"display:table;white-space:pre-wrap;\">\n");
    for (String name : kv.keySet()) {
      String path = kv.get(name);

      sb.append(String.format("<div style=\"display:table-row\">" +
              "<div style=\"display:table-cell;width:150px\">%s</div>" +
              "<div style=\"display:table-cell;\">%s</div>" +
              "</div>\n",
          name, path));
    }
    sb.append("</div>\n");

    return sb.toString();
  }

  public static Map<String, String> parseCondaCommonStdout(String out)
      throws IOException, InterruptedException {

    Map<String, String> kv = new LinkedHashMap<String, String>();
    String[] lines = out.split("\n");
    for (String s : lines) {
      if (s == null || s.isEmpty() || s.startsWith("#")) {
        continue;
      }
      Matcher match = PATTERN_OUTPUT_ENV_LIST.matcher(s);

      if (!match.matches()) {
        continue;
      }
      kv.put(match.group(1), match.group(2));
    }

    return kv;
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
    PythonInterpreter pythonInterpreter = null;
    try {
      pythonInterpreter = getPythonInterpreter();
      if (pythonInterpreter != null) {
        return pythonInterpreter.getScheduler();
      } else {
        return null;
      }
    } catch (InterpreterException e) {
      e.printStackTrace();
      return null;
    }
  }

  public static String runCommand(List<String> commands)
      throws IOException, InterruptedException {

    StringBuilder sb = new StringBuilder();

    ProcessBuilder builder = new ProcessBuilder(commands);
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

    if (r != 0) {
      throw new RuntimeException("Failed to execute `" +
          StringUtils.join(commands, " ") + "` exited with " + r);
    }

    return sb.toString();
  }

  public static String runCommand(String ... command)
      throws IOException, InterruptedException {

    List<String> list = new ArrayList<>(command.length);
    for (String arg : command) {
      list.add(arg);
    }

    return runCommand(list);
  }

  public static List<String> getRestArgsFromMatcher(Matcher m) {
    // Arrays.asList just returns fixed-size, so we should use ctor instead of
    return new ArrayList<>(Arrays.asList(m.group(1).split(" ")));
  }
}
