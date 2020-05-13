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
package org.apache.zeppelin.flink;


import junit.framework.TestCase;
import net.jodah.concurrentunit.Waiter;
import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.ui.CheckBox;
import org.apache.zeppelin.display.ui.Select;
import org.apache.zeppelin.display.ui.TextBox;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;


public class FlinkInterpreterTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(FlinkInterpreterTest.class);

  private FlinkInterpreter interpreter;
  private AngularObjectRegistry angularObjectRegistry;

  @Before
  public void setUp() throws InterpreterException {
    Properties p = new Properties();
    p.setProperty("zeppelin.flink.printREPLOutput", "true");
    p.setProperty("zeppelin.flink.scala.color", "false");
    p.setProperty("flink.execution.mode", "local");
    p.setProperty("local.number-taskmanager", "4");

    interpreter = new FlinkInterpreter(p);
    InterpreterGroup intpGroup = new InterpreterGroup();
    interpreter.setInterpreterGroup(intpGroup);
    interpreter.open();

    angularObjectRegistry = new AngularObjectRegistry("flink", null);
  }

  @After
  public void tearDown() throws InterpreterException {
    if (interpreter != null) {
      interpreter.close();
    }
  }

  @Test
  public void testScalaBasic() throws InterpreterException, IOException {
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = interpreter.interpret("val a=\"hello world\"", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TEXT, resultMessages.get(0).getType());
    assertEquals("a: String = hello world\n", resultMessages.get(0).getData());

    context = getInterpreterContext();
    result = interpreter.interpret("print(a)", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TEXT, resultMessages.get(0).getType());
    assertEquals("hello world", resultMessages.get(0).getData());

    // java stdout
    context = getInterpreterContext();
    result = interpreter.interpret("System.out.print(a)", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TEXT, resultMessages.get(0).getType());
    assertEquals("hello world", resultMessages.get(0).getData());

    // java stderr
    context = getInterpreterContext();
    result = interpreter.interpret("System.err.print(a)", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TEXT, resultMessages.get(0).getType());
    assertEquals("hello world", resultMessages.get(0).getData());

    // incomplete
    result = interpreter.interpret("println(a", getInterpreterContext());
    assertEquals(InterpreterResult.Code.INCOMPLETE, result.code());

    // syntax error
    context = getInterpreterContext();
    result = interpreter.interpret("println(b)", context);
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TEXT, resultMessages.get(0).getType());
    assertTrue(resultMessages.get(0).getData(),
            resultMessages.get(0).getData().contains("not found: value b"));

    // multiple line
    context = getInterpreterContext();
    result = interpreter.interpret("\"123\".\ntoInt", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    // single line comment
    context = getInterpreterContext();
    result = interpreter.interpret("/*comment here*/", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    context = getInterpreterContext();
    result = interpreter.interpret("/*comment here*/\nprint(\"hello world\")",
        context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    // multiple line comment
    context = getInterpreterContext();
    result = interpreter.interpret("/*line 1 \n line 2*/", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    // test function
    context = getInterpreterContext();
    result = interpreter.interpret("def add(x:Int, y:Int)\n{ return x+y }", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    result = interpreter.interpret("print(add(1,2))", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    result = interpreter.interpret("/*line 1 \n line 2*/print(\"hello world\")",
        getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    // companion object
    result = interpreter.interpret("class Counter {\n " +
        "var value: Long = 0} \n" +
        "object Counter {\n def apply(x: Long) = new Counter()\n}", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    // case class
    //    context = getInterpreterContext();result = interpreter.interpret(
    //            "case class WC(word: String, count: Int)\n" +
    //            "val wordCounts = benv.fromElements(\n" +
    //            "WC(\"hello\", 1),\n" +
    //            "WC(\"world\", 2),\n" +
    //            "WC(\"world\", 8))\n" +
    //            "wordCounts.collect()",
    //        context);
    //    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    context = getInterpreterContext();
    result = interpreter.interpret("z.input(\"name\", \"default_name\")",
        context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(1, context.getGui().getForms().size());
    assertTrue(context.getGui().getForms().get("name") instanceof TextBox);
    TextBox textBox = (TextBox) context.getGui().getForms().get("name");
    assertEquals("name", textBox.getName());
    assertEquals("default_name", textBox.getDefaultValue());

    context = getInterpreterContext();
    result = interpreter.interpret("z.checkbox(\"checkbox_1\", " +
        "Seq(\"value_2\"), Seq((\"value_1\", \"name_1\"), (\"value_2\", \"name_2\")))", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(1, context.getGui().getForms().size());
    assertTrue(context.getGui().getForms().get("checkbox_1") instanceof CheckBox);
    CheckBox checkBox = (CheckBox) context.getGui().getForms().get("checkbox_1");
    assertEquals("checkbox_1", checkBox.getName());
    assertEquals(1, checkBox.getDefaultValue().length);
    assertEquals("value_2", checkBox.getDefaultValue()[0]);
    assertEquals(2, checkBox.getOptions().length);
    assertEquals("value_1", checkBox.getOptions()[0].getValue());
    assertEquals("name_1", checkBox.getOptions()[0].getDisplayName());
    assertEquals("value_2", checkBox.getOptions()[1].getValue());
    assertEquals("name_2", checkBox.getOptions()[1].getDisplayName());

    context = getInterpreterContext();
    result = interpreter.interpret("z.select(\"select_1\", Seq(\"value_2\"), " +
        "Seq((\"value_1\", \"name_1\"), (\"value_2\", \"name_2\")))", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(1, context.getGui().getForms().size());
    assertTrue(context.getGui().getForms().get("select_1") instanceof Select);
    Select select = (Select) context.getGui().getForms().get("select_1");
    assertEquals("select_1", select.getName());
    // TODO(zjffdu) it seems a bug of GUI, the default value should be 'value_2',
    // but it is List(value_2)
    // assertEquals("value_2", select.getDefaultValue());
    assertEquals(2, select.getOptions().length);
    assertEquals("value_1", select.getOptions()[0].getValue());
    assertEquals("name_1", select.getOptions()[0].getDisplayName());
    assertEquals("value_2", select.getOptions()[1].getValue());
    assertEquals("name_2", select.getOptions()[1].getDisplayName());
  }

  @Test
  public void testZShow() throws InterpreterException, IOException {
    // show dataset
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = interpreter.interpret(
            "val data = benv.fromElements((1, \"jeff\"), (2, \"andy\"), (3, \"james\"))",
            context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    context = getInterpreterContext();
    result = interpreter.interpret("z.show(data)", context);
    assertEquals(context.out.toString(), InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertEquals("_1\t_2\n1\tjeff\n2\tandy\n3\tjames\n", resultMessages.get(0).getData());
  }

  @Test
  public void testCompletion() throws InterpreterException {
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = interpreter.interpret("val a=\"hello world\"", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    List<InterpreterCompletion> completions = interpreter.completion("a.", 2,
        getInterpreterContext());
    assertTrue(completions.size() > 0);

    completions = interpreter.completion("benv.", 5, getInterpreterContext());
    assertTrue(completions.size() > 0);
  }

  @Test
  public void testBatchWordCount() throws InterpreterException, IOException {
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = interpreter.interpret(
            "val data = benv.fromElements(\"hello world\", \"hello flink\", \"hello hadoop\")",
        context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    context = getInterpreterContext();
    result = interpreter.interpret(
            "data.flatMap(line => line.split(\"\\\\s\"))\n" +
            "  .map(w => (w, 1))\n" +
            "  .groupBy(0)\n" +
            "  .sum(1)\n" +
            "  .print()", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    String[] expectedCounts = {"(hello,3)", "(world,1)", "(flink,1)", "(hadoop,1)"};
    Arrays.sort(expectedCounts);

    String[] counts = context.out.toInterpreterResultMessage().get(0).getData().split("\n");
    Arrays.sort(counts);

    assertArrayEquals(expectedCounts, counts);
  }

  @Test
  public void testStreamWordCount() throws InterpreterException, IOException {
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = interpreter.interpret(
            "val data = senv.fromElements(\"hello world\", \"hello flink\", \"hello hadoop\")",
            context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    context = getInterpreterContext();
    result = interpreter.interpret(
            "data.flatMap(line => line.split(\"\\\\s\"))\n" +
                    "  .map(w => (w, 1))\n" +
                    "  .keyBy(0)\n" +
                    "  .sum(1)\n" +
                    "  .print()\n" +
                    "senv.execute()", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    String[] expectedCounts = {"(hello,3)", "(world,1)", "(flink,1)", "(hadoop,1)"};
    String output = context.out.toInterpreterResultMessage().get(0).getData();
    for (String expectedCount : expectedCounts) {
      assertTrue(output, output.contains(expectedCount));
    }
  }

  @Test
  public void testCancelStreamSql() throws IOException, InterpreterException, InterruptedException, TimeoutException {
    String initStreamScalaScript = FlinkStreamSqlInterpreterTest.getInitStreamScript(1000);
    InterpreterResult result = interpreter.interpret(initStreamScalaScript,
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    final Waiter waiter = new Waiter();
    Thread thread = new Thread(() -> {
      try {
        InterpreterContext context = getInterpreterContext();
        context.getLocalProperties().put("type", "update");
        InterpreterResult result2 = interpreter.interpret(
                "val table = stenv.sqlQuery(\"select url, count(1) as pv from " +
                "log group by url\")\nz.show(table, streamType=\"update\")", context);
        LOGGER.info("---------------" + context.out.toString());
        LOGGER.info("---------------" + result2);
        waiter.assertTrue(context.out.toString().contains("Job was cancelled"));
        waiter.assertEquals(InterpreterResult.Code.ERROR, result2.code());
      } catch (Exception e) {
        e.printStackTrace();
        waiter.fail("Should not fail here");
      }
      waiter.resume();
    });
    thread.start();

    // the streaming job will run for 20 seconds. check init_stream.scala
    // sleep 10 seconds to make sure the job is started but not finished
    Thread.sleep(10 * 1000);

    InterpreterContext context = getInterpreterContext();
    context.getLocalProperties().put("type", "update");
    interpreter.cancel(context);
    waiter.await(10 * 1000);
    // resume job
    interpreter.interpret("val table = stenv.sqlQuery(\"select url, count(1) as pv from " +
            "log group by url\")\nz.show(table, streamType=\"update\")", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    TestCase.assertTrue(resultMessages.toString(),
            resultMessages.get(0).getData().contains("url\tpv\n"));
  }

  // TODO(zjffdu) flaky test
  // @Test
  public void testResumeStreamSqlFromSavePoint() throws IOException, InterpreterException, InterruptedException, TimeoutException {
    String initStreamScalaScript = FlinkStreamSqlInterpreterTest.getInitStreamScript(1000);
    InterpreterResult result = interpreter.interpret(initStreamScalaScript,
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    File savePointDir = FileUtils.getTempDirectory();
    final Waiter waiter = new Waiter();
    Thread thread = new Thread(() -> {
      try {
        InterpreterContext context = getInterpreterContext();
        context.getLocalProperties().put("type", "update");
        context.getLocalProperties().put("savepointDir", savePointDir.getAbsolutePath());
        context.getLocalProperties().put("parallelism", "1");
        context.getLocalProperties().put("maxParallelism", "10");
        InterpreterResult result2 = interpreter.interpret(
                "val table = stenv.sqlQuery(\"select url, count(1) as pv from " +
                "log group by url\")\nz.show(table, streamType=\"update\")", context);
        System.out.println("------------" + context.out.toString());
        System.out.println("------------" + result2);
        waiter.assertTrue(context.out.toString().contains("url\tpv\n"));
        waiter.assertEquals(InterpreterResult.Code.SUCCESS, result2.code());
      } catch (Exception e) {
        e.printStackTrace();
        waiter.fail("Should not fail here");
      }
      waiter.resume();
    });
    thread.start();

    // the streaming job will run for 60 seconds. check init_stream.scala
    // sleep 20 seconds to make sure the job is started but not finished
    Thread.sleep(20 * 1000);

    InterpreterContext context = getInterpreterContext();
    context.getLocalProperties().put("type", "update");
    context.getLocalProperties().put("savepointDir", savePointDir.getAbsolutePath());
    context.getLocalProperties().put("parallelism", "2");
    context.getLocalProperties().put("maxParallelism", "10");
    interpreter.cancel(context);
    waiter.await(20 * 1000);
    // resume job from savepoint
    interpreter.interpret(
            "val table = stenv.sqlQuery(\"select url, count(1) as pv from " +
            "log group by url\")\nz.show(table, streamType=\"update\")", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    TestCase.assertTrue(resultMessages.toString(),
            resultMessages.get(0).getData().contains("url\tpv\n"));
  }

  private InterpreterContext getInterpreterContext() {
    InterpreterContext context = InterpreterContext.builder()
            .setParagraphId("paragraphId")
            .setInterpreterOut(new InterpreterOutput(null))
            .setAngularObjectRegistry(angularObjectRegistry)
            .setIntpEventClient(mock(RemoteInterpreterEventClient.class))
            .build();
    InterpreterContext.set(context);
    return context;
  }
}
