package org.apache.zeppelin.python;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.zeppelin.interpreter.InterpreterResult;
import org.junit.Test;

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
 * mvn -Dpython.test.exclude='' test -pl python
 * </code>
 */
public class PythonInterpreterWithPythonInstalledTest {

  @Test
  public void badSqlSyntaxFails() {
    //given
    PythonInterpreter realPython = new PythonInterpreter(
        PythonInterpreterTest.getPythonTestProperties());
    realPython.open();

    //when
    InterpreterResult ret = realPython.interpret("select wrong syntax", null);

    //then
    assertNotNull("Interpreter returned 'null'", ret);
    //System.out.println("\nInterpreter response: \n" + ret.message());
    assertEquals(InterpreterResult.Code.ERROR, ret.code());
    assertTrue(ret.message().length() > 0);
  }

}
