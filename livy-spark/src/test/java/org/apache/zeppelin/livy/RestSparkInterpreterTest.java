package org.apache.zeppelin.livy;

import static org.junit.Assert.*;

import java.util.Properties;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.livy.RestSparkInterpreter;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class RestSparkInterpreterTest {

  private static RestSparkInterpreter yspark;
  private static InterpreterContext context;

  @BeforeClass
  public static void setUp() {
    Properties p = new Properties();
    p.setProperty("livy.server.host", "locahost:8998");
    yspark = new RestSparkInterpreter(p);
    yspark.open();
    context = new InterpreterContext(null, null, null, null, null, null, null, null, null, null,
        null);

  }

  @AfterClass
  public static void tearDown() {
    yspark.close();
  }

  @Test
  public void testServerShutdown() {
    Properties p = new Properties();
    RestSparkInterpreter yspark1 = new RestSparkInterpreter(p);
    yspark1.open();
    InterpreterResult result = yspark1.interpret("val a=1", context);
    assertEquals("you need to have the livy server running in the master node of the cluster and set the property:  livy.server.host to <master-node-hostname-or-ip>:8998",
            result.message());
  }

  @Test
  public void testSyntaxError() {
    InterpreterResult result = yspark.interpret("sc.paralize(1 to 10)", context);
    assertEquals(Code.ERROR, result.code());
  }

  @Test
  public void testNormalCommand() {
    InterpreterResult result = yspark.interpret("print(\"1\")", context);
    assertEquals("1", result.message());
  }

  @Test
  public void testWithNumberExecutorCores() {
    yspark.interpret("sc.parallelize(1 to 1000000)", context);
    InterpreterResult result = yspark.interpret("sc.parallelize(1 to 1000000).partitions.size",
        context);
    boolean message = result.message().startsWith("Int = 3", 6);
    assertTrue(message);
  }

  @Test
  public void testOverResources() {
    Properties p = new Properties();
    p.setProperty("livy.server.host", "localhost:8998");
    p.setProperty("spark.executor.memory", "20G");
    RestSparkInterpreter yspark1 = new RestSparkInterpreter(p);
    yspark1.open();
    InterpreterResult result = yspark1.interpret("sc.parallelize(1 to 1000000).partitions.size",
        context);
    yspark1.close();
    assertEquals(Code.ERROR, result.code());
    assertEquals("Resources aren't enough or error happened while creating session,"
        + " please try again.", result.message());
  }

}
