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

package org.apache.zeppelin.jupyter;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.resource.ResourcePool;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;


/**
 * TODO(zjffdu) These tests are copied from python module.
 * Should reorganize them to avoid code duplication.
 */
public class IPythonKernelTest {

  protected InterpreterGroup intpGroup;
  protected Interpreter interpreter;
  private ResourcePool resourcePool;

  @Before
  public void setUp() throws InterpreterException {
    Properties properties = new Properties();
    interpreter = new LazyOpenInterpreter(new JupyterInterpreter(properties));
    intpGroup = new InterpreterGroup();
    resourcePool = new LocalResourcePool("local");
    intpGroup.setResourcePool(resourcePool);
    intpGroup.put("session_1", new ArrayList<>());
    intpGroup.get("session_1").add(interpreter);
    interpreter.setInterpreterGroup(intpGroup);

    interpreter.open();
  }

  @After
  public void tearDown() throws InterpreterException {
    if (interpreter != null) {
      interpreter.close();
    }
  }

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

    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, interpreterResultMessages.size());
    assertTrue(interpreterResultMessages.get(0).getData().contains(
            "name 'unknown' is not defined"));

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

    // multiple text output
    context = getInterpreterContext();
    result = interpreter.interpret(
            "for i in range(1,4):\n" + "\tprint(i)", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, interpreterResultMessages.size());
    assertEquals("1\n2\n3\n", interpreterResultMessages.get(0).getData());
  }

  @Test
  public void testInterpolate() throws InterpreterException, IOException {
    intpGroup.getResourcePool().put("name", "hello");
    InterpreterContext context = getInterpreterContext();
    context.getLocalProperties().put("interpolate", "true");

    String st = "print('{name}')";
    InterpreterResult result = interpreter.interpret(st, context);

    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> interpreterResultMessages =
            context.out.toInterpreterResultMessage();
    assertEquals(1, interpreterResultMessages.size());
    assertEquals("hello\n", interpreterResultMessages.get(0).getData());
  }

  @Test
  public void testCodeCompletion() throws InterpreterException, InterruptedException {
    // define `a` first
    InterpreterContext context = getInterpreterContext();
    String st = "a='hello'";
    InterpreterResult result = interpreter.interpret(st, context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    // now we can get the completion for `a.`
    context = getInterpreterContext();
    st = "a.";
    List<InterpreterCompletion> completions = interpreter.completion(st, st.length(), context);
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
  public void testUpdateOutput() throws IOException, InterpreterException {
    InterpreterContext context = getInterpreterContext();
    String st = "import sys\n" +
            "import time\n" +
            "from IPython.display import display, clear_output\n" +
            "for i in range(10):\n" +
            "    time.sleep(0.25)\n" +
            "    clear_output(wait=True)\n" +
            "    print(i)\n" +
            "    sys.stdout.flush()";
    InterpreterResult result = interpreter.interpret(st, context);
    List<InterpreterResultMessage> interpreterResultMessages =
            context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals("9\n", interpreterResultMessages.get(0).getData());
  }

  protected InterpreterContext getInterpreterContext() {
    Map<String, String> localProperties = new HashMap<>();
    localProperties.put("kernel", "python");
    return InterpreterContext.builder()
            .setNoteId("noteId")
            .setParagraphId("paragraphId")
            .setInterpreterOut(new InterpreterOutput(null))
            .setIntpEventClient(mock(RemoteInterpreterEventClient.class))
            .setLocalProperties(localProperties)
            .setResourcePool(resourcePool)
            .build();
  }
}
