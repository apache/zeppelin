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
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;
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
  public static final String ZEPPELIN_PY4JPATH = "interpreter/python/py4j-0.9.2/src";
  public static final String ZEPPELIN_PYTHON_LIBS = "interpreter/lib/python";
  public static final String DEFAULT_ZEPPELIN_PYTHON = "python";
  public static final String MAX_RESULT = "zeppelin.python.maxResult";

  private PythonZeppelinContext zeppelinContext;
  private InterpreterContext context;
  private Pattern errorInLastLine = Pattern.compile(".*(Error|Exception): .*$");
  private String pythonPath;
  private int maxResult;
  private String py4jLibPath;
  private String pythonLibPath;

  private String pythonCommand;

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
  private IPythonInterpreter iPythonInterpreter;

  Integer statementSetNotifier = new Integer(0);

  public PythonInterpreter(Properties property) {
    super(property);
    try {
      File scriptFile = File.createTempFile("zeppelin_python-", ".py", new File("/tmp"));
      scriptPath = scriptFile.getAbsolutePath();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private String workingDir() {
    URL myURL = getClass().getProtectionDomain().getCodeSource().getLocation();
    java.net.URI myURI = null;
    try {
      myURI = myURL.toURI();
    } catch (URISyntaxException e1)
    {}
    String path = java.nio.file.Paths.get(myURI).toFile().toString();
    return path;
  }

  private void createPythonScript() throws InterpreterException {
    File out = new File(scriptPath);

    if (out.exists() && out.isDirectory()) {
      throw new InterpreterException("Can't create python script " + out.getAbsolutePath());
    }

    copyFile(out, ZEPPELIN_PYTHON);
    logger.info("File {} created", scriptPath);
  }

  public String getScriptPath() {
    return scriptPath;
  }

  private void copyFile(File out, String sourceFile) throws InterpreterException {
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

  private void createGatewayServerAndStartScript()
      throws UnknownHostException, InterpreterException {
    createPythonScript();
    if (System.getenv("ZEPPELIN_HOME") != null) {
      py4jLibPath = System.getenv("ZEPPELIN_HOME") + File.separator + ZEPPELIN_PY4JPATH;
      pythonLibPath = System.getenv("ZEPPELIN_HOME") + File.separator + ZEPPELIN_PYTHON_LIBS;
    } else {
      Path workingPath = Paths.get("..").toAbsolutePath();
      py4jLibPath = workingPath + File.separator + ZEPPELIN_PY4JPATH;
      pythonLibPath = workingPath + File.separator + ZEPPELIN_PYTHON_LIBS;
    }

    port = findRandomOpenPortOnAllLocalInterfaces();
    gatewayServer = new GatewayServer(this,
        port,
        GatewayServer.DEFAULT_PYTHON_PORT,
        InetAddress.getByName("0.0.0.0"),
        InetAddress.getByName("0.0.0.0"),
        GatewayServer.DEFAULT_CONNECT_TIMEOUT,
        GatewayServer.DEFAULT_READ_TIMEOUT,
        (List) null);

    gatewayServer.start();

    // Run python shell
    String pythonCmd = getPythonCommand();
    CommandLine cmd = CommandLine.parse(pythonCmd);

    if (!pythonCmd.endsWith(".py")) {
      // PythonDockerInterpreter set pythoncmd with script
      cmd.addArgument(getScriptPath(), false);
    }
    cmd.addArgument(Integer.toString(port), false);
    cmd.addArgument(getLocalIp(), false);

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
        env.put("PYTHONPATH", py4jLibPath + File.pathSeparator + pythonLibPath);
      } else {
        env.put("PYTHONPATH", env.get("PYTHONPATH") + File.pathSeparator +
                py4jLibPath + File.pathSeparator + pythonLibPath);
      }

      logger.info("cmd = {}", cmd.toString());
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
  public void open() throws InterpreterException {
    // try IPythonInterpreter first. If it is not available, we will fallback to the original
    // python interpreter implementation.
    iPythonInterpreter = getIPythonInterpreter();
    this.zeppelinContext = new PythonZeppelinContext(
        getInterpreterGroup().getInterpreterHookRegistry(),
        Integer.parseInt(getProperty("zeppelin.python.maxResult", "1000")));
    if (getProperty("zeppelin.python.useIPython", "true").equals("true") &&
      iPythonInterpreter.checkIPythonPrerequisite()) {
      try {
        iPythonInterpreter.open();
        if (InterpreterContext.get() != null) {
          InterpreterContext.get().out.write(("IPython is available, " +
              "use IPython for PythonInterpreter\n")
              .getBytes());
        }
        LOG.info("Use IPythonInterpreter to replace PythonInterpreter");
        return;
      } catch (Exception e) {
        iPythonInterpreter = null;
      }
    }
    // reset iPythonInterpreter to null
    iPythonInterpreter = null;

    try {
      if (InterpreterContext.get() != null) {
        InterpreterContext.get().out.write(("IPython is not available, " +
            "use the native PythonInterpreter\n")
            .getBytes());
      }
    } catch (IOException e) {
      LOG.warn("Fail to write InterpreterOutput", e.getMessage());
    }

    // Add matplotlib display hook
    InterpreterGroup intpGroup = getInterpreterGroup();
    if (intpGroup != null && intpGroup.getInterpreterHookRegistry() != null) {
      registerHook(HookType.POST_EXEC_DEV, "__zeppelin__._displayhook()");
    }
    // Add matplotlib display hook
    try {
      createGatewayServerAndStartScript();
    } catch (UnknownHostException e) {
      throw new InterpreterException(e);
    }
  }

  private IPythonInterpreter getIPythonInterpreter() {
    LazyOpenInterpreter lazy = null;
    IPythonInterpreter ipython = null;
    Interpreter p = getInterpreterInTheSameSessionByClassName(IPythonInterpreter.class.getName());

    while (p instanceof WrappedInterpreter) {
      if (p instanceof LazyOpenInterpreter) {
        lazy = (LazyOpenInterpreter) p;
      }
      p = ((WrappedInterpreter) p).getInnerInterpreter();
    }
    ipython = (IPythonInterpreter) p;
    return ipython;
  }

  @Override
  public void close() {
    if (iPythonInterpreter != null) {
      iPythonInterpreter.close();
      return;
    }
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

    public PythonInterpretRequest(String statements) {
      this.statements = statements;
    }

    public String statements() {
      return statements;
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
  public InterpreterResult interpret(String cmd, InterpreterContext contextInterpreter)
      throws InterpreterException {
    if (iPythonInterpreter != null) {
      return iPythonInterpreter.interpret(cmd, contextInterpreter);
    }

    if (cmd == null || cmd.isEmpty()) {
      return new InterpreterResult(Code.SUCCESS, "");
    }

    this.context = contextInterpreter;

    zeppelinContext.setGui(context.getGui());
    zeppelinContext.setNoteGui(context.getNoteGui());

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

    pythonInterpretRequest = new PythonInterpretRequest(cmd);
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

  public InterpreterContext getCurrentInterpreterContext() {
    return context;
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
    if (iPythonInterpreter != null) {
      iPythonInterpreter.cancel(context);
    }
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
    if (iPythonInterpreter != null) {
      return iPythonInterpreter.getProgress(context);
    }
    return 0;
  }

  @Override
  public Scheduler getScheduler() {
    if (iPythonInterpreter != null) {
      return iPythonInterpreter.getScheduler();
    }
    return SchedulerFactory.singleton().createOrGetFIFOScheduler(
        PythonInterpreter.class.getName() + this.hashCode());
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor,
      InterpreterContext interpreterContext) {
    if (iPythonInterpreter != null) {
      return iPythonInterpreter.completion(buf, cursor, interpreterContext);
    }
    return null;
  }

  public void setPythonCommand(String cmd) {
    logger.info("Set Python Command : {}", cmd);
    pythonCommand = cmd;
  }

  private String getPythonCommand() {
    if (pythonCommand == null) {
      return getPythonBindPath();
    } else {
      return pythonCommand;
    }
  }

  public String getPythonBindPath() {
    String path = getProperty("zeppelin.python");
    if (path == null) {
      return DEFAULT_ZEPPELIN_PYTHON;
    } else {
      return path;
    }
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

  void bootStrapInterpreter(String file) throws IOException {
    BufferedReader bootstrapReader = new BufferedReader(
        new InputStreamReader(
            PythonInterpreter.class.getResourceAsStream(file)));
    String line = null;
    String bootstrapCode = "";

    while ((line = bootstrapReader.readLine()) != null) {
      bootstrapCode += line + "\n";
    }

    try {
      interpret(bootstrapCode, context);
    } catch (InterpreterException e) {
      throw new IOException(e);
    }
  }

  public PythonZeppelinContext getZeppelinContext() {
    return zeppelinContext;
  }

  String getLocalIp() {
    try {
      return Inet4Address.getLocalHost().getHostAddress();
    } catch (UnknownHostException e) {
      logger.error("can't get local IP", e);
    }
    // fall back to loopback addreess
    return "127.0.0.1";
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
