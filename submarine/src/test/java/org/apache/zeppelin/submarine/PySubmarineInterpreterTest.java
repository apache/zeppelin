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

package org.apache.zeppelin.submarine;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.submarine.commons.SubmarineConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.Properties;

import static org.apache.zeppelin.submarine.commons.SubmarineConstants.ZEPPELIN_SUBMARINE_AUTH_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PySubmarineInterpreterTest extends BaseInterpreterTest {

  PySubmarineInterpreter pySubmarineIntp;
  protected InterpreterGroup intpGroup;

  @Override
  @BeforeEach
  public void setUp() throws InterpreterException {
    intpGroup = new InterpreterGroup();
    Properties properties = new Properties();
    properties.setProperty(ZEPPELIN_SUBMARINE_AUTH_TYPE, "simple");
    properties.setProperty("zeppelin.python.useIPython", "false");
    properties.setProperty("zeppelin.python.gatewayserver_address", "127.0.0.1");
    properties.setProperty(SubmarineConstants.SUBMARINE_HADOOP_PRINCIPAL, "user");

    pySubmarineIntp = new PySubmarineInterpreter(properties);

    intpGroup.put("note", new LinkedList<Interpreter>());
    intpGroup.get("note").add(pySubmarineIntp);
    pySubmarineIntp.setInterpreterGroup(intpGroup);

    InterpreterContext.set(getIntpContext());
    pySubmarineIntp.open();
  }

  @Test
  void testTensorflow() throws InterpreterException {
    String callTensorflowFunc = "import tensorflow as tf\n" +
        "print('Installed TensorFlow version:' + tf.__version__)";

    InterpreterContext intpContext = getIntpContext();
    InterpreterResult intpResult = pySubmarineIntp.interpret(callTensorflowFunc, intpContext);

    // Check if the SubmarineInterpreter performs the tensorlfow function whether successfully.
    assertEquals(InterpreterResult.Code.SUCCESS, intpResult.code());

    // Successfully execute tensorflow to get the version function,
    // otherwise it will trigger an exception.
    String tfVersionInfo = intpContext.out().getCurrentOutput().toString();
    boolean getVersion = tfVersionInfo.contains("Installed TensorFlow version:");
    assertTrue(getVersion, tfVersionInfo);
  }

  @Override
  @AfterEach
  public void tearDown() throws InterpreterException {
    pySubmarineIntp.close();
    intpGroup.close();
  }
}
