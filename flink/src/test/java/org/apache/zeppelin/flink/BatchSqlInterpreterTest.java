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

import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public abstract class BatchSqlInterpreterTest extends FlinkSqlInterpreterTest {

  protected abstract String getPlanner();

  @Override
  protected Properties getFlinkProperties() throws IOException {
    Properties p = super.getFlinkProperties();
    p.setProperty("zeppelin.flink.planner", getPlanner());
    return p;
  }

  @Override
  protected FlinkSqlInterrpeter createFlinkSqlInterpreter(Properties properties) {
    return new FlinkBatchSqlInterpreter(properties);
  }

  @Test
  public void testBatchSQL() throws InterpreterException {
    if (getPlanner().equals("blink")) {
      return;
    }
    InterpreterResult result = flinkInterpreter.interpret(
            "val ds = benv.fromElements((1, \"jeff\"), (2, \"andy\"))", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    result = flinkInterpreter
            .interpret("btenv.registerDataSet(\"table_1\", ds, 'a, 'b)",
                    getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    sqlInterpreter.flinkInterpreter.getBatchTableEnvironment().useCatalog("default_catalog");
    sqlInterpreter.flinkInterpreter.getBatchTableEnvironment().useDatabase("default_database");

    result = sqlInterpreter.interpret("select * from default_catalog.default_database.table_1",
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals("a\tb\n" +
            "1\tjeff\n" +
            "2\tandy\n", appendOutput);
  }

  //@Test
  public void testHiveTable() throws InterpreterException {
    //    hiveShell.execute("create table hive_table (id int, name string)");
    InterpreterResult result = sqlInterpreter.interpret(
            "select * from hive_table",
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
  }

  //@Test
  public void testInsertInto() throws InterpreterException {
    if (getPlanner().equals("flink")) {
      return;
    }
    //    hiveShell.execute("create table table_inserted (id int, name string)");
    //    hiveShell.executeQuery("show tables");
    InterpreterResult result = flinkInterpreter.interpret(
            "val ds = benv.fromElements((1, \"jeff\"), (2, \"andy\"))", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    result = flinkInterpreter
            .interpret("btenv.registerDataSet(\"table_2\", ds, 'a, 'b)",
                    getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    result = sqlInterpreter.interpret(
            "insert into table_inserted select * from default_catalog.default_database.table_2",
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
  }

  //@Test
  public void testUDF() throws InterpreterException {

    InterpreterResult result = flinkInterpreter.interpret(
            "class AddOne extends ScalarFunction {\n" +
                    "  def eval(a: Int): Int = a + 1\n" +
                    "}", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    result = flinkInterpreter.interpret("btenv.registerFunction(\"addOne\", new $AddOne())",
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    result = sqlInterpreter.interpret("INSERT INTO dest SELECT addOne(int_col) FROM source",
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
  }
}
