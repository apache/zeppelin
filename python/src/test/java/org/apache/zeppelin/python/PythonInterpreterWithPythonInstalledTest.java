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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.junit.Test;

import java.util.Arrays;

/**
 * Python interpreter unit test that user real Python
 *
 * Important: ALL tests here REQUIRE Python to be installed
 * They are excluded from default build, to run them manually do:
 *
 * <code>
 * mvn "-Dtest=org.apache.zeppelin.python.PythonInterpreterWithPythonInstalledTest" test -pl python
 * </code>
 *
 * or
 * <code>
 * mvn -Dpython.test.exclude='' test -pl python -am
 * </code>
 */
public class PythonInterpreterWithPythonInstalledTest {

  @Test
  public void badPythonSyntaxFails() {
    //given
    PythonInterpreter realPython = new PythonInterpreter(
        PythonInterpreterTest.getPythonTestProperties());
    // create interpreter group
    InterpreterGroup group = new InterpreterGroup();
    group.put("note", Arrays.asList((Interpreter) realPython));
    realPython.setInterpreterGroup(group);

    realPython.open();

    //when
    InterpreterResult ret = realPython.interpret("select wrong syntax", null);

    //then
    assertNotNull("Interpreter returned 'null'", ret);
    //System.out.println("\nInterpreter response: \n" + ret.message());
    assertEquals(InterpreterResult.Code.ERROR, ret.code());
    assertTrue(ret.message().get(0).getData().length() > 0);

    realPython.close();
  }

  @Test
  public void goodPythonSyntaxRuns() {
    //given
    PythonInterpreter realPython = new PythonInterpreter(
        PythonInterpreterTest.getPythonTestProperties());
    InterpreterGroup group = new InterpreterGroup();
    group.put("note", Arrays.asList((Interpreter) realPython));
    realPython.setInterpreterGroup(group);
    realPython.open();

    //when
    InterpreterResult ret = realPython.interpret("help()", null);

    //then
    assertNotNull("Interpreter returned 'null'", ret);
    //System.out.println("\nInterpreter response: \n" + ret.message());
    assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
    assertTrue(ret.message().get(0).getData().length() > 0);

    realPython.close();
  }

  @Test
  public void testZeppelin1555() {
    //given
    PythonInterpreter realPython = new PythonInterpreter(
            PythonInterpreterTest.getPythonTestProperties());
    InterpreterGroup group = new InterpreterGroup();
    group.put("note", Arrays.asList((Interpreter) realPython));
    realPython.setInterpreterGroup(group);
    realPython.open();

    //when
    InterpreterResult ret1 = realPython.interpret("print(\"...\")", null);

    //then
    //System.out.println("\nInterpreter response: \n" + ret.message());
    assertEquals(InterpreterResult.Code.SUCCESS, ret1.code());
    assertEquals("...\n", ret1.message().get(0).getData());


    InterpreterResult ret2 = realPython.interpret("for i in range(5):", null);
    //then
    //System.out.println("\nInterpreterResultterpreter response: \n" + ret2.message());
    assertEquals(InterpreterResult.Code.ERROR, ret2.code());
    assertEquals("   File \"<stdin>\", line 2\n" +
            "    \n" +
            "    ^\n" +
            "IndentationError: expected an indented block\n", ret2.message().get(0).getData());

    realPython.close();
  }

}
