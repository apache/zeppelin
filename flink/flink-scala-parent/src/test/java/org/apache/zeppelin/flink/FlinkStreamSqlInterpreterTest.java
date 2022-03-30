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

import net.jodah.concurrentunit.Waiter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.core.execution.JobClient;
import org.apache.flink.core.execution.JobListener;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.junit.Test;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

public class FlinkStreamSqlInterpreterTest extends FlinkSqlInterpreterTest {


  private static class FlinkJobListener implements JobListener {

    private int jobCount = 0;

    public int getJobCount() {
      return jobCount;
    }

    @Override
    public void onJobSubmitted(@Nullable JobClient jobClient, @Nullable Throwable throwable) {
      jobCount ++;
    }

    @Override
    public void onJobExecuted(@Nullable JobExecutionResult jobExecutionResult, @Nullable Throwable throwable) {

    }
  }

  private FlinkJobListener flinkJobListener;

  @Override
  protected FlinkSqlInterpreter createFlinkSqlInterpreter(Properties properties) {
    return new FlinkStreamSqlInterpreter(properties);
  }

  @Override
  public void setUp() throws InterpreterException, IOException {
    super.setUp();
    flinkJobListener = new FlinkJobListener();
    flinkInterpreter.getStreamExecutionEnvironment().getJavaEnv().registerJobListener(flinkJobListener);
  }

  @Test
  public void testSingleStreamSql() throws IOException, InterpreterException {
    String initStreamScalaScript = getInitStreamScript(100);
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = flinkInterpreter.interpret(initStreamScalaScript, context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    context = getInterpreterContext();
    context.getLocalProperties().put("type", "single");
    context.getLocalProperties().put("template", "Total Count: {1} <br/> {0}");
    result = sqlInterpreter.interpret("select max(rowtime), count(1) " +
            "from log", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.ANGULAR, resultMessages.get(0).getType());
    assertTrue(resultMessages.toString(),
            resultMessages.get(0).getData().contains("Total Count"));
  }

  @Test
  public void testSingleStreamTableApi() throws IOException, InterpreterException {
    String initStreamScalaScript = getInitStreamScript(100);
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = flinkInterpreter.interpret(initStreamScalaScript, context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    context = getInterpreterContext();
    String code = "val table = stenv.sqlQuery(\"select max(rowtime), count(1) from log\")\n" +
            "z.show(table,streamType=\"single\", configs = Map(\"template\" -> \"Total Count: {1} <br/> {0}\"))";
    result = flinkInterpreter.interpret(code, context);
    assertEquals(context.out.toString(), InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.ANGULAR, resultMessages.get(0).getType());
    assertTrue(resultMessages.toString(),
            resultMessages.get(0).getData().contains("Total Count"));

    context = getInterpreterContext();
    result = sqlInterpreter.interpret("show tables", context);
    assertEquals(context.out.toString(), InterpreterResult.Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertEquals("table\nlog\n", resultMessages.get(0).getData());
  }

  @Test
  public void testUpdateStreamSql() throws IOException, InterpreterException {
    String initStreamScalaScript = getInitStreamScript(100);
    InterpreterResult result = flinkInterpreter.interpret(initStreamScalaScript,
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    InterpreterContext context = getInterpreterContext();
    result = sqlInterpreter.interpret("select url, count(1) as pv from " +
            "log group by url", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertTrue(resultMessages.toString(),
            resultMessages.get(0).getData().contains("url\tpv\n"));
  }

  @Test
  public void testUpdateStreamTableApi() throws IOException, InterpreterException {
    String initStreamScalaScript = getInitStreamScript(100);
    InterpreterResult result = flinkInterpreter.interpret(initStreamScalaScript,
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    InterpreterContext context = getInterpreterContext();
    String code = "val table = stenv.sqlQuery(\"select url, count(1) as pv from log group by url\")\nz.show(table, streamType=\"update\")";
    result = flinkInterpreter.interpret(code, context);
    assertEquals(context.out.toString(), InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertTrue(resultMessages.toString(),
            resultMessages.get(0).getData().contains("url\tpv\n"));
  }

  @Test
  public void testAppendStreamSql() throws IOException, InterpreterException {
    String initStreamScalaScript = getInitStreamScript(100);
    InterpreterResult result = flinkInterpreter.interpret(initStreamScalaScript,
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    InterpreterContext context = getInterpreterContext();
    context.getLocalProperties().put("type", "append");
    result = sqlInterpreter.interpret("select TUMBLE_START(rowtime, INTERVAL '5' SECOND) as " +
            "start_time, url, count(1) as pv from log group by " +
            "TUMBLE(rowtime, INTERVAL '5' SECOND), url", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertTrue(resultMessages.toString(),
            resultMessages.get(0).getData().contains("url\tpv\n"));
  }

  @Test
  public void testAppendStreamTableApi() throws IOException, InterpreterException {
    String initStreamScalaScript = getInitStreamScript(100);
    InterpreterResult result = flinkInterpreter.interpret(initStreamScalaScript,
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    InterpreterContext context = getInterpreterContext();
    String code = "val table = stenv.sqlQuery(\"select TUMBLE_START(rowtime, INTERVAL '5' SECOND) as " +
            "start_time, url, count(1) as pv from log group by " +
            "TUMBLE(rowtime, INTERVAL '5' SECOND), url\")\nz.show(table, streamType=\"append\")";
    result = flinkInterpreter.interpret(code, context);
    assertEquals(context.out.toString(), InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertTrue(resultMessages.toString(),
            resultMessages.get(0).getData().contains("url\tpv\n"));
  }

  @Test
  public void testCancelStreamSql() throws IOException, InterpreterException, InterruptedException, TimeoutException {
    // clean checkpoint dir first, checkpoint dir is defined in init_stream.scala
    File checkpointDir = new File("/tmp/flink/checkpoints");
    FileUtils.deleteDirectory(checkpointDir);

    String initStreamScalaScript = getInitStreamScript(1000);
    InterpreterResult result = flinkInterpreter.interpret(initStreamScalaScript,
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    final Waiter waiter = new Waiter();
    Thread thread = new Thread(() -> {
      try {
        InterpreterContext context = getInterpreterContext();
        InterpreterResult result2 = sqlInterpreter.interpret("select url, count(1) as pv from " +
                "log group by url", context);
        waiter.assertTrue(context.out.toString().contains("Job was cancelled"));
        waiter.assertEquals(InterpreterResult.Code.ERROR, result2.code());
      } catch (Exception e) {
        e.printStackTrace();
        waiter.fail("Should not fail here");
      }
      waiter.resume();
    });
    thread.start();

    // the streaming job will run for 60 seconds. check init_stream.scala
    // sleep 30 seconds to make sure the job is started but not finished
    Thread.sleep(30 * 1000);

    InterpreterContext context = getInterpreterContext();
    sqlInterpreter.cancel(context);
    waiter.await(30 * 1000);

    // verify checkpoints
    assertTrue(checkpointDir.listFiles(f -> f.isDirectory()).length > 0);

    // resume job
    sqlInterpreter.interpret("select url, count(1) as pv from " +
            "log group by url", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertTrue(resultMessages.toString(),
            resultMessages.get(0).getData().contains("url\tpv\n"));
  }

  @Test
  public void testResumeStreamSqlFromSavePointDir() throws IOException, InterpreterException, InterruptedException, TimeoutException {
    if (!flinkInterpreter.getFlinkShims().getFlinkVersion().isAfterFlink114()) {
      LOGGER.info("Skip testResumeStreamSqlFromSavePointPath, because this test is only passed after Flink 1.14 due to FLINK-23654");
      // By default, this thread pool in Flink JobManager is the number of cpu cores. While the cpu cores in github action container is too small
      return;
    }

    String initStreamScalaScript = getInitStreamScript(1000);
    InterpreterResult result = flinkInterpreter.interpret(initStreamScalaScript,
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    File savePointDir = Files.createTempDirectory("zeppelin-flink").toFile();
    final Waiter waiter = new Waiter();
    Thread thread = new Thread(() -> {
      try {
        InterpreterContext context = getInterpreterContext();
        context.getLocalProperties().put(JobManager.SAVEPOINT_DIR, savePointDir.getAbsolutePath());
        context.getLocalProperties().put("parallelism", "1");
        context.getLocalProperties().put("maxParallelism", "10");
        InterpreterResult result2 = sqlInterpreter.interpret("select url, count(1) as pv from " +
                "log group by url", context);
        LOGGER.info("------------" + context.out.toString());
        LOGGER.info("------------" + result2);
        waiter.assertTrue(context.out.toString().contains("url\tpv\n"));
        // Flink job is succeed when it is cancelled with save point.
        waiter.assertEquals(InterpreterResult.Code.SUCCESS, result2.code());
      } catch (Exception e) {
        LOGGER.error("Should not throw exception", e);
        waiter.fail("Should not fail here");
      }
      waiter.resume();
    });
    thread.start();

    // the streaming job will run for 60 seconds. check init_stream.scala
    // sleep 30 seconds to make sure the job is started but not finished
    Thread.sleep(30 * 1000);

    InterpreterContext context = getInterpreterContext();
    context.getLocalProperties().put(JobManager.SAVEPOINT_DIR, savePointDir.getAbsolutePath());
    context.getLocalProperties().put("parallelism", "2");
    context.getLocalProperties().put("maxParallelism", "10");
    sqlInterpreter.cancel(context);
    waiter.await(30 * 1000);

    // verify save point is generated
    String[] allSavepointPath = savePointDir.list((dir, name) -> name.startsWith("savepoint"));
    assertTrue(allSavepointPath.length > 0);

    // resume job from savepoint
    context = getInterpreterContext();
    context.getLocalProperties().put(JobManager.SAVEPOINT_DIR, savePointDir.getAbsolutePath());
    context.getLocalProperties().put("parallelism", "2");
    context.getLocalProperties().put("maxParallelism", "10");
    sqlInterpreter.interpret("select url, count(1) as pv from " +
            "log group by url", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertEquals(resultMessages.toString(), "url\tpv\n" +
                    "home\t10\n" +
                    "product\t30\n" +
                    "search\t20\n",
            resultMessages.get(0).getData());
  }

  @Test
  public void testResumeStreamSqlFromExistSavePointPath() throws IOException, InterpreterException, InterruptedException, TimeoutException {
    if (!flinkInterpreter.getFlinkShims().getFlinkVersion().isAfterFlink114()) {
      LOGGER.info("Skip testResumeStreamSqlFromSavePointPath, because this test is only passed after Flink 1.14 due to FLINK-23654");
      // By default, this thread pool in Flink JobManager is the number of cpu cores. While the cpu cores in github action container is too small
      return;
    }

    String initStreamScalaScript = getInitStreamScript(1000);
    InterpreterResult result = flinkInterpreter.interpret(initStreamScalaScript,
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    File savePointDir = Files.createTempDirectory("zeppelin-flink").toFile();
    final Waiter waiter = new Waiter();
    Thread thread = new Thread(() -> {
      try {
        InterpreterContext context = getInterpreterContext();
        context.getLocalProperties().put(JobManager.SAVEPOINT_DIR, savePointDir.getAbsolutePath());
        context.getLocalProperties().put("parallelism", "1");
        context.getLocalProperties().put("maxParallelism", "10");
        InterpreterResult result2 = sqlInterpreter.interpret("select url, count(1) as pv from " +
                "log group by url", context);
        LOGGER.info("------------" + context.out.toString());
        LOGGER.info("------------" + result2);
        waiter.assertTrue(context.out.toString().contains("url\tpv\n"));
        // Flink job is succeed when it is cancelled with save point.
        waiter.assertEquals(InterpreterResult.Code.SUCCESS, result2.code());
      } catch (Exception e) {
        LOGGER.error("Should not throw exception", e);
        waiter.fail("Should not fail here");
      }
      waiter.resume();
    });
    thread.start();

    // the streaming job will run for 60 seconds. check init_stream.scala
    // sleep 30 seconds to make sure the job is started but not finished
    Thread.sleep(30 * 1000);

    InterpreterContext context = getInterpreterContext();
    context.getLocalProperties().put(JobManager.SAVEPOINT_DIR, savePointDir.getAbsolutePath());
    context.getLocalProperties().put("parallelism", "2");
    context.getLocalProperties().put("maxParallelism", "10");
    sqlInterpreter.cancel(context);
    waiter.await(30 * 1000);

    // get exist savepoint path from tempDirectory
    // if dir more than 1 then get first or throw error
    String[] allSavepointPath = savePointDir.list((dir, name) -> name.startsWith("savepoint"));
    assertTrue(allSavepointPath.length > 0);

    String savepointPath = savePointDir.getAbsolutePath().concat(File.separator).concat(allSavepointPath[0]);

    // resume job from exist savepointPath
    context = getInterpreterContext();
    context.getConfig().put(JobManager.SAVEPOINT_PATH,savepointPath);
    context.getLocalProperties().put("parallelism", "2");
    context.getLocalProperties().put("maxParallelism", "10");
    sqlInterpreter.interpret("select url, count(1) as pv from " +
            "log group by url", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertEquals(resultMessages.toString(), "url\tpv\n" +
                    "home\t10\n" +
                    "product\t30\n" +
                    "search\t20\n",
            resultMessages.get(0).getData());
  }

  @Test
  public void testResumeStreamSqlFromInvalidSavePointPath() throws IOException, InterpreterException, InterruptedException, TimeoutException {
    if (!flinkInterpreter.getFlinkShims().getFlinkVersion().isAfterFlink114()) {
      LOGGER.info("Skip testResumeStreamSqlFromSavePointPath, because this test is only passed after Flink 1.14 due to FLINK-23654");
      // By default, this thread pool in Flink JobManager is the number of cpu cores. While the cpu cores in github action container is too small
      return;
    }

    String initStreamScalaScript = getInitStreamScript(1000);
    InterpreterResult result = flinkInterpreter.interpret(initStreamScalaScript,
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    InterpreterContext context = getInterpreterContext();
    context.getLocalProperties().put("parallelism", "1");
    context.getLocalProperties().put("maxParallelism", "10");
    context.getLocalProperties().put(JobManager.RESUME_FROM_SAVEPOINT, "true");
    context.getConfig().put(JobManager.SAVEPOINT_PATH, "/invalid_savepoint");
    
    result = sqlInterpreter.interpret("select url, count(1) as pv from " +
            "log group by url", context);

    assertEquals(InterpreterResult.Code.ERROR, result.code());
    assertTrue(context.out.toString(), context.out.toString().contains("Cannot find checkpoint or savepoint"));
  }

  @Test
  public void testStreamUDF() throws IOException, InterpreterException {
    String initStreamScalaScript = getInitStreamScript(100);
    InterpreterResult result = flinkInterpreter.interpret(initStreamScalaScript,
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    result = flinkInterpreter.interpret(
            "class MyUpper extends ScalarFunction {\n" +
                    "  def eval(a: String): String = a.toUpperCase()\n" +
                    "}\n" + "stenv.registerFunction(\"myupper\", new MyUpper())", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    InterpreterContext context = getInterpreterContext();
    result = sqlInterpreter.interpret("select myupper(url), count(1) as pv from " +
            "log group by url", context);
    assertEquals(context.out.toString(), InterpreterResult.Code.SUCCESS, result.code());
    assertTrue(context.out.toString(), !context.out.toInterpreterResultMessage().isEmpty());
    assertEquals(InterpreterResult.Type.TABLE,
            context.out.toInterpreterResultMessage().get(0).getType());
    assertTrue(context.out.toInterpreterResultMessage().get(0).getData(),
            !context.out.toInterpreterResultMessage().get(0).getData().isEmpty());
  }

  @Test
  public void testInsertInto() throws InterpreterException, IOException {
    hiveShell.execute("create table source_table (id int, name string)");

    File destDir = Files.createTempDirectory("flink_test").toFile();
    FileUtils.deleteDirectory(destDir);
    InterpreterResult result = sqlInterpreter.interpret(
            "CREATE TABLE dest_table (\n" +
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

    result = sqlInterpreter.interpret(
            "insert into dest_table select * from source_table",
            getInterpreterContext());

    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    // after these select queries, `show tables` should still show only one source table,
    // other temporary tables should not be displayed.
    InterpreterContext context = getInterpreterContext();
    result = sqlInterpreter.interpret("show tables", context);
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(1, resultMessages.size());
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertEquals(resultMessages.get(0).toString(),
            "table\ndest_table\nsource_table\n", resultMessages.get(0).getData());
  }

  @Test
  public void testMultipleInsertIntoSeparately() throws InterpreterException, IOException {
    hiveShell.execute("create table source_table (id int, name string)");
    hiveShell.execute("insert into source_table values(1, 'name')");

    File destDir = Files.createTempDirectory("flink_test").toFile();
    FileUtils.deleteDirectory(destDir);
    InterpreterResult result = sqlInterpreter.interpret(
            "CREATE TABLE dest_table (\n" +
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
            "CREATE TABLE dest_table2 (\n" +
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

    InterpreterContext context = getInterpreterContext();
    result = sqlInterpreter.interpret(
            "insert into dest_table select * from source_table;\n" +
                    "insert into dest_table2 select * from source_table",
            context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    // two  flink jobs are executed
    assertEquals(2, flinkJobListener.getJobCount());

    // check dest_table
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("select count(1) as c from dest_table", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals("c\n1\n", context.out.toString());

    // check dest_table2
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("select count(1) as c from dest_table2", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals("c\n1\n", context.out.toString());
  }

  @Test
  public void testMultipleInsertIntoRunAsOne() throws InterpreterException, IOException {
    hiveShell.execute("create table source_table (id int, name string)");
    hiveShell.execute("insert into source_table values(1, 'name')");

    File destDir = Files.createTempDirectory("flink_test").toFile();
    FileUtils.deleteDirectory(destDir);
    InterpreterResult result = sqlInterpreter.interpret(
            "CREATE TABLE dest_table (\n" +
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
            "CREATE TABLE dest_table2 (\n" +
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

    InterpreterContext context = getInterpreterContext();
    context.getLocalProperties().put("runAsOne", "true");
    result = sqlInterpreter.interpret(
            "insert into dest_table select * from source_table;insert into dest_table2 select * from source_table",
            context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    // only one flink job is executed
    assertEquals(1, flinkJobListener.getJobCount());

    // check dest_table
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("select count(1) as c from dest_table", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals("c\n1\n", context.out.toString());
    assertEquals(2, flinkJobListener.getJobCount());

    // check dest_table2
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("select count(1) as c from dest_table2", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals("c\n1\n", context.out.toString());
    assertEquals(3, flinkJobListener.getJobCount());

    // runAsOne won't affect the select statement.
    context = getInterpreterContext();
    context.getLocalProperties().put("runAsOne", "true");
    result = sqlInterpreter.interpret(
            "select 1 as a",
            context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals("a\n1\n", context.out.toString());
    assertEquals(4, flinkJobListener.getJobCount());
  }

  @Test
  public void testStatementSet() throws IOException, InterpreterException {
    if (flinkInterpreter.getFlinkVersion().getMinorVersion() == 12) {
      LOGGER.warn("Skip Flink 1.12 as statement set is not supported before 1.12");
      return;
    }
    hiveShell.execute("create table source_table (id int, name string)");
    hiveShell.execute("insert into source_table values(1, 'name')");

    File destDir = Files.createTempDirectory("flink_test").toFile();
    FileUtils.deleteDirectory(destDir);
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = sqlInterpreter.interpret(
            "CREATE TABLE dest_table (\n" +
                    "id int,\n" +
                    "name string" +
                    ") WITH (\n" +
                    "'format.field-delimiter'=',',\n" +
                    "'connector.type'='filesystem',\n" +
                    "'format.derive-schema'='true',\n" +
                    "'connector.path'='" + destDir.getAbsolutePath() + "',\n" +
                    "'format.type'='csv'\n" +
                    ");", context);

    assertEquals(context.out.toString(), InterpreterResult.Code.SUCCESS, result.code());

    File destDir2 = Files.createTempDirectory("flink_test").toFile();
    FileUtils.deleteDirectory(destDir2);
    context = getInterpreterContext();
    result = sqlInterpreter.interpret(
            "CREATE TABLE dest_table2 (\n" +
                    "id int,\n" +
                    "name string" +
                    ") WITH (\n" +
                    "'format.field-delimiter'=',',\n" +
                    "'connector.type'='filesystem',\n" +
                    "'format.derive-schema'='true',\n" +
                    "'connector.path'='" + destDir2.getAbsolutePath() + "',\n" +
                    "'format.type'='csv'\n" +
                    ");", context);
    assertEquals(context.out.toString(), InterpreterResult.Code.SUCCESS, result.code());

    // insert into 2 sink tables in one statement set
    context = getInterpreterContext();
    result = sqlInterpreter.interpret(
            "begin statement set;\n" +
                    "insert into dest_table select * from source_table;\n" +
                    "insert into dest_table2 select * from source_table;\n" +
                    "end;",
            context);
    assertEquals(context.out.toString(), InterpreterResult.Code.SUCCESS, result.code());
    // only one flink job is executed
    assertEquals(1, flinkJobListener.getJobCount());

    // check dest_table
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("select count(1) as c from dest_table", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals("c\n1\n", context.out.toString());
    assertEquals(2, flinkJobListener.getJobCount());

    // check dest_table2
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("select count(1) as c from dest_table2", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals("c\n1\n", context.out.toString());
    assertEquals(3, flinkJobListener.getJobCount());
  }

  @Test
  public void testCreateTableWithWaterMark() throws InterpreterException, IOException {
    // create table
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = sqlInterpreter.interpret(
            "CREATE TABLE sink_kafka (\n" +
                    "    status  STRING,\n" +
                    "    direction STRING,\n" +
                    "    event_ts TIMESTAMP(3),\n" +
                    "    WATERMARK FOR event_ts AS event_ts - INTERVAL '5' SECOND\n" +
                    ") WITH (\n" +
                    "  'connector.type' = 'kafka',       \n" +
                    "  'connector.version' = 'universal',    \n" +
                    "  'connector.topic' = 'generated.events2',\n" +
                    "  'connector.properties.zookeeper.connect' = 'localhost:2181',\n" +
                    "  'connector.properties.bootstrap.servers' = 'localhost:9092',\n" +
                    "  'connector.properties.group.id' = 'testGroup',\n" +
                    "  'format.type'='json',\n" +
                    "  'update-mode' = 'append'\n" +
                    ")\n",
            context);
    assertEquals(context.out.toString(), InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, resultMessages.size());
    assertEquals(InterpreterResult.Type.TEXT, resultMessages.get(0).getType());
    assertEquals("Table has been created.\n", resultMessages.get(0).getData());
  }

  public static String getInitStreamScript(int sleep_interval) throws IOException {
    return IOUtils.toString(FlinkStreamSqlInterpreterTest.class.getResource("/init_stream.scala"))
            .replace("{{sleep_interval}}", sleep_interval + "");
  }
}
