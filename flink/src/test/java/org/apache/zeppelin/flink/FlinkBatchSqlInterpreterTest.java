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


import org.apache.commons.io.FileUtils;
import org.apache.flink.table.api.config.ExecutionConfigOptions;
import org.apache.flink.table.api.config.OptimizerConfigOptions;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

public class FlinkBatchSqlInterpreterTest extends SqlInterpreterTest {

  @Override
  protected FlinkSqlInterrpeter createFlinkSqlInterpreter(Properties properties) {
    return new FlinkBatchSqlInterpreter(properties);
  }

  @Test
  public void testSelect() throws InterpreterException, IOException {
    hiveShell.execute("create table source_table (id int, name string)");
    hiveShell.execute("insert into source_table values(1, 'a'), (2, 'b')");

    // verify select from
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result =
            sqlInterpreter.interpret("show tables", context);
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(1, resultMessages.size());
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertEquals(resultMessages.get(0).toString(),
            "table\nsource_table\n", resultMessages.get(0).getData());

    // verify select from
    context = getInterpreterContext();
    result =
            sqlInterpreter.interpret("select * from source_table", context);
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(1, resultMessages.size());
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertEquals("id\tname\n1\ta\n2\tb\n", resultMessages.get(0).getData());

    // define scala udf
    result = flinkInterpreter.interpret(
            "class AddOne extends ScalarFunction {\n" +
                    "  def eval(a: Int): Int = a + 1\n" +
                    "}", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    result = flinkInterpreter.interpret("btenv.registerFunction(\"addOne\", new AddOne())",
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    // select which use scala udf
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("SELECT addOne(id) as add_one FROM source_table", context);
    assertEquals(new String(context.out.toByteArray()), InterpreterResult.Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, resultMessages.size());
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertEquals("add_one\n2\n3\n", resultMessages.get(0).getData());

    // define python udf via PyFlinkInterpreter
    result = pyFlinkInterpreter.interpret(
            "class PythonUpper(ScalarFunction):\n" +
                    "  def eval(self, s):\n" +
                    "    return s.upper()", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    result = pyFlinkInterpreter.interpret("bt_env.register_function(\"python_upper\", " +
                    "udf(PythonUpper(), DataTypes.STRING(), DataTypes.STRING()))",
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, resultMessages.size());
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertEquals("add_one\n2\n3\n", resultMessages.get(0).getData());

    // select which use python udf
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("SELECT python_upper(name) as name FROM source_table", context);
    assertEquals(new String(context.out.toByteArray()), InterpreterResult.Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, resultMessages.size());
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertEquals("name\nA\nB\n", resultMessages.get(0).getData());

    // define python udf via IPyFlinkInterpreter
    result = iPyFlinkInterpreter.interpret(
            "class IPythonUpper(ScalarFunction):\n" +
                    "  def eval(self, s):\n" +
                    "    return s.upper()", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    result = iPyFlinkInterpreter.interpret("bt_env.register_function(\"ipython_upper\", " +
                    "udf(IPythonUpper(), DataTypes.STRING(), DataTypes.STRING()))",
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    // select which use python udf
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("SELECT ipython_upper(name) as name FROM source_table", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, resultMessages.size());
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertEquals("name\nA\nB\n", resultMessages.get(0).getData());
  }

  @Test
  public void testInsertInto() throws InterpreterException, IOException {
    hiveShell.execute("create table source_table (id int, name string)");
    hiveShell.execute("insert into source_table values(1, 'a'), (2, 'b')");

    File destDir = Files.createTempDirectory("flink_test").toFile();
    FileUtils.deleteDirectory(destDir);
    InterpreterResult result = sqlInterpreter.interpret(
            "CREATE TABLE sink_table (\n" +
                    "id int,\n" +
                    "name string" +
                    ") WITH (\n" +
                    "'format.field-delimiter'=',',\n" +
                    "'connector.type'='filesystem',\n" +
                    "'format.derive-schema'='true',\n" +
                    "'connector.path'='" + destDir.getAbsolutePath() + "',\n" +
                    "'format.type'='csv'\n" +
                    ");", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    // insert into
    InterpreterContext context = getInterpreterContext();
    result = sqlInterpreter.interpret(
            "insert into sink_table select * from source_table", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals("Insertion successfully.\n", resultMessages.get(0).getData());

    // verify insert into via select from sink_table
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("select * from sink_table", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals("id\tname\n1\ta\n2\tb\n", resultMessages.get(0).getData());

    // insert into again will fail
    context = getInterpreterContext();
    result = sqlInterpreter.interpret(
            "insert into sink_table select * from source_table", context);
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertTrue(resultMessages.get(0).getData(),
            resultMessages.get(0).getData().contains("already exists"));

    // insert overwrite into
    //    context = getInterpreterContext();
    //    result = sqlInterpreter.interpret(
    //            "insert overwrite dest_table select id + 1, name from source_table", context);
    //    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    //    resultMessages = context.out.toInterpreterResultMessage();
    //    assertEquals("Insertion successfully.\n", resultMessages.get(0).getData());
    //
    //    // verify insert into via select from the dest_table
    //    context = getInterpreterContext();
    //    result = sqlInterpreter.interpret(
    //            "select * from dest_table", context);
    //    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    //    resultMessages = context.out.toInterpreterResultMessage();
    //    assertEquals("id\tname\n2\ta\n3\tb\n", resultMessages.get(0).getData());
    //
    //    // define scala udf
    //    result = flinkInterpreter.interpret(
    //            "class AddOne extends ScalarFunction {\n" +
    //                    "  def eval(a: Int): Int = a + 1\n" +
    //                    "}", getInterpreterContext());
    //    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    //
    //    result = flinkInterpreter.interpret("btenv.registerFunction(\"addOne\", new AddOne())",
    //            getInterpreterContext());
    //    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    //
    //    // insert into dest_table2 using udf
    //    destDir = Files.createTempDirectory("flink_test").toFile();
    //    FileUtils.deleteDirectory(destDir);
    //    result = sqlInterpreter.interpret(
    //            "CREATE TABLE dest_table2 (\n" +
    //                    "id int,\n" +
    //                    "name string" +
    //                    ") WITH (\n" +
    //                    "'format.field-delimiter'=',',\n" +
    //                    "'connector.type'='filesystem',\n" +
    //                    "'format.derive-schema'='true',\n" +
    //                    "'connector.path'='" + destDir.getAbsolutePath() + "',\n" +
    //                    "'format.type'='csv'\n" +
    //                    ");", getInterpreterContext());
    //    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    //
    //    context = getInterpreterContext();
    //    result = sqlInterpreter.interpret(
    //            "insert into dest_table2 select addOne(id), name from source_table", context);
    //    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    //    resultMessages = context.out.toInterpreterResultMessage();
    //    assertEquals("Insertion successfully.\n", resultMessages.get(0).getData());
    //
    //    // verify insert into via select from the dest table
    //    context = getInterpreterContext();
    //    result = sqlInterpreter.interpret(
    //            "select * from dest_table2", context);
    //    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    //    resultMessages = context.out.toInterpreterResultMessage();
    //    assertEquals("id\tname\n2\ta\n3\tb\n", resultMessages.get(0).getData());
  }

  @Test
  public void testSetTableConfig() throws InterpreterException, IOException {
    hiveShell.execute("create table source_table (id int, name string)");
    hiveShell.execute("insert into source_table values(1, 'a'), (2, 'b')");

    File destDir = Files.createTempDirectory("flink_test").toFile();
    FileUtils.deleteDirectory(destDir);
    InterpreterResult result = sqlInterpreter.interpret(
            "CREATE TABLE sink_table (\n" +
                    "id int,\n" +
                    "name string" +
                    ") WITH (\n" +
                    "'format.field-delimiter'=',',\n" +
                    "'connector.type'='filesystem',\n" +
                    "'format.derive-schema'='true',\n" +
                    "'connector.path'='" + destDir.getAbsolutePath() + "',\n" +
                    "'format.type'='csv'\n" +
                    ");", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    
    // set parallelism then insert into
    InterpreterContext context = getInterpreterContext();
    result = sqlInterpreter.interpret(
            "set table.exec.resource.default-parallelism=10;" +
                    "insert into sink_table select * from source_table", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals("Insertion successfully.\n", resultMessages.get(0).getData());
    assertEquals(ExecutionConfigOptions.TABLE_EXEC_RESOURCE_DEFAULT_PARALLELISM.defaultValue(),
            sqlInterpreter.tbenv.getConfig().getConfiguration().get(ExecutionConfigOptions.TABLE_EXEC_RESOURCE_DEFAULT_PARALLELISM));

    // set then insert into
    destDir.delete();
    context = getInterpreterContext();
    result = sqlInterpreter.interpret(
            "set table.optimizer.source.predicate-pushdown-enabled=false;" +
                    "insert into sink_table select * from source_table", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals("Insertion successfully.\n", resultMessages.get(0).getData());
    assertEquals(ExecutionConfigOptions.TABLE_EXEC_RESOURCE_DEFAULT_PARALLELISM.defaultValue(),
            sqlInterpreter.tbenv.getConfig().getConfiguration().get(ExecutionConfigOptions.TABLE_EXEC_RESOURCE_DEFAULT_PARALLELISM));
    assertEquals(OptimizerConfigOptions.TABLE_OPTIMIZER_SOURCE_PREDICATE_PUSHDOWN_ENABLED.defaultValue(),
            sqlInterpreter.tbenv.getConfig().getConfiguration().get(OptimizerConfigOptions.TABLE_OPTIMIZER_SOURCE_PREDICATE_PUSHDOWN_ENABLED));

    // invalid config
    destDir.delete();
    context = getInterpreterContext();
    result = sqlInterpreter.interpret(
            "set table.invalid_config=false;" +
                    "insert into sink_table select * from source_table", context);
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertTrue(resultMessages.get(0).getData(),
            resultMessages.get(0).getData().contains("table.invalid_config is not a valid table/sql config"));
  }

  @Test
  public void testMultipleInsertInto() throws InterpreterException, IOException {
    hiveShell.execute("create table source_table (id int, name string)");
    hiveShell.execute("insert into source_table values(1, 'a'), (2, 'b')");

    File destDir = Files.createTempDirectory("flink_test").toFile();
    FileUtils.deleteDirectory(destDir);
    InterpreterResult result = sqlInterpreter.interpret(
            "CREATE TABLE sink_table (\n" +
                    "id int,\n" +
                    "name string" +
                    ") WITH (\n" +
                    "'format.field-delimiter'=',',\n" +
                    "'connector.type'='filesystem',\n" +
                    "'format.derive-schema'='true',\n" +
                    "'connector.path'='" + destDir.getAbsolutePath() + "',\n" +
                    "'format.type'='csv'\n" +
                    ");", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    File destDir2 = Files.createTempDirectory("flink_test").toFile();
    FileUtils.deleteDirectory(destDir2);
    result = sqlInterpreter.interpret(
            "CREATE TABLE sink_table2 (\n" +
                    "id int,\n" +
                    "name string" +
                    ") WITH (\n" +
                    "'format.field-delimiter'=',',\n" +
                    "'connector.type'='filesystem',\n" +
                    "'format.derive-schema'='true',\n" +
                    "'connector.path'='" + destDir2.getAbsolutePath() + "',\n" +
                    "'format.type'='csv'\n" +
                    ");", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    // insert into
    InterpreterContext context = getInterpreterContext();
    result = sqlInterpreter.interpret(
            "insert into sink_table select * from source_table;insert into sink_table2 select * from source_table", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals("Insertion successfully.\nInsertion successfully.\n", resultMessages.get(0).getData());

    // verify insert into via select from sink_table
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("select * from sink_table", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals("id\tname\n1\ta\n2\tb\n", resultMessages.get(0).getData());

    context = getInterpreterContext();
    result = sqlInterpreter.interpret("select * from sink_table2", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals("id\tname\n1\ta\n2\tb\n", resultMessages.get(0).getData());

    // insert into (runAsOne)
    destDir.delete();
    destDir2.delete();

    context = getInterpreterContext();
    context.getLocalProperties().put("runAsOne", "true");
    result = sqlInterpreter.interpret(
            "insert into sink_table select * from source_table;insert into sink_table2 select * from source_table", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals("Insertion successfully.\n", resultMessages.get(0).getData());

    // verify insert into via select from sink_table
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("select * from sink_table", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals("id\tname\n1\ta\n2\tb\n", resultMessages.get(0).getData());

    context = getInterpreterContext();
    result = sqlInterpreter.interpret("select * from sink_table2", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals("id\tname\n1\ta\n2\tb\n", resultMessages.get(0).getData());
  }
}
