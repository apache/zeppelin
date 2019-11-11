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
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class IPyFlinkInterpreterTest {

  private InterpreterGroup intpGroup;
  private Interpreter interpreter;
  private RemoteInterpreterEventClient mockIntpEventClient =
          mock(RemoteInterpreterEventClient.class);

  protected Properties initIntpProperties() {
    Properties p = new Properties();
    p.setProperty("zeppelin.pyflink.python", "python");
    p.setProperty("zeppelin.flink.maxResult", "3");
    p.setProperty("zeppelin.flink.test", "true");
    p.setProperty("zeppelin.dep.localrepo", Files.createTempDir().getAbsolutePath());
    p.setProperty("zeppelin.python.gatewayserver_address", "127.0.0.1");
    return p;
  }

  protected void startInterpreter(Properties properties) throws InterpreterException {
    InterpreterContext context = getInterpreterContext();
    context.setIntpEventClient(mockIntpEventClient);
    InterpreterContext.set(context);

    LazyOpenInterpreter flinkInterpreter = new LazyOpenInterpreter(
        new FlinkInterpreter(properties));
    intpGroup = new InterpreterGroup();
    intpGroup.put("session_1", new ArrayList<Interpreter>());
    intpGroup.get("session_1").add(flinkInterpreter);
    flinkInterpreter.setInterpreterGroup(intpGroup);

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
  public void testIPyFlink() throws InterpreterException {
    testBatchPyFlink(interpreter);
    testStreamPyFlink(interpreter);
  }

  public static void testBatchPyFlink(Interpreter interpreter) throws InterpreterException {
    InterpreterContext context = createInterpreterContext(mock(RemoteInterpreterEventClient.class));
    InterpreterResult result = interpreter.interpret(
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
  }

  public static void testStreamPyFlink(Interpreter interpreter) throws InterpreterException {
    InterpreterContext context = createInterpreterContext(mock(RemoteInterpreterEventClient.class));
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

  private static InterpreterContext createInterpreterContext(
          RemoteInterpreterEventClient mockRemoteEventClient) {
    return InterpreterContext.builder()
        .setNoteId("noteId")
        .setParagraphId("paragraphId")
        .setIntpEventClient(mockRemoteEventClient)
        .setInterpreterOut(new InterpreterOutput(null))
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
