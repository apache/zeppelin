/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.flink;

import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

public class FlinkStreamSqlInterpreterTest extends FlinkSqlInterpreterTest {

  @Override
  protected FlinkSqlInterrpeter createFlinkSqlInterpreter(Properties properties) {
    return new FlinkStreamSqlInterpreter(properties);
  }

  @Test
  public void testSingleStreamSql() throws IOException, InterpreterException {
    String initStreamScalaScript = IOUtils.toString(getClass().getResource("/init_stream.scala"));
    InterpreterResult result = flinkInterpreter.interpret(initStreamScalaScript,
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    InterpreterContext context = getInterpreterContext();
    context.getLocalProperties().put("type", "single");
    result = sqlInterpreter.interpret("select max(rowtime), count(1) " +
            "from default_catalog.default_database.log", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(InterpreterResult.Type.HTML, updatedOutput.toInterpreterResultMessage().getType());
    assertTrue(updatedOutput.toInterpreterResultMessage().getData(),
            !updatedOutput.toInterpreterResultMessage().getData().isEmpty());
  }

  @Test
  public void testRetractStreamSql() throws IOException, InterpreterException {
    String initStreamScalaScript = IOUtils.toString(getClass().getResource("/init_stream.scala"));
    InterpreterResult result = flinkInterpreter.interpret(initStreamScalaScript,
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    InterpreterContext context = getInterpreterContext();
    context.getLocalProperties().put("type", "retract");
    result = sqlInterpreter.interpret("select url, count(1) as pv from " +
            "default_catalog.default_database.log group by url", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(InterpreterResult.Type.TABLE,
            updatedOutput.toInterpreterResultMessage().getType());
    assertTrue(updatedOutput.toInterpreterResultMessage().getData(),
            !updatedOutput.toInterpreterResultMessage().getData().isEmpty());
  }

  @Test
  public void testTimeSeriesStreamSql() throws IOException, InterpreterException {
    String initStreamScalaScript = IOUtils.toString(getClass().getResource("/init_stream.scala"));
    InterpreterResult result = flinkInterpreter.interpret(initStreamScalaScript,
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    InterpreterContext context = getInterpreterContext();
    context.getLocalProperties().put("type", "ts");
    result = sqlInterpreter.interpret("select TUMBLE_START(rowtime, INTERVAL '5' SECOND) as " +
            "start_time, url, count(1) as pv from default_catalog.default_database.log group by " +
            "TUMBLE(rowtime, INTERVAL '5' SECOND), url", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(InterpreterResult.Type.TABLE,
            updatedOutput.toInterpreterResultMessage().getType());
    assertTrue(updatedOutput.toInterpreterResultMessage().getData(),
            !updatedOutput.toInterpreterResultMessage().getData().isEmpty());
  }
}
