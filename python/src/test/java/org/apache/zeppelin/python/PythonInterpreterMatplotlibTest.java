
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

import java.util.*;

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterOutputListener;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Type;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * In order for this test to work, test env must have installed:
 * <ol>
 *  - <li>Python</li>
 *  - <li>Matplotlib</li>
 * <ol>
 *
 * Your PYTHONPATH should also contain the directory of the Matplotlib
 * backend files. Usually these can be found in $ZEPPELIN_HOME/interpreter/lib/python.
 *
 * To run manually on such environment, use:
 * <code>
 *   mvn -Dpython.test.exclude='' test -pl python -am
 * </code>
 */
public class PythonInterpreterMatplotlibTest {

  private InterpreterGroup intpGroup;
  private PythonInterpreter python;

  private InterpreterContext context;

  @Before
  public void setUp() throws Exception {
    Properties p = new Properties();
    p.setProperty("zeppelin.python", "python");
    p.setProperty("zeppelin.python.maxResult", "100");

    intpGroup = new InterpreterGroup();

    python = new PythonInterpreter(p);
    python.setInterpreterGroup(intpGroup);
    python.open();

    List<Interpreter> interpreters = new LinkedList<>();
    interpreters.add(python);
    intpGroup.put("note", interpreters);

    context = new InterpreterContext("note", "id", null, "title", "text", new AuthenticationInfo(),
        new HashMap<String, Object>(), new GUI(),
        new AngularObjectRegistry(intpGroup.getId(), null), null,
        new LinkedList<InterpreterContextRunner>(), new InterpreterOutput(null));
  }

  @Test
  public void dependenciesAreInstalled() {
    // matplotlib
    InterpreterResult ret = python.interpret("import matplotlib", context);
    assertEquals(ret.message().toString(), InterpreterResult.Code.SUCCESS, ret.code());
    
    // inline backend
    ret = python.interpret("import backend_zinline", context);
    assertEquals(ret.message().toString(), InterpreterResult.Code.SUCCESS, ret.code());
  }

  @Test
  public void showPlot() {
    // Simple plot test
    InterpreterResult ret;
    ret = python.interpret("import matplotlib.pyplot as plt", context);
    ret = python.interpret("z.configure_mpl(interactive=False)", context);
    ret = python.interpret("plt.plot([1, 2, 3])", context);
    ret = python.interpret("plt.show()", context);

    assertEquals(ret.message().get(0).getData(), InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals(ret.message().get(0).getData(), Type.HTML, ret.message().get(0).getType());
    assertTrue(ret.message().get(0).getData().contains("data:image/png;base64"));
    assertTrue(ret.message().get(0).getData().contains("<div>"));
  }

  @Test
  // Test for when configuration is set to auto-close figures after show().
  public void testClose() {
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
    assertEquals(0, ret.message().size());
    
    // Now test that new plot is drawn. It should be identical to the
    // previous one.
    ret = python.interpret("plt.plot([1, 2, 3])", context);
    ret2 = python.interpret("plt.show()", context);
    assertEquals(ret1.message().get(0).getType(), ret2.message().get(0).getType());
    assertEquals(ret1.message().get(0).getData(), ret2.message().get(0).getData());
  }
  
  @Test
  // Test for when configuration is set to not auto-close figures after show().
  public void testNoClose() {
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
    assertEquals("", ret.message().get(0).getData());
    
    // Now test that plot can be reshown if it is updated. It should be
    // different from the previous one because it will plot the same line
    // again but in a different color.
    ret = python.interpret("plt.plot([1, 2, 3])", context);
    ret2 = python.interpret("plt.show()", context);
    assertNotSame(ret1.message().get(0).getData(), ret2.message().get(0).getData());
  }
}
