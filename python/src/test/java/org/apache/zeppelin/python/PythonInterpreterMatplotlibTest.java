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

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.InterpreterException;
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

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;

public class PythonInterpreterMatplotlibTest implements InterpreterOutputListener {
  private InterpreterGroup intpGroup;
  private PythonInterpreter python;

  private InterpreterContext context;
  InterpreterOutput out;

  @Before
  public void setUp() throws Exception {
    Properties p = new Properties();
    p.setProperty("zeppelin.python", "python");
    p.setProperty("zeppelin.python.maxResult", "100");
    p.setProperty("zeppelin.python.useIPython", "false");

    intpGroup = new InterpreterGroup();

    python = new PythonInterpreter(p);
    python.setInterpreterGroup(intpGroup);

    List<Interpreter> interpreters = new LinkedList<>();
    interpreters.add(python);
    intpGroup.put("note", interpreters);

    out = new InterpreterOutput(this);

    context = new InterpreterContext("note", "id", null, "title", "text",
        new AuthenticationInfo(),
        new HashMap<String, Object>(),
        new GUI(),
        new GUI(),
        new AngularObjectRegistry(intpGroup.getId(), null),
        new LocalResourcePool("id"),
        new LinkedList<InterpreterContextRunner>(),
        out);
    python.open();
  }

  @After
  public void afterTest() throws IOException {
    python.close();
  }

  @Test
  public void dependenciesAreInstalled() throws InterpreterException {
    // matplotlib
    InterpreterResult ret = python.interpret("import matplotlib", context);
    assertEquals(ret.message().toString(), InterpreterResult.Code.SUCCESS, ret.code());

    // inline backend
    ret = python.interpret("import backend_zinline", context);
    assertEquals(ret.message().toString(), InterpreterResult.Code.SUCCESS, ret.code());
  }

  @Test
  public void showPlot() throws IOException, InterpreterException {
    // Simple plot test
    InterpreterResult ret;
    ret = python.interpret("import matplotlib.pyplot as plt", context);
    ret = python.interpret("z.configure_mpl(interactive=False)", context);
    ret = python.interpret("plt.plot([1, 2, 3])", context);
    ret = python.interpret("plt.show()", context);

    assertEquals(new String(out.getOutputAt(0).toByteArray()), InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals(new String(out.getOutputAt(0).toByteArray()), InterpreterResult.Type.TEXT, out.getOutputAt(0).getType());
    assertEquals(new String(out.getOutputAt(1).toByteArray()), InterpreterResult.Type.HTML, out.getOutputAt(1).getType());
    assertTrue(new String(out.getOutputAt(1).toByteArray()).contains("data:image/png;base64"));
    assertTrue(new String(out.getOutputAt(1).toByteArray()).contains("<div>"));
  }

  @Test
  // Test for when configuration is set to auto-close figures after show().
  public void testClose() throws IOException, InterpreterException {
    InterpreterResult ret;
    InterpreterResult ret1;
    InterpreterResult ret2;
    ret = python.interpret("import matplotlib.pyplot as plt", context);
    ret = python.interpret("z.configure_mpl(interactive=False)", context);
    ret = python.interpret("plt.plot([1, 2, 3])", context);
    ret1 = python.interpret("plt.show()", context);

    // Second call to show() should print nothing, and Type should be TEXT.
    // This is because when close=True, there should be no living instances
    // of FigureManager, causing show() to return before setting the output
    // type to HTML.
    ret = python.interpret("plt.show()", context);

    assertEquals(new String(out.getOutputAt(0).toByteArray()), InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals(0, ret.message().size());

    // Now test that new plot is drawn. It should be identical to the
    // previous one.
    ret = python.interpret("plt.plot([1, 2, 3])", context);
    String msg1 =  new String(out.getOutputAt(0).toByteArray());
    InterpreterResult.Type type1 = out.getOutputAt(0).getType();

    ret2 = python.interpret("plt.show()", context);
    String msg2 =  new String(out.getOutputAt(0).toByteArray());
    InterpreterResult.Type type2 = out.getOutputAt(0).getType();

    assertEquals(msg1, msg2);
    assertEquals(type1, type2);
  }

  @Test
  // Test for when configuration is set to not auto-close figures after show().
  public void testNoClose() throws IOException, InterpreterException {
    InterpreterResult ret;
    InterpreterResult ret1;
    InterpreterResult ret2;
    ret = python.interpret("import matplotlib.pyplot as plt", context);
    ret = python.interpret("z.configure_mpl(interactive=False, close=False)", context);
    ret = python.interpret("plt.plot([1, 2, 3])", context);
    ret1 = python.interpret("plt.show()", context);

    // Second call to show() should print nothing, and Type should be HTML.
    // This is because when close=False, there should be living instances
    // of FigureManager, causing show() to set the output
    // type to HTML even though the figure is inactive.
    ret = python.interpret("plt.show()", context);
    String msg1 =  new String(out.getOutputAt(0).toByteArray());
    assertNotSame("", msg1);

    // Now test that plot can be reshown if it is updated. It should be
    // different from the previous one because it will plot the same line
    // again but in a different color.
    ret = python.interpret("plt.plot([1, 2, 3])", context);
    msg1 =  new String(out.getOutputAt(1).toByteArray());
    ret2 = python.interpret("plt.show()", context);
    String msg2 =  new String(out.getOutputAt(1).toByteArray());

    assertNotSame(msg1, msg2);
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
