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
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterOutputListener;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessageOutput;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.apache.zeppelin.python.PythonInterpreterTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Properties;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;


public class PyFlinkInterpreterTest extends PythonInterpreterTest {

  private RemoteInterpreterEventClient mockRemoteEventClient =
          mock(RemoteInterpreterEventClient.class);

  private Interpreter flinkScalaInterpreter;
  private Interpreter streamSqlInterpreter;
  private Interpreter batchSqlInterpreter;

  // catch the streaming appendOutput in onAppend
  protected volatile String appendOutput = "";
  protected volatile InterpreterResult.Type appendOutputType;
  // catch the flinkInterpreter appendOutput in onUpdate
  protected InterpreterResultMessageOutput updatedOutput;

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
    intpGroup.put("session_1", new LinkedList<>());

    InterpreterContext context = InterpreterContext.builder()
        .setInterpreterOut(new InterpreterOutput(null))
        .setIntpEventClient(mockRemoteEventClient)
        .build();
    InterpreterContext.set(context);
    flinkScalaInterpreter = new LazyOpenInterpreter(new FlinkInterpreter(properties));
    intpGroup.get("session_1").add(flinkScalaInterpreter);
    flinkScalaInterpreter.setInterpreterGroup(intpGroup);

    LazyOpenInterpreter iPyFlinkInterpreter =
        new LazyOpenInterpreter(new IPyFlinkInterpreter(properties));
    intpGroup.get("session_1").add(iPyFlinkInterpreter);
    iPyFlinkInterpreter.setInterpreterGroup(intpGroup);

    interpreter = new LazyOpenInterpreter(new PyFlinkInterpreter(properties));
    intpGroup.get("session_1").add(interpreter);
    interpreter.setInterpreterGroup(intpGroup);

    streamSqlInterpreter = new LazyOpenInterpreter(new FlinkStreamSqlInterpreter(properties));
    batchSqlInterpreter = new LazyOpenInterpreter(new FlinkBatchSqlInterpreter(properties));
    intpGroup.get("session_1").add(streamSqlInterpreter);
    intpGroup.get("session_1").add(batchSqlInterpreter);
    streamSqlInterpreter.setInterpreterGroup(intpGroup);
    batchSqlInterpreter.setInterpreterGroup(intpGroup);

    interpreter.open();
  }

  @Override
  public void tearDown() {
    intpGroup.close();
    intpGroup = null;
    interpreter = null;
  }

  @Test
  public void testPyFlink() throws InterpreterException, IOException {
    IPyFlinkInterpreterTest.testBatchPyFlink(interpreter, flinkScalaInterpreter);
    IPyFlinkInterpreterTest.testStreamPyFlink(interpreter, flinkScalaInterpreter);
  }

  protected InterpreterContext getInterpreterContext() {
    appendOutput = "";
    InterpreterContext context = InterpreterContext.builder()
            .setInterpreterOut(new InterpreterOutput(null))
            .setAngularObjectRegistry(new AngularObjectRegistry("flink", null))
            .setIntpEventClient(mockRemoteEventClient)
            .build();
    context.out = new InterpreterOutput(
            new InterpreterOutputListener() {
              @Override
              public void onUpdateAll(InterpreterOutput out) {
                System.out.println();
              }

              @Override
              public void onAppend(int index, InterpreterResultMessageOutput out, byte[] line) {
                try {
                  appendOutputType = out.toInterpreterResultMessage().getType();
                  appendOutput = out.toInterpreterResultMessage().getData();
                } catch (IOException e) {
                  e.printStackTrace();
                }
              }

              @Override
              public void onUpdate(int index, InterpreterResultMessageOutput out) {
                updatedOutput = out;
              }
            });
    return context;
  }
}
