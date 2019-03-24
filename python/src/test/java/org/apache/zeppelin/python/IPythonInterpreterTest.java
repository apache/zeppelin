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

import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.display.ui.CheckBox;
import org.apache.zeppelin.display.ui.Select;
import org.apache.zeppelin.display.ui.TextBox;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterOutputListener;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.InterpreterResultMessageOutput;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;


public class IPythonInterpreterTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(IPythonInterpreterTest.class);
  private IPythonInterpreter interpreter;

  public void startInterpreter(Properties properties) throws InterpreterException {
    interpreter = new IPythonInterpreter(properties);
    InterpreterGroup mockInterpreterGroup = mock(InterpreterGroup.class);
    interpreter.setInterpreterGroup(mockInterpreterGroup);
    interpreter.open();
  }

  @After
  public void close() throws InterpreterException {
    interpreter.close();
  }


  @Test
  public void testIPython() throws IOException, InterruptedException, InterpreterException {
    startInterpreter(new Properties());
    testInterpreter(interpreter);
  }

  @Test
<<<<<<< HEAD
  public void testGrpcFrameSize() throws InterpreterException, IOException {
    Properties properties = new Properties();
    properties.setProperty("zeppelin.ipython.grpc.message_size", "4");
    startInterpreter(properties);

    // to make this test can run under both python2 and python3
    InterpreterResult result = interpreter.interpret("from __future__ import print_function", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    InterpreterContext context = getInterpreterContext();
    result = interpreter.interpret("print(11111111111111111111111111111)", context);
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    List<InterpreterResultMessage> interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, interpreterResultMessages.size());
    assertTrue(interpreterResultMessages.get(0).getData().contains("exceeds maximum size 4"));

    // next call continue work
    result = interpreter.interpret("print(1)", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    close();

    // increase framesize to make it work
    properties.setProperty("zeppelin.ipython.grpc.message_size", "40");
    startInterpreter(properties);
    // to make this test can run under both python2 and python3
    result = interpreter.interpret("from __future__ import print_function", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    context = getInterpreterContext();
    result = interpreter.interpret("print(11111111111111111111111111111)", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
  }

  public static void testInterpreter(final Interpreter interpreter) throws IOException, InterruptedException, InterpreterException {
    // to make this test can run under both python2 and python3
    InterpreterResult result = interpreter.interpret("from __future__ import print_function", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    InterpreterContext context = getInterpreterContext();
    result = interpreter.interpret("import sys\nprint(sys.version[0])", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    Thread.sleep(100);
    List<InterpreterResultMessage> interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, interpreterResultMessages.size());
    boolean isPython2 = interpreterResultMessages.get(0).getData().equals("2\n");

    // single output without print
    context = getInterpreterContext();
    result = interpreter.interpret("'hello world'", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, interpreterResultMessages.size());
    assertEquals("'hello world'", interpreterResultMessages.get(0).getData());

    // unicode
    context = getInterpreterContext();
    result = interpreter.interpret("print(u'你好')", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, interpreterResultMessages.size());
    assertEquals("你好\n", interpreterResultMessages.get(0).getData());

    // only the last statement is printed
    context = getInterpreterContext();
    result = interpreter.interpret("'hello world'\n'hello world2'", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, interpreterResultMessages.size());
    assertEquals("'hello world2'", interpreterResultMessages.get(0).getData());

    // single output
    context = getInterpreterContext();
    result = interpreter.interpret("print('hello world')", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, interpreterResultMessages.size());
    assertEquals("hello world\n", interpreterResultMessages.get(0).getData());

    // multiple output
    context = getInterpreterContext();
    result = interpreter.interpret("print('hello world')\nprint('hello world2')", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, interpreterResultMessages.size());
    assertEquals("hello world\nhello world2\n", interpreterResultMessages.get(0).getData());

    // assignment
    context = getInterpreterContext();
    result = interpreter.interpret("abc=1",context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(0, interpreterResultMessages.size());

    // if block
    context = getInterpreterContext();
    result = interpreter.interpret("if abc > 0:\n\tprint('True')\nelse:\n\tprint('False')", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, interpreterResultMessages.size());
    assertEquals("True\n", interpreterResultMessages.get(0).getData());

    // for loop
    context = getInterpreterContext();
    result = interpreter.interpret("for i in range(3):\n\tprint(i)", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, interpreterResultMessages.size());
    assertEquals("0\n1\n2\n", interpreterResultMessages.get(0).getData());

    // syntax error
    context = getInterpreterContext();
    result = interpreter.interpret("print(unknown)", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, interpreterResultMessages.size());
    assertTrue(interpreterResultMessages.get(0).getData().contains("name 'unknown' is not defined"));

    // raise runtime exception
    context = getInterpreterContext();
    result = interpreter.interpret("1/0", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, interpreterResultMessages.size());
    assertTrue(interpreterResultMessages.get(0).getData().contains("ZeroDivisionError"));

    // ZEPPELIN-1133
    context = getInterpreterContext();
    result = interpreter.interpret("def greet(name):\n" +
        "    print('Hello', name)\n" +
        "greet('Jack')", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, interpreterResultMessages.size());
    assertEquals("Hello Jack\n",interpreterResultMessages.get(0).getData());

    // ZEPPELIN-1114
    context = getInterpreterContext();
    result = interpreter.interpret("print('there is no Error: ok')", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, interpreterResultMessages.size());
    assertEquals("there is no Error: ok\n", interpreterResultMessages.get(0).getData());

    // completion
    context = getInterpreterContext();
    List<InterpreterCompletion> completions = interpreter.completion("ab", 2, context);
    assertEquals(2, completions.size());
    assertEquals("abc", completions.get(0).getValue());
    assertEquals("abs", completions.get(1).getValue());

    context = getInterpreterContext();
    interpreter.interpret("import sys", context);
    completions = interpreter.completion("sys.", 4, context);
    assertFalse(completions.isEmpty());

    context = getInterpreterContext();
    completions = interpreter.completion("sys.std", 7, context);
    for (InterpreterCompletion completion : completions) {
      System.out.println(completion.getValue());
    }
    assertEquals(3, completions.size());
    assertEquals("stderr", completions.get(0).getValue());
    assertEquals("stdin", completions.get(1).getValue());
    assertEquals("stdout", completions.get(2).getValue());

    // there's no completion for 'a.' because it is not recognized by compiler for now.
    context = getInterpreterContext();
    String st = "a='hello'\na.";
    completions = interpreter.completion(st, st.length(), context);
    assertEquals(0, completions.size());

    // define `a` first
    context = getInterpreterContext();
    st = "a='hello'";
    result = interpreter.interpret(st, context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(0, interpreterResultMessages.size());

    // now we can get the completion for `a.`
    context = getInterpreterContext();
    st = "a.";
    completions = interpreter.completion(st, st.length(), context);
    // it is different for python2 and python3 and may even different for different minor version
    // so only verify it is larger than 20
    assertTrue(completions.size() > 20);

    context = getInterpreterContext();
    st = "a.co";
    completions = interpreter.completion(st, st.length(), context);
    assertEquals(1, completions.size());
    assertEquals("count", completions.get(0).getValue());

    // cursor is in the middle of code
    context = getInterpreterContext();
    st = "a.co\b='hello";
    completions = interpreter.completion(st, 4, context);
    assertEquals(1, completions.size());
    assertEquals("count", completions.get(0).getValue());

    // ipython help
    context = getInterpreterContext();
    result = interpreter.interpret("range?", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
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

    // matplotlib
    context = getInterpreterContext();
    result = interpreter.interpret("%matplotlib inline\nimport matplotlib.pyplot as plt\ndata=[1,1,2,3,4]\nplt.figure()\nplt.plot(data)", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
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
    result = interpreter.interpret("from bokeh.io import output_notebook, show\n" +
        "from bokeh.plotting import figure\n" +
        "import bkzep\n" +
        "output_notebook(notebook_type='zeppelin')", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    for (InterpreterResultMessage message : interpreterResultMessages) {
      LOGGER.info("Data:" + message.getData());
    }
    assertEquals(2, interpreterResultMessages.size());
    assertEquals(InterpreterResult.Type.HTML, interpreterResultMessages.get(0).getType());
    assertTrue(interpreterResultMessages.get(0).getData().contains("Loading BokehJS"));
    assertEquals(InterpreterResult.Type.HTML, interpreterResultMessages.get(1).getType());
    assertTrue(interpreterResultMessages.get(1).getData().contains("BokehJS is being loaded"));

    // bokeh plotting
    context = getInterpreterContext();
    result = interpreter.interpret("from bokeh.plotting import figure, output_file, show\n" +
        "x = [1, 2, 3, 4, 5]\n" +
        "y = [6, 7, 2, 4, 5]\n" +
        "p = figure(title=\"simple line example\", x_axis_label='x', y_axis_label='y')\n" +
        "p.line(x, y, legend=\"Temp.\", line_width=2)\n" +
        "show(p)", context);
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
    result = interpreter.interpret("from ggplot import *\n" +
        "ggplot(diamonds, aes(x='price', fill='cut')) +\\\n" +
        "    geom_density(alpha=0.25) +\\\n" +
        "    facet_wrap(\"clarity\")", context);
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

    // ZeppelinContext

    // TextBox
    context = getInterpreterContext();
    result = interpreter.interpret("z.input(name='text_1', defaultValue='value_1')", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertTrue(interpreterResultMessages.get(0).getData().contains("'value_1'"));
    assertEquals(1, context.getGui().getForms().size());
    assertTrue(context.getGui().getForms().get("text_1") instanceof TextBox);
    TextBox textbox = (TextBox) context.getGui().getForms().get("text_1");
    assertEquals("text_1", textbox.getName());
    assertEquals("value_1", textbox.getDefaultValue());

    // Select
    context = getInterpreterContext();
    result = interpreter.interpret("z.select(name='select_1', options=[('value_1', 'name_1'), ('value_2', 'name_2')])", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(1, context.getGui().getForms().size());
    assertTrue(context.getGui().getForms().get("select_1") instanceof Select);
    Select select = (Select) context.getGui().getForms().get("select_1");
    assertEquals("select_1", select.getName());
    assertEquals(2, select.getOptions().length);
    assertEquals("name_1", select.getOptions()[0].getDisplayName());
    assertEquals("value_1", select.getOptions()[0].getValue());

    // CheckBox
    context = getInterpreterContext();
    result = interpreter.interpret("z.checkbox(name='checkbox_1', options=[('value_1', 'name_1'), ('value_2', 'name_2')])", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(1, context.getGui().getForms().size());
    assertTrue(context.getGui().getForms().get("checkbox_1") instanceof CheckBox);
    CheckBox checkbox = (CheckBox) context.getGui().getForms().get("checkbox_1");
    assertEquals("checkbox_1", checkbox.getName());
    assertEquals(2, checkbox.getOptions().length);
    assertEquals("name_1", checkbox.getOptions()[0].getDisplayName());
    assertEquals("value_1", checkbox.getOptions()[0].getValue());

    // Pandas DataFrame
    context = getInterpreterContext();
    result = interpreter.interpret("import pandas as pd\ndf = pd.DataFrame({'id':[1,2,3], 'name':['a','b','c']})\nz.show(df)", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TABLE, interpreterResultMessages.get(0).getType());
    assertEquals("id\tname\n1\ta\n2\tb\n3\tc\n", interpreterResultMessages.get(0).getData());

    // clear output
    context = getInterpreterContext();
    result = interpreter.interpret("import time\nprint(\"Hello\")\ntime.sleep(0.5)\nz.getInterpreterContext().out().clear()\nprint(\"world\")\n", context);
    assertEquals("%text world\n", context.out.getCurrentOutput().toString());
  }
  
  public void testIpythonKernelCrash_shouldNotHangExecution()
          throws InterpreterException, IOException {
    // The goal of this test is to ensure that we handle case when the kernel die.
    // In order to do so, we will kill the kernel process from the python code.
    // A real example of that could be a out of memory by the code we execute.
    String codeDep = "!pip install psutil";
    String codeFindPID = "from os import getpid\n"
            + "import psutil\n"
            + "pids = psutil.pids()\n"
            + "my_pid = getpid()\n"
            + "pidToKill = []\n"
            + "for pid in pids:\n"
            + "    try:\n"
            + "        p = psutil.Process(pid)\n"
            + "        cmd = p.cmdline()\n"
            + "        for arg in cmd:\n"
            + "            if arg.count('ipykernel'):\n"
            + "                pidToKill.append(pid)\n"
            + "    except:\n"
            + "        continue\n"
            + "len(pidToKill)";
    String codeKillKernel = "from os import kill\n"
            + "import signal\n"
            + "for pid in pidToKill:\n"
            + "    kill(pid, signal.SIGKILL)";
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = interpreter.interpret(codeDep, context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    context = getInterpreterContext();
    result = interpreter.interpret(codeFindPID, context);
    assertEquals(Code.SUCCESS, result.code());
    InterpreterResultMessage output = context.out.toInterpreterResultMessage().get(0);
    int numberOfPID = Integer.parseInt(output.getData());
    assertTrue(numberOfPID > 0);
    context = getInterpreterContext();
    result = interpreter.interpret(codeKillKernel, context);
    assertEquals(Code.ERROR, result.code());
    output = context.out.toInterpreterResultMessage().get(0);
    assertTrue(output.getData().equals("Ipython kernel has been stopped. Please check logs. "
            + "It might be because of an out of memory issue."));
  }

  private static InterpreterContext getInterpreterContext() {
    return new InterpreterContext(
        "noteId",
        "paragraphId",
        "replName",
        "paragraphTitle",
        "paragraphText",
        new AuthenticationInfo(),
        new HashMap<String, Object>(),
        new GUI(),
        new GUI(),
        null,
        null,
        null,
        new InterpreterOutput(null));
  }
}
