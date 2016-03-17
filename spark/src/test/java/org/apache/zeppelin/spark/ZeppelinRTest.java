package org.apache.zeppelin.spark;

import org.apache.spark.SparkRBackend;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterOutputListener;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.junit.*;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * SparkR
 */

public class ZeppelinRTest implements InterpreterOutputListener {
  private static ZeppelinR zr;

  @BeforeClass
  public static void setUp() throws IOException {
    int port = SparkRBackend.init();
    SparkRBackend.start();
    zr = new ZeppelinR("/Library/Frameworks/R.framework/Resources/bin/R",
        new File("../spark-1.6.0-bin-hadoop2.6/R/lib").getAbsolutePath(),
        port);
    zr.open();

  }

  @AfterClass
  public static void tearDown() {
    zr.close();
    SparkRBackend.close();
  }


  @Test
  public void testEval() throws IOException, InterruptedException {
    zr.eval("a = 1+1");
    assertEquals(2.0, zr.get("a"));
  }

  @Test
  public void testEvalError() {
    try {
      zr.eval("nonExistObject");
      assertTrue(false);
    } catch (RuntimeException e) {
      assertTrue(true);
    }
    zr.eval("a = \"Hello\"");
  }

  @Test
  public void testSetGet() {
    zr.set("a", 1);
    assertEquals(1, zr.get("a"));
  }

  @Override
  public void onAppend(InterpreterOutput out, byte[] line) {
  }

  @Override
  public void onUpdate(InterpreterOutput out, byte[] output) {

  }
}
