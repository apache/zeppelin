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

package org.apache.zeppelin.scio;

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ScioInterpreterTest {
  private static ScioInterpreter repl;
  private static InterpreterGroup intpGroup;
  private InterpreterContext context;

  private final String newline = "\n";

  private InterpreterContext getNewContext() {
    return new InterpreterContext("note", "id", "title", "text",
        new AuthenticationInfo(),
        new HashMap<String, Object>(),
        new GUI(),
        new AngularObjectRegistry(intpGroup.getId(), null),
        new LocalResourcePool("id"),
        new LinkedList<InterpreterContextRunner>(),
        new InterpreterOutput(new InterpreterOutputListener() {
          @Override
          public void onAppend(InterpreterOutput out, byte[] line) {}
          @Override
          public void onUpdate(InterpreterOutput out, byte[] output) {}
        }));
  }

  @Before
  public void setUp() throws Exception {
    if (repl == null) {
      intpGroup = new InterpreterGroup();
      intpGroup.put("note", new LinkedList<Interpreter>());
      repl = new ScioInterpreter(new Properties());
      repl.setInterpreterGroup(intpGroup);
      intpGroup.get("note").add(repl);
      repl.open();
    }

    context = getNewContext();
  }

  @Test
  public void testBasicSuccess() {
    assertEquals(InterpreterResult.Code.SUCCESS,
        repl.interpret("val a = 1" + newline + "val b = 2", context).code());
  }

  @Test
  public void testBasicSyntaxError() {
    InterpreterResult error = repl.interpret("val a:Int = 'ds'", context);
    assertEquals(InterpreterResult.Code.ERROR, error.code());
    assertTrue(error.message().length() > 0);
  }

  @Test
  public void testBasicIncomplete() {
    InterpreterResult incomplete = repl.interpret("val a = \"\"\"", context);
    assertEquals(InterpreterResult.Code.INCOMPLETE, incomplete.code());
    assertTrue(incomplete.message().length() > 0);
  }

  @Test
  public void testBasicPipeline() {
    assertEquals(InterpreterResult.Code.SUCCESS,
        repl.interpret("val (sc, _) = ContextAndArgs(argz)" + newline
            + "sc.parallelize(1 to 10).closeAndCollect().toList", context).code());
  }

  @Test
  public void testBasicMultiStepPipeline() {
    final StringBuilder code = new StringBuilder();
    code.append("val (sc, _) = ContextAndArgs(argz)").append(newline)
        .append("val numbers = sc.parallelize(1 to 10)").append(newline)
        .append("val results = numbers.closeAndCollect().toList").append(newline)
        .append("println(results)");
    assertEquals(InterpreterResult.Code.SUCCESS,
        repl.interpret(code.toString(), context).code());
  }

  @Test
  public void testException() {
    InterpreterResult exception = repl.interpret("val (sc, _) = ContextAndArgs(argz)" + newline
        + "throw new Exception(\"test\")", context);
    assertEquals(InterpreterResult.Code.ERROR, exception.code());
    assertTrue(exception.message().length() > 0);
  }

}
