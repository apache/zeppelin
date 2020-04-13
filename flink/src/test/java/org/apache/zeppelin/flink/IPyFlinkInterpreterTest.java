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


import com.google.common.io.Files;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.apache.zeppelin.python.IPythonInterpreterTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class IPyFlinkInterpreterTest extends IPythonInterpreterTest {

  private RemoteInterpreterEventClient mockIntpEventClient =
          mock(RemoteInterpreterEventClient.class);
  private LazyOpenInterpreter flinkScalaInterpreter;

  protected Properties initIntpProperties() {
    Properties p = new Properties();
    p.setProperty("zeppelin.pyflink.python", "python");
    p.setProperty("zeppelin.flink.maxResult", "3");
    p.setProperty("zeppelin.flink.test", "true");
    p.setProperty("zeppelin.dep.localrepo", Files.createTempDir().getAbsolutePath());
    p.setProperty("zeppelin.python.gatewayserver_address", "127.0.0.1");
    return p;
  }

  @Override
  protected void startInterpreter(Properties properties) throws InterpreterException {
    InterpreterContext context = getInterpreterContext();
    context.setIntpEventClient(mockIntpEventClient);
    InterpreterContext.set(context);

    this.flinkScalaInterpreter = new LazyOpenInterpreter(
        new FlinkInterpreter(properties));
    intpGroup = new InterpreterGroup();
    intpGroup.put("session_1", new ArrayList<Interpreter>());
    intpGroup.get("session_1").add(flinkScalaInterpreter);
    flinkScalaInterpreter.setInterpreterGroup(intpGroup);

    LazyOpenInterpreter pyFlinkInterpreter =
        new LazyOpenInterpreter(new PyFlinkInterpreter(properties));
    intpGroup.get("session_1").add(pyFlinkInterpreter);
    pyFlinkInterpreter.setInterpreterGroup(intpGroup);

    interpreter = new LazyOpenInterpreter(new IPyFlinkInterpreter(properties));
    intpGroup.get("session_1").add(interpreter);
    interpreter.setInterpreterGroup(intpGroup);

    interpreter.open();
  }

  @Before
  public void setUp() throws InterpreterException {
    Properties properties = initIntpProperties();
    startInterpreter(properties);
  }

  @After
  public void tearDown() throws InterpreterException {
    intpGroup.close();
  }

  @Test
  public void testBatchIPyFlink() throws InterpreterException, IOException {
    testBatchPyFlink(interpreter, flinkScalaInterpreter);
  }

  @Test
  public void testStreamIPyFlink() throws InterpreterException, IOException {
    testStreamPyFlink(interpreter, flinkScalaInterpreter);
  }

  @Test
  public void testSingleStreamTableApi() throws InterpreterException, IOException {
    testSingleStreamTableApi(interpreter, flinkScalaInterpreter);
  }

  @Test
  public void testUpdateStreamTableApi() throws InterpreterException, IOException {
    testUpdateStreamTableApi(interpreter, flinkScalaInterpreter);
  }

  @Test
  public void testAppendStreamTableApi() throws InterpreterException, IOException {
    testAppendStreamTableApi(interpreter, flinkScalaInterpreter);
  }

  public static void testBatchPyFlink(Interpreter pyflinkInterpreter, Interpreter flinkScalaInterpreter) throws InterpreterException, IOException {
    InterpreterContext context = createInterpreterContext();
    InterpreterResult result = pyflinkInterpreter.interpret(
        "import tempfile\n" +
        "import os\n" +
        "import shutil\n" +
        "sink_path = tempfile.gettempdir() + '/batch.csv'\n" +
        "if os.path.exists(sink_path):\n" +
        "  if os.path.isfile(sink_path):\n" +
        "    os.remove(sink_path)\n" +
        "  else:\n" +
        "    shutil.rmtree(sink_path)\n" +
        "b_env.set_parallelism(1)\n" +
        "t = bt_env.from_elements([(1, 'hi', 'hello'), (2, 'hi', 'hello')], ['a', 'b', 'c'])\n" +
        "bt_env.connect(FileSystem().path(sink_path)) \\\n" +
        "   .with_format(OldCsv()\n" +
        "     .field_delimiter(',')\n" +
        "     .field(\"a\", DataTypes.BIGINT())\n" +
        "     .field(\"b\", DataTypes.STRING())\n" +
        "     .field(\"c\", DataTypes.STRING())) \\\n" +
        "   .with_schema(Schema()\n" +
        "     .field(\"a\", DataTypes.BIGINT())\n" +
        "     .field(\"b\", DataTypes.STRING())\n" +
        "     .field(\"c\", DataTypes.STRING())) \\\n" +
        "   .register_table_sink(\"batch_sink\")\n" +
        "t.select(\"a + 1, b, c\").insert_into(\"batch_sink\")\n" +
        "bt_env.execute(\"batch_job\")"
            , context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    // use group by
    context = createInterpreterContext();
    result = pyflinkInterpreter.interpret(
            "import tempfile\n" +
            "import os\n" +
            "import shutil\n" +
            "sink_path = tempfile.gettempdir() + '/streaming.csv'\n" +
            "if os.path.exists(sink_path):\n" +
            "    if os.path.isfile(sink_path):\n" +
            "      os.remove(sink_path)\n" +
            "    else:\n" +
            "      shutil.rmtree(sink_path)\n" +
            "b_env.set_parallelism(1)\n" +
            "t = bt_env.from_elements([(1, 'hi', 'hello'), (2, 'hi', 'hello')], ['a', 'b', 'c'])\n" +
            "bt_env.connect(FileSystem().path(sink_path)) \\\n" +
            "    .with_format(OldCsv()\n" +
            "      .field_delimiter(',')\n" +
            "      .field(\"a\", DataTypes.STRING())\n" +
            "      .field(\"b\", DataTypes.BIGINT())\n" +
            "      .field(\"c\", DataTypes.BIGINT())) \\\n" +
            "    .with_schema(Schema()\n" +
            "      .field(\"a\", DataTypes.STRING())\n" +
            "      .field(\"b\", DataTypes.BIGINT())\n" +
            "      .field(\"c\", DataTypes.BIGINT())) \\\n" +
            "    .register_table_sink(\"batch_sink4\")\n" +
            "t.group_by(\"c\").select(\"c, sum(a), count(b)\").insert_into(\"batch_sink4\")\n" +
            "bt_env.execute(\"batch_job4\")"
            , context);
    assertEquals(result.toString(),InterpreterResult.Code.SUCCESS, result.code());

    // use scala udf in pyflink
    // define scala udf
    result = flinkScalaInterpreter.interpret(
            "class AddOne extends ScalarFunction {\n" +
                    "  def eval(a: java.lang.Long): String = a + \"\1\"\n" +
                    "}", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    result = flinkScalaInterpreter.interpret("btenv.registerFunction(\"addOne\", new AddOne())",
            context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    context = createInterpreterContext();
    result = pyflinkInterpreter.interpret(
            "import tempfile\n" +
            "import os\n" +
            "import shutil\n" +
            "sink_path = tempfile.gettempdir() + '/streaming.csv'\n" +
            "if os.path.exists(sink_path):\n" +
            "    if os.path.isfile(sink_path):\n" +
            "      os.remove(sink_path)\n" +
            "    else:\n" +
            "      shutil.rmtree(sink_path)\n" +
            "b_env.set_parallelism(1)\n" +
            "t = bt_env.from_elements([(1, 'hi', 'hello'), (2, 'hi', 'hello')], ['a', 'b', 'c'])\n" +
            "bt_env.connect(FileSystem().path(sink_path)) \\\n" +
            "    .with_format(OldCsv()\n" +
            "      .field_delimiter(',')\n" +
            "      .field(\"a\", DataTypes.BIGINT())\n" +
            "      .field(\"b\", DataTypes.STRING())\n" +
            "      .field(\"c\", DataTypes.STRING())) \\\n" +
            "    .with_schema(Schema()\n" +
            "      .field(\"a\", DataTypes.BIGINT())\n" +
            "      .field(\"b\", DataTypes.STRING())\n" +
            "      .field(\"c\", DataTypes.STRING())) \\\n" +
            "    .register_table_sink(\"batch_sink3\")\n" +
            "t.select(\"a, addOne(a), c\").insert_into(\"batch_sink3\")\n" +
            "bt_env.execute(\"batch_job3\")"
            , context);
    assertEquals(result.toString(),InterpreterResult.Code.SUCCESS, result.code());

    // z.show
    context = createInterpreterContext();
    result = pyflinkInterpreter.interpret(
            "import tempfile\n" +
            "import os\n" +
            "import shutil\n" +
            "sink_path = tempfile.gettempdir() + '/streaming.csv'\n" +
            "if os.path.exists(sink_path):\n" +
            "    if os.path.isfile(sink_path):\n" +
            "      os.remove(sink_path)\n" +
            "    else:\n" +
            "      shutil.rmtree(sink_path)\n" +
            "b_env.set_parallelism(1)\n" +
            "t = bt_env.from_elements([(1, 'hi', 'hello'), (2, 'hi', 'hello')], ['a', 'b', 'c'])\n" +
            "z.show(t)"
            , context);
    assertEquals(result.toString(),InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(new String(context.out.toByteArray()), 1, resultMessages.size());
    assertEquals(new String(context.out.toByteArray()), InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertEquals(new String(context.out.toByteArray()), "a\tb\tc\n1\thi\thello\n2\thi\thello\n", resultMessages.get(0).getData());
  }

  @Override
  public void testIPythonFailToLaunch() throws InterpreterException {
    tearDown();

    Properties properties = initIntpProperties();
    properties.setProperty("zeppelin.pyflink.python", "invalid_python");

    try {
      startInterpreter(properties);
      fail("Should not be able to start IPyFlinkInterpreter");
    } catch (InterpreterException e) {
      String exceptionMsg = ExceptionUtils.getStackTrace(e);
      assertTrue(exceptionMsg, exceptionMsg.contains("No such file or directory"));
    }
  }

  public static void testStreamPyFlink(Interpreter interpreter, Interpreter flinkScalaInterpreter) throws InterpreterException, IOException {
    InterpreterContext context = createInterpreterContext();
    InterpreterResult result = interpreter.interpret(
            "import tempfile\n" +
            "import os\n" +
            "import shutil\n" +
            "sink_path = tempfile.gettempdir() + '/streaming.csv'\n" +
            "if os.path.exists(sink_path):\n" +
            "    if os.path.isfile(sink_path):\n" +
            "      os.remove(sink_path)\n" +
            "    else:\n" +
            "      shutil.rmtree(sink_path)\n" +
            "s_env.set_parallelism(1)\n" +
            "t = st_env.from_elements([(1, 'hi', 'hello'), (2, 'hi', 'hello')], ['a', 'b', 'c'])\n" +
            "st_env.connect(FileSystem().path(sink_path)) \\\n" +
            "    .with_format(OldCsv()\n" +
            "      .field_delimiter(',')\n" +
            "      .field(\"a\", DataTypes.BIGINT())\n" +
            "      .field(\"b\", DataTypes.STRING())\n" +
            "      .field(\"c\", DataTypes.STRING())) \\\n" +
            "    .with_schema(Schema()\n" +
            "      .field(\"a\", DataTypes.BIGINT())\n" +
            "      .field(\"b\", DataTypes.STRING())\n" +
            "      .field(\"c\", DataTypes.STRING())) \\\n" +
            "    .register_table_sink(\"stream_sink\")\n" +
            "t.select(\"a + 1, b, c\").insert_into(\"stream_sink\")\n" +
            "st_env.execute(\"stream_job\")"
            , context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
  }

  public static void testSingleStreamTableApi(Interpreter interpreter,
                                              Interpreter flinkScalaInterpreter) throws IOException, InterpreterException {
    String initStreamScalaScript = IOUtils.toString(IPyFlinkInterpreterTest.class.getResource("/init_stream.scala"));
    InterpreterContext context = createInterpreterContext();
    InterpreterResult result = flinkScalaInterpreter.interpret(initStreamScalaScript, context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    context = createInterpreterContext();
    String code = "table = st_env.sql_query('select max(rowtime), count(1) from log')\nz.show(table,stream_type='single',template = 'Total Count: {1} <br/> {0}')";
    result = interpreter.interpret(code, context);
    assertEquals(new String(context.out.toByteArray()), InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.HTML, resultMessages.get(0).getType());
    assertTrue(resultMessages.toString(),
            resultMessages.get(0).getData().contains("Total Count"));
  }

  public static void testUpdateStreamTableApi(Interpreter interpreter,
                                              Interpreter flinkScalaInterpreter) throws IOException, InterpreterException {
    String initStreamScalaScript = IOUtils.toString(IPyFlinkInterpreterTest.class.getResource("/init_stream.scala"));
    InterpreterContext context = createInterpreterContext();
    InterpreterResult result = flinkScalaInterpreter.interpret(initStreamScalaScript, context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    context = createInterpreterContext();
    String code = "table = st_env.sql_query('select url, count(1) as pv from log group by url')\nz.show(table,stream_type='update')";
    result = interpreter.interpret(code, context);
    assertEquals(new String(context.out.toByteArray()), InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertTrue(resultMessages.toString(),
            resultMessages.get(0).getData().contains("url\tpv\n"));
  }

  public static void testAppendStreamTableApi(Interpreter interpreter,
                                              Interpreter flinkScalaInterpreter) throws IOException, InterpreterException {
    String initStreamScalaScript = IOUtils.toString(IPyFlinkInterpreterTest.class.getResource("/init_stream.scala"));
    InterpreterContext context = createInterpreterContext();
    InterpreterResult result = flinkScalaInterpreter.interpret(initStreamScalaScript, context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    context = createInterpreterContext();
    String code = "table = st_env.sql_query(\"select TUMBLE_START(rowtime, INTERVAL '5' SECOND) as " +
            "start_time, url, count(1) as pv from log group by " +
            "TUMBLE(rowtime, INTERVAL '5' SECOND), url\")\nz.show(table,stream_type='append')";
    result = interpreter.interpret(code, context);
    assertEquals(new String(context.out.toByteArray()), InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertTrue(resultMessages.toString(),
            resultMessages.get(0).getData().contains("url\tpv\n"));
  }

  private static InterpreterContext createInterpreterContext() {
    return InterpreterContext.builder()
        .setNoteId("noteId")
        .setParagraphId("paragraphId")
        .setInterpreterOut(new InterpreterOutput(null))
        .setIntpEventClient(mock(RemoteInterpreterEventClient.class))
        .build();
  }

  protected InterpreterContext getInterpreterContext() {
    return InterpreterContext.builder()
            .setNoteId("noteId")
            .setParagraphId("paragraphId")
            .setInterpreterOut(new InterpreterOutput(null))
            .setIntpEventClient(mock(RemoteInterpreterEventClient.class))
            .build();
  }
}
