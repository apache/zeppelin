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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conda support
 */
public class PythonCondaInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(PythonCondaInterpreter.class);

  Pattern condaEnvListPattern = Pattern.compile("([^\\s]*)[\\s*]*\\s(.*)");
  Pattern listPattern = Pattern.compile("env\\s*list\\s?");
  Pattern activatePattern = Pattern.compile("activate\\s*(.*)");
  Pattern deactivatePattern = Pattern.compile("deactivate");
  String pythonCommand = null;

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
    Matcher listMatcher = listPattern.matcher(st);
    Matcher activateMatcher = activatePattern.matcher(st);
    Matcher deactivateMatcher = deactivatePattern.matcher(st);
    if (st == null || st.isEmpty() || listMatcher.matches()) {
      return listEnv();
    } else if (activateMatcher.matches()) {
      String envName = activateMatcher.group(1);
      pythonCommand = "conda run -n " + envName + " \"python -iu\"";
      restartPythonProcess();
      return new InterpreterResult(InterpreterResult.Code.SUCCESS, envName + " activated");
    } else if (deactivateMatcher.matches()) {
      pythonCommand = null;
      restartPythonProcess();
      return new InterpreterResult(InterpreterResult.Code.SUCCESS, "conda deactivated");
    }

    return new InterpreterResult(InterpreterResult.Code.ERROR, "Not supported command: " + st);
  }

  private void restartPythonProcess() {
    PythonInterpreter python = getPythonInterpreter();
    python.close();
    python.open();
  }

  private PythonInterpreter getPythonInterpreter() {
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

  public String getPythonCommand() {
    return pythonCommand;
  }

  private InterpreterResult listEnv() {
    StringBuilder sb = new StringBuilder();
    try {
      int exit = runCommand(sb, "conda", "env", "list");
      if (exit == 0) {
        StringBuffer result = new StringBuffer();

        String[] lines = sb.toString().split("\n");
        for (String s : lines) {
          if (s == null || s.isEmpty() || s.startsWith("#")) {
            continue;
          }
          Matcher match = condaEnvListPattern.matcher(s);

          if (!match.matches()) {
            continue;
          }

          result.append(match.group(1) + "\t" + match.group(2) + "\n");
        }
        return new InterpreterResult(InterpreterResult.Code.SUCCESS, result.toString());
      } else {
        return new InterpreterResult(InterpreterResult.Code.ERROR, sb.toString());
      }
    } catch (IOException | InterruptedException e) {
      throw new InterpreterException(e);
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

  private int runCommand(StringBuilder sb, String ... command)
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
}
