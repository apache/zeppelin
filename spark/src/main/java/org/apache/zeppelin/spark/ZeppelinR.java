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
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterOutputListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * R repl interaction
 */
public class ZeppelinR implements ExecuteResultHandler {
  Logger logger = LoggerFactory.getLogger(ZeppelinR.class);
  private final String rCmdPath;
  private DefaultExecutor executor;
  private SparkOutputStream outputStream;
  private PipedOutputStream input;
  private final String scriptPath;
  private final String libPath;
  static Map<Integer, ZeppelinR> zeppelinR = Collections.synchronizedMap(
      new HashMap<Integer, ZeppelinR>());

  private InterpreterOutput initialOutput;
  private final int port;
  private boolean rScriptRunning;

  /**
   * To be notified R repl initialization
   */
  boolean rScriptInitialized = false;
  Integer rScriptInitializeNotifier = new Integer(0);


  /**
   * Request to R repl
   */
  Request rRequestObject = null;
  Integer rRequestNotifier = new Integer(0);

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
  public ZeppelinR(String rCmdPath, String libPath, int sparkRBackendPort) {
    this.rCmdPath = rCmdPath;
    this.libPath = libPath;
    this.port = sparkRBackendPort;
    try {
      File scriptFile = File.createTempFile("zeppelin_sparkr-", ".R");
      scriptPath = scriptFile.getAbsolutePath();
    } catch (IOException e) {
      throw new InterpreterException(e);
    }
  }

  /**
   * Start R repl
   * @throws IOException
   */
  public void open() throws IOException {
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

    executor = new DefaultExecutor();
    outputStream = new SparkOutputStream();

    input = new PipedOutputStream();
    PipedInputStream in = new PipedInputStream(input);

    PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream, outputStream, in);
    executor.setWatchdog(new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT));
    executor.setStreamHandler(streamHandler);
    Map env = EnvironmentUtils.getProcEnvironment();


    initialOutput = new InterpreterOutput(new InterpreterOutputListener() {
      @Override
      public void onAppend(InterpreterOutput out, byte[] line) {
        logger.debug(new String(line));
      }

      @Override
      public void onUpdate(InterpreterOutput out, byte[] output) {
      }
    });
    outputStream.setInterpreterOutput(initialOutput);
    executor.execute(cmd, env, this);
    rScriptRunning = true;

    // flush output
    eval("cat('')");
  }

  /**
   * Evaluate expression
   * @param expr
   * @return
   */
  public Object eval(String expr) {
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
  public void set(String key, Object value) {
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
  public Object get(String key) {
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
  public String getS0(String key) {
    synchronized (this) {
      rRequestObject = new Request("getS", key, null);
      return (String) request();
    }
  }


  /**
   * Send request to r repl and return response
   * @return responseValue
   */
  private Object request() throws RuntimeException {
    if (!rScriptRunning) {
      throw new RuntimeException("r repl is not running");
    }

    // wait for rscript initialized
    if (!rScriptInitialized) {
      waitForRScriptInitialized();
    }

    rResponseValue = null;

    synchronized (rRequestNotifier) {
      rRequestNotifier.notify();
    }

    Object respValue = null;
    synchronized (rResponseNotifier) {
      while (rResponseValue == null && rScriptRunning) {
        try {
          rResponseNotifier.wait(1000);
        } catch (InterruptedException e) {
          logger.error(e.getMessage(), e);
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
   * Wait until src/main/resources/R/zeppelin_sparkr.R is initialized
   * and call onScriptInitialized()
   *
   * @throws InterpreterException
   */
  private void waitForRScriptInitialized() throws InterpreterException {
    synchronized (rScriptInitializeNotifier) {
      long startTime = System.nanoTime();
      while (rScriptInitialized == false &&
          rScriptRunning &&
          System.nanoTime() - startTime < 10L * 1000 * 1000000) {
        try {
          rScriptInitializeNotifier.wait(1000);
        } catch (InterruptedException e) {
          logger.error(e.getMessage(), e);
        }
      }
    }

    String errorMessage = "";
    try {
      initialOutput.flush();
      errorMessage = new String(initialOutput.toByteArray());
    } catch (IOException e) {
      e.printStackTrace();
    }


    if (rScriptInitialized == false) {
      throw new InterpreterException("sparkr is not responding " + errorMessage);
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
          logger.error(e.getMessage(), e);
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
    synchronized (rScriptInitializeNotifier) {
      rScriptInitialized = true;
      rScriptInitializeNotifier.notifyAll();
    }
  }


  /**
   * Create R script in tmp dir
   */
  private void createRScript() {
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

    logger.info("File {} created", scriptPath);
  }

  /**
   * Terminate this R repl
   */
  public void close() {
    executor.getWatchdog().destroyProcess();
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


  /**
   * Pass InterpreterOutput to capture the repl output
   * @param out
   */
  public void setInterpreterOutput(InterpreterOutput out) {
    outputStream.setInterpreterOutput(out);
  }



  @Override
  public void onProcessComplete(int i) {
    logger.info("process complete {}", i);
    rScriptRunning = false;
  }

  @Override
  public void onProcessFailed(ExecuteException e) {
    logger.error(e.getMessage(), e);
    rScriptRunning = false;
  }


}
