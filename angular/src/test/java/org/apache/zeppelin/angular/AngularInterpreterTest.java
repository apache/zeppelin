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

package org.apache.zeppelin.angular;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.FIFOScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AngularInterpreterTest {

  private AngularInterpreter angular;
  private InterpreterContext context;

  @BeforeEach
  void setUp() {
    Properties p = new Properties();
    angular = new AngularInterpreter(p);
    angular.open();
    context = InterpreterContext.builder()
        .setParagraphId("paragraphId")
        .build();
  }

  @AfterEach
  void tearDown() {
    if (angular != null) {
      angular.close();
    }
  }

  @Test
  void testInterpret() {
    String input = "<div>{{value}}</div>\n<span ng-if=\"cond\">ok</span>";
    InterpreterResult res = angular.interpret(input, context);

    assertEquals(InterpreterResult.Code.SUCCESS, res.code());
    assertEquals(1, res.message().size());
    assertEquals(InterpreterResult.Type.ANGULAR, res.message().get(0).getType());
    assertEquals(input, res.message().get(0).getData());
  }

  @Test
  void testEmptyStringIsSuccess() {
    InterpreterResult res = angular.interpret("", context);

    assertEquals(InterpreterResult.Code.SUCCESS, res.code());
    assertEquals(InterpreterResult.Type.ANGULAR, res.message().get(0).getType());
    assertEquals("", res.message().get(0).getData());
  }

  @Test
  void testCancelNotThrow() {
    assertDoesNotThrow(() -> angular.cancel(context));
  }

  @Test
  void testCompletionIsEmpty() {
    List<InterpreterCompletion> comps = angular.completion("{{value}}", 3, context);
    assertTrue(comps.isEmpty());
  }

  @Test
  void testFormTypeIsNative() {
    assertEquals(Interpreter.FormType.NATIVE, angular.getFormType());
  }

  @Test
  void testProgressIsZero() {
    assertEquals(0, angular.getProgress(context));
  }

  @Test
  void testSchedulerIsFIFOSchedulerInstance() {
    assertTrue(angular.getScheduler() instanceof FIFOScheduler);
    assertTrue(angular.getScheduler().getName().contains(AngularInterpreter.class.getName()));
  }
}
