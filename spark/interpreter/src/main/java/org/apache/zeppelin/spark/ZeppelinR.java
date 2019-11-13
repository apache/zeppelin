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

import org.apache.commons.exec.*;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.commons.io.IOUtils;
import org.apache.spark.SparkRBackend;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.util.ProcessLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * R repl interaction
 */
public class ZeppelinR {
  private static Logger LOGGER = LoggerFactory.getLogger(ZeppelinR.class);

  private final SparkRInterpreter sparkRInterpreter;
  private final String rCmdPath;
  private final SparkVersion sparkVersion;
  private final int timeout;
  private RProcessLogOutputStream processOutputStream;
  private final String scriptPath;
  private final String libPath;
  static Map<Integer, ZeppelinR> zeppelinR = Collections.synchronizedMap(new HashMap());
  private final int port;
  private RProcessLauncher rProcessLauncher;

  /**
   * Request to R repl
   */
  Request rRequestObject = null;
  Integer rRequestNotifier = new Integer(0);

  public void setInterpreterOutput(InterpreterOutput out) {
    processOutputStream.setInterpreterOutput(out);
  }

  /**
   * Request object
   *
   * type : "eval", "set", "get"
   * stmt : statement to evaluate when type is "eval"
   *        key when type is "set" or "get"
   * value : value object when type is "put"
   */
  public static class Request {
    String type;
    String stmt;
    Object value;

    public Request(String type, String stmt, Object value) {
      this.type = type;
      this.stmt = stmt;
      this.value = value;
    }

    public String getType() {
      return type;
    }

    public String getStmt() {
      return stmt;
    }

    public Object getValue() {
      return value;
    }
  }

  /**
   * Response from R repl
   */
  Object rResponseValue = null;
  boolean rResponseError = false;
  Integer rResponseNotifier = new Integer(0);

  /**
   * Create ZeppelinR instance
   * @param rCmdPath R repl commandline path
   * @param libPath sparkr library path
   */
  public ZeppelinR(String rCmdPath, String libPath, int sparkRBackendPort,
      SparkVersion sparkVersion, int timeout, SparkRInterpreter sparkRInterpreter) {
    this.rCmdPath = rCmdPath;
    this.libPath = libPath;
    this.sparkVersion = sparkVersion;
    this.port = sparkRBackendPort;
    this.timeout = timeout;
    this.sparkRInterpreter = sparkRInterpreter;
    try {
      File scriptFile = File.createTempFile("zeppelin_sparkr-", ".R");
      scriptPath = scriptFile.getAbsolutePath();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Start R repl
   * @throws IOException
   */
  public void open() throws IOException, InterpreterException {
    createRScript();

    zeppelinR.put(hashCode(), this);

    CommandLine cmd = CommandLine.parse(rCmdPath);
    cmd.addArgument("--no-save");
    cmd.addArgument("--no-restore");
    cmd.addArgument("-f");
    cmd.addArgument(scriptPath);
    cmd.addArgument("--args");
    cmd.addArgument(Integer.toString(hashCode()));
    cmd.addArgument(Integer.toString(port));
    cmd.addArgument(libPath);
    cmd.addArgument(Integer.toString(sparkVersion.toNumber()));
    cmd.addArgument(Integer.toString(timeout));
    if (sparkVersion.isSecretSocketSupported()) {
      cmd.addArgument(SparkRBackend.socketSecret());
    }
    // dump out the R command to facilitate manually running it, e.g. for fault diagnosis purposes
    LOGGER.info("R Command: " + cmd.toString());
    processOutputStream = new RProcessLogOutputStream(sparkRInterpreter);
    Map env = EnvironmentUtils.getProcEnvironment();
    rProcessLauncher = new RProcessLauncher(cmd, env, processOutputStream);
    rProcessLauncher.launch();
    rProcessLauncher.waitForReady(30 * 1000);

    if (!rProcessLauncher.isRunning()) {
      if (rProcessLauncher.isLaunchTimeout()) {
        throw new IOException("Launch r process is time out.\n" +
                rProcessLauncher.getErrorMessage());
      } else {
        throw new IOException("Fail to launch r process.\n" +
                rProcessLauncher.getErrorMessage());
      }
    }
    // flush output
    eval("cat('')");
  }

  /**
   * Evaluate expression
   * @param expr
   * @return
   */
  public Object eval(String expr) throws InterpreterException {
    synchronized (this) {
      rRequestObject = new Request("eval", expr, null);
      return request();
    }
  }

  /**
   * assign value to key
   * @param key
   * @param value
   */
  public void set(String key, Object value) throws InterpreterException {
    synchronized (this) {
      rRequestObject = new Request("set", key, value);
      request();
    }
  }

  /**
   * get value of key
   * @param key
   * @return
   */
  public Object get(String key) throws InterpreterException {
    synchronized (this) {
      rRequestObject = new Request("get", key, null);
      return request();
    }
  }

  /**
   * get value of key, as a string
   * @param key
   * @return
   */
  public String getS0(String key) throws InterpreterException {
    synchronized (this) {
      rRequestObject = new Request("getS", key, null);
      return (String) request();
    }
  }

  private boolean isRProcessInitialized() {
    return rProcessLauncher != null && rProcessLauncher.isRunning();
  }

  /**
   * Send request to r repl and return response
   * @return responseValue
   */
  private Object request() throws RuntimeException {
    if (!isRProcessInitialized()) {
      throw new RuntimeException("r repl is not running");
    }

    rResponseValue = null;
    synchronized (rRequestNotifier) {
      rRequestNotifier.notify();
    }

    Object respValue = null;
    synchronized (rResponseNotifier) {
      while (rResponseValue == null && isRProcessInitialized()) {
        try {
          rResponseNotifier.wait(1000);
        } catch (InterruptedException e) {
          LOGGER.error(e.getMessage(), e);
        }
      }
      respValue = rResponseValue;
      rResponseValue = null;
    }

    if (rResponseError) {
      throw new RuntimeException(respValue.toString());
    } else {
      return respValue;
    }
  }

  /**
   * invoked by src/main/resources/R/zeppelin_sparkr.R
   * @return
   */
  public Request getRequest() {
    synchronized (rRequestNotifier) {
      while (rRequestObject == null) {
        try {
          rRequestNotifier.wait(1000);
        } catch (InterruptedException e) {
          LOGGER.error(e.getMessage(), e);
        }
      }

      Request req = rRequestObject;
      rRequestObject = null;
      return req;
    }
  }

  /**
   * invoked by src/main/resources/R/zeppelin_sparkr.R
   * @param value
   * @param error
   */
  public void setResponse(Object value, boolean error) {
    synchronized (rResponseNotifier) {
      rResponseValue = value;
      rResponseError = error;
      rResponseNotifier.notify();
    }
  }

  /**
   * invoked by src/main/resources/R/zeppelin_sparkr.R
   */
  public void onScriptInitialized() {
    rProcessLauncher.initialized();
  }

  /**
   * Create R script in tmp dir
   */
  private void createRScript() throws InterpreterException {
    ClassLoader classLoader = getClass().getClassLoader();
    File out = new File(scriptPath);

    if (out.exists() && out.isDirectory()) {
      throw new InterpreterException("Can't create r script " + out.getAbsolutePath());
    }

    try {
      FileOutputStream outStream = new FileOutputStream(out);
      IOUtils.copy(
          classLoader.getResourceAsStream("R/zeppelin_sparkr.R"),
          outStream);
      outStream.close();
    } catch (IOException e) {
      throw new InterpreterException(e);
    }

    LOGGER.info("File {} created", scriptPath);
  }

  /**
   * Terminate this R repl
   */
  public void close() {
    if (rProcessLauncher != null) {
      rProcessLauncher.stop();
    }
    new File(scriptPath).delete();
    zeppelinR.remove(hashCode());
  }

  /**
   * Get instance
   * This method will be invoded from zeppelin_sparkr.R
   * @param hashcode
   * @return
   */
  public static ZeppelinR getZeppelinR(int hashcode) {
    return zeppelinR.get(hashcode);
  }

  class RProcessLauncher extends ProcessLauncher {

    public RProcessLauncher(CommandLine commandLine,
                           Map<String, String> envs,
                           ProcessLogOutputStream processLogOutput) {
      super(commandLine, envs, processLogOutput);
    }

    @Override
    public void waitForReady(int timeout) {
      long startTime = System.currentTimeMillis();
      synchronized (this) {
        while (state == State.LAUNCHED) {
          LOGGER.info("Waiting for R process initialized");
          try {
            wait(100);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          if ((System.currentTimeMillis() - startTime) > timeout) {
            onTimeout();
            break;
          }
        }
      }
    }

    public void initialized() {
      synchronized (this) {
        this.state = State.RUNNING;
        notify();
      }
    }
  }

  public static class RProcessLogOutputStream extends ProcessLauncher.ProcessLogOutputStream {

    private InterpreterOutput interpreterOutput;
    private SparkRInterpreter sparkRInterpreter;

    public RProcessLogOutputStream(SparkRInterpreter sparkRInterpreter) {
      this.sparkRInterpreter = sparkRInterpreter;
    }

    /**
     * Redirect r process output to interpreter output.
     * @param interpreterOutput
     */
    public void setInterpreterOutput(InterpreterOutput interpreterOutput) {
      this.interpreterOutput = interpreterOutput;
    }

    @Override
    protected void processLine(String s, int i) {
      super.processLine(s, i);
      if (s.contains("Java SparkR backend might have failed") // spark 2.x
          || s.contains("Execution halted")) { // spark 1.x
        sparkRInterpreter.getRbackendDead().set(true);
      }
      if (interpreterOutput != null) {
        try {
          interpreterOutput.write(s);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }

    @Override
    public void close() throws IOException {
      super.close();
      if (interpreterOutput != null) {
        interpreterOutput.close();
      }
    }
  }
}
