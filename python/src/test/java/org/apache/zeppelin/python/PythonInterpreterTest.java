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

package org.apache.zeppelin.python;

import static org.apache.zeppelin.python.PythonInterpreter.DEFAULT_ZEPPELIN_PYTHON;
import static org.apache.zeppelin.python.PythonInterpreter.MAX_RESULT;
import static org.apache.zeppelin.python.PythonInterpreter.ZEPPELIN_PYTHON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterOutputListener;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessageOutput;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PythonInterpreterTest implements InterpreterOutputListener {
  PythonInterpreter pythonInterpreter = null;
  String cmdHistory;
  private InterpreterContext context;
  InterpreterOutput out;

  public static Properties getPythonTestProperties() {
    Properties p = new Properties();
    p.setProperty(ZEPPELIN_PYTHON, DEFAULT_ZEPPELIN_PYTHON);
    p.setProperty(MAX_RESULT, "1000");
    return p;
  }

  @Before
  public void beforeTest() throws IOException {
    cmdHistory = "";

    // python interpreter
    pythonInterpreter = new PythonInterpreter(getPythonTestProperties());

    // create interpreter group
    InterpreterGroup group = new InterpreterGroup();
    group.put("note", new LinkedList<Interpreter>());
    group.get("note").add(pythonInterpreter);
    pythonInterpreter.setInterpreterGroup(group);

    out = new InterpreterOutput(this);

    context = new InterpreterContext("note", "id", null, "title", "text",
        new AuthenticationInfo(),
        new HashMap<String, Object>(),
        new GUI(),
        new AngularObjectRegistry(group.getId(), null),
        new LocalResourcePool("id"),
        new LinkedList<InterpreterContextRunner>(),
        out);
    pythonInterpreter.open();
  }

  @After
  public void afterTest() throws IOException {
    pythonInterpreter.close();
  }

  @Test
  public void testInterpret() throws InterruptedException, IOException {
    InterpreterResult result = pythonInterpreter.interpret("print \"hi\"", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
  }

  @Test
  public void testInterpretInvalidSyntax() throws IOException {
    InterpreterResult result = pythonInterpreter.interpret("for x in range(0,3):  print (\"hi\")\n", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertTrue(new String(out.getOutputAt(0).toByteArray()).contains("hi\nhi\nhi"));
 }

  @Override
  public void onUpdateAll(InterpreterOutput out) {

  }

  @Override
  public void onAppend(int index, InterpreterResultMessageOutput out, byte[] line) {

  }

  @Override
  public void onUpdate(int index, InterpreterResultMessageOutput out) {

  }
}
