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

package org.apache.zeppelin.flink;


import com.google.common.io.Files;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.apache.zeppelin.python.PythonInterpreterTest;
import org.junit.Test;

import java.util.LinkedList;
import java.util.Properties;

import static org.mockito.Mockito.mock;


public class PyFlinkInterpreterTest extends PythonInterpreterTest {

  private RemoteInterpreterEventClient mockRemoteEventClient =
          mock(RemoteInterpreterEventClient.class);

  @Override
  public void setUp() throws InterpreterException {
    Properties properties = new Properties();
    properties.setProperty("zeppelin.pyflink.python", "python");
    properties.setProperty("zeppelin.flink.maxResult", "3");
    properties.setProperty("zeppelin.dep.localrepo", Files.createTempDir().getAbsolutePath());
    properties.setProperty("zeppelin.pyflink.useIPython", "false");
    properties.setProperty("zeppelin.flink.test", "true");
    properties.setProperty("zeppelin.python.gatewayserver_address", "127.0.0.1");

    // create interpreter group
    intpGroup = new InterpreterGroup();
    intpGroup.put("note", new LinkedList<Interpreter>());

    InterpreterContext context = InterpreterContext.builder()
        .setInterpreterOut(new InterpreterOutput(null))
        .setIntpEventClient(mockRemoteEventClient)
        .build();
    InterpreterContext.set(context);
    LazyOpenInterpreter flinkInterpreter =
        new LazyOpenInterpreter(new FlinkInterpreter(properties));

    intpGroup.get("note").add(flinkInterpreter);
    flinkInterpreter.setInterpreterGroup(intpGroup);

    LazyOpenInterpreter iPyFlinkInterpreter =
        new LazyOpenInterpreter(new IPyFlinkInterpreter(properties));
    intpGroup.get("note").add(iPyFlinkInterpreter);
    iPyFlinkInterpreter.setInterpreterGroup(intpGroup);

    interpreter = new LazyOpenInterpreter(new PyFlinkInterpreter(properties));
    intpGroup.get("note").add(interpreter);
    interpreter.setInterpreterGroup(intpGroup);

    interpreter.open();
  }

  @Override
  public void tearDown() {
    intpGroup.close();
    intpGroup = null;
    interpreter = null;
  }

  @Test
  public void testPyFlink() throws InterpreterException {
    IPyFlinkInterpreterTest.testBatchPyFlink(interpreter);
    IPyFlinkInterpreterTest.testStreamPyFlink(interpreter);
  }

  private static InterpreterContext createInterpreterContext(
          RemoteInterpreterEventClient mockRemoteEventClient) {
    return InterpreterContext.builder()
            .setNoteId("noteId")
            .setParagraphId("paragraphId")
            .setIntpEventClient(mockRemoteEventClient)
            .setInterpreterOut(new InterpreterOutput(null))
            .build();
  }

}
