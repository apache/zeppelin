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

import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.Test;

import java.util.Properties;

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
        new InterpreterContext("note", "id", null, "title", "text", null, null, null, null, null, null, null);
    lazyOpenInterpreter.interpret("intp 1", interpreterContext);
    assertTrue("Interpeter is open", lazyOpenInterpreter.isOpen());
  }

  @Test
  public void testPropertyWithReplacedContextFields() throws InterpreterException {
    Properties p = new Properties();
    p.put("p1", "replName #{noteId}, #{paragraphTitle}, #{paragraphId}, #{paragraphText}, #{replName}, #{noteId}, #{user}," +
            " #{authenticationInfo}");
    String noteId = "testNoteId";
    String paragraphTitle = "testParagraphTitle";
    String paragraphText = "testParagraphText";
    String paragraphId = "testParagraphId";
    String user = "username";

    Interpreter intp = new InterpreterTest.DummyInterpreter(p);
    intp.setUserName(user);
    LazyOpenInterpreter lazyOpenInterpreter = new LazyOpenInterpreter(intp);


    InterpreterContext interpreterContext =
            new InterpreterContext(noteId,
                    paragraphId,
                    null,
                    paragraphTitle,
                    paragraphText,
                    new AuthenticationInfo(user, null, "testTicket"),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
    InterpreterContext.set(interpreterContext);
    lazyOpenInterpreter.interpret("intp 1", interpreterContext);
    assertTrue("Interpeter is open", lazyOpenInterpreter.isOpen());

    String actual = intp.getProperty("p1");
    InterpreterContext.remove();

    assertEquals(
            String.format("replName %s, #{paragraphTitle}, #{paragraphId}, #{paragraphText}, , %s, %s, #{authenticationInfo}", noteId,
                    noteId, user),
            actual
    );
  }

}