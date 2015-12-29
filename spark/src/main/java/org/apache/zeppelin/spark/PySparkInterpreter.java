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
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SQLContext;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.interpreter.WrappedInterpreter;
import org.apache.zeppelin.spark.dep.DependencyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import py4j.GatewayServer;

/**
 *
 */
public class PySparkInterpreter extends Interpreter implements ExecuteResultHandler {
  Logger logger = LoggerFactory.getLogger(PySparkInterpreter.class);
  private GatewayServer gatewayServer;
  private DefaultExecutor executor;
  private int port;
  private ByteArrayOutputStream outputStream;
  private ByteArrayOutputStream errStream;
  private BufferedWriter ins;
  private PipedInputStream in;
  private ByteArrayOutputStream input;
  private String scriptPath;
  boolean pythonscriptRunning = false;

  static {
    Interpreter.register(
        "pyspark",
        "spark",
        PySparkInterpreter.class.getName(),
        new InterpreterPropertyBuilder()
          .add("zeppelin.pyspark.python",
               SparkInterpreter.getSystemDefault("PYSPARK_PYTHON", null, "python"),
               "Python command to run pyspark with").build());
  }

  public PySparkInterpreter(Properties property) {
    super(property);

    scriptPath = System.getProperty("java.io.tmpdir") + "/zeppelin_pyspark.py";
  }

  private void createPythonScript() {
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

    logger.info("File {} created", scriptPath);
  }

  @Override
  public void open() {
    DepInterpreter depInterpreter = getDepInterpreter();

    // load libraries from Dependency Interpreter
    URL [] urls = new URL[0];

    if (depInterpreter != null) {
      DependencyContext depc = depInterpreter.getDependencyContext();
      if (depc != null) {
        List<File> files = depc.getFiles();
        List<URL> urlList = new LinkedList<URL>();
        if (files != null) {
          for (File f : files) {
            try {
              urlList.add(f.toURI().toURL());
            } catch (MalformedURLException e) {
              logger.error("Error", e);
            }
          }

          urls = urlList.toArray(urls);
        }
      }
    }

    ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
    try {
      URLClassLoader newCl = new URLClassLoader(urls, oldCl);
      Thread.currentThread().setContextClassLoader(newCl);
      createGatewayServerAndStartScript();
    } catch (Exception e) {
      logger.error("Error", e);
      throw new InterpreterException(e);
    } finally {
      Thread.currentThread().setContextClassLoader(oldCl);
    }
  }

  private void createGatewayServerAndStartScript() {
    // create python script
    createPythonScript();

    port = findRandomOpenPortOnAllLocalInterfaces();

    gatewayServer = new GatewayServer(this, port);
    gatewayServer.start();

    // Run python shell
    CommandLine cmd = CommandLine.parse(getProperty("zeppelin.pyspark.python"));
    cmd.addArgument(scriptPath, false);
    cmd.addArgument(Integer.toString(port), false);
    cmd.addArgument(Integer.toString(getSparkInterpreter().getSparkVersion().toNumber()), false);
    executor = new DefaultExecutor();
    outputStream = new ByteArrayOutputStream();
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

  private int findRandomOpenPortOnAllLocalInterfaces() {
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
    executor.getWatchdog().destroyProcess();
    gatewayServer.shutdown();
  }

  PythonInterpretRequest pythonInterpretRequest = null;

  /**
   *
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
      statementOutput = out;
      statementError = error;
      statementFinishedNotifier.notify();
    }

  }

  boolean pythonScriptInitialized = false;
  Integer pythonScriptInitializeNotifier = new Integer(0);

  public void onPythonScriptInitialized() {
    synchronized (pythonScriptInitializeNotifier) {
      pythonScriptInitialized = true;
      pythonScriptInitializeNotifier.notifyAll();
    }
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    SparkInterpreter sparkInterpreter = getSparkInterpreter();
    if (sparkInterpreter.getSparkVersion().isUnsupportedVersion()) {
      return new InterpreterResult(Code.ERROR, "Spark "
          + sparkInterpreter.getSparkVersion().toString() + " is not supported");
    }

    if (!pythonscriptRunning) {
      return new InterpreterResult(Code.ERROR, "python process not running"
          + outputStream.toString());
    }

    outputStream.reset();

    synchronized (pythonScriptInitializeNotifier) {
      long startTime = System.currentTimeMillis();
      while (pythonScriptInitialized == false
          && pythonscriptRunning
          && System.currentTimeMillis() - startTime < 10 * 1000) {
        try {
          pythonScriptInitializeNotifier.wait(1000);
        } catch (InterruptedException e) {
        }
      }
    }

    if (pythonscriptRunning == false) {
      // python script failed to initialize and terminated
      return new InterpreterResult(Code.ERROR, "failed to start pyspark"
          + outputStream.toString());
    }
    if (pythonScriptInitialized == false) {
      // timeout. didn't get initialized message
      return new InterpreterResult(Code.ERROR, "pyspark is not responding "
          + outputStream.toString());
    }

    if (!sparkInterpreter.getSparkVersion().isPysparkSupported()) {
      return new InterpreterResult(Code.ERROR, "pyspark "
          + sparkInterpreter.getSparkContext().version() + " is not supported");
    }
    String jobGroup = sparkInterpreter.getJobGroup(context);
    ZeppelinContext z = sparkInterpreter.getZeppelinContext();
    z.setInterpreterContext(context);
    z.setGui(context.getGui());
    pythonInterpretRequest = new PythonInterpretRequest(st, jobGroup);
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
      return new InterpreterResult(Code.SUCCESS, statementOutput);
    }
  }

  @Override
  public void cancel(InterpreterContext context) {
    SparkInterpreter sparkInterpreter = getSparkInterpreter();
    sparkInterpreter.cancel(context);
  }

  @Override
  public FormType getFormType() {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    SparkInterpreter sparkInterpreter = getSparkInterpreter();
    return sparkInterpreter.getProgress(context);
  }


  @Override
  public List<String> completion(String buf, int cursor) {
    if (buf.length() < cursor) {
      cursor = buf.length();
    }
    String completionString = getCompletionTargetString(buf, cursor);
    String completionCommand = "completion.getCompletion('" + completionString + "')";

    //start code for completion
    SparkInterpreter sparkInterpreter = getSparkInterpreter();
    if (sparkInterpreter.getSparkVersion().isUnsupportedVersion() == false
            && pythonscriptRunning == false) {
      return new LinkedList<String>();
    }

    outputStream.reset();

    pythonInterpretRequest = new PythonInterpretRequest(completionCommand, "");
    statementOutput = null;

    synchronized (statementSetNotifier) {
      statementSetNotifier.notify();
    }

    synchronized (statementFinishedNotifier) {
      while (statementOutput == null) {
        try {
          statementFinishedNotifier.wait(1000);
        } catch (InterruptedException e) {
          // not working
          logger.info("wait drop");
          return new LinkedList<String>();
        }
      }
    }

    if (statementError) {
      return new LinkedList<String>();
    }
    InterpreterResult completionResult = new InterpreterResult(Code.SUCCESS, statementOutput);
    //end code for completion

    Gson gson = new Gson();

    return gson.fromJson(completionResult.message(), LinkedList.class);
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
      logger.error(e.toString());
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


  private SparkInterpreter getSparkInterpreter() {
    InterpreterGroup intpGroup = getInterpreterGroup();
    LazyOpenInterpreter lazy = null;
    SparkInterpreter spark = null;
    synchronized (intpGroup) {
      for (Interpreter intp : getInterpreterGroup()){
        if (intp.getClassName().equals(SparkInterpreter.class.getName())) {
          Interpreter p = intp;
          while (p instanceof WrappedInterpreter) {
            if (p instanceof LazyOpenInterpreter) {
              lazy = (LazyOpenInterpreter) p;
            }
            p = ((WrappedInterpreter) p).getInnerInterpreter();
          }
          spark = (SparkInterpreter) p;
        }
      }
    }
    if (lazy != null) {
      lazy.open();
    }
    return spark;
  }

  public ZeppelinContext getZeppelinContext() {
    SparkInterpreter sparkIntp = getSparkInterpreter();
    if (sparkIntp != null) {
      return getSparkInterpreter().getZeppelinContext();
    } else {
      return null;
    }
  }

  public JavaSparkContext getJavaSparkContext() {
    SparkInterpreter intp = getSparkInterpreter();
    if (intp == null) {
      return null;
    } else {
      return new JavaSparkContext(intp.getSparkContext());
    }
  }

  public SparkConf getSparkConf() {
    JavaSparkContext sc = getJavaSparkContext();
    if (sc == null) {
      return null;
    } else {
      return getJavaSparkContext().getConf();
    }
  }

  public SQLContext getSQLContext() {
    SparkInterpreter intp = getSparkInterpreter();
    if (intp == null) {
      return null;
    } else {
      return intp.getSQLContext();
    }
  }

  private DepInterpreter getDepInterpreter() {
    InterpreterGroup intpGroup = getInterpreterGroup();
    if (intpGroup == null) return null;
    synchronized (intpGroup) {
      for (Interpreter intp : intpGroup) {
        if (intp.getClassName().equals(DepInterpreter.class.getName())) {
          Interpreter p = intp;
          while (p instanceof WrappedInterpreter) {
            p = ((WrappedInterpreter) p).getInnerInterpreter();
          }
          return (DepInterpreter) p;
        }
      }
    }
    return null;
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
