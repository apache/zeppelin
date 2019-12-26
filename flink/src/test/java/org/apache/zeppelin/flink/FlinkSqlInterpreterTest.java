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
//import com.klarna.hiverunner.HiveShell;
//import com.klarna.hiverunner.annotations.HiveSQL;
import org.apache.commons.io.IOUtils;
//import org.apache.flink.connectors.hive.FlinkStandaloneHiveRunner;
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
import org.apache.zeppelin.interpreter.InterpreterOutputListener;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessageOutput;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.junit.After;
import org.junit.Before;
//import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
//import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;


//@RunWith(FlinkStandaloneHiveRunner.class)
public abstract class FlinkSqlInterpreterTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(FlinkSqlInterpreterTest.class);
  protected static final String[][] INPUT_DATA = {
          {"1", "1.1", "hello world", "true"},
          {"2", "2.3", "hello flink", "true"},
          {"3", "3.2", "hello hadoop", "false"},
  };


  protected FlinkInterpreter flinkInterpreter;
  protected FlinkSqlInterrpeter sqlInterpreter;

  // catch the streaming appendOutput in onAppend
  protected volatile String appendOutput = "";
  protected volatile InterpreterResult.Type appendOutputType;

  // catch the flinkInterpreter appendOutput in onUpdate
  protected InterpreterResultMessageOutput updatedOutput;

  //  @HiveSQL(files = {})
  //  protected static HiveShell hiveShell;


  protected Properties getFlinkProperties() throws IOException {
    Properties p = new Properties();
    p.setProperty("zeppelin.flink.enableHive", "false");
    p.setProperty("zeppelin.flink.planner", "blink");
    p.setProperty("taskmanager.managed.memory.size", "32");
    p.setProperty("zeppelin.flink.hive.version", "2.3.4");
    File hiveConfDir = Files.createTempDir();
    //    hiveShell.getHiveConf().writeXml(new FileWriter(new File(hiveConfDir, "hive-site.xml")));
    p.setProperty("HIVE_CONF_DIR", hiveConfDir.getAbsolutePath());
    return p;
  }

  @Before
  public void setUp() throws InterpreterException, IOException {
    Properties p = getFlinkProperties();
    flinkInterpreter = new FlinkInterpreter(p);
    sqlInterpreter = createFlinkSqlInterpreter(p);
    InterpreterGroup intpGroup = new InterpreterGroup();
    flinkInterpreter.setInterpreterGroup(intpGroup);
    sqlInterpreter.setInterpreterGroup(intpGroup);
    intpGroup.addInterpreterToSession(flinkInterpreter, "session_1");
    intpGroup.addInterpreterToSession(sqlInterpreter, "session_1");

    flinkInterpreter.open();
    sqlInterpreter.open();

    //    hiveShell.execute("drop database if exists test_db CASCADE");
    //    hiveShell.execute("create database test_db");
    //    hiveShell.execute("use test_db");

    //    InterpreterResult result = sqlInterpreter.interpret("use database test_db",
    //            getInterpreterContext());
    //    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
  }

  @After
  public void tearDown() throws InterpreterException {
    flinkInterpreter.close();
  }

  protected abstract FlinkSqlInterrpeter createFlinkSqlInterpreter(Properties properties);

  //@Test
  public void testDatabases() throws InterpreterException {
    InterpreterResult result = sqlInterpreter.interpret("show databases",
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(InterpreterResult.Type.TABLE, appendOutputType);
    assertEquals("database\ndefault\ntest_db\n", appendOutput);

    result = sqlInterpreter.interpret("create database db1",
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(InterpreterResult.Type.TEXT, appendOutputType);
    assertEquals("Database has been created.\n", appendOutput);

    result = sqlInterpreter.interpret("use db1",
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    result = sqlInterpreter.interpret("show tables",
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(InterpreterResult.Type.TABLE, appendOutputType);
    assertEquals("table\n", appendOutput);

    result = sqlInterpreter.interpret(
            "CREATE TABLE source (msg INT) with (type='csv', path='/tmp')",
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    result = sqlInterpreter.interpret("show tables",
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(InterpreterResult.Type.TABLE, appendOutputType);
    assertEquals("table\nsource\n", appendOutput);

    result = sqlInterpreter.interpret("use `default`",
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    result = sqlInterpreter.interpret("show tables",
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(InterpreterResult.Type.TABLE, appendOutputType);
    assertEquals("table\n", appendOutput);

    result = sqlInterpreter.interpret("drop database db1",
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    assertTrue(result.message().get(0).getData(),
            result.message().get(0).getData().contains("Database db1 is not empty"));

    result = sqlInterpreter.interpret("drop table db1.source",
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    result = sqlInterpreter.interpret("drop database db1",
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    result = sqlInterpreter.interpret("show databases",
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(InterpreterResult.Type.TABLE, appendOutputType);
    assertEquals("database\ndefault\ntest_db\n", appendOutput);
  }

  //@Test
  public void testDescribe() throws InterpreterException {
    InterpreterResult result = sqlInterpreter.interpret("create database hive.db1",
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(InterpreterResult.Type.TEXT, appendOutputType);
    assertEquals("Database has been created.\n", appendOutput);

    result = sqlInterpreter.interpret("describe database hive.db1", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(InterpreterResult.Type.TEXT, appendOutputType);
    assertTrue(appendOutput, appendOutput.contains("db1"));

    //TODO(zjffdu) hive and flink share the same namespace for db.
    //    result = sqlInterpreter.interpret("create database flink.db1",
    //            getInterpreterContext());
    //    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    //    assertEquals(InterpreterResult.Type.TEXT, outputType);
    //    assertEquals("Database has been created.\n", output);
    //
    //    result = sqlInterpreter.interpret("describe database flink.db1",
    // getInterpreterContext());
    //    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    //    assertEquals(InterpreterResult.Type.TEXT, outputType);
    //    assertTrue(output, output.contains("db1"));

    result = sqlInterpreter.interpret(
            "CREATE TABLE source (int_col INT, double_col double, varchar_col varchar, " +
                    "bool_col boolean) with (type='csv', path='/tmp')",
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    // TODO(zjffdu) this is bug of calcite, that table name should be
    // quoted with single quote if it is keyword
    result = sqlInterpreter.interpret("describe `source`", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(InterpreterResult.Type.TEXT, appendOutputType);
    assertTrue(appendOutput, appendOutput.contains("name: int_col"));
  }

  protected InterpreterContext getInterpreterContext() {
    appendOutput = "";
    InterpreterContext context = InterpreterContext.builder()
            .setInterpreterOut(new InterpreterOutput(null))
            .setAngularObjectRegistry(new AngularObjectRegistry("flink", null))
            .setIntpEventClient(mock(RemoteInterpreterEventClient.class))
            .build();
    context.out = new InterpreterOutput(
        new InterpreterOutputListener() {
          @Override
          public void onUpdateAll(InterpreterOutput out) {
            System.out.println();
          }

          @Override
          public void onAppend(int index, InterpreterResultMessageOutput out, byte[] line) {
            try {
              appendOutputType = out.toInterpreterResultMessage().getType();
              appendOutput = out.toInterpreterResultMessage().getData();
            } catch (IOException e) {
              e.printStackTrace();
            }
          }

          @Override
          public void onUpdate(int index, InterpreterResultMessageOutput out) {
              updatedOutput = out;
            }
        });
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
