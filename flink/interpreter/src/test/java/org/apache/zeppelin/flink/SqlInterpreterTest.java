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
import com.klarna.hiverunner.HiveShell;
import com.klarna.hiverunner.annotations.HiveSQL;
import org.apache.commons.io.IOUtils;
import org.apache.flink.connectors.hive.FlinkStandaloneHiveRunner;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.orc.OrcFile;
import org.apache.orc.TypeDescription;
import org.apache.orc.Writer;
import org.apache.parquet.column.ParquetProperties;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.example.GroupWriteSupport;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.MessageTypeParser;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Properties;

import static org.apache.zeppelin.interpreter.InterpreterResult.Code;
import static org.apache.zeppelin.interpreter.InterpreterResult.Type;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;


@RunWith(FlinkStandaloneHiveRunner.class)
public abstract class SqlInterpreterTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(SqlInterpreterTest.class);
  protected static final String[][] INPUT_DATA = {
          {"1", "1.1", "hello world", "true"},
          {"2", "2.3", "hello flink", "true"},
          {"3", "3.2", "hello hadoop", "false"},
  };


  protected FlinkInterpreter flinkInterpreter;
  protected IPyFlinkInterpreter iPyFlinkInterpreter;
  protected PyFlinkInterpreter pyFlinkInterpreter;
  protected FlinkSqlInterrpeter sqlInterpreter;

  private AngularObjectRegistry angularObjectRegistry;

  @HiveSQL(files = {})
  protected static HiveShell hiveShell;


  protected Properties getFlinkProperties() throws IOException {
    Properties p = new Properties();
    p.setProperty("zeppelin.flink.enableHive", "true");
    p.setProperty("taskmanager.managed.memory.size", "32");
    p.setProperty("taskmanager.memory.task.off-heap.size", "80mb");
    p.setProperty("zeppelin.flink.hive.version", "2.3.4");
    p.setProperty("zeppelin.pyflink.useIPython", "false");
    p.setProperty("local.number-taskmanager", "4");
    File hiveConfDir = Files.createTempDir();
    hiveShell.getHiveConf().writeXml(new FileWriter(new File(hiveConfDir, "hive-site.xml")));
    p.setProperty("HIVE_CONF_DIR", hiveConfDir.getAbsolutePath());
    return p;
  }

  @Before
  public void setUp() throws InterpreterException, IOException {
    Properties p = getFlinkProperties();
    flinkInterpreter = new FlinkInterpreter(p);
    iPyFlinkInterpreter = new IPyFlinkInterpreter(p);
    pyFlinkInterpreter = new PyFlinkInterpreter(p);
    sqlInterpreter = createFlinkSqlInterpreter(p);
    InterpreterGroup intpGroup = new InterpreterGroup();
    flinkInterpreter.setInterpreterGroup(intpGroup);
    sqlInterpreter.setInterpreterGroup(intpGroup);
    iPyFlinkInterpreter.setInterpreterGroup(intpGroup);
    pyFlinkInterpreter.setInterpreterGroup(intpGroup);
    intpGroup.addInterpreterToSession(flinkInterpreter, "session_1");
    intpGroup.addInterpreterToSession(sqlInterpreter, "session_1");
    intpGroup.addInterpreterToSession(iPyFlinkInterpreter, "session_1");
    intpGroup.addInterpreterToSession(pyFlinkInterpreter, "session_1");

    angularObjectRegistry = new AngularObjectRegistry("flink", null);
    InterpreterContext.set(getInterpreterContext());
    flinkInterpreter.open();
    sqlInterpreter.open();
    iPyFlinkInterpreter.open();
    pyFlinkInterpreter.open();

    hiveShell.execute("drop database if exists test_db CASCADE");
    hiveShell.execute("create database test_db");
    hiveShell.execute("use test_db");

    InterpreterResult result = sqlInterpreter.interpret("use test_db",
            getInterpreterContext());
    assertEquals(Code.SUCCESS, result.code());
  }

  @After
  public void tearDown() throws InterpreterException {
    if (flinkInterpreter != null) {
      flinkInterpreter.close();
    }
    if (sqlInterpreter != null) {
      sqlInterpreter.close();
    }
    if (iPyFlinkInterpreter != null) {
      iPyFlinkInterpreter.close();
    }
    if (pyFlinkInterpreter != null) {
      pyFlinkInterpreter.close();
    }
  }

  protected abstract FlinkSqlInterrpeter createFlinkSqlInterpreter(Properties properties);

  @Test
  public void testDatabases() throws InterpreterException, IOException {
    // show databases
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = sqlInterpreter.interpret("show databases", context);
    assertEquals(Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, resultMessages.size());
    assertEquals(Type.TABLE, resultMessages.get(0).getType());
    assertEquals("database\ndefault\ntest_db\n", resultMessages.get(0).getData());

    // create database
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("create database db1", context);
    assertEquals(Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(Type.TEXT, resultMessages.get(0).getType());
    assertEquals("Database has been created.\n", resultMessages.get(0).getData());

    // use database
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("use db1", context);
    assertEquals(Code.SUCCESS, result.code());

    context = getInterpreterContext();
    result = sqlInterpreter.interpret("show tables", context);
    assertEquals(Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(Type.TABLE, resultMessages.get(0).getType());
    assertEquals("table\n", resultMessages.get(0).getData());

    context = getInterpreterContext();
    result = sqlInterpreter.interpret("CREATE TABLE source (msg INT)", context);
    assertEquals(Code.SUCCESS, result.code());

    context = getInterpreterContext();
    result = sqlInterpreter.interpret("show tables", context);
    assertEquals(Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(Type.TABLE, resultMessages.get(0).getType());
    assertEquals("table\nsource\n", resultMessages.get(0).getData());

    context = getInterpreterContext();
    result = sqlInterpreter.interpret("use default", context);
    assertEquals(Code.SUCCESS, result.code());

    context = getInterpreterContext();
    result = sqlInterpreter.interpret("show tables", context);
    assertEquals(Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(Type.TABLE, resultMessages.get(0).getType());
    assertEquals("table\n", resultMessages.get(0).getData());

    // fail to drop database if there's tables under this database
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("drop database db1", context);
    assertEquals(Code.ERROR, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertTrue(resultMessages.get(0).getData(),
            resultMessages.get(0).getData().contains("is not empty"));

    // drop table first then drop db
    result = sqlInterpreter.interpret("drop table db1.source",
            getInterpreterContext());
    assertEquals(Code.SUCCESS, result.code());

    result = sqlInterpreter.interpret("drop database db1",
            getInterpreterContext());
    assertEquals(Code.SUCCESS, result.code());

    // verify database is dropped
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("show databases", context);
    assertEquals(Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(Type.TABLE, resultMessages.get(0).getType());
    assertEquals("database\ndefault\ntest_db\n", resultMessages.get(0).getData());
  }

  @Test
  public void testTable() throws InterpreterException, IOException {
    // create table
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = sqlInterpreter.interpret(
            "CREATE TABLE source_table (int_col INT, double_col double, " +
                    "varchar_col varchar, bool_col boolean)",
            context);
    assertEquals(Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, resultMessages.size());
    assertEquals(Type.TEXT, resultMessages.get(0).getType());
    assertEquals("Table has been created.\n", resultMessages.get(0).getData());

    // describe table
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("describe source_table", context);
    assertEquals(Code.SUCCESS, result.code());
    assertEquals(1, resultMessages.size());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(Type.TABLE, resultMessages.get(0).getType());
    assertEquals("Column\tType\n" +
            "int_col\tINT\n" +
            "double_col\tDOUBLE\n" +
            "varchar_col\tSTRING\n" +
            "bool_col\tBOOLEAN\n"
            , resultMessages.get(0).getData());

    // describe unknown table
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("describe unknown_table", context);
    assertEquals(Code.ERROR, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, resultMessages.size());
    assertTrue(resultMessages.toString(),
            resultMessages.get(0).getData().contains("Table `unknown_table` was not found."));

    // drop unknown table
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("drop table unknown_table", context);
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(Code.ERROR, result.code());
    assertEquals(1, resultMessages.size());
    assertTrue(resultMessages.toString(),
            resultMessages.get(0).getData().contains("does not exist"));

    // drop table
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("drop table source_table", context);
    assertEquals(Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, resultMessages.size());
    assertEquals("Table has been dropped.\n", resultMessages.get(0).getData());

    // describe the dropped table
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("describe source_table", context);
    assertEquals(Code.ERROR, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, resultMessages.size());
    assertTrue(resultMessages.get(0).getData(),
            resultMessages.get(0).getData().contains("Table `source_table` was not found"));
  }

  @Test
  public void testView() throws InterpreterException, IOException {
    // create table
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = sqlInterpreter.interpret(
            "CREATE TABLE source_table (int_col INT, double_col double, " +
                    "varchar_col varchar, bool_col boolean)" +
                    " WITH (\n" +
                    "'format.field-delimiter'='\\n',\n" +
                    "'connector.type'='filesystem',\n" +
                    "'format.derive-schema'='true',\n" +
                    "'connector.path'='hdfs:///tmp/bank.csv',\n" +
                    "'format.type'='csv'\n" +
                    ");",
            context);
    assertEquals(Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, resultMessages.size());
    assertEquals(Type.TEXT, resultMessages.get(0).getType());
    assertEquals("Table has been created.\n", resultMessages.get(0).getData());

    // create view
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("create view my_view as select int_col from source_table", context);
    assertEquals(Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, resultMessages.size());
    assertEquals(Type.TEXT, resultMessages.get(0).getType());
    assertEquals("View has been created.\n", resultMessages.get(0).getData());

    // create same view again
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("create view my_view as select int_col from source_table", context);
    assertEquals(Code.ERROR, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, resultMessages.size());
    assertEquals(Type.TEXT, resultMessages.get(0).getType());
    assertTrue(resultMessages.get(0).getData(), resultMessages.get(0).getData().contains("already exists"));

    // show view
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("show tables", context);
    assertEquals(Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(Type.TABLE, resultMessages.get(0).getType());
    assertEquals("table\nmy_view\nsource_table\n", resultMessages.get(0).getData());

    // drop view
    context = getInterpreterContext();
    result = sqlInterpreter.interpret("drop view my_view", context);
    assertEquals(Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, resultMessages.size());
    assertEquals("View has been dropped.\n", resultMessages.get(0).getData());
  }

  @Test
  public void testInvalidSql() throws InterpreterException, IOException {

    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = sqlInterpreter.interpret("Invalid sql", context);
    assertEquals(Code.ERROR, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, resultMessages.size());
    assertEquals(Type.TEXT, resultMessages.get(0).getType());
    assertTrue(resultMessages.get(0).getData(),
            resultMessages.get(0).getData().contains("Invalid Sql statement: Invalid sql"));
    assertTrue(resultMessages.get(0).getData(),
            resultMessages.get(0).getData().contains("The following commands are available"));
  }


  @Test
  public void testFunction() throws IOException, InterpreterException {

    FlinkVersion flinkVersion = flinkInterpreter.getFlinkVersion();
    if(!flinkVersion.isFlink110()){
      InterpreterContext context = getInterpreterContext();

      // CREATE UDF
      InterpreterResult result = sqlInterpreter.interpret(
              "CREATE FUNCTION myudf AS 'org.apache.zeppelin.flink.JavaUpper' ;", context);
      assertEquals(context.out.toString(), InterpreterResult.Code.SUCCESS, result.code());
      List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
      assertTrue(resultMessages.toString(),resultMessages.get(0).getData().contains("Function has been created."));

      // SHOW UDF
      context = getInterpreterContext();
      result = sqlInterpreter.interpret(
              "SHOW FUNCTIONS ;", context);
      assertEquals(context.out.toString(), InterpreterResult.Code.SUCCESS, result.code());
      resultMessages = context.out.toInterpreterResultMessage();
      assertTrue(resultMessages.toString(),resultMessages.get(0).getData().contains("myudf"));


      // ALTER
      context = getInterpreterContext();
      result = sqlInterpreter.interpret(
              "ALTER FUNCTION myUDF AS 'org.apache.zeppelin.flink.JavaLower' ; ", context);
      assertEquals(context.out.toString(), InterpreterResult.Code.SUCCESS, result.code());
      resultMessages = context.out.toInterpreterResultMessage();
      assertTrue(resultMessages.toString(),resultMessages.get(0).getData().contains("Function has been modified."));


      // DROP UDF
      context = getInterpreterContext();
      result = sqlInterpreter.interpret("DROP FUNCTION myudf ;", context);
      assertEquals(context.out.toString(), InterpreterResult.Code.SUCCESS, result.code());
      resultMessages = context.out.toInterpreterResultMessage();
      assertTrue(resultMessages.toString(),resultMessages.get(0).getData().contains("Function has been dropped."));


      // SHOW UDF. Due to drop UDF before, it shouldn't contain 'myudf'
      result = sqlInterpreter.interpret(
              "SHOW FUNCTIONS ;", context);
      assertEquals(context.out.toString(), InterpreterResult.Code.SUCCESS, result.code());
      resultMessages = context.out.toInterpreterResultMessage();
      assertFalse(resultMessages.toString(), resultMessages.get(0).getData().contains("myudf"));
    } else {
      // Flink1.10 don't support ddl for function
      assertTrue(flinkVersion.isFlink110());
    }

  }

  @Test
  public void testCatalog() throws IOException, InterpreterException{
    FlinkVersion flinkVersion = flinkInterpreter.getFlinkVersion();

    if (!flinkVersion.isFlink110()){
      InterpreterContext context = getInterpreterContext();

      // CREATE CATALOG
      InterpreterResult result = sqlInterpreter.interpret(
              "CREATE CATALOG test_catalog \n" +
                      "WITH( \n" +
                      "'type'='generic_in_memory' \n" +
                      ");", context);
      assertEquals(context.out.toString(), InterpreterResult.Code.SUCCESS, result.code());
      List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
      assertTrue(resultMessages.toString(),resultMessages.get(0).getData().contains("Catalog has been created."));

      // USE CATALOG & SHOW DATABASES;
      context = getInterpreterContext();
      result = sqlInterpreter.interpret(
              "USE CATALOG test_catalog ;\n" +
                      "SHOW DATABASES;", context);
      assertEquals(context.out.toString(), InterpreterResult.Code.SUCCESS, result.code());
      resultMessages = context.out.toInterpreterResultMessage();
      assertTrue(resultMessages.toString(),resultMessages.get(0).getData().contains("default"));

      // DROP CATALOG
      context = getInterpreterContext();
      result = sqlInterpreter.interpret(
              "DROP CATALOG test_catalog ;\n", context);
      assertEquals(context.out.toString(), InterpreterResult.Code.SUCCESS, result.code());
      resultMessages = context.out.toInterpreterResultMessage();
      assertTrue(resultMessages.toString(),resultMessages.get(0).getData().contains("Catalog has been dropped."));

      // SHOW CATALOG. Due to drop CATALOG before, it shouldn't contain 'test_catalog'
      context = getInterpreterContext();
      result = sqlInterpreter.interpret(
              "SHOW CATALOGS ;\n", context);
      assertEquals(context.out.toString(), InterpreterResult.Code.SUCCESS, result.code());
      resultMessages = context.out.toInterpreterResultMessage();
      assertTrue(resultMessages.toString(),resultMessages.get(0).getData().contains("default_catalog"));
      assertFalse(resultMessages.toString(),resultMessages.get(0).getData().contains("test_catalog"));
    } else {
      // Flink1.10 don't support ddl for catalog
      assertTrue(flinkVersion.isFlink110());
    }

  }

  @Test
  public void testSetProperty() throws InterpreterException {
    FlinkVersion flinkVersion = flinkInterpreter.getFlinkVersion();

    if (!flinkVersion.isFlink110()){
      InterpreterContext context = getInterpreterContext();
      InterpreterResult result = sqlInterpreter.interpret(
              "set table.sql-dialect=hive", context);
      assertEquals(context.out.toString(), InterpreterResult.Code.SUCCESS, result.code());

    } else {
      // Flink1.10 doesn't support set table.sql-dialet which is introduced in flink 1.11
      InterpreterContext context = getInterpreterContext();
      InterpreterResult result = sqlInterpreter.interpret(
              "set table.sql-dialect=hive", context);
      assertEquals(context.out.toString(), Code.ERROR, result.code());
    }
  }

  @Test
  public void testShowModules() throws InterpreterException, IOException {
    FlinkVersion flinkVersion = flinkInterpreter.getFlinkVersion();

    if (!flinkVersion.isFlink110()) {
      InterpreterContext context = getInterpreterContext();

      // CREATE CATALOG
      InterpreterResult result = sqlInterpreter.interpret(
              "show modules", context);
      assertEquals(context.out.toString(), InterpreterResult.Code.SUCCESS, result.code());
      List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
      assertTrue(resultMessages.toString(), resultMessages.get(0).getData().contains("core"));
    } else {
      // Flink1.10 don't support show modules
      assertTrue(flinkVersion.isFlink110());
    }
  }


  protected InterpreterContext getInterpreterContext() {
    InterpreterContext context = InterpreterContext.builder()
            .setParagraphId("paragraphId")
            .setInterpreterOut(new InterpreterOutput(null))
            .setAngularObjectRegistry(angularObjectRegistry)
            .setIntpEventClient(mock(RemoteInterpreterEventClient.class))
            .setInterpreterOut(new InterpreterOutput(null))
            .build();
    InterpreterContext.set(context);
    return context;
  }

  public static File createInputFile(String data) throws IOException {
    File file = File.createTempFile("zeppelin-flink-input", ".csv");
    FileOutputStream out = null;
    try {
      out = new FileOutputStream(file);
      IOUtils.write(data, out);
    } finally {
      if (out != null) {
        out.close();
      }
    }
    return file;
  }

  public static File createInputFile(String[][] data) throws IOException {
    File file = File.createTempFile("zeppelin-flink-input", ".csv");
    PrintWriter writer = null;
    try {
      writer = new PrintWriter(new FileOutputStream(file));
      // int
      int rowCount = data.length;
      int colCount = data[0].length;
      for (int i = 0; i < rowCount; ++i) {
        for (int j = 0; j < colCount; ++j) {
          writer.print(data[i][j]);
          if (j != colCount - 1) {
            writer.print(",");
          }
        }
        // TODO(zjffdu) This is a bug of CSV Sink of Flink, it always put
        // line separator at the end
        //  which is not necessary.
        writer.print("\n");
      }
    } finally {
      if (writer != null) {
        writer.close();
      }
    }
    return file;
  }

  public File createORCFile(int[] values) throws IOException {
    File file = File.createTempFile("zeppelin-flink-input", ".orc");
    file.delete();
    Path path = new Path(file.getAbsolutePath());
    Configuration conf = new Configuration();
    conf.set("orc.compress", "snappy");
    TypeDescription schema = TypeDescription.fromString("struct<msg:int>");
    Writer writer = OrcFile.createWriter(path,
            OrcFile.writerOptions(conf)
                    .setSchema(schema));
    VectorizedRowBatch batch = schema.createRowBatch();
    LongColumnVector x = (LongColumnVector) batch.cols[0];
    for (int i = 0; i < values.length; ++i) {
      int row = batch.size++;
      x.vector[row] = values[i];
      // If the batch is full, write it out and start over.
      if (batch.size == batch.getMaxSize()) {
        writer.addRowBatch(batch);
        batch.reset();
      }
    }
    if (batch.size != 0) {
      writer.addRowBatch(batch);
      batch.reset();
    }
    writer.close();
    return file;
  }

  public File createParquetFile(int[] values,
                                ParquetProperties.WriterVersion version) throws IOException {
    File file = File.createTempFile("zeppelin-flink-input", ".par");
    file.delete();
    Path path = new Path(file.getAbsolutePath());
    Configuration conf = new Configuration();

    MessageType schema = MessageTypeParser.parseMessageType(
            "message test { "
                    + "required int32 int32_field; "
                    + "} ");
    GroupWriteSupport.setSchema(schema, conf);
    SimpleGroupFactory f = new SimpleGroupFactory(schema);

    ParquetWriter<Group> writer = new ParquetWriter<Group>(
            path,
            new GroupWriteSupport(),
            CompressionCodecName.UNCOMPRESSED, 1024, 1024, 512, true, false, version, conf);
    for (int i = 0; i < values.length; i++) {
      writer.write(f.newGroup()
              .append("int32_field", values[i]));
    }
    writer.close();
    return file;
  }
}
