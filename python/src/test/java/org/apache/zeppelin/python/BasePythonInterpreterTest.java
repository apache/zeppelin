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

import net.jodah.concurrentunit.ConcurrentTestCase;
import org.apache.zeppelin.display.ui.CheckBox;
import org.apache.zeppelin.display.ui.Password;
import org.apache.zeppelin.display.ui.Select;
import org.apache.zeppelin.display.ui.TextBox;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.serving.RestApiServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public abstract class BasePythonInterpreterTest extends ConcurrentTestCase {

  protected InterpreterGroup intpGroup;
  protected Interpreter interpreter;

  @Before
  public abstract void setUp() throws InterpreterException;

  @After
  public abstract void tearDown() throws InterpreterException;


  @Test
  public void testPythonBasics() throws InterpreterException, InterruptedException, IOException {

    InterpreterContext context = getInterpreterContext();
    InterpreterResult result =
        interpreter.interpret("import sys\nprint(sys.version[0])", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    Thread.sleep(100);
    List<InterpreterResultMessage> interpreterResultMessages =
        context.out.toInterpreterResultMessage();
    assertEquals(1, interpreterResultMessages.size());

    // single output without print
    context = getInterpreterContext();
    result = interpreter.interpret("'hello world'", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, interpreterResultMessages.size());
    assertEquals("'hello world'", interpreterResultMessages.get(0).getData().trim());

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
    assertEquals("'hello world2'", interpreterResultMessages.get(0).getData().trim());

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
    result = interpreter.interpret("abc=1", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(0, interpreterResultMessages.size());

    // if block
    context = getInterpreterContext();
    result =
        interpreter.interpret("if abc > 0:\n\tprint('True')\nelse:\n\tprint('False')", context);
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
    if (interpreter instanceof IPythonInterpreter) {
      interpreterResultMessages = context.out.toInterpreterResultMessage();
      assertEquals(1, interpreterResultMessages.size());
      assertTrue(interpreterResultMessages.get(0).getData().contains(
          "name 'unknown' is not defined"));
    } else if (interpreter instanceof PythonInterpreter) {
      assertTrue(result.message().get(0).getData().contains("name 'unknown' is not defined"));
    }

    // raise runtime exception
    context = getInterpreterContext();
    result = interpreter.interpret("1/0", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    if (interpreter instanceof IPythonInterpreter) {
      interpreterResultMessages = context.out.toInterpreterResultMessage();
      assertEquals(1, interpreterResultMessages.size());
      assertTrue(interpreterResultMessages.get(0).getData().contains("ZeroDivisionError"));
    } else if (interpreter instanceof PythonInterpreter) {
      assertTrue(result.message().get(0).getData().contains("ZeroDivisionError"));
    }

    // ZEPPELIN-1133
    context = getInterpreterContext();
    result = interpreter.interpret(
        "from __future__ import print_function\n" +
            "def greet(name):\n" +
            "    print('Hello', name)\n" +
            "greet('Jack')", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, interpreterResultMessages.size());
    assertEquals("Hello Jack\n", interpreterResultMessages.get(0).getData());

    // ZEPPELIN-1114
    context = getInterpreterContext();
    result = interpreter.interpret("print('there is no Error: ok')", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, interpreterResultMessages.size());
    assertEquals("there is no Error: ok\n", interpreterResultMessages.get(0).getData());

    // ZEPPELIN-3687
    context = getInterpreterContext();
    result = interpreter.interpret("# print('Hello')", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(0, interpreterResultMessages.size());

    context = getInterpreterContext();
    result = interpreter.interpret(
        "# print('Hello')\n# print('How are u?')\n# time.sleep(1)", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(0, interpreterResultMessages.size());
  }

  @Test
  public void testCodeCompletion() throws InterpreterException, IOException, InterruptedException {
    // there's no completion for 'a.' because it is not recognized by compiler for now.
    InterpreterContext context = getInterpreterContext();
    String st = "a='hello'\na.";
    List<InterpreterCompletion> completions = interpreter.completion(st, st.length(), context);
    assertEquals(0, completions.size());

    // define `a` first
    context = getInterpreterContext();
    st = "a='hello'";
    InterpreterResult result = interpreter.interpret(st, context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

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
  }

  @Test
  public void testZeppelinContext() throws InterpreterException, InterruptedException, IOException {
    // TextBox
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result =
        interpreter.interpret("z.input(name='text_1', defaultValue='value_1')", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> interpreterResultMessages =
        context.out.toInterpreterResultMessage();
    assertTrue(interpreterResultMessages.get(0).getData().contains("'value_1'"));
    assertEquals(1, context.getGui().getForms().size());
    assertTrue(context.getGui().getForms().get("text_1") instanceof TextBox);
    TextBox textbox = (TextBox) context.getGui().getForms().get("text_1");
    assertEquals("text_1", textbox.getName());
    assertEquals("value_1", textbox.getDefaultValue());

    // Password
    context = getInterpreterContext();
    result =
        interpreter.interpret("z.password(name='pwd_1')", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertTrue(context.getGui().getForms().get("pwd_1") instanceof Password);
    Password password = (Password) context.getGui().getForms().get("pwd_1");
    assertEquals("pwd_1", password.getName());

    // Select
    context = getInterpreterContext();
    result = interpreter.interpret("z.select(name='select_1'," +
        " options=[('value_1', 'name_1'), ('value_2', 'name_2')])", context);
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
    result = interpreter.interpret("z.checkbox(name='checkbox_1'," +
        "options=[('value_1', 'name_1'), ('value_2', 'name_2')])", context);
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
    result = interpreter.interpret("import pandas as pd\n" +
        "df = pd.DataFrame({'id':[1,2,3], 'name':['a','b','c']})\nz.show(df)", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, interpreterResultMessages.size());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResultMessages.get(0).getType());
    assertEquals("id\tname\n1\ta\n2\tb\n3\tc\n", interpreterResultMessages.get(0).getData());

    context = getInterpreterContext();
    result = interpreter.interpret("import pandas as pd\n" +
        "df = pd.DataFrame({'id':[1,2,3,4], 'name':['a','b','c', 'd']})\nz.show(df)", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(2, interpreterResultMessages.size());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResultMessages.get(0).getType());
    assertEquals("id\tname\n1\ta\n2\tb\n3\tc\n", interpreterResultMessages.get(0).getData());
    assertEquals(InterpreterResult.Type.HTML, interpreterResultMessages.get(1).getType());
    assertEquals("<font color=red>Results are limited by 3.</font>\n",
        interpreterResultMessages.get(1).getData());

    // z.show(matplotlib)
    context = getInterpreterContext();
    result = interpreter.interpret("import matplotlib.pyplot as plt\n" +
        "data=[1,1,2,3,4]\nplt.figure()\nplt.plot(data)\nz.show(plt)", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, interpreterResultMessages.size());
    assertEquals(InterpreterResult.Type.HTML, interpreterResultMessages.get(0).getType());

    // clear output
    context = getInterpreterContext();
    result = interpreter.interpret("import time\nprint(\"Hello\")\n" +
        "time.sleep(0.5)\nz.getInterpreterContext().out().clear()\nprint(\"world\")\n", context);
    assertEquals("%text world\n", context.out.getCurrentOutput().toString());
  }

  @Test
  public void testRedefinitionZeppelinContext() throws InterpreterException {
    String redefinitionCode = "z = 1\n";
    String restoreCode = "z = __zeppelin__\n";
    String validCode = "z.input(\"test\")\n";

    assertEquals(InterpreterResult.Code.SUCCESS,
        interpreter.interpret(validCode, getInterpreterContext()).code());
    assertEquals(InterpreterResult.Code.SUCCESS,
        interpreter.interpret(redefinitionCode, getInterpreterContext()).code());
    assertEquals(InterpreterResult.Code.ERROR,
        interpreter.interpret(validCode, getInterpreterContext()).code());
    assertEquals(InterpreterResult.Code.SUCCESS,
        interpreter.interpret(restoreCode, getInterpreterContext()).code());
    assertEquals(InterpreterResult.Code.SUCCESS,
        interpreter.interpret(validCode, getInterpreterContext()).code());
  }

  protected InterpreterContext getInterpreterContext() {
    return InterpreterContext.builder()
        .setNoteId("noteId")
        .setParagraphId("paragraphId")
        .setInterpreterOut(new InterpreterOutput(null))
        .setIntpEventClient(mock(RemoteInterpreterEventClient.class))
        .setRestApiServer(RestApiServer.singleton())
        .build();
  }
}
