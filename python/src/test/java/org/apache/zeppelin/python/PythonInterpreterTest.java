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

import static org.apache.zeppelin.python.PythonInterpreter.DEFAULT_ZEPPELIN_PYTHON;
import static org.apache.zeppelin.python.PythonInterpreter.MAX_RESULT;
import static org.apache.zeppelin.python.PythonInterpreter.ZEPPELIN_PYTHON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.LinkedList;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.zeppelin.interpreter.ClassloaderInterpreter;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Python interpreter unit test
 *
 * Important: ALL tests here DO NOT REQUIRE Python to be installed
 * If Python dependency is required, please look at PythonInterpreterWithPythonInstalledTest
 */
public class PythonInterpreterTest {
  private static final Logger LOG = LoggerFactory.getLogger(PythonProcess.class);

  PythonInterpreter pythonInterpreter = null;
  PythonProcess mockPythonProcess;
  String cmdHistory;

  public static Properties getPythonTestProperties() {
    Properties p = new Properties();
    p.setProperty(ZEPPELIN_PYTHON, DEFAULT_ZEPPELIN_PYTHON);
    p.setProperty(MAX_RESULT, "1000");
    return p;
  }

  @Before
  public void beforeTest() throws IOException {
    cmdHistory = "";

    /*Mock python process*/
    mockPythonProcess = mock(PythonProcess.class);
    when(mockPythonProcess.getPid()).thenReturn(1L);
    when(mockPythonProcess.sendAndGetResult(anyString())).thenAnswer(new Answer<String>() {
      @Override public String answer(InvocationOnMock invocationOnMock) throws Throwable {
        return answerFromPythonMock(invocationOnMock);
      }
    });

    // python interpreter
    pythonInterpreter = spy(new PythonInterpreter(getPythonTestProperties()));

    // create interpreter group
    InterpreterGroup group = new InterpreterGroup();
    group.put("note", new LinkedList<Interpreter>());
    group.get("note").add(pythonInterpreter);
    pythonInterpreter.setInterpreterGroup(group);

    when(pythonInterpreter.getPythonProcess()).thenReturn(mockPythonProcess);
    when(mockPythonProcess.sendAndGetResult(eq("\n\nimport py4j\n"))).thenReturn("ImportError");
  }

  @Test
  public void testOpenInterpreter() {
    pythonInterpreter.open();
    assertEquals(pythonInterpreter.getPythonProcess().getPid(), 1);
  }

  /**
   * If Py4J is not installed, bootstrap_input.py
   * is not sent to Python process and py4j JavaGateway is not running
   */
  @Test
  public void testPy4jIsNotInstalled() {
    pythonInterpreter.open();
    assertNull(pythonInterpreter.getPy4jPort());
    assertTrue(cmdHistory.contains("def help()"));
    assertTrue(cmdHistory.contains("class PyZeppelinContext(object):"));
    assertTrue(cmdHistory.contains("z = PyZeppelinContext"));
    assertTrue(cmdHistory.contains("def show"));
    assertFalse(cmdHistory.contains("GatewayClient"));
  }

  /**
   * If Py4J installed, bootstrap_input.py
   * is sent to interpreter and JavaGateway is running
   */
  @Test
  public void testPy4jInstalled() throws IOException, InterruptedException {
    when(mockPythonProcess.sendAndGetResult(eq("\n\nimport py4j\n"))).thenReturn("");

    pythonInterpreter.open();
    Integer py4jPort = pythonInterpreter.getPy4jPort();
    assertNotNull(py4jPort);

    assertTrue(cmdHistory.contains("def help()"));
    assertTrue(cmdHistory.contains("class PyZeppelinContext(object):"));
    assertTrue(cmdHistory.contains("z = Py4jZeppelinContext"));
    assertTrue(cmdHistory.contains("def show"));
    assertTrue(cmdHistory.contains("GatewayClient(port=" + py4jPort + ")"));
    assertTrue(cmdHistory.contains("org.apache.zeppelin.display.Input"));

    assertTrue(serverIsListeningOn(py4jPort));
    pythonInterpreter.close();
    TimeUnit.MILLISECONDS.sleep(100);
    assertFalse(serverIsListeningOn(py4jPort));
  }

  @Test
  public void testClose() throws IOException, InterruptedException {
    //given: py4j is installed
    when(mockPythonProcess.sendAndGetResult(eq("\n\nimport py4j\n"))).thenReturn("");

    pythonInterpreter.open();
    Integer py4jPort = pythonInterpreter.getPy4jPort();
    assertNotNull(py4jPort);

    //when
    pythonInterpreter.close();
    TimeUnit.MILLISECONDS.sleep(100);

    //then
    assertFalse(serverIsListeningOn(py4jPort));
    verify(mockPythonProcess, times(1)).close();
  }

  @Test
  public void testInterpret() {
    pythonInterpreter.open();
    cmdHistory = "";
    InterpreterResult result = pythonInterpreter.interpret("print a", null);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals("%text print a", result.message().get(0).toString());
  }

  /**
   * Checks if given port is open on 'localhost'
   * @param port
   */
  private boolean serverIsListeningOn(Integer port) {
    Socket s = new Socket();
    boolean serverIsListening = false;

    int retryCount = 0;
    boolean connected = false;
    while (connected = tryToConnect(s, port) && retryCount < 10) {
      serverIsListening = connected;
      tryToClose(s);
      retryCount++;
      s = new Socket();
    }
    return serverIsListening;
  }

  private boolean tryToConnect(Socket s, Integer port) {
    boolean connected = false;
    SocketAddress sa = new InetSocketAddress("localhost", port);
    try {
      s.connect(sa, 10000);
      connected = true;
    } catch (IOException e) {
      //LOG.warn("Can't open connection to " + sa, e);
    }
    return connected;
  }

  private void tryToClose(Socket s) {
    try {
      s.close();
    } catch (IOException e) {
      LOG.error("Can't close connection to " + s.getInetAddress(), e);
    }
  }

  private String answerFromPythonMock(InvocationOnMock invocationOnMock) {
    Object[] inputs = invocationOnMock.getArguments();
    String cmdToExecute = (String) inputs[0];

    if (cmdToExecute != null) {
      cmdHistory += cmdToExecute;
      String[] lines = cmdToExecute.split("\\n");
      String output = "";

      for (int i = 0; i < lines.length; i++) {
        output += lines[i];
      }
      return output;
    } else {
      return "";
    }
  }

  @Test
  public void checkMultiRowErrorFails() {

    PythonInterpreter pythonInterpreter = new PythonInterpreter(
      PythonInterpreterTest.getPythonTestProperties()
    );
    // create interpreter group
    InterpreterGroup group = new InterpreterGroup();
    group.put("note", new LinkedList<Interpreter>());
    group.get("note").add(pythonInterpreter);
    pythonInterpreter.setInterpreterGroup(group);

    pythonInterpreter.open();

    String codeRaiseException = "raise Exception(\"test exception\")";
    InterpreterResult ret = pythonInterpreter.interpret(codeRaiseException, null);

    assertNotNull("Interpreter result for raise exception is Null", ret);

    System.err.println("ret = '" + ret + "'");
    assertEquals(InterpreterResult.Code.ERROR, ret.code());
    assertTrue(ret.message().get(0).getData().length() > 0);

    assertNotNull("Interpreter result for text is Null", ret);
    String codePrintText = "print (\"Exception(\\\"test exception\\\")\")";
    ret = pythonInterpreter.interpret(codePrintText, null);
    assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
    assertTrue(ret.message().get(0).getData().length() > 0);
  }

}
