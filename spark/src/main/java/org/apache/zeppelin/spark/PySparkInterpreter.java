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

package org.apache.zeppelin.spark;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SQLContext;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.InterpreterHookRegistry.HookType;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.interpreter.util.InterpreterOutputStream;
import org.apache.zeppelin.spark.dep.SparkDependencyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import py4j.GatewayServer;

/**
 *
 */
public class PySparkInterpreter extends Interpreter implements ExecuteResultHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(PySparkInterpreter.class);
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
  private long pythonPid;

  private IPySparkInterpreter iPySparkInterpreter;

  public PySparkInterpreter(Properties property) {
    super(property);

    pythonPid = -1;
    try {
      File scriptFile = File.createTempFile("zeppelin_pyspark-", ".py");
      scriptPath = scriptFile.getAbsolutePath();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void createPythonScript() throws InterpreterException {
    ClassLoader classLoader = getClass().getClassLoader();
    File out = new File(scriptPath);

    if (out.exists() && out.isDirectory()) {
      throw new InterpreterException("Can't create python script " + out.getAbsolutePath());
    }

    try {
      FileOutputStream outStream = new FileOutputStream(out);
      IOUtils.copy(
          classLoader.getResourceAsStream("python/zeppelin_pyspark.py"),
          outStream);
      outStream.close();
    } catch (IOException e) {
      throw new InterpreterException(e);
    }

    LOGGER.info("File {} created", scriptPath);
  }

  @Override
  public void open() throws InterpreterException {
    // try IPySparkInterpreter first
    iPySparkInterpreter = getIPySparkInterpreter();
    if (getProperty("zeppelin.pyspark.useIPython", "true").equals("true") &&
        iPySparkInterpreter.checkIPythonPrerequisite()) {
      try {
        iPySparkInterpreter.open();
        if (InterpreterContext.get() != null) {
          // don't print it when it is in testing, just for easy output check in test.
          InterpreterContext.get().out.write(("IPython is available, " +
              "use IPython for PySparkInterpreter\n")
              .getBytes());
        }
        LOGGER.info("Use IPySparkInterpreter to replace PySparkInterpreter");
        return;
      } catch (Exception e) {
        LOGGER.warn("Fail to open IPySparkInterpreter", e);
      }
    }
    iPySparkInterpreter = null;
    if (getProperty("zeppelin.pyspark.useIPython", "true").equals("true")) {
      // don't print it when it is in testing, just for easy output check in test.
      try {
        InterpreterContext.get().out.write(("IPython is not available, " +
            "use the native PySparkInterpreter\n")
            .getBytes());
      } catch (IOException e) {
        LOGGER.warn("Fail to write InterpreterOutput", e);
      }
    }

    // Add matplotlib display hook
    InterpreterGroup intpGroup = getInterpreterGroup();
    if (intpGroup != null && intpGroup.getInterpreterHookRegistry() != null) {
      registerHook(HookType.POST_EXEC_DEV, "__zeppelin__._displayhook()");
    }
    DepInterpreter depInterpreter = getDepInterpreter();

    // load libraries from Dependency Interpreter
    URL [] urls = new URL[0];
    List<URL> urlList = new LinkedList<>();

    if (depInterpreter != null) {
      SparkDependencyContext depc = depInterpreter.getDependencyContext();
      if (depc != null) {
        List<File> files = depc.getFiles();
        if (files != null) {
          for (File f : files) {
            try {
              urlList.add(f.toURI().toURL());
            } catch (MalformedURLException e) {
              LOGGER.error("Error", e);
            }
          }
        }
      }
    }

    String localRepo = getProperty("zeppelin.interpreter.localRepo");
    if (localRepo != null) {
      File localRepoDir = new File(localRepo);
      if (localRepoDir.exists()) {
        File[] files = localRepoDir.listFiles();
        if (files != null) {
          for (File f : files) {
            try {
              urlList.add(f.toURI().toURL());
            } catch (MalformedURLException e) {
              LOGGER.error("Error", e);
            }
          }
        }
      }
    }

    urls = urlList.toArray(urls);
    ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
    try {
      URLClassLoader newCl = new URLClassLoader(urls, oldCl);
      Thread.currentThread().setContextClassLoader(newCl);
      createGatewayServerAndStartScript();
    } catch (Exception e) {
      LOGGER.error("Error", e);
      throw new InterpreterException(e);
    } finally {
      Thread.currentThread().setContextClassLoader(oldCl);
    }
  }

  private Map setupPySparkEnv() throws IOException, InterpreterException {
    Map env = EnvironmentUtils.getProcEnvironment();

    // only set PYTHONPATH in local or yarn-client mode.
    // yarn-cluster will setup PYTHONPATH automatically.
    SparkConf conf = getSparkConf();
    if (!conf.get("spark.submit.deployMode", "client").equals("cluster")) {
      if (!env.containsKey("PYTHONPATH")) {
        env.put("PYTHONPATH", PythonUtils.sparkPythonPath());
      } else {
        env.put("PYTHONPATH", PythonUtils.sparkPythonPath());
      }
    }

    // get additional class paths when using SPARK_SUBMIT and not using YARN-CLIENT
    // also, add all packages to PYTHONPATH since there might be transitive dependencies
    if (SparkInterpreter.useSparkSubmit() &&
        !getSparkInterpreter().isYarnMode()) {

      String sparkSubmitJars = getSparkConf().get("spark.jars").replace(",", ":");

      if (!"".equals(sparkSubmitJars)) {
        env.put("PYTHONPATH", env.get("PYTHONPATH") + sparkSubmitJars);
      }
    }

    LOGGER.info("PYTHONPATH: " + env.get("PYTHONPATH"));

    // set PYSPARK_PYTHON
    if (getSparkConf().contains("spark.pyspark.python")) {
      env.put("PYSPARK_PYTHON", getSparkConf().get("spark.pyspark.python"));
    }
    return env;
  }

  // Run python shell
  // Choose python in the order of
  // PYSPARK_DRIVER_PYTHON > PYSPARK_PYTHON > zeppelin.pyspark.python
  public static String getPythonExec(Properties properties) {
    String pythonExec = properties.getProperty("zeppelin.pyspark.python", "python");
    if (System.getenv("PYSPARK_PYTHON") != null) {
      pythonExec = System.getenv("PYSPARK_PYTHON");
    }
    if (System.getenv("PYSPARK_DRIVER_PYTHON") != null) {
      pythonExec = System.getenv("PYSPARK_DRIVER_PYTHON");
    }
    return pythonExec;
  }

  private void createGatewayServerAndStartScript() throws InterpreterException {
    // create python script
    createPythonScript();

    port = findRandomOpenPortOnAllLocalInterfaces();

    gatewayServer = new GatewayServer(this, port);
    gatewayServer.start();

    String pythonExec = getPythonExec(getProperties());
    LOGGER.info("pythonExec: " + pythonExec);
    CommandLine cmd = CommandLine.parse(pythonExec);
    cmd.addArgument(scriptPath, false);
    cmd.addArgument(Integer.toString(port), false);
    cmd.addArgument(Integer.toString(getSparkInterpreter().getSparkVersion().toNumber()), false);
    executor = new DefaultExecutor();
    outputStream = new InterpreterOutputStream(LOGGER);
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
      Map env = setupPySparkEnv();
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

  private int findRandomOpenPortOnAllLocalInterfaces() throws InterpreterException {
    int port;
    try (ServerSocket socket = new ServerSocket(0);) {
      port = socket.getLocalPort();
      socket.close();
    } catch (IOException e) {
      throw new InterpreterException(e);
    }
    return port;
  }

  @Override
  public void close() {
    if (iPySparkInterpreter != null) {
      iPySparkInterpreter.close();
      return;
    }
    executor.getWatchdog().destroyProcess();
    new File(scriptPath).delete();
    gatewayServer.shutdown();
  }

  PythonInterpretRequest pythonInterpretRequest = null;

  /**
   *
   */
  public class PythonInterpretRequest {
    public String statements;
    public String jobGroup;
    public String jobDescription;

    public PythonInterpretRequest(String statements, String jobGroup,
        String jobDescription) {
      this.statements = statements;
      this.jobGroup = jobGroup;
      this.jobDescription = jobDescription;
    }

    public String statements() {
      return statements;
    }

    public String jobGroup() {
      return jobGroup;
    }

    public String jobDescription() {
      return jobDescription;
    }
  }

  Integer statementSetNotifier = new Integer(0);

  public PythonInterpretRequest getStatements() {
    synchronized (statementSetNotifier) {
      while (pythonInterpretRequest == null) {
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
      LOGGER.debug("Setting python statement output: " + out + ", error: " + error);
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
      LOGGER.debug("onPythonScriptInitialized is called");
      pythonScriptInitialized = true;
      pythonScriptInitializeNotifier.notifyAll();
    }
  }

  public void appendOutput(String message) throws IOException {
    LOGGER.debug("Output from python process: " + message);
    outputStream.getInterpreterOutput().write(message);
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context)
      throws InterpreterException {
    SparkInterpreter sparkInterpreter = getSparkInterpreter();
    sparkInterpreter.populateSparkWebUrl(context);
    if (sparkInterpreter.isUnsupportedSparkVersion()) {
      return new InterpreterResult(Code.ERROR, "Spark "
          + sparkInterpreter.getSparkVersion().toString() + " is not supported");
    }

    if (iPySparkInterpreter != null) {
      return iPySparkInterpreter.interpret(st, context);
    }

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


    if (pythonscriptRunning == false) {
      // python script failed to initialize and terminated
      errorMessage.add(new InterpreterResultMessage(
          InterpreterResult.Type.TEXT, "failed to start pyspark"));
      return new InterpreterResult(Code.ERROR, errorMessage);
    }
    if (pythonScriptInitialized == false) {
      // timeout. didn't get initialized message
      errorMessage.add(new InterpreterResultMessage(
          InterpreterResult.Type.TEXT, "pyspark is not responding"));
      return new InterpreterResult(Code.ERROR, errorMessage);
    }

    if (!sparkInterpreter.getSparkVersion().isPysparkSupported()) {
      errorMessage.add(new InterpreterResultMessage(
          InterpreterResult.Type.TEXT,
          "pyspark " + sparkInterpreter.getSparkContext().version() + " is not supported"));
      return new InterpreterResult(Code.ERROR, errorMessage);
    }
    String jobGroup = Utils.buildJobGroupId(context);
    String jobDesc = "Started by: " + Utils.getUserName(context.getAuthenticationInfo());
    SparkZeppelinContext __zeppelin__ = sparkInterpreter.getZeppelinContext();
    __zeppelin__.setInterpreterContext(context);
    __zeppelin__.setGui(context.getGui());
    __zeppelin__.setNoteGui(context.getNoteGui());
    pythonInterpretRequest = new PythonInterpretRequest(st, jobGroup, jobDesc);
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
      LOGGER.info("Sending SIGINT signal to PID : " + pythonPid);
      Runtime.getRuntime().exec("kill -SIGINT " + pythonPid);
    } else {
      LOGGER.warn("Non UNIX/Linux system, close the interpreter");
      close();
    }
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    if (iPySparkInterpreter != null) {
      iPySparkInterpreter.cancel(context);
      return;
    }
    SparkInterpreter sparkInterpreter = getSparkInterpreter();
    sparkInterpreter.cancel(context);
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
    if (iPySparkInterpreter != null) {
      return iPySparkInterpreter.getProgress(context);
    }
    SparkInterpreter sparkInterpreter = getSparkInterpreter();
    return sparkInterpreter.getProgress(context);
  }


  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor,
      InterpreterContext interpreterContext) throws InterpreterException {
    if (iPySparkInterpreter != null) {
      return iPySparkInterpreter.completion(buf, cursor, interpreterContext);
    }
    if (buf.length() < cursor) {
      cursor = buf.length();
    }
    String completionString = getCompletionTargetString(buf, cursor);
    String completionCommand = "completion.getCompletion('" + completionString + "')";

    //start code for completion
    SparkInterpreter sparkInterpreter = getSparkInterpreter();
    if (sparkInterpreter.isUnsupportedSparkVersion() || pythonscriptRunning == false) {
      return new LinkedList<>();
    }

    pythonInterpretRequest = new PythonInterpretRequest(completionCommand, "", "");
    statementOutput = null;

    synchronized (statementSetNotifier) {
      statementSetNotifier.notify();
    }

    String[] completionList = null;
    synchronized (statementFinishedNotifier) {
      long startTime = System.currentTimeMillis();
      while (statementOutput == null
        && pythonscriptRunning) {
        try {
          if (System.currentTimeMillis() - startTime > MAX_TIMEOUT_SEC * 1000) {
            LOGGER.error("pyspark completion didn't have response for {}sec.", MAX_TIMEOUT_SEC);
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
    for (String name: completionList) {
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
    }
    catch (Exception e) {
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
    }
    else
    {
      completionStartPosition = completionEndPosition - completionStartPosition;
    }
    resultCompletionText = completionScriptText.substring(
            completionStartPosition , completionEndPosition);

    return resultCompletionText;
  }


  private SparkInterpreter getSparkInterpreter() throws InterpreterException {
    LazyOpenInterpreter lazy = null;
    SparkInterpreter spark = null;
    Interpreter p = getInterpreterInTheSameSessionByClassName(SparkInterpreter.class.getName());

    while (p instanceof WrappedInterpreter) {
      if (p instanceof LazyOpenInterpreter) {
        lazy = (LazyOpenInterpreter) p;
      }
      p = ((WrappedInterpreter) p).getInnerInterpreter();
    }
    spark = (SparkInterpreter) p;

    if (lazy != null) {
      lazy.open();
    }
    return spark;
  }

  private IPySparkInterpreter getIPySparkInterpreter() {
    LazyOpenInterpreter lazy = null;
    IPySparkInterpreter iPySpark = null;
    Interpreter p = getInterpreterInTheSameSessionByClassName(IPySparkInterpreter.class.getName());

    while (p instanceof WrappedInterpreter) {
      if (p instanceof LazyOpenInterpreter) {
        lazy = (LazyOpenInterpreter) p;
      }
      p = ((WrappedInterpreter) p).getInnerInterpreter();
    }
    iPySpark = (IPySparkInterpreter) p;
    return iPySpark;
  }

  public SparkZeppelinContext getZeppelinContext() throws InterpreterException {
    SparkInterpreter sparkIntp = getSparkInterpreter();
    if (sparkIntp != null) {
      return getSparkInterpreter().getZeppelinContext();
    } else {
      return null;
    }
  }

  public JavaSparkContext getJavaSparkContext() throws InterpreterException {
    SparkInterpreter intp = getSparkInterpreter();
    if (intp == null) {
      return null;
    } else {
      return new JavaSparkContext(intp.getSparkContext());
    }
  }

  public Object getSparkSession() throws InterpreterException {
    SparkInterpreter intp = getSparkInterpreter();
    if (intp == null) {
      return null;
    } else {
      return intp.getSparkSession();
    }
  }

  public SparkConf getSparkConf() throws InterpreterException {
    JavaSparkContext sc = getJavaSparkContext();
    if (sc == null) {
      return null;
    } else {
      return getJavaSparkContext().getConf();
    }
  }

  public SQLContext getSQLContext() throws InterpreterException {
    SparkInterpreter intp = getSparkInterpreter();
    if (intp == null) {
      return null;
    } else {
      return intp.getSQLContext();
    }
  }

  private DepInterpreter getDepInterpreter() {
    Interpreter p = getInterpreterInTheSameSessionByClassName(DepInterpreter.class.getName());
    if (p == null) {
      return null;
    }

    while (p instanceof WrappedInterpreter) {
      p = ((WrappedInterpreter) p).getInnerInterpreter();
    }
    return (DepInterpreter) p;
  }


  @Override
  public void onProcessComplete(int exitValue) {
    pythonscriptRunning = false;
    LOGGER.info("python process terminated. exit code " + exitValue);
  }

  @Override
  public void onProcessFailed(ExecuteException e) {
    pythonscriptRunning = false;
    LOGGER.error("python process failed", e);
  }
}
