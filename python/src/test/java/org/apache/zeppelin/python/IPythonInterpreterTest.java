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

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.junit.Test;

public class IPythonInterpreterTest extends BasePythonInterpreterTest {

  protected Properties initIntpProperties() {
    Properties properties = new Properties();
    properties.setProperty("zeppelin.python.maxResult", "3");
    properties.setProperty("zeppelin.python.gatewayserver_address", "127.0.0.1");
    return properties;
  }

  protected void startInterpreter(Properties properties) throws InterpreterException {
    interpreter = new LazyOpenInterpreter(new IPythonInterpreter(properties));
    intpGroup = new InterpreterGroup();
    intpGroup.put("session_1", new ArrayList<Interpreter>());
    intpGroup.get("session_1").add(interpreter);
    interpreter.setInterpreterGroup(intpGroup);

    interpreter.open();
  }

  @Override
  public void setUp() throws InterpreterException {
    Properties properties = initIntpProperties();
    startInterpreter(properties);
  }

  @Override
  public void tearDown() throws InterpreterException {
    intpGroup.close();
  }

  @Test
  public void testIPythonAdvancedFeatures()
      throws InterpreterException, InterruptedException, IOException {
    // ipython help
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = interpreter.interpret("range?", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> interpreterResultMessages =
        context.out.toInterpreterResultMessage();
    assertTrue(interpreterResultMessages.get(0).getData().contains("range(stop)"));

    // timeit
    context = getInterpreterContext();
    result = interpreter.interpret("%timeit range(100)", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertTrue(interpreterResultMessages.get(0).getData().contains("loops"));

    // cancel
    final InterpreterContext context2 = getInterpreterContext();
    new Thread() {
      @Override
      public void run() {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        try {
          interpreter.cancel(context2);
        } catch (InterpreterException e) {
          e.printStackTrace();
        }
      }
    }.start();
    result = interpreter.interpret("import time\ntime.sleep(10)", context2);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    interpreterResultMessages = context2.out.toInterpreterResultMessage();
    assertTrue(interpreterResultMessages.get(0).getData().contains("KeyboardInterrupt"));
  }

  @Test
  public void testIPythonPlotting() throws InterpreterException, InterruptedException, IOException {
    // matplotlib
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result =
        interpreter.interpret(
            "%matplotlib inline\n"
                + "import matplotlib.pyplot as plt\ndata=[1,1,2,3,4]\nplt.figure()\nplt.plot(data)",
            context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> interpreterResultMessages =
        context.out.toInterpreterResultMessage();
    // the order of IMAGE and TEXT is not determined
    // check there must be one IMAGE output
    boolean hasImageOutput = false;
    boolean hasLineText = false;
    boolean hasFigureText = false;
    for (InterpreterResultMessage msg : interpreterResultMessages) {
      if (msg.getType() == InterpreterResult.Type.IMG) {
        hasImageOutput = true;
      }
      if (msg.getType() == InterpreterResult.Type.TEXT
          && msg.getData().contains("matplotlib.lines.Line2D")) {
        hasLineText = true;
      }
      if (msg.getType() == InterpreterResult.Type.TEXT
          && msg.getData().contains("matplotlib.figure.Figure")) {
        hasFigureText = true;
      }
    }
    assertTrue("No Image Output", hasImageOutput);
    assertTrue("No Line Text", hasLineText);
    assertTrue("No Figure Text", hasFigureText);

    // bokeh
    // bokeh initialization
    context = getInterpreterContext();
    result =
        interpreter.interpret(
            "from bokeh.io import output_notebook, show\n"
                + "from bokeh.plotting import figure\n"
                + "import bkzep\n"
                + "output_notebook(notebook_type='zeppelin')",
            context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(2, interpreterResultMessages.size());
    assertEquals(InterpreterResult.Type.HTML, interpreterResultMessages.get(0).getType());
    assertTrue(interpreterResultMessages.get(0).getData().contains("Loading BokehJS"));
    assertEquals(InterpreterResult.Type.HTML, interpreterResultMessages.get(1).getType());
    assertTrue(interpreterResultMessages.get(1).getData().contains("BokehJS is being loaded"));

    // bokeh plotting
    context = getInterpreterContext();
    result =
        interpreter.interpret(
            "from bokeh.plotting import figure, output_file, show\n"
                + "x = [1, 2, 3, 4, 5]\n"
                + "y = [6, 7, 2, 4, 5]\n"
                + "p = figure(title=\"simple line example\", x_axis_label='x', y_axis_label='y')\n"
                + "p.line(x, y, legend=\"Temp.\", line_width=2)\n"
                + "show(p)",
            context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(2, interpreterResultMessages.size());
    assertEquals(InterpreterResult.Type.HTML, interpreterResultMessages.get(0).getType());
    assertEquals(InterpreterResult.Type.HTML, interpreterResultMessages.get(1).getType());
    // docs_json is the source data of plotting which bokeh would use to render the plotting.
    assertTrue(interpreterResultMessages.get(1).getData().contains("docs_json"));

    // ggplot
    context = getInterpreterContext();
    result =
        interpreter.interpret(
            "from ggplot import *\n"
                + "ggplot(diamonds, aes(x='price', fill='cut')) +\\\n"
                + "    geom_density(alpha=0.25) +\\\n"
                + "    facet_wrap(\"clarity\")",
            context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    // the order of IMAGE and TEXT is not determined
    // check there must be one IMAGE output
    hasImageOutput = false;
    for (InterpreterResultMessage msg : interpreterResultMessages) {
      if (msg.getType() == InterpreterResult.Type.IMG) {
        hasImageOutput = true;
      }
    }
    assertTrue("No Image Output", hasImageOutput);
  }

  @Test
  public void testGrpcFrameSize() throws InterpreterException, IOException {
    tearDown();

    Properties properties = initIntpProperties();
    properties.setProperty("zeppelin.ipython.grpc.message_size", "3000");

    startInterpreter(properties);

    // to make this test can run under both python2 and python3
    InterpreterResult result =
        interpreter.interpret("from __future__ import print_function", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    InterpreterContext context = getInterpreterContext();
    result = interpreter.interpret("print('1'*3000)", context);
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    List<InterpreterResultMessage> interpreterResultMessages =
        context.out.toInterpreterResultMessage();
    assertEquals(1, interpreterResultMessages.size());
    assertTrue(interpreterResultMessages.get(0).getData().contains("exceeds maximum: 3000"));

    // next call continue work
    result = interpreter.interpret("print(1)", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    tearDown();

    // increase framesize to make it work
    properties.setProperty("zeppelin.ipython.grpc.message_size", "5000");
    startInterpreter(properties);
    // to make this test can run under both python2 and python3
    result =
        interpreter.interpret("from __future__ import print_function", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    context = getInterpreterContext();
    result = interpreter.interpret("print('1'*3000)", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
  }
}
