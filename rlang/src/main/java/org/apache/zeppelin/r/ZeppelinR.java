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
package org.apache.zeppelin.r;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.r.SparkRBackend;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.util.ProcessLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * R repl interaction
 */
public class ZeppelinR {
  private static Logger LOGGER = LoggerFactory.getLogger(ZeppelinR.class);

  private RInterpreter rInterpreter;
  private RProcessLogOutputStream processOutputStream;
  static Map<Integer, ZeppelinR> zeppelinR = Collections.synchronizedMap(new HashMap());
  private RProcessLauncher rProcessLauncher;

  /**
   * Request to R repl
   */
  private Request rRequestObject = null;
  private Integer rRequestNotifier = new Integer(0);

  /**
   * Response from R repl
   */
  private Object rResponseValue = null;
  private boolean rResponseError = false;
  private Integer rResponseNotifier = new Integer(0);

  public ZeppelinR(RInterpreter rInterpreter) {
    this.rInterpreter = rInterpreter;
  }

  /**
   * Start R repl
   * @throws IOException
   */
  public void open() throws IOException, InterpreterException {

    String rCmdPath = rInterpreter.getProperty("zeppelin.R.cmd", "R");
    String sparkRLibPath;

    if (System.getenv("SPARK_HOME") != null) {
      // local or yarn-client mode when SPARK_HOME is specified
      sparkRLibPath = System.getenv("SPARK_HOME") + "/R/lib";
    } else if (System.getenv("ZEPPELIN_HOME") != null){
      // embedded mode when SPARK_HOME is not specified or for native R support
      String interpreter = "r";
      if (rInterpreter.isSparkSupported()) {
        interpreter = "spark";
      }
      sparkRLibPath = System.getenv("ZEPPELIN_HOME") + "/interpreter/" + interpreter + "/R/lib";
      // workaround to make sparkr work without SPARK_HOME
      System.setProperty("spark.test.home", System.getenv("ZEPPELIN_HOME") + "/interpreter/" + interpreter);
    } else {
      // yarn-cluster mode
      sparkRLibPath = "sparkr";
    }
    if (!new File(sparkRLibPath).exists()) {
      throw new InterpreterException(String.format("sparkRLib %s doesn't exist", sparkRLibPath));
    }

    File scriptFile = File.createTempFile("zeppelin_sparkr-", ".R");
    FileOutputStream out = null;
    InputStream in = null;
    try {
      out = new FileOutputStream(scriptFile);
      in = getClass().getClassLoader().getResourceAsStream("R/zeppelin_sparkr.R");
      IOUtils.copy(in, out);
    } catch (IOException e) {
      throw new InterpreterException(e);
    } finally {
      if (out != null) {
        out.close();
      }
      if (in != null) {
        in.close();
      }
    }

    zeppelinR.put(hashCode(), this);
    String timeout = rInterpreter.getProperty("spark.r.backendConnectionTimeout", "6000");

    CommandLine cmd = CommandLine.parse(rCmdPath);
    cmd.addArgument("--no-save");
    cmd.addArgument("--no-restore");
    cmd.addArgument("-f");
    cmd.addArgument(scriptFile.getAbsolutePath());
    cmd.addArgument("--args");
    cmd.addArgument(Integer.toString(hashCode()));
    cmd.addArgument(Integer.toString(SparkRBackend.get().port()));
    cmd.addArgument(sparkRLibPath);
    cmd.addArgument(rInterpreter.sparkVersion() + "");
    cmd.addArgument(timeout);
    cmd.addArgument(rInterpreter.isSparkSupported() + "");
    if (rInterpreter.isSecretSupported()) {
      cmd.addArgument(SparkRBackend.get().socketSecret());
    }
    // dump out the R command to facilitate manually running it, e.g. for fault diagnosis purposes
    LOGGER.info("R Command: " + cmd.toString());
    processOutputStream = new RProcessLogOutputStream(rInterpreter);
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
   * Terminate this R repl
   */
  public void close() {
    if (rProcessLauncher != null) {
      rProcessLauncher.stop();
    }
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
    private RInterpreter rInterpreter;

    public RProcessLogOutputStream(RInterpreter rInterpreter) {
      this.rInterpreter = rInterpreter;
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
        rInterpreter.getRbackendDead().set(true);
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
