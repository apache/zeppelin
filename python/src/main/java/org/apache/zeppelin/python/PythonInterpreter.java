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

import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import py4j.GatewayServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

/**
 * Python interpreter for Zeppelin.
 */
public class PythonInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(PythonInterpreter.class);

  public static final String BOOTSTRAP_PY = "/bootstrap.py";
  public static final String BOOTSTRAP_INPUT_PY = "/bootstrap_input.py";
  public static final String ZEPPELIN_PYTHON = "zeppelin.python";
  public static final String DEFAULT_ZEPPELIN_PYTHON = "python";

  private Integer port;
  private GatewayServer gatewayServer;
  private long pythonPid;
  private Boolean py4J = false;
  private InterpreterContext context;

  PythonProcess process = null;

  static {
    Interpreter.register(
        "python",
        "python",
        PythonInterpreter.class.getName(),
        new InterpreterPropertyBuilder()
            .add(ZEPPELIN_PYTHON, DEFAULT_ZEPPELIN_PYTHON,
                "Python directory. Default : python (assume python is in your $PATH)")
            .build()
    );
  }

  public PythonInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    logger.info("Starting Python interpreter .....");
    logger.info("Python path is set to:" + property.getProperty(ZEPPELIN_PYTHON));

    process = getPythonProcess();

    try {
      process.open();
    } catch (IOException e) {
      logger.error("Can't start the python process", e);
    }

    try {
      logger.info("python PID : " + process.getPid());
    } catch (Exception e) {
      logger.warn("Can't find python pid process", e);
    }

    try {
      logger.info("Bootstrap interpreter with " + BOOTSTRAP_PY);
      bootStrapInterpreter(BOOTSTRAP_PY);
    } catch (IOException e) {
      logger.error("Can't execute " + BOOTSTRAP_PY + " to initiate python process", e);
    }

    if (py4J = isPy4jInstalled()) {
      port = findRandomOpenPortOnAllLocalInterfaces();
      logger.info("Py4j gateway port : " + port);
      try {
        gatewayServer = new GatewayServer(this, port);
        gatewayServer.start();
        logger.info("Bootstrap inputs with " + BOOTSTRAP_INPUT_PY);
        bootStrapInterpreter(BOOTSTRAP_INPUT_PY);
      } catch (IOException e) {
        logger.error("Can't execute " + BOOTSTRAP_INPUT_PY + " to " +
            "initialize Zeppelin inputs in python process", e);
      }
    }
  }

  @Override
  public void close() {
    logger.info("closing Python interpreter .....");
    try {
      if (process != null) {
        process.close();
      }
      if (gatewayServer != null) {
        gatewayServer.shutdown();
      }
    } catch (IOException e) {
      logger.error("Can't close the interpreter", e);
    }
  }

  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext contextInterpreter) {
    this.context = contextInterpreter;

    String output = sendCommandToPython(cmd);
    return new InterpreterResult(Code.SUCCESS, output.replaceAll(">>>", "")
        .replaceAll("\\.\\.\\.", "").trim());
  }

  @Override
  public void cancel(InterpreterContext context) {
    try {
      process.interrupt();
    } catch (IOException e) {
      logger.error("Can't interrupt the python interpreter", e);
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

  public PythonProcess getPythonProcess() {
    if (process == null) {
      return new PythonProcess(getProperty(ZEPPELIN_PYTHON));
    } else {
      return process;
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


  private String sendCommandToPython(String cmd) {
    String output = "";
    logger.info("Sending : \n" + (cmd.length() > 200 ? cmd.substring(0, 120) + "..." : cmd));
    try {
      output = process.sendAndGetResult(cmd);
    } catch (IOException e) {
      logger.error("Error when sending commands to python process", e);
    }
    return output;
  }

  private void bootStrapInterpreter(String file) throws IOException {
    BufferedReader bootstrapReader = new BufferedReader(
        new InputStreamReader(
            PythonInterpreter.class.getResourceAsStream(file)));
    String line = null;
    String bootstrapCode = "";

    while ((line = bootstrapReader.readLine()) != null) {
      bootstrapCode += line + "\n";
    }
    if (py4J && port != null && port != -1) {
      bootstrapCode = bootstrapCode.replaceAll("\\%PORT\\%", port.toString());
    }
    logger.info("Bootstrap python interpreter with code from \n " + file);
    sendCommandToPython(bootstrapCode);
  }

  public GUI getGui() {
    return context.getGui();
  }

  public Integer getPy4JPort() {
    return port;
  }

  public Boolean isPy4jInstalled() {
    String output = sendCommandToPython("\n\nimport py4j\n");
    if (output.contains("ImportError")) {
      return false;
    } else {
      return true;
    }
  }

  private int findRandomOpenPortOnAllLocalInterfaces() {
    Integer port = -1;
    try (ServerSocket socket = new ServerSocket(0);) {
      port = socket.getLocalPort();
      socket.close();
    } catch (IOException e) {
      logger.error("Can't find an open port", e);
    }
    return port;
  }

}
