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
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.python.IPythonInterpreterTest;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

public class IPySparkInterpreterTest extends IPythonInterpreterTest {

  private InterpreterGroup intpGroup;
  private RemoteInterpreterEventClient mockIntpEventClient = mock(RemoteInterpreterEventClient.class);

  @Override
  protected Properties initIntpProperties() {
    Properties p = new Properties();
    p.setProperty("spark.master", "local[4]");
    p.setProperty("master", "local[4]");
    p.setProperty("spark.submit.deployMode", "client");
    p.setProperty("spark.app.name", "Zeppelin Test");
    p.setProperty("zeppelin.spark.useHiveContext", "false");
    p.setProperty("zeppelin.spark.maxResult", "3");
    p.setProperty("zeppelin.spark.importImplicit", "true");
    p.setProperty("zeppelin.spark.useNew", "true");
    p.setProperty("zeppelin.pyspark.python", "python");
    p.setProperty("zeppelin.dep.localrepo", Files.createTempDir().getAbsolutePath());
    p.setProperty("zeppelin.python.gatewayserver_address", "127.0.0.1");
    return p;
  }


  @Override
  protected void startInterpreter(Properties properties) throws InterpreterException {
    InterpreterContext context = getInterpreterContext();
    context.setIntpEventClient(mockIntpEventClient);
    InterpreterContext.set(context);

    LazyOpenInterpreter sparkInterpreter = new LazyOpenInterpreter(
        new SparkInterpreter(properties));
    intpGroup = new InterpreterGroup();
    intpGroup.put("session_1", new ArrayList<Interpreter>());
    intpGroup.get("session_1").add(sparkInterpreter);
    sparkInterpreter.setInterpreterGroup(intpGroup);

    LazyOpenInterpreter pySparkInterpreter =
        new LazyOpenInterpreter(new PySparkInterpreter(properties));
    intpGroup.get("session_1").add(pySparkInterpreter);
    pySparkInterpreter.setInterpreterGroup(intpGroup);

    interpreter = new LazyOpenInterpreter(new IPySparkInterpreter(properties));
    intpGroup.get("session_1").add(interpreter);
    interpreter.setInterpreterGroup(intpGroup);

    interpreter.open();
  }


  @Override
  public void tearDown() throws InterpreterException {
    intpGroup.close();
    interpreter = null;
    intpGroup = null;
  }

  @Test
  public void testIPySpark() throws InterruptedException, InterpreterException, IOException {
    testPySpark(interpreter, mockIntpEventClient);
  }

  public static void testPySpark(final Interpreter interpreter, RemoteInterpreterEventClient mockIntpEventClient)
      throws InterpreterException, IOException, InterruptedException {
    reset(mockIntpEventClient);
    // rdd
    InterpreterContext context = createInterpreterContext(mockIntpEventClient);
    InterpreterResult result = interpreter.interpret("sc.version", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    String sparkVersion = context.out.toInterpreterResultMessage().get(0).getData();

    context = createInterpreterContext(mockIntpEventClient);
    result = interpreter.interpret("sc.range(1,10).sum()", context);
    Thread.sleep(100);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals("45", interpreterResultMessages.get(0).getData().trim());
    // spark job url is sent
    verify(mockIntpEventClient).onParaInfosReceived(any(Map.class));

    // spark sql
    context = createInterpreterContext(mockIntpEventClient);
    if (!isSpark2(sparkVersion)) {
      result = interpreter.interpret("df = sqlContext.createDataFrame([(1,'a'),(2,'b')])\ndf.show()", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      interpreterResultMessages = context.out.toInterpreterResultMessage();
      assertEquals(
          "+---+---+\n" +
              "| _1| _2|\n" +
              "+---+---+\n" +
              "|  1|  a|\n" +
              "|  2|  b|\n" +
              "+---+---+", interpreterResultMessages.get(0).getData().trim());

      context = createInterpreterContext(mockIntpEventClient);
      result = interpreter.interpret("z.show(df)", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      interpreterResultMessages = context.out.toInterpreterResultMessage();
      assertEquals(
          "_1	_2\n" +
              "1	a\n" +
              "2	b", interpreterResultMessages.get(0).getData().trim());
    } else {
      result = interpreter.interpret("df = spark.createDataFrame([(1,'a'),(2,'b')])\ndf.show()", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      interpreterResultMessages = context.out.toInterpreterResultMessage();
      assertEquals(
          "+---+---+\n" +
              "| _1| _2|\n" +
              "+---+---+\n" +
              "|  1|  a|\n" +
              "|  2|  b|\n" +
              "+---+---+", interpreterResultMessages.get(0).getData().trim());

      context = createInterpreterContext(mockIntpEventClient);
      result = interpreter.interpret("z.show(df)", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      interpreterResultMessages = context.out.toInterpreterResultMessage();
      assertEquals(
          "_1	_2\n" +
              "1	a\n" +
              "2	b", interpreterResultMessages.get(0).getData().trim());
    }
    // cancel
    if (interpreter instanceof IPySparkInterpreter) {
      final InterpreterContext context2 = createInterpreterContext(mockIntpEventClient);

      Thread thread = new Thread() {
        @Override
        public void run() {
          InterpreterResult result = null;
          try {
            result = interpreter.interpret("import time\nsc.range(1,10).foreach(lambda x: time.sleep(1))", context2);
          } catch (InterpreterException e) {
            e.printStackTrace();
          }
          assertEquals(InterpreterResult.Code.ERROR, result.code());
          List<InterpreterResultMessage> interpreterResultMessages = null;
          try {
            interpreterResultMessages = context2.out.toInterpreterResultMessage();
            assertTrue(interpreterResultMessages.get(0).getData().contains("KeyboardInterrupt"));
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      };
      thread.start();

      // sleep 1 second to wait for the spark job starts
      Thread.sleep(1000);
      interpreter.cancel(context);
      thread.join();
    }

    // completions
    List<InterpreterCompletion> completions = interpreter.completion("sc.ran", 6, createInterpreterContext(mockIntpEventClient));
    assertEquals(1, completions.size());
    assertEquals("range", completions.get(0).getValue());

    completions = interpreter.completion("sc.", 3, createInterpreterContext(mockIntpEventClient));
    assertTrue(completions.size() > 0);
    completions.contains(new InterpreterCompletion("range", "range", ""));

    completions = interpreter.completion("1+1\nsc.", 7, createInterpreterContext(mockIntpEventClient));
    assertTrue(completions.size() > 0);
    completions.contains(new InterpreterCompletion("range", "range", ""));

    completions = interpreter.completion("s", 1, createInterpreterContext(mockIntpEventClient));
    assertTrue(completions.size() > 0);
    completions.contains(new InterpreterCompletion("sc", "sc", ""));

    // pyspark streaming
    context = createInterpreterContext(mockIntpEventClient);
    result = interpreter.interpret(
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
    interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, interpreterResultMessages.size());
    assertTrue(interpreterResultMessages.get(0).getData().contains("(0, 100)"));
  }

  private static boolean isSpark2(String sparkVersion) {
    return sparkVersion.startsWith("'2.") || sparkVersion.startsWith("u'2.");
  }

  private static InterpreterContext createInterpreterContext(RemoteInterpreterEventClient mockRemoteEventClient) {
    return InterpreterContext.builder()
        .setNoteId("noteId")
        .setParagraphId("paragraphId")
        .setIntpEventClient(mockRemoteEventClient)
        .setInterpreterOut(new InterpreterOutput(null))
        .build();
  }
}
