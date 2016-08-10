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

package org.apache.zeppelin.python2;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Properties;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Python interpreter for Zeppelin.
 */
public class PythonInterpreter2 extends Interpreter {
  private static final Logger LOG = LoggerFactory.getLogger(PythonInterpreter2.class);

  public static final String INTERPRETER_PY = "/interpreter.py";
  public static final String ZEPPELIN_PYTHON = "zeppelin.python";
  public static final String DEFAULT_ZEPPELIN_PYTHON = "python";
  public static final String MAX_RESULT = "zeppelin.python.maxResult";

  private int maxResult;

  public PythonInterpreter2(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    LOG.info("Starting Python interpreter .....");
    LOG.info("Python path is set to:" + property.getProperty(ZEPPELIN_PYTHON));

    //pick open serverPort
    //start gRPC server ./interpreter.py on serverPort
    //connect to it
  }

  @Override
  public void close() {
    LOG.info("closing Python interpreter .....");
//    LOG.error("Can't close the interpreter", e);
  }

  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext contextInterpreter) {
    if (cmd == null || cmd.isEmpty()) {
      return new InterpreterResult(Code.SUCCESS, "");
    }

    String output = sendCommandToPython(cmd);

    InterpreterResult result;
    if (pythonErrorIn(output)) {
      result = new InterpreterResult(Code.ERROR, output);
    } else {
      result = new InterpreterResult(Code.SUCCESS, output);
    }
    return result;
  }

  @Override
  public void cancel(InterpreterContext context) {
//      LOG.error("Can't interrupt the python interpreter", e);
  }

  @Override
  public int getProgress(InterpreterContext context) {
    //TODO(bzz): get progreess!
    return 0;
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor) {
    //TODO(bzz): get progreess!
    return null;
  }

  @Override
  public FormType getFormType() {
    return FormType.NATIVE;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler(
        PythonInterpreter2.class.getName() + this.hashCode());
  }

  /**
   * Checks if there is a syntax error or an exception
   *
   * @param output Python interpreter output
   * @return true if syntax error or exception has happened
   */
  private boolean pythonErrorIn(String output) {
    return false;
  }

  /**
   * Sends given text to Python interpreter
   * 
   * @param cmd Python expression text
   * @return output
   */
  String sendCommandToPython(String cmd) {
    LOG.debug("Sending : \n" + (cmd.length() > 200 ? cmd.substring(0, 200) + "..." : cmd));
    String output = "";
//    output = ...(cmd);
//    LOG.error("Error when sending commands to python process", e);
    LOG.debug("Got : \n" + output);
    return output;
  }

  public Boolean isPy4jInstalled() {
    String output = sendCommandToPython("\n\nimport py4j\n");
    return !output.contains("ImportError");
  }

  private static int findRandomOpenPortOnAllLocalInterfaces() {
    Integer port = -1;
    try (ServerSocket socket = new ServerSocket(0);) {
      port = socket.getLocalPort();
      socket.close();
    } catch (IOException e) {
      LOG.error("Can't find an open port", e);
    }
    return port;
  }

}
