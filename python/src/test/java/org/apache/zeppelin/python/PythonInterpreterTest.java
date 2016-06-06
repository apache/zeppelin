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


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.anyString;

import org.apache.zeppelin.interpreter.InterpreterResult;

import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Properties;

/**
 * Python interpreter unit test
 */
public class PythonInterpreterTest {

  Logger logger = LoggerFactory.getLogger(PythonProcess.class);

  public static final String ZEPPELIN_PYTHON = "zeppelin.python";
  public static final String DEFAULT_ZEPPELIN_PYTHON = "python";

  PythonInterpreter pythonInterpreter = null;
  PythonProcess mockPythonProcess;
  String cmdHistory;

  @Before
  public void beforeTest() {
    cmdHistory = "";

    /*Mock python process*/
    mockPythonProcess = mock(PythonProcess.class);
    when(mockPythonProcess.getPid()).thenReturn((long) 1);
    try {
      when(mockPythonProcess.sendAndGetResult(anyString())).thenAnswer(
          new Answer<String>() {
        @Override
        public String answer(InvocationOnMock invocationOnMock) throws Throwable {
          return answerFromPythonMock(invocationOnMock);
        }
      });
    } catch (IOException e) {
      logger.error("Can't initiate python process", e);
    }

    Properties properties = new Properties();
    properties.put(ZEPPELIN_PYTHON, DEFAULT_ZEPPELIN_PYTHON);
    pythonInterpreter = spy(new PythonInterpreter(properties));

    when(pythonInterpreter.getPythonProcess()).thenReturn(mockPythonProcess);


    try {
      when(mockPythonProcess.sendAndGetResult(eq("\n\nimport py4j\n"))).thenReturn("ImportError");
    } catch (IOException e) {
      e.printStackTrace();
    }


  }

  @Test
  public void testOpenInterpreter() {
    pythonInterpreter.open();
    assertEquals(pythonInterpreter.getPythonProcess().getPid(), 1);

  }

  @Test
  public void testPy4jIsNotInstalled() {

    /*
    If Py4J is not installed, bootstrap_input.py
    is not sent to Python process and
    py4j JavaGateway is not running
     */
    pythonInterpreter.open();
    assertNull(pythonInterpreter.getPy4JPort());

    assertTrue(cmdHistory.contains("def help()"));
    assertTrue(cmdHistory.contains("class PyZeppelinContext():"));
    assertTrue(cmdHistory.contains("z = PyZeppelinContext"));
    assertTrue(cmdHistory.contains("def zeppelin_show"));
    assertFalse(cmdHistory.contains("GatewayClient"));

  }

  @Test
  public void testPy4JInstalled() {


    /*
    If Py4J installed, bootstrap_input.py
    is sent to interpreter and JavaGateway is
    running
     */

    try {
      when(mockPythonProcess.sendAndGetResult(eq("\n\nimport py4j\n"))).thenReturn(">>>");
    } catch (IOException e) {
      e.printStackTrace();
    }
    pythonInterpreter.open();
    Integer py4jPort = pythonInterpreter.getPy4JPort();
    assertNotNull(py4jPort);

    assertTrue(cmdHistory.contains("def help()"));
    assertTrue(cmdHistory.contains("class PyZeppelinContext():"));
    assertTrue(cmdHistory.contains("z = PyZeppelinContext"));
    assertTrue(cmdHistory.contains("def zeppelin_show"));
    assertTrue(cmdHistory.contains("GatewayClient(port=" + py4jPort + ")"));
    assertTrue(cmdHistory.contains("org.apache.zeppelin.display.Input"));


    assertTrue(checkSocketAdress(py4jPort));

  }


  @Test
  public void testClose() {

    try {
      when(mockPythonProcess.sendAndGetResult(eq("\n\nimport py4j\n"))).thenReturn(">>>");
    } catch (IOException e) {
      e.printStackTrace();
    }
    pythonInterpreter.open();
    Integer py4jPort = pythonInterpreter.getPy4JPort();

    assertNotNull(py4jPort);
    pythonInterpreter.close();

    assertFalse(checkSocketAdress(py4jPort));
    try {
      verify(mockPythonProcess, times(1)).close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  @Test
  public void testInterpret() {

    pythonInterpreter.open();
    cmdHistory = "";
    InterpreterResult result = pythonInterpreter.interpret("print a", null);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals("%text print a", result.toString());

  }



  private boolean checkSocketAdress(Integer py4jPort) {
    Socket s = new Socket();
    SocketAddress sa = new InetSocketAddress("localhost", py4jPort);
    Boolean working = null;
    try {
      s.connect(sa, 10000);
    } catch (IOException e) {
      working = false;
    }

    if (working == null) {
      working = s.isConnected();
      try {
        s.close();
      } catch (IOException e) {
        logger.error("Can't close connection to localhost:" + py4jPort, e);
      }
    }
    return working;
  }



  private String answerFromPythonMock(InvocationOnMock invocationOnMock) {
    Object[] inputs = invocationOnMock.getArguments();
    String cmdToExecute = (String) inputs[0];

    if (cmdToExecute != null) {
      cmdHistory += cmdToExecute;
      String[] lines = cmdToExecute.split("\\n");
      String output = "";

      for (int i = 0; i < lines.length; i++) {
        output += ">>>" + lines[i];
      }
      return output;
    } else {
      return ">>>";
    }
  }

}
