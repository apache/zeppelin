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

package org.apache.zeppelin.spark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Type;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PySparkInterpreterMatplotlibTest {

  @ClassRule public static TemporaryFolder tmpDir = new TemporaryFolder();

  static SparkInterpreter sparkInterpreter;
  static PySparkInterpreter pyspark;
  static InterpreterGroup intpGroup;
  static Logger LOGGER = LoggerFactory.getLogger(PySparkInterpreterTest.class);
  static InterpreterContext context;

  public static class AltPySparkInterpreter extends PySparkInterpreter {
    /**
     * Since pyspark output is sent to an outputstream rather than being directly provided by
     * interpret(), this subclass is created to override interpret() to append the result from the
     * outputStream for the sake of convenience in testing.
     */
    public AltPySparkInterpreter(Properties property) {
      super(property);
    }

    /**
     * This code is mainly copied from RemoteInterpreterServer.java which normally handles this in
     * real use cases.
     */
    @Override
    public InterpreterResult interpret(String st, InterpreterContext context)
        throws InterpreterException {
      context.out.clear();
      InterpreterResult result = super.interpret(st, context);
      List<InterpreterResultMessage> resultMessages = null;
      try {
        context.out.flush();
        resultMessages = context.out.toInterpreterResultMessage();
      } catch (IOException e) {
        e.printStackTrace();
      }
      resultMessages.addAll(result.message());

      return new InterpreterResult(result.code(), resultMessages);
    }
  }

  private static Properties getPySparkTestProperties() throws IOException {
    Properties p = new Properties();
    p.setProperty("spark.master", "local[*]");
    p.setProperty("spark.app.name", "Zeppelin Test");
    p.setProperty("zeppelin.spark.useHiveContext", "true");
    p.setProperty("zeppelin.spark.maxResult", "1000");
    p.setProperty("zeppelin.spark.importImplicit", "true");
    p.setProperty("zeppelin.pyspark.python", "python");
    p.setProperty("zeppelin.dep.localrepo", tmpDir.newFolder().getAbsolutePath());
    p.setProperty("zeppelin.pyspark.useIPython", "false");
    p.setProperty("zeppelin.python.gatewayserver_address", "127.0.0.1");
    return p;
  }

  /**
   * Get spark version number as a numerical value. eg. 1.1.x => 11, 1.2.x => 12, 1.3.x => 13 ...
   */
  public static int getSparkVersionNumber() {
    if (sparkInterpreter == null) {
      return 0;
    }

    String[] split = sparkInterpreter.getSparkContext().version().split("\\.");
    int version = Integer.parseInt(split[0]) * 10 + Integer.parseInt(split[1]);
    return version;
  }

  @BeforeClass
  public static void setUp() throws Exception {
    intpGroup = new InterpreterGroup();
    intpGroup.put("note", new LinkedList<Interpreter>());
    context =
        InterpreterContext.builder()
            .setNoteId("note")
            .setInterpreterOut(new InterpreterOutput(null))
            .setIntpEventClient(mock(RemoteInterpreterEventClient.class))
            .setAngularObjectRegistry(new AngularObjectRegistry(intpGroup.getId(), null))
            .build();
    InterpreterContext.set(context);

    sparkInterpreter = new SparkInterpreter(getPySparkTestProperties());
    intpGroup.get("note").add(sparkInterpreter);
    sparkInterpreter.setInterpreterGroup(intpGroup);
    sparkInterpreter.open();

    pyspark = new AltPySparkInterpreter(getPySparkTestProperties());
    intpGroup.get("note").add(pyspark);
    pyspark.setInterpreterGroup(intpGroup);
    pyspark.open();
  }

  @AfterClass
  public static void tearDown() throws InterpreterException {
    pyspark.close();
    sparkInterpreter.close();
  }

  @Test
  public void dependenciesAreInstalled() throws InterpreterException {
    // matplotlib
    InterpreterResult ret = pyspark.interpret("import matplotlib", context);
    assertEquals(ret.message().toString(), InterpreterResult.Code.SUCCESS, ret.code());

    // inline backend
    ret = pyspark.interpret("import backend_zinline", context);
    assertEquals(ret.message().toString(), InterpreterResult.Code.SUCCESS, ret.code());
  }

  @Test
  public void showPlot() throws InterpreterException {
    // Simple plot test
    InterpreterResult ret;
    ret = pyspark.interpret("import matplotlib.pyplot as plt", context);
    ret = pyspark.interpret("plt.close()", context);
    ret = pyspark.interpret("z.configure_mpl(interactive=False)", context);
    ret = pyspark.interpret("plt.plot([1, 2, 3])", context);
    ret = pyspark.interpret("plt.show()", context);

    assertEquals(ret.message().toString(), InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals(ret.message().toString(), Type.HTML, ret.message().get(0).getType());
    assertTrue(ret.message().get(0).getData().contains("data:image/png;base64"));
    assertTrue(ret.message().get(0).getData().contains("<div>"));
  }

  @Test
  // Test for when configuration is set to auto-close figures after show().
  public void testClose() throws InterpreterException {
    InterpreterResult ret;
    InterpreterResult ret1;
    InterpreterResult ret2;
    ret = pyspark.interpret("import matplotlib.pyplot as plt", context);
    ret = pyspark.interpret("plt.close()", context);
    ret =
        pyspark.interpret("z.configure_mpl(interactive=False, close=True, angular=False)", context);
    ret = pyspark.interpret("plt.plot([1, 2, 3])", context);
    ret1 = pyspark.interpret("plt.show()", context);

    // Second call to show() should print nothing, and Type should be TEXT.
    // This is because when close=True, there should be no living instances
    // of FigureManager, causing show() to return before setting the output
    // type to HTML.
    ret = pyspark.interpret("plt.show()", context);
    assertEquals(0, ret.message().size());

    // Now test that new plot is drawn. It should be identical to the
    // previous one.
    ret = pyspark.interpret("plt.plot([1, 2, 3])", context);
    ret2 = pyspark.interpret("plt.show()", context);
    assertEquals(ret1.message().get(0).getType(), ret2.message().get(0).getType());
    assertEquals(ret1.message().get(0).getData(), ret2.message().get(0).getData());
  }

  @Test
  // Test for when configuration is set to not auto-close figures after show().
  public void testNoClose() throws InterpreterException {
    InterpreterResult ret;
    InterpreterResult ret1;
    InterpreterResult ret2;
    ret = pyspark.interpret("import matplotlib.pyplot as plt", context);
    ret = pyspark.interpret("plt.close()", context);
    ret =
        pyspark.interpret(
            "z.configure_mpl(interactive=False, close=False, angular=False)", context);
    ret = pyspark.interpret("plt.plot([1, 2, 3])", context);
    ret1 = pyspark.interpret("plt.show()", context);

    // Second call to show() should print nothing, and Type should be HTML.
    // This is because when close=False, there should be living instances
    // of FigureManager, causing show() to set the output
    // type to HTML even though the figure is inactive.
    ret = pyspark.interpret("plt.show()", context);
    assertEquals(ret.message().toString(), InterpreterResult.Code.SUCCESS, ret.code());

    // Now test that plot can be reshown if it is updated. It should be
    // different from the previous one because it will plot the same line
    // again but in a different color.
    ret = pyspark.interpret("plt.plot([1, 2, 3])", context);
    ret2 = pyspark.interpret("plt.show()", context);
    assertNotSame(ret1.message().get(0).getData(), ret2.message().get(0).getData());
  }

  @Test
  // Test angular mode
  public void testAngular() throws InterpreterException {
    InterpreterResult ret;
    ret = pyspark.interpret("import matplotlib.pyplot as plt", context);
    ret = pyspark.interpret("plt.close()", context);
    ret =
        pyspark.interpret("z.configure_mpl(interactive=False, close=False, angular=True)", context);
    ret = pyspark.interpret("plt.plot([1, 2, 3])", context);
    ret = pyspark.interpret("plt.show()", context);
    assertEquals(ret.message().toString(), InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals(ret.message().toString(), Type.ANGULAR, ret.message().get(0).getType());

    // Check if the figure data is in the Angular Object Registry
    AngularObjectRegistry registry = context.getAngularObjectRegistry();
    String figureData = registry.getAll("note", null).get(0).toString();
    assertTrue(figureData.contains("data:image/png;base64"));
  }
}
