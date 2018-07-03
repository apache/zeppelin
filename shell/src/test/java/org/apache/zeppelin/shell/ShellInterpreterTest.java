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

package org.apache.zeppelin.shell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;

public class ShellInterpreterTest {

  private ShellInterpreter shell;
  private InterpreterContext context;
  private InterpreterResult result;

  @Before
  public void setUp() throws Exception {
    Properties p = new Properties();
    p.setProperty("shell.command.timeout.millisecs", "2000");
    shell = new ShellInterpreter(p);

    context = InterpreterContext.builder().setParagraphId("paragraphId").build();
    shell.open();
  }

  @After
  public void tearDown() throws Exception {
  }

  @Test
  public void test() {
    if (System.getProperty("os.name").startsWith("Windows")) {
      result = shell.interpret("dir", context);
    } else {
      result = shell.interpret("ls", context);
    }
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertTrue(shell.executors.isEmpty());
    // it should be fine to cancel a statement that has been completed.
    shell.cancel(context);
    assertTrue(shell.executors.isEmpty());
  }

  @Test
  public void testInvalidCommand(){
    if (System.getProperty("os.name").startsWith("Windows")) {
      result = shell.interpret("invalid_command\ndir", context);
    } else {
      result = shell.interpret("invalid_command\nls", context);
    }
    assertEquals(Code.SUCCESS, result.code());
    assertTrue(shell.executors.isEmpty());
  }

  @Test
  public void testShellTimeout() {
    if (System.getProperty("os.name").startsWith("Windows")) {
      result = shell.interpret("timeout 4", context);
    } else {
      result = shell.interpret("sleep 4", context);
    }

    assertEquals(Code.INCOMPLETE, result.code());
    assertTrue(result.message().get(0).getData().contains("Paragraph received a SIGTERM"));
  }
}
