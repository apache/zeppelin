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


import com.google.common.io.Files;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterOutputListener;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.InterpreterResultMessageOutput;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.python.IPythonInterpreterTest;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IPySparkInterpreterTest {

  private IPySparkInterpreter iPySparkInterpreter;
  private InterpreterGroup intpGroup;

  @Before
  public void setup() throws InterpreterException {
    Properties p = new Properties();
    p.setProperty("spark.master", "local[4]");
    p.setProperty("master", "local[4]");
    p.setProperty("spark.submit.deployMode", "client");
    p.setProperty("spark.app.name", "Zeppelin Test");
    p.setProperty("zeppelin.spark.useHiveContext", "true");
    p.setProperty("zeppelin.spark.maxResult", "1000");
    p.setProperty("zeppelin.spark.importImplicit", "true");
    p.setProperty("zeppelin.pyspark.python", "python");
    p.setProperty("zeppelin.dep.localrepo", Files.createTempDir().getAbsolutePath());

    intpGroup = new InterpreterGroup();
    intpGroup.put("session_1", new LinkedList<Interpreter>());

    SparkInterpreter sparkInterpreter = new SparkInterpreter(p);
    intpGroup.get("session_1").add(sparkInterpreter);
    sparkInterpreter.setInterpreterGroup(intpGroup);
    sparkInterpreter.open();

    iPySparkInterpreter = new IPySparkInterpreter(p);
    intpGroup.get("session_1").add(iPySparkInterpreter);
    iPySparkInterpreter.setInterpreterGroup(intpGroup);
    iPySparkInterpreter.open();
  }


  @After
  public void tearDown() {
    if (iPySparkInterpreter != null) {
      iPySparkInterpreter.close();
    }
  }

  @Test
  public void testBasics() throws InterruptedException, IOException, InterpreterException {
    // all the ipython test should pass too.
    IPythonInterpreterTest.testInterpreter(iPySparkInterpreter);

    // rdd
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = iPySparkInterpreter.interpret("sc.range(1,10).sum()", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> interpreterResultMessages = context.out.getInterpreterResultMessages();
    assertEquals("45", interpreterResultMessages.get(0).getData());

    context = getInterpreterContext();
    result = iPySparkInterpreter.interpret("sc.version", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.getInterpreterResultMessages();
    // spark sql
    context = getInterpreterContext();
    if (interpreterResultMessages.get(0).getData().startsWith("'1.") ||
        interpreterResultMessages.get(0).getData().startsWith("u'1.")) {
      result = iPySparkInterpreter.interpret("df = sqlContext.createDataFrame([(1,'a'),(2,'b')])\ndf.show()", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      interpreterResultMessages = context.out.getInterpreterResultMessages();
      assertEquals(
          "+---+---+\n" +
          "| _1| _2|\n" +
          "+---+---+\n" +
          "|  1|  a|\n" +
          "|  2|  b|\n" +
          "+---+---+\n\n", interpreterResultMessages.get(0).getData());
    } else {
      result = iPySparkInterpreter.interpret("df = spark.createDataFrame([(1,'a'),(2,'b')])\ndf.show()", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      interpreterResultMessages = context.out.getInterpreterResultMessages();
      assertEquals(
          "+---+---+\n" +
          "| _1| _2|\n" +
          "+---+---+\n" +
          "|  1|  a|\n" +
          "|  2|  b|\n" +
          "+---+---+\n\n", interpreterResultMessages.get(0).getData());
    }

    // cancel
    final InterpreterContext context2 = getInterpreterContext();

    Thread thread = new Thread(){
      @Override
      public void run() {
        InterpreterResult result = iPySparkInterpreter.interpret("import time\nsc.range(1,10).foreach(lambda x: time.sleep(1))", context2);
        assertEquals(InterpreterResult.Code.ERROR, result.code());
        List<InterpreterResultMessage> interpreterResultMessages = null;
        try {
          interpreterResultMessages = context2.out.getInterpreterResultMessages();
          assertTrue(interpreterResultMessages.get(0).getData().contains("KeyboardInterrupt"));
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    };
    thread.start();

    // sleep 1 second to wait for the spark job starts
    Thread.sleep(1000);
    iPySparkInterpreter.cancel(context);
    thread.join();

    // completions
    List<InterpreterCompletion> completions = iPySparkInterpreter.completion("sc.ran", 6, getInterpreterContext());
    assertEquals(1, completions.size());
    assertEquals("sc.range", completions.get(0).getValue());

    // pyspark streaming
    context = getInterpreterContext();
    result = iPySparkInterpreter.interpret(
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
    Thread.sleep(1000);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    interpreterResultMessages = context.out.getInterpreterResultMessages();
    assertEquals(1, interpreterResultMessages.size());
    assertTrue(interpreterResultMessages.get(0).getData().contains("(0, 100)"));
  }

  private InterpreterContext getInterpreterContext() {
    return new InterpreterContext(
        "noteId",
        "paragraphId",
        "replName",
        "paragraphTitle",
        "paragraphText",
        new AuthenticationInfo(),
        new HashMap<String, Object>(),
        new GUI(),
        null,
        null,
        null,
        new InterpreterOutput(null));
  }
}
