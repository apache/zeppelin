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

import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterOutput;
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
    p.setProperty("shell.command.timeout.millisecs", "5000");
    p.setProperty("shell.command.timeout.check.interval", "1000");
    shell = new ShellInterpreter(p);
    context = InterpreterContext.builder()
            .setInterpreterOut(new InterpreterOutput())
            .setParagraphId("paragraphId").build();
    shell.open();
  }

  @After
  public void tearDown() throws Exception {
  }

  @Test
  public void test() throws InterpreterException {
    if (System.getProperty("os.name").startsWith("Windows")) {
      result = shell.interpret("dir", context);
    } else {
      result = shell.interpret("ls", context);
    }
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertTrue(shell.getExecutorMap().isEmpty());
    // it should be fine to cancel a statement that has been completed.
    shell.cancel(context);
    assertTrue(shell.getExecutorMap().isEmpty());
  }

  @Test
  public void testInvalidCommand() throws InterpreterException {
    if (System.getProperty("os.name").startsWith("Windows")) {
      result = shell.interpret("invalid_command\ndir", context);
    } else {
      result = shell.interpret("invalid_command\nls", context);
    }
    assertEquals(Code.SUCCESS, result.code());
    assertTrue(shell.getExecutorMap().isEmpty());
  }

  @Test
  public void testShellTimeout() throws InterpreterException {
    if (System.getProperty("os.name").startsWith("Windows")) {
      result = shell.interpret("timeout 8", context);
    } else {
      result = shell.interpret("sleep 8", context);
    }
    // exit shell process because no output is produced during the timeout threshold
    assertEquals(Code.INCOMPLETE, result.code());
    assertTrue(result.message().get(0).getData().contains("Paragraph received a SIGTERM"));
  }

  @Test
  public void testShellTimeout2() throws InterpreterException {
    context = InterpreterContext.builder()
            .setParagraphId("paragraphId")
            .setInterpreterOut(new InterpreterOutput())
            .build();
    result = shell.interpret("for i in {1..10}\ndo\n\tsleep 1\n\techo $i\ndone", context);
    // won't exit shell because the continues output is produced
    assertEquals(Code.SUCCESS, result.code());
    assertEquals("1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n", context.out.toString());
  }
}
