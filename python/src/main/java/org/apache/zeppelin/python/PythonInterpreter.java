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
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterHookRegistry.HookType;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import py4j.GatewayServer;

/**
 * Python interpreter for Zeppelin.
 */
public class PythonInterpreter extends Interpreter {
  private static final Logger LOG = LoggerFactory.getLogger(PythonInterpreter.class);

  public static final String BOOTSTRAP_PY = "/bootstrap.py";
  public static final String BOOTSTRAP_INPUT_PY = "/bootstrap_input.py";
  public static final String ZEPPELIN_PYTHON = "zeppelin.python";
  public static final String DEFAULT_ZEPPELIN_PYTHON = "python";
  public static final String MAX_RESULT = "zeppelin.python.maxResult";

  private Integer port;
  private GatewayServer gatewayServer;
  private Boolean py4JisInstalled = false;
  private InterpreterContext context;
  private Pattern errorInLastLine = Pattern.compile(".*(Error|Exception): .*$");
  private String pythonPath;
  private int maxResult;

  PythonProcess process = null;
  private String pythonCommand = null;

  public PythonInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    // Add matplotlib display hook
    InterpreterGroup intpGroup = getInterpreterGroup();
    if (intpGroup != null && intpGroup.getInterpreterHookRegistry() != null) {
      registerHook(HookType.POST_EXEC_DEV, "z._displayhook()");
    }
    
    // Add zeppelin-bundled libs to PYTHONPATH
    setPythonPath("../interpreter/lib/python:$PYTHONPATH");
    LOG.info("Starting Python interpreter ---->");
    LOG.info("Python path is set to:" + property.getProperty(ZEPPELIN_PYTHON));

    maxResult = Integer.valueOf(getProperty(MAX_RESULT));
    process = getPythonProcess();

    try {
      process.open();
    } catch (IOException e) {
      LOG.error("Can't start the python process", e);
    }

    try {
      LOG.info("python PID : " + process.getPid());
    } catch (Exception e) {
      LOG.warn("Can't find python pid process", e);
    }

    try {
      LOG.info("Bootstrap interpreter with " + BOOTSTRAP_PY);
      bootStrapInterpreter(BOOTSTRAP_PY);
    } catch (IOException e) {
      LOG.error("Can't execute " + BOOTSTRAP_PY + " to initiate python process", e);
    }

    py4JisInstalled = isPy4jInstalled();
    if (py4JisInstalled) {
      port = findRandomOpenPortOnAllLocalInterfaces();
      LOG.info("Py4j gateway port : " + port);
      try {
        gatewayServer = new GatewayServer(this, port);
        gatewayServer.start();
        LOG.info("Bootstrap inputs with " + BOOTSTRAP_INPUT_PY);
        bootStrapInterpreter(BOOTSTRAP_INPUT_PY);
      } catch (IOException e) {
        LOG.error("Can't execute " + BOOTSTRAP_INPUT_PY + " to " +
            "initialize Zeppelin inputs in python process", e);
      }
    }
  }

  @Override
  public void close() {
    LOG.info("closing Python interpreter <----");
    try {
      if (process != null) {
        process.close();
        process = null;
      }
      if (gatewayServer != null) {
        gatewayServer.shutdown();
      }
    } catch (IOException e) {
      LOG.error("Can't close the interpreter", e);
    }
  }

  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext contextInterpreter) {
    if (cmd == null || cmd.isEmpty()) {
      return new InterpreterResult(Code.SUCCESS, "");
    }
    this.context = contextInterpreter;
    String output = sendCommandToPython(cmd);

    InterpreterResult result;
    if (pythonErrorIn(output)) {
      result = new InterpreterResult(Code.ERROR, output.replaceAll("\\.\\.\\.", ""));
    } else {
      result = new InterpreterResult(Code.SUCCESS, output);
    }
    return result;
  }

  /**
   * Checks if there is a syntax error or an exception
   *
   * @param output Python interpreter output
   * @return true if syntax error or exception has happened
   */
  private boolean pythonErrorIn(String output) {
    boolean isError = false;
    String[] outputMultiline = output.split("\n");
    Matcher errorMatcher;
    for (String row : outputMultiline) {
      errorMatcher = errorInLastLine.matcher(row);
      if (errorMatcher.find() == true) {
        isError = true;
        break;
      }
    }
    return isError;
  }

  @Override
  public void cancel(InterpreterContext context) {
    try {
      process.interrupt();
    } catch (IOException e) {
      LOG.error("Can't interrupt the python interpreter", e);
    }
  }

  @Override
  public FormType getFormType() {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler(
        PythonInterpreter.class.getName() + this.hashCode());
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor) {
    return null;
  }

  public void setPythonPath(String pythonPath) {
    this.pythonPath = pythonPath;
  }

  public PythonProcess getPythonProcess() {
    if (process == null) {
      String binPath = getProperty(ZEPPELIN_PYTHON);
      if (pythonCommand != null) {
        binPath = pythonCommand;
      }
      return new PythonProcess(binPath, pythonPath);
    } else {
      return process;
    }
  }

  public void setPythonCommand(String cmd) {
    pythonCommand = cmd;
  }

  public String getPythonCommand() {
    return pythonCommand;
  }

  private Job getRunningJob(String paragraphId) {
    Job foundJob = null;
    Collection<Job> jobsRunning = getScheduler().getJobsRunning();
    for (Job job : jobsRunning) {
      if (job.getId().equals(paragraphId)) {
        foundJob = job;
        break;
      }
    }
    return foundJob;
  }


  /**
   * Sends given text to Python interpreter, blocks and returns the output
   * @param cmd Python expression text
   * @return output
   */
  String sendCommandToPython(String cmd) {
    String output = "";
    LOG.debug("Sending : \n" + (cmd.length() > 200 ? cmd.substring(0, 200) + "..." : cmd));
    try {
      output = process.sendAndGetResult(cmd);
    } catch (IOException e) {
      LOG.error("Error when sending commands to python process", e);
    }
    LOG.debug("Got : \n" + output);
    return output;
  }

  void bootStrapInterpreter(String file) throws IOException {
    BufferedReader bootstrapReader = new BufferedReader(
        new InputStreamReader(
            PythonInterpreter.class.getResourceAsStream(file)));
    String line = null;
    String bootstrapCode = "";

    while ((line = bootstrapReader.readLine()) != null) {
      bootstrapCode += line + "\n";
    }
    if (py4JisInstalled && port != null && port != -1) {
      bootstrapCode = bootstrapCode.replaceAll("\\%PORT\\%", port.toString());
    }
    LOG.info("Bootstrap python interpreter with code from \n " + file);
    sendCommandToPython(bootstrapCode);
  }

  public GUI getGui() {
    return context.getGui();
  }

  public Integer getPy4jPort() {
    return port;
  }

  public Boolean isPy4jInstalled() {
    String output = sendCommandToPython("\n\nimport py4j\n");
    return !output.contains("ImportError");
  }

  private int findRandomOpenPortOnAllLocalInterfaces() {
    Integer port = -1;
    try (ServerSocket socket = new ServerSocket(0);) {
      port = socket.getLocalPort();
      socket.close();
    } catch (IOException e) {
      LOG.error("Can't find an open port", e);
    }
    return port;
  }

  public int getMaxResult() {
    return maxResult;
  }
}
