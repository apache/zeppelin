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

package org.apache.zeppelin.python;

import com.google.common.io.Files;
import com.google.gson.Gson;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.interpreter.BaseZeppelinContext;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterHookRegistry.HookType;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.InvalidHookException;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.interpreter.util.InterpreterOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import py4j.GatewayServer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Interpreter for Python, it is the first implementation of interpreter for Python, so with less
 * features compared to IPythonInterpreter, but requires less prerequisites than
 * IPythonInterpreter, only python installation is required.
 */
public class PythonInterpreter extends Interpreter implements ExecuteResultHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(PythonInterpreter.class);
  private static final int MAX_TIMEOUT_SEC = 30;

  private GatewayServer gatewayServer;
  private DefaultExecutor executor;
  private File pythonWorkDir;
  protected boolean useBuiltinPy4j = true;

  // used to forward output from python process to InterpreterOutput
  private InterpreterOutputStream outputStream;
  private AtomicBoolean pythonScriptRunning = new AtomicBoolean(false);
  private AtomicBoolean pythonScriptInitialized = new AtomicBoolean(false);
  private long pythonPid = -1;
  private IPythonInterpreter iPythonInterpreter;
  private BaseZeppelinContext zeppelinContext;
  private String condaPythonExec;  // set by PythonCondaInterpreter
  private boolean usePy4jAuth = false;

  public PythonInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() throws InterpreterException {
    // try IPythonInterpreter first
    iPythonInterpreter = getIPythonInterpreter();
    if (getProperty("zeppelin.python.useIPython", "true").equals("true") &&
        StringUtils.isEmpty(
            iPythonInterpreter.checkIPythonPrerequisite(getPythonExec()))) {
      try {
        iPythonInterpreter.open();
        LOGGER.info("IPython is available, Use IPythonInterpreter to replace PythonInterpreter");
        return;
      } catch (Exception e) {
        iPythonInterpreter = null;
        LOGGER.warn("Fail to open IPythonInterpreter", e);
      }
    }

    // reset iPythonInterpreter to null as it is not available
    iPythonInterpreter = null;
    LOGGER.info("IPython is not available, use the native PythonInterpreter");
    // Add matplotlib display hook
    InterpreterGroup intpGroup = getInterpreterGroup();
    if (intpGroup != null && intpGroup.getInterpreterHookRegistry() != null) {
      try {
        // just for unit test I believe (zjffdu)
        registerHook(HookType.POST_EXEC_DEV.getName(), "__zeppelin__._displayhook()");
      } catch (InvalidHookException e) {
        throw new InterpreterException(e);
      }
    }

    try {
      this.usePy4jAuth = Boolean.parseBoolean(getProperty("zeppelin.py4j.useAuth", "true"));
      createGatewayServerAndStartScript();
    } catch (IOException e) {
      LOGGER.error("Fail to open PythonInterpreter", e);
      throw new InterpreterException("Fail to open PythonInterpreter", e);
    }
  }

  // start gateway sever and start python process
  private void createGatewayServerAndStartScript() throws IOException {
    // start gateway server in JVM side
    int port = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
    // use the FQDN as the server address instead of 127.0.0.1 so that python process in docker
    // container can also connect to this gateway server.
    String serverAddress = PythonUtils.getLocalIP(properties);
    String secret = PythonUtils.createSecret(256);
    this.gatewayServer = PythonUtils.createGatewayServer(this, serverAddress, port, secret,
        usePy4jAuth);
    gatewayServer.start();

    // launch python process to connect to the gateway server in JVM side
    createPythonScript();
    String pythonExec = getPythonExec();
    CommandLine cmd = CommandLine.parse(pythonExec);
    if (!pythonExec.endsWith(".py")) {
      // PythonDockerInterpreter set pythonExec with script
      cmd.addArgument(pythonWorkDir + "/zeppelin_python.py", false);
    }
    cmd.addArgument(serverAddress, false);
    cmd.addArgument(Integer.toString(port), false);

    executor = new DefaultExecutor();
    outputStream = new InterpreterOutputStream(LOGGER);
    PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
    executor.setStreamHandler(streamHandler);
    executor.setWatchdog(new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT));
    Map<String, String> env = setupPythonEnv();
    if (usePy4jAuth) {
      env.put("PY4J_GATEWAY_SECRET", secret);
    }
    LOGGER.info("Launching Python Process Command: " + cmd.getExecutable() +
        " " + StringUtils.join(cmd.getArguments(), " "));
    executor.execute(cmd, env, this);
    pythonScriptRunning.set(true);
  }



  private void createPythonScript() throws IOException {
    // set java.io.tmpdir to /tmp on MacOS, because docker can not share the /var folder which will
    // cause PythonDockerInterpreter fails.
    // https://stackoverflow.com/questions/45122459/docker-mounts-denied-the-paths-are-not-shared-
    // from-os-x-and-are-not-known
    if (System.getProperty("os.name", "").contains("Mac")) {
      System.setProperty("java.io.tmpdir", "/tmp");
    }
    this.pythonWorkDir = Files.createTempDir();
    this.pythonWorkDir.deleteOnExit();
    LOGGER.info("Create Python working dir: " + pythonWorkDir.getAbsolutePath());
    copyResourceToPythonWorkDir("python/zeppelin_python.py", "zeppelin_python.py");
    copyResourceToPythonWorkDir("python/zeppelin_context.py", "zeppelin_context.py");
    copyResourceToPythonWorkDir("python/backend_zinline.py", "backend_zinline.py");
    copyResourceToPythonWorkDir("python/mpl_config.py", "mpl_config.py");
    copyResourceToPythonWorkDir("python/py4j-src-0.10.7.zip", "py4j-src-0.10.7.zip");
  }

  protected boolean useIPython() {
    return this.iPythonInterpreter != null;
  }

  private void copyResourceToPythonWorkDir(String srcResourceName,
                                           String dstFileName) throws IOException {
    FileOutputStream out = null;
    try {
      out = new FileOutputStream(pythonWorkDir.getAbsoluteFile() + "/" + dstFileName);
      IOUtils.copy(
          getClass().getClassLoader().getResourceAsStream(srcResourceName),
          out);
    } finally {
      if (out != null) {
        out.close();
      }
    }
  }

  protected Map<String, String> setupPythonEnv() throws IOException {
    Map<String, String> env = EnvironmentUtils.getProcEnvironment();
    appendToPythonPath(env, pythonWorkDir.getAbsolutePath());
    if (useBuiltinPy4j) {
      appendToPythonPath(env, pythonWorkDir.getAbsolutePath() + "/py4j-src-0.10.7.zip");
    }
    LOGGER.info("PYTHONPATH: " + env.get("PYTHONPATH"));
    return env;
  }

  private void appendToPythonPath(Map<String, String> env, String path) {
    if (!env.containsKey("PYTHONPATH")) {
      env.put("PYTHONPATH", path);
    } else {
      env.put("PYTHONPATH", env.get("PYTHONPATH") + ":" + path);
    }
  }

  // Run python script
  // Choose python in the order of
  // condaPythonExec > zeppelin.python
  protected String getPythonExec() {
    if (condaPythonExec != null) {
      return condaPythonExec;
    } else {
      return getProperty("zeppelin.python", "python");
    }
  }

  public File getPythonWorkDir() {
    return pythonWorkDir;
  }

  @Override
  public void close() throws InterpreterException {
    if (iPythonInterpreter != null) {
      iPythonInterpreter.close();
      return;
    }

    pythonScriptRunning.set(false);
    pythonScriptInitialized.set(false);
    executor.getWatchdog().destroyProcess();
    gatewayServer.shutdown();

    // reset these 2 monitors otherwise when you restart PythonInterpreter it would fails to execute
    // python code as these 2 objects are in incorrect state.
    statementSetNotifier = new Integer(0);
    statementFinishedNotifier = new Integer(0);
  }

  private PythonInterpretRequest pythonInterpretRequest = null;
  private Integer statementSetNotifier = new Integer(0);
  private Integer statementFinishedNotifier = new Integer(0);
  private String statementOutput = null;
  private boolean statementError = false;

  public void setPythonExec(String pythonExec) {
    LOGGER.info("Set Python Command : {}", pythonExec);
    this.condaPythonExec = pythonExec;
  }

  /**
   * Request send to Python Daemon
   */
  public class PythonInterpretRequest {
    public String statements;
    public boolean isForCompletion;
    public boolean isCallHooks;

    public PythonInterpretRequest(String statements, boolean isForCompletion) {
      this(statements, isForCompletion, true);
    }

    public PythonInterpretRequest(String statements, boolean isForCompletion, boolean isCallHooks) {
      this.statements = statements;
      this.isForCompletion = isForCompletion;
      this.isCallHooks = isCallHooks;
    }

    public String statements() {
      return statements;
    }

    public boolean isForCompletion() {
      return isForCompletion;
    }

    public boolean isCallHooks() {
      return isCallHooks;
    }
  }

  // called by Python Process
  public PythonInterpretRequest getStatements() {
    synchronized (statementSetNotifier) {
      while (pythonInterpretRequest == null) {
        try {
          statementSetNotifier.wait(1000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      PythonInterpretRequest req = pythonInterpretRequest;
      pythonInterpretRequest = null;
      return req;
    }
  }

  // called by Python Process
  public void setStatementsFinished(String out, boolean error) {
    synchronized (statementFinishedNotifier) {
      LOGGER.debug("Setting python statement output: " + out + ", error: " + error);
      statementOutput = out;
      statementError = error;
      statementFinishedNotifier.notify();
    }
  }

  // called by Python Process
  public void onPythonScriptInitialized(long pid) {
    pythonPid = pid;
    synchronized (pythonScriptInitialized) {
      LOGGER.debug("onPythonScriptInitialized is called");
      pythonScriptInitialized.set(true);
      pythonScriptInitialized.notifyAll();
    }
  }

  // called by Python Process
  public void appendOutput(String message) throws IOException {
    LOGGER.debug("Output from python process: " + message);
    outputStream.getInterpreterOutput().write(message);
  }

  // used by subclass such as PySparkInterpreter to set JobGroup before executing spark code
  protected void preCallPython(InterpreterContext context) {

  }

  // blocking call. Send python code to python process and get response
  protected void callPython(PythonInterpretRequest request) {
    synchronized (statementSetNotifier) {
      this.pythonInterpretRequest = request;
      statementOutput = null;
      statementSetNotifier.notify();
    }

    synchronized (statementFinishedNotifier) {
      while (statementOutput == null) {
        try {
          statementFinishedNotifier.wait(1000);
        } catch (InterruptedException e) {
          // ignore this exception
        }
      }
    }
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context)
      throws InterpreterException {
    if (iPythonInterpreter != null) {
      return iPythonInterpreter.interpret(st, context);
    }

    if (!pythonScriptRunning.get()) {
      return new InterpreterResult(Code.ERROR, "python process not running "
          + outputStream.toString());
    }

    outputStream.setInterpreterOutput(context.out);

    synchronized (pythonScriptInitialized) {
      long startTime = System.currentTimeMillis();
      while (!pythonScriptInitialized.get()
          && System.currentTimeMillis() - startTime < MAX_TIMEOUT_SEC * 1000) {
        try {
          LOGGER.info("Wait for PythonScript initialized");
          pythonScriptInitialized.wait(100);
        } catch (InterruptedException e) {
          e.printStackTrace();
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

    if (!pythonScriptInitialized.get()) {
      // timeout. didn't get initialized message
      errorMessage.add(new InterpreterResultMessage(
          InterpreterResult.Type.TEXT, "Failed to initialize Python"));
      return new InterpreterResult(Code.ERROR, errorMessage);
    }

    BaseZeppelinContext z = getZeppelinContext();
    z.setInterpreterContext(context);
    z.setGui(context.getGui());
    z.setNoteGui(context.getNoteGui());
    InterpreterContext.set(context);

    preCallPython(context);
    callPython(new PythonInterpretRequest(st, false));

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

  public void interrupt() throws IOException, InterpreterException {
    if (pythonPid > -1) {
      LOGGER.info("Sending SIGINT signal to PID : " + pythonPid);
      Runtime.getRuntime().exec("kill -SIGINT " + pythonPid);
    } else {
      LOGGER.warn("Non UNIX/Linux system, close the interpreter");
      close();
    }
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    if (iPythonInterpreter != null) {
      iPythonInterpreter.cancel(context);
      return;
    }
    try {
      interrupt();
    } catch (IOException e) {
      LOGGER.error("Error", e);
    }
  }

  @Override
  public FormType getFormType() {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    if (iPythonInterpreter != null) {
      return iPythonInterpreter.getProgress(context);
    }
    return 0;
  }


  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor,
                                                InterpreterContext interpreterContext)
      throws InterpreterException {
    if (iPythonInterpreter != null) {
      return iPythonInterpreter.completion(buf, cursor, interpreterContext);
    }
    if (buf.length() < cursor) {
      cursor = buf.length();
    }
    String completionString = getCompletionTargetString(buf, cursor);
    String completionCommand = "__zeppelin_completion__.getCompletion('" + completionString + "')";
    LOGGER.debug("completionCommand: " + completionCommand);

    pythonInterpretRequest = new PythonInterpretRequest(completionCommand, true);
    statementOutput = null;

    synchronized (statementSetNotifier) {
      statementSetNotifier.notify();
    }

    String[] completionList = null;
    synchronized (statementFinishedNotifier) {
      long startTime = System.currentTimeMillis();
      while (statementOutput == null
          && pythonScriptRunning.get()) {
        try {
          if (System.currentTimeMillis() - startTime > MAX_TIMEOUT_SEC * 1000) {
            LOGGER.error("Python completion didn't have response for {}sec.", MAX_TIMEOUT_SEC);
            break;
          }
          statementFinishedNotifier.wait(1000);
        } catch (InterruptedException e) {
          // not working
          LOGGER.info("wait drop");
          return new LinkedList<>();
        }
      }
      if (statementError) {
        return new LinkedList<>();
      }
      Gson gson = new Gson();
      completionList = gson.fromJson(statementOutput, String[].class);
    }
    //end code for completion
    if (completionList == null) {
      return new LinkedList<>();
    }

    List<InterpreterCompletion> results = new LinkedList<>();
    for (String name : completionList) {
      results.add(new InterpreterCompletion(name, name, StringUtils.EMPTY));
    }
    return results;
  }

  private String getCompletionTargetString(String text, int cursor) {
    String[] completionSeqCharaters = {" ", "\n", "\t"};
    int completionEndPosition = cursor;
    int completionStartPosition = cursor;
    int indexOfReverseSeqPostion = cursor;

    String resultCompletionText = "";
    String completionScriptText = "";
    try {
      completionScriptText = text.substring(0, cursor);
    } catch (Exception e) {
      LOGGER.error(e.toString());
      return null;
    }
    completionEndPosition = completionScriptText.length();

    String tempReverseCompletionText = new StringBuilder(completionScriptText).reverse().toString();

    for (String seqCharacter : completionSeqCharaters) {
      indexOfReverseSeqPostion = tempReverseCompletionText.indexOf(seqCharacter);

      if (indexOfReverseSeqPostion < completionStartPosition && indexOfReverseSeqPostion > 0) {
        completionStartPosition = indexOfReverseSeqPostion;
      }

    }

    if (completionStartPosition == completionEndPosition) {
      completionStartPosition = 0;
    } else {
      completionStartPosition = completionEndPosition - completionStartPosition;
    }
    resultCompletionText = completionScriptText.substring(
        completionStartPosition, completionEndPosition);

    return resultCompletionText;
  }

  protected IPythonInterpreter getIPythonInterpreter() throws InterpreterException {
    return getInterpreterInTheSameSessionByClassName(IPythonInterpreter.class, false);
  }

  protected BaseZeppelinContext createZeppelinContext() {
    return new PythonZeppelinContext(
        getInterpreterGroup().getInterpreterHookRegistry(),
        Integer.parseInt(getProperty("zeppelin.python.maxResult", "1000")));
  }

  public BaseZeppelinContext getZeppelinContext() {
    if (zeppelinContext == null) {
      zeppelinContext = createZeppelinContext();
    }
    return zeppelinContext;
  }

  protected void bootstrapInterpreter(String resourceName) throws IOException {
    LOGGER.info("Bootstrap interpreter via " + resourceName);
    String bootstrapCode =
        IOUtils.toString(getClass().getClassLoader().getResourceAsStream(resourceName));
    try {
      // Add hook explicitly, otherwise python will fail to execute the statement
      InterpreterResult result = interpret(bootstrapCode + "\n" + "__zeppelin__._displayhook()",
          InterpreterContext.get());
      if (result.code() != Code.SUCCESS) {
        throw new IOException("Fail to run bootstrap script: " + resourceName);
      }
    } catch (InterpreterException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void onProcessComplete(int exitValue) {
    LOGGER.info("python process terminated. exit code " + exitValue);
    pythonScriptRunning.set(false);
    pythonScriptInitialized.set(false);
  }

  @Override
  public void onProcessFailed(ExecuteException e) {
    LOGGER.error("python process failed", e);
    pythonScriptRunning.set(false);
    pythonScriptInitialized.set(false);
  }

  // Called by Python Process, used for debugging purpose
  public void logPythonOutput(String message) {
    LOGGER.debug("Python Process Output: " + message);
  }
}
