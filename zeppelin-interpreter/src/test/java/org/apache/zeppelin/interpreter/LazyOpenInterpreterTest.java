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

package org.apache.zeppelin.interpreter;

import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LazyOpenInterpreterTest {
  Interpreter interpreter = mock(Interpreter.class);

  @Test
  public void isOpenTest() throws InterpreterException {
    InterpreterResult interpreterResult = new InterpreterResult(InterpreterResult.Code.SUCCESS, "");
    when(interpreter.interpret(any(String.class), any(InterpreterContext.class))).thenReturn(interpreterResult);

    LazyOpenInterpreter lazyOpenInterpreter = new LazyOpenInterpreter(interpreter);

    assertFalse("Interpreter is not open", lazyOpenInterpreter.isOpen());
    InterpreterContext interpreterContext =
        new InterpreterContext("note", "id", null, "title", "text", null, null, null, null, null, null, null, null);
    lazyOpenInterpreter.interpret("intp 1", interpreterContext);
    assertTrue("Interpeter is open", lazyOpenInterpreter.isOpen());
  }
}