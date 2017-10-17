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

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PySparkInterpreterTest {

  @ClassRule
  public static TemporaryFolder tmpDir = new TemporaryFolder();

  static SparkInterpreter sparkInterpreter;
  static PySparkInterpreter pySparkInterpreter;
  static InterpreterGroup intpGroup;
  static Logger LOGGER = LoggerFactory.getLogger(PySparkInterpreterTest.class);
  static InterpreterContext context;

  private static Properties getPySparkTestProperties() throws IOException {
    Properties p = new Properties();
    p.setProperty("master", "local[*]");
    p.setProperty("spark.app.name", "Zeppelin Test");
    p.setProperty("zeppelin.spark.useHiveContext", "true");
    p.setProperty("zeppelin.spark.maxResult", "1000");
    p.setProperty("zeppelin.spark.importImplicit", "true");
    p.setProperty("zeppelin.pyspark.python", "python");
    p.setProperty("zeppelin.dep.localrepo", tmpDir.newFolder().getAbsolutePath());
    p.setProperty("zeppelin.pyspark.useIPython", "false");
    return p;
  }

  /**
   * Get spark version number as a numerical value.
   * eg. 1.1.x => 11, 1.2.x => 12, 1.3.x => 13 ...
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

    context = new InterpreterContext("note", "id", null, "title", "text",
        new AuthenticationInfo(),
        new HashMap<String, Object>(),
        new GUI(),
        new AngularObjectRegistry(intpGroup.getId(), null),
        new LocalResourcePool("id"),
        new LinkedList<InterpreterContextRunner>(),
        new InterpreterOutput(null));
    InterpreterContext.set(context);

    sparkInterpreter = new SparkInterpreter(getPySparkTestProperties());
    intpGroup.get("note").add(sparkInterpreter);
    sparkInterpreter.setInterpreterGroup(intpGroup);
    sparkInterpreter.open();

    pySparkInterpreter = new PySparkInterpreter(getPySparkTestProperties());
    intpGroup.get("note").add(pySparkInterpreter);
    pySparkInterpreter.setInterpreterGroup(intpGroup);
    pySparkInterpreter.open();


  }

  @AfterClass
  public static void tearDown() {
    pySparkInterpreter.close();
    sparkInterpreter.close();
  }

  @Test
  public void testBasicIntp() throws InterpreterException {
    if (getSparkVersionNumber() > 11) {
      assertEquals(InterpreterResult.Code.SUCCESS,
        pySparkInterpreter.interpret("a = 1\n", context).code());
    }

    InterpreterResult result = pySparkInterpreter.interpret(
        "from pyspark.streaming import StreamingContext\n" +
            "import time\n" +
            "ssc = StreamingContext(sc, 1)\n" +
            "rddQueue = []\n" +
            "for i in range(5):\n" +
            "    rddQueue += [ssc.sparkContext.parallelize([j for j in range(1, 1001)], 10)]\n" +
            "inputStream = ssc.queueStream(rddQueue)\n" +
            "mappedStream = inputStream.map(lambda x: (x % 10, 1))\n" +
            "reducedStream = mappedStream.reduceByKey(lambda a, b: a + b)\n" +
            "reducedStream.pprint()\n" +
            "ssc.start()\n" +
            "time.sleep(6)\n" +
            "ssc.stop(stopSparkContext=False, stopGraceFully=True)", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
  }

  @Test
  public void testCompletion() throws InterpreterException {
    if (getSparkVersionNumber() > 11) {
      List<InterpreterCompletion> completions = pySparkInterpreter.completion("sc.", "sc.".length(), null);
      assertTrue(completions.size() > 0);
    }
  }

  @Test
  public void testRedefinitionZeppelinContext() throws InterpreterException {
    if (getSparkVersionNumber() > 11) {
      String redefinitionCode = "z = 1\n";
      String restoreCode = "z = __zeppelin__\n";
      String validCode = "z.input(\"test\")\n";

      assertEquals(InterpreterResult.Code.SUCCESS, pySparkInterpreter.interpret(validCode, context).code());
      assertEquals(InterpreterResult.Code.SUCCESS, pySparkInterpreter.interpret(redefinitionCode, context).code());
      assertEquals(InterpreterResult.Code.ERROR, pySparkInterpreter.interpret(validCode, context).code());
      assertEquals(InterpreterResult.Code.SUCCESS, pySparkInterpreter.interpret(restoreCode, context).code());
      assertEquals(InterpreterResult.Code.SUCCESS, pySparkInterpreter.interpret(validCode, context).code());
    }
  }

  private class infinityPythonJob implements Runnable {
    @Override
    public void run() {
      String code = "import time\nwhile True:\n  time.sleep(1)" ;
      InterpreterResult ret = null;
      try {
        ret = pySparkInterpreter.interpret(code, context);
      } catch (InterpreterException e) {
        e.printStackTrace();
      }
      assertNotNull(ret);
      Pattern expectedMessage = Pattern.compile("KeyboardInterrupt");
      Matcher m = expectedMessage.matcher(ret.message().toString());
      assertTrue(m.find());
    }
  }

  @Test
  public void testCancelIntp() throws InterruptedException, InterpreterException {
    if (getSparkVersionNumber() > 11) {
      assertEquals(InterpreterResult.Code.SUCCESS,
        pySparkInterpreter.interpret("a = 1\n", context).code());

      Thread t = new Thread(new infinityPythonJob());
      t.start();
      Thread.sleep(5000);
      pySparkInterpreter.cancel(context);
      assertTrue(t.isAlive());
      t.join(2000);
      assertFalse(t.isAlive());
    }
  }
}
