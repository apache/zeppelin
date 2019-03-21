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

import net.jodah.concurrentunit.Waiter;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.junit.Test;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Properties;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class PythonInterpreterTest extends BasePythonInterpreterTest {
  
  @Override
  public void setUp() throws InterpreterException {

    intpGroup = new InterpreterGroup();

    Properties properties = new Properties();
    properties.setProperty("zeppelin.python.maxResult", "3");
    properties.setProperty("zeppelin.python.useIPython", "false");
    properties.setProperty("zeppelin.python.gatewayserver_address", "127.0.0.1");

    interpreter = new LazyOpenInterpreter(new PythonInterpreter(properties));

    intpGroup.put("note", new LinkedList<Interpreter>());
    intpGroup.get("note").add(interpreter);
    interpreter.setInterpreterGroup(intpGroup);

    InterpreterContext.set(getInterpreterContext());
    interpreter.open();
  }

  @Override
  public void tearDown() throws InterpreterException {
    intpGroup.close();
  }

  @Override
  public void testCodeCompletion() throws InterpreterException, IOException, InterruptedException {
    super.testCodeCompletion();

    //TODO(zjffdu) PythonInterpreter doesn't support this kind of code completion for now.
    // completion
    //    InterpreterContext context = getInterpreterContext();
    //    List<InterpreterCompletion> completions = interpreter.completion("ab", 2, context);
    //    assertEquals(2, completions.size());
    //    assertEquals("abc", completions.get(0).getValue());
    //    assertEquals("abs", completions.get(1).getValue());
  }

  private class infinityPythonJob implements Runnable {
    @Override
    public void run() {
      String code = "import time\nwhile True:\n  time.sleep(1)";
      InterpreterResult ret = null;
      try {
        ret = interpreter.interpret(code, getInterpreterContext());
      } catch (InterpreterException e) {
        e.printStackTrace();
      }
      assertNotNull(ret);
      Pattern expectedMessage = Pattern.compile("KeyboardInterrupt");
      Matcher m = expectedMessage.matcher(ret.message().toString());
      assertTrue(m.find());
    }
  }

  @Test
  public void testCancelIntp() throws InterruptedException, InterpreterException {
    assertEquals(InterpreterResult.Code.SUCCESS,
        interpreter.interpret("a = 1\n", getInterpreterContext()).code());
    Thread t = new Thread(new infinityPythonJob());
    t.start();
    Thread.sleep(5000);
    interpreter.cancel(getInterpreterContext());
    assertTrue(t.isAlive());
    t.join(2000);
    assertFalse(t.isAlive());
  }

  @Test
  public void testPythonProcessKilled() throws InterruptedException, TimeoutException {
    final Waiter waiter = new Waiter();
    Thread thread = new Thread() {
      @Override
      public void run() {
        try {
          InterpreterResult result = interpreter.interpret("import time\ntime.sleep(1000)",
                  getInterpreterContext());
          waiter.assertEquals(InterpreterResult.Code.ERROR, result.code());
          waiter.assertEquals(
                  "Python process is abnormally exited, please check your code and log.",
                  result.message().get(0).getData());
        } catch (InterpreterException e) {
          waiter.fail("Should not throw exception\n" + ExceptionUtils.getStackTrace(e));
        }
        waiter.resume();
      }
    };
    thread.start();
    Thread.sleep(3000);
    PythonInterpreter pythonInterpreter = (PythonInterpreter)
            ((LazyOpenInterpreter) interpreter).getInnerInterpreter();
    pythonInterpreter.getPythonExecutor().getWatchdog().destroyProcess();
    waiter.await(3000);
  }
}
