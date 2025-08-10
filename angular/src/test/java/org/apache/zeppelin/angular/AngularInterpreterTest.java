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

import static org.junit.jupiter.api.Assertions.*;

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