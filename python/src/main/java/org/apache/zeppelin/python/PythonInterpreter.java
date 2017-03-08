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

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.ServerSocket;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterHookRegistry.HookType;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.interpreter.util.InterpreterOutputStream;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import py4j.GatewayServer;

/**
 * Python interpreter for Zeppelin.
 */
public class PythonInterpreter extends Interpreter implements ExecuteResultHandler {
  private static final Logger LOG = LoggerFactory.getLogger(PythonInterpreter.class);
  public static final String ZEPPELIN_PYTHON = "python/zeppelin_python.py";
  public static final String ZEPPELIN_PY4JPATH = "python/py4j-0.9-src.zip";
  public static final String DEFAULT_ZEPPELIN_PYTHON = "python";
  public static final String MAX_RESULT = "zeppelin.python.maxResult";

  private InterpreterContext context;
  private Pattern errorInLastLine = Pattern.compile(".*(Error|Exception): .*$");
  private String pythonPath;
  private int maxResult;
  private String py4jLibPath;

  private String pythonCommand = DEFAULT_ZEPPELIN_PYTHON;

  private GatewayServer gatewayServer;
  private DefaultExecutor executor;
  private int port;
  private InterpreterOutputStream outputStream;
  private BufferedWriter ins;
  private PipedInputStream in;
  private ByteArrayOutputStream input;
  private String scriptPath;
  boolean pythonscriptRunning = false;
  private static final int MAX_TIMEOUT_SEC = 10;

  private long pythonPid = 0;

  Integer statementSetNotifier = new Integer(0);


  public PythonInterpreter(Properties property) {
    super(property);
    try {
      File scriptFile = File.createTempFile("zeppelin_python-", ".py");
      scriptPath = scriptFile.getAbsolutePath();
    } catch (IOException e) {
      throw new InterpreterException(e);
    }
  }

  private void createPy4jLib() {
    py4jLibPath = System.getProperty("user.dir") +
            File.separator + "interpreter" + File.separator + ZEPPELIN_PY4JPATH;
    File py4jLib = new File(py4jLibPath);
    if (py4jLib.exists()) {
      return;
    }

    copyFile(py4jLib, ZEPPELIN_PY4JPATH);
    logger.info("py4j library path : {}", py4jLibPath);
  }

  private void createPythonScript() {
    File out = new File(scriptPath);

    if (out.exists() && out.isDirectory()) {
      throw new InterpreterException("Can't create python script " + out.getAbsolutePath());
    }

    copyFile(out, ZEPPELIN_PYTHON);
    logger.info("File {} created", scriptPath);
  }

  private void copyFile(File out, String sourceFile) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      FileOutputStream outStream = new FileOutputStream(out);
      IOUtils.copy(
          classLoader.getResourceAsStream(sourceFile),
          outStream);
      outStream.close();
    } catch (IOException e) {
      throw new InterpreterException(e);
    }
  }

  private void createGatewayServerAndStartScript() {
    createPythonScript();
    createPy4jLib();

    port = findRandomOpenPortOnAllLocalInterfaces();
    gatewayServer = new GatewayServer(this, port);
    gatewayServer.start();

    // Run python shell
    CommandLine cmd = CommandLine.parse(getPythonCommand());
    cmd.addArgument(scriptPath, false);
    cmd.addArgument(Integer.toString(port), false);
    executor = new DefaultExecutor();
    outputStream = new InterpreterOutputStream(logger);
    PipedOutputStream ps = new PipedOutputStream();
    in = null;
    try {
      in = new PipedInputStream(ps);
    } catch (IOException e1) {
      throw new InterpreterException(e1);
    }
    ins = new BufferedWriter(new OutputStreamWriter(ps));
    input = new ByteArrayOutputStream();

    PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream, outputStream, in);
    executor.setStreamHandler(streamHandler);
    executor.setWatchdog(new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT));

    try {
      Map env = EnvironmentUtils.getProcEnvironment();
      if (!env.containsKey("PYTHONPATH")) {
        env.put("PYTHONPATH", py4jLibPath);
      }
      executor.execute(cmd, env, this);
      pythonscriptRunning = true;
    } catch (IOException e) {
      throw new InterpreterException(e);
    }

    try {
      input.write("import sys, getopt\n".getBytes());
      ins.flush();
    } catch (IOException e) {
      throw new InterpreterException(e);
    }
  }

  @Override
  public void open() {
    // Add matplotlib display hook
    InterpreterGroup intpGroup = getInterpreterGroup();
    if (intpGroup != null && intpGroup.getInterpreterHookRegistry() != null) {
      registerHook(HookType.POST_EXEC_DEV, "z._displayhook()");
    }
    // Add matplotlib display hook
    createGatewayServerAndStartScript();
  }

  @Override
  public void close() {
    pythonscriptRunning = false;
    pythonScriptInitialized = false;

    try {
      ins.flush();
      ins.close();
      input.flush();
      input.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    executor.getWatchdog().destroyProcess();
    new File(scriptPath).delete();
    gatewayServer.shutdown();

    // wait until getStatements stop
    synchronized (statementSetNotifier) {
      try {
        statementSetNotifier.wait(1500);
      } catch (InterruptedException e) {
      }
      statementSetNotifier.notify();
    }
  }

  PythonInterpretRequest pythonInterpretRequest = null;
  /**
   * Result class of python interpreter
   */
  public class PythonInterpretRequest {
    public String statements;
    public String jobGroup;

    public PythonInterpretRequest(String statements, String jobGroup) {
      this.statements = statements;
      this.jobGroup = jobGroup;
    }

    public String statements() {
      return statements;
    }

    public String jobGroup() {
      return jobGroup;
    }
  }

  public PythonInterpretRequest getStatements() {
    synchronized (statementSetNotifier) {

      while (pythonInterpretRequest == null && pythonscriptRunning && pythonScriptInitialized) {
        try {
          statementSetNotifier.wait(1000);
        } catch (InterruptedException e) {
        }
      }
      PythonInterpretRequest req = pythonInterpretRequest;
      pythonInterpretRequest = null;

      return req;
    }
  }

  String statementOutput = null;
  boolean statementError = false;
  Integer statementFinishedNotifier = new Integer(0);

  public void setStatementsFinished(String out, boolean error) {
    synchronized (statementFinishedNotifier) {
      statementOutput = out;
      statementError = error;
      statementFinishedNotifier.notify();
    }
  }

  boolean pythonScriptInitialized = false;
  Integer pythonScriptInitializeNotifier = new Integer(0);

  public void onPythonScriptInitialized(long pid) {
    pythonPid = pid;
    synchronized (pythonScriptInitializeNotifier) {
      pythonScriptInitialized = true;
      pythonScriptInitializeNotifier.notifyAll();
    }
  }

  public void appendOutput(String message) throws IOException {
    outputStream.getInterpreterOutput().write(message);
  }

  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext contextInterpreter) {
    if (cmd == null || cmd.isEmpty()) {
      return new InterpreterResult(Code.SUCCESS, "");
    }

    this.context = contextInterpreter;

    if (!pythonscriptRunning) {
      return new InterpreterResult(Code.ERROR, "python process not running"
        + outputStream.toString());
    }

    outputStream.setInterpreterOutput(context.out);

    synchronized (pythonScriptInitializeNotifier) {
      long startTime = System.currentTimeMillis();
      while (pythonScriptInitialized == false
        && pythonscriptRunning
        && System.currentTimeMillis() - startTime < MAX_TIMEOUT_SEC * 1000) {
        try {
          pythonScriptInitializeNotifier.wait(1000);
        } catch (InterruptedException e) {
        }
      }
    }

    List<InterpreterResultMessage> errorMessage;
    try {
      context.out.flush();
      errorMessage = context.out.toInterpreterResultMessage();
    } catch (IOException e) {
      throw new InterpreterException(e);
    }

    if (pythonscriptRunning == false) {
      // python script failed to initialize and terminated
      errorMessage.add(new InterpreterResultMessage(
        InterpreterResult.Type.TEXT, "failed to start python"));
      return new InterpreterResult(Code.ERROR, errorMessage);
    }
    if (pythonScriptInitialized == false) {
      // timeout. didn't get initialized message
      errorMessage.add(new InterpreterResultMessage(
        InterpreterResult.Type.TEXT, "python is not responding"));
      return new InterpreterResult(Code.ERROR, errorMessage);
    }

    pythonInterpretRequest = new PythonInterpretRequest(cmd, null);
    statementOutput = null;

    synchronized (statementSetNotifier) {
      statementSetNotifier.notify();
    }

    synchronized (statementFinishedNotifier) {
      while (statementOutput == null) {
        try {
          statementFinishedNotifier.wait(1000);
        } catch (InterruptedException e) {
        }
      }
    }

    if (statementError) {
      return new InterpreterResult(Code.ERROR, statementOutput);
    } else {

      try {
        context.out.flush();
      } catch (IOException e) {
        throw new InterpreterException(e);
      }

      return new InterpreterResult(Code.SUCCESS);
    }
  }

  public void interrupt() throws IOException {
    if (pythonPid > -1) {
      logger.info("Sending SIGINT signal to PID : " + pythonPid);
      Runtime.getRuntime().exec("kill -SIGINT " + pythonPid);
    } else {
      logger.warn("Non UNIX/Linux system, close the interpreter");
      close();
    }
  }

  @Override
  public void cancel(InterpreterContext context) {
    try {
      interrupt();
    } catch (IOException e) {
      e.printStackTrace();
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

  public GUI getGui() {
    return context.getGui();
  }

  public Integer getPy4jPort() {
    return port;
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

  @Override
  public void onProcessComplete(int exitValue) {
    pythonscriptRunning = false;
    logger.info("python process terminated. exit code " + exitValue);
  }

  @Override
  public void onProcessFailed(ExecuteException e) {
    pythonscriptRunning = false;
    logger.error("python process failed", e);
  }
}
