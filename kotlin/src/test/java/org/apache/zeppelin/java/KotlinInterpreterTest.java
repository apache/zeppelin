package org.apache.zeppelin.java;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.kotlin.KotlinInterpreter;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Properties;

import static org.apache.zeppelin.interpreter.InterpreterResult.Code.SUCCESS;
import static org.apache.zeppelin.interpreter.InterpreterResult.Code.ERROR;
import static org.junit.Assert.assertEquals;

/**
 * KotlinInterpreterTest
 */
public class KotlinInterpreterTest {

  private static KotlinInterpreter interpreter;
  private static InterpreterContext context;

  @BeforeClass
  public static void setUp() throws InterpreterException {
    context = InterpreterContext.builder().build();
    interpreter = new KotlinInterpreter(new Properties());
    interpreter.open();
  }

  @AfterClass
  public static void tearDown() {
    interpreter.close();
  }

  private static void testCodeForResult(String code, String expected) throws Exception {
    InterpreterResult result = interpreter.interpret(code, context);
    assertEquals(SUCCESS, result.code());
    assertEquals(1, result.message().size());
    assertEquals(expected, result.message().get(0).getData().trim());
  }

  @Test
  public void testLiteral() throws Exception {
    testCodeForResult("1", "1: kotlin.Int");
  }

  @Test
  public void testOperation() throws Exception {
    testCodeForResult("\"foo\" + \"bar\"", "foobar: kotlin.String");
  }

  @Test
  public void testFunction() throws Exception {
    testCodeForResult("fun square(x: Int): Int = x * x\nsquare(10)", "100: kotlin.Int");
  }

  // TODO(dkaznacheev): work out why it's not incomplete
  public void testIncomplete() throws Exception {
    InterpreterResult result = interpreter.interpret("if (10 > 2) {\n", context);
    assertEquals(ERROR, result.code());
    assertEquals("incomplete code", result.message().get(0).getData().trim());
  }

  @Test
  public void testCompileError() throws Exception {
    InterpreterResult result = interpreter.interpret("prinln(1)", context);
    assertEquals(ERROR, result.code());
    assertEquals(
        "Unresolved reference: prinln", result.message().get(0).getData().trim());
  }
}
