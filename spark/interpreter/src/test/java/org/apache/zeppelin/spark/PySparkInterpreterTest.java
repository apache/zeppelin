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

import static org.mockito.Mockito.mock;

import com.google.common.io.Files;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Properties;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.apache.zeppelin.python.PythonInterpreterTest;
import org.junit.Test;

public class PySparkInterpreterTest extends PythonInterpreterTest {

  private RemoteInterpreterEventClient mockRemoteEventClient =
      mock(RemoteInterpreterEventClient.class);

  @Override
  public void setUp() throws InterpreterException {
    Properties properties = new Properties();
    properties.setProperty("spark.master", "local");
    properties.setProperty("spark.app.name", "Zeppelin Test");
    properties.setProperty("zeppelin.spark.useHiveContext", "false");
    properties.setProperty("zeppelin.spark.maxResult", "3");
    properties.setProperty("zeppelin.spark.importImplicit", "true");
    properties.setProperty("zeppelin.pyspark.python", "python");
    properties.setProperty("zeppelin.dep.localrepo", Files.createTempDir().getAbsolutePath());
    properties.setProperty("zeppelin.pyspark.useIPython", "false");
    properties.setProperty("zeppelin.spark.useNew", "true");
    properties.setProperty("zeppelin.spark.test", "true");
    properties.setProperty("zeppelin.python.gatewayserver_address", "127.0.0.1");

    // create interpreter group
    intpGroup = new InterpreterGroup();
    intpGroup.put("note", new LinkedList<Interpreter>());

    InterpreterContext context =
        InterpreterContext.builder()
            .setInterpreterOut(new InterpreterOutput(null))
            .setIntpEventClient(mockRemoteEventClient)
            .build();
    InterpreterContext.set(context);
    LazyOpenInterpreter sparkInterpreter =
        new LazyOpenInterpreter(new SparkInterpreter(properties));

    intpGroup.get("note").add(sparkInterpreter);
    sparkInterpreter.setInterpreterGroup(intpGroup);

    LazyOpenInterpreter iPySparkInterpreter =
        new LazyOpenInterpreter(new IPySparkInterpreter(properties));
    intpGroup.get("note").add(iPySparkInterpreter);
    iPySparkInterpreter.setInterpreterGroup(intpGroup);

    interpreter = new LazyOpenInterpreter(new PySparkInterpreter(properties));
    intpGroup.get("note").add(interpreter);
    interpreter.setInterpreterGroup(intpGroup);

    interpreter.open();
  }

  @Override
  public void tearDown() throws InterpreterException {
    intpGroup.close();
    intpGroup = null;
    interpreter = null;
  }

  @Test
  public void testPySpark() throws InterruptedException, InterpreterException, IOException {
    IPySparkInterpreterTest.testPySpark(interpreter, mockRemoteEventClient);
  }

  @Override
  protected InterpreterContext getInterpreterContext() {
    InterpreterContext context = super.getInterpreterContext();
    context.setIntpEventClient(mockRemoteEventClient);
    return context;
  }
}
