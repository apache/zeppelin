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

import org.apache.commons.cli.CommandLine;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.scala.DataSet;
import org.apache.flink.client.cli.CliFrontend;
import org.apache.flink.configuration.ConfigOption;
import org.apache.flink.python.PythonOptions;
import org.apache.flink.python.util.ResourceUtil;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.TableUtils;
import org.apache.flink.table.api.config.ExecutionConfigOptions;
import org.apache.flink.table.api.config.OptimizerConfigOptions;
import org.apache.flink.table.api.java.internal.StreamTableEnvironmentImpl;
import org.apache.flink.table.api.scala.BatchTableEnvironment;
import org.apache.flink.table.catalog.CatalogManager;
import org.apache.flink.table.catalog.GenericInMemoryCatalog;
import org.apache.flink.table.functions.AggregateFunction;
import org.apache.flink.table.functions.TableAggregateFunction;
import org.apache.flink.table.functions.TableFunction;
import org.apache.flink.table.sinks.TableSink;
import org.apache.flink.types.Row;
import org.apache.zeppelin.flink.shims111.CollectStreamTableSink;
import org.apache.zeppelin.flink.shims111.Flink110ScalaShims;
import org.apache.zeppelin.flink.sql.SqlCommandParser;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;


/**
 * Shims for flink 1.10
 */
public class Flink110Shims extends FlinkShims {

  private static final Logger LOGGER = LoggerFactory.getLogger(Flink110Shims.class);
  public static final AttributedString MESSAGE_HELP = new AttributedStringBuilder()
          .append("The following commands are available:\n\n")
          .append(formatCommand(SqlCommandParser.SqlCommand.CREATE_TABLE, "Create table under current catalog and database."))
          .append(formatCommand(SqlCommandParser.SqlCommand.DROP_TABLE, "Drop table with optional catalog and database. Syntax: 'DROP TABLE [IF EXISTS] <name>;'"))
          .append(formatCommand(SqlCommandParser.SqlCommand.CREATE_VIEW, "Creates a virtual table from a SQL query. Syntax: 'CREATE VIEW <name> AS <query>;'"))
          .append(formatCommand(SqlCommandParser.SqlCommand.DESCRIBE, "Describes the schema of a table with the given name."))
          .append(formatCommand(SqlCommandParser.SqlCommand.DROP_VIEW, "Deletes a previously created virtual table. Syntax: 'DROP VIEW <name>;'"))
          .append(formatCommand(SqlCommandParser.SqlCommand.EXPLAIN, "Describes the execution plan of a query or table with the given name."))
          .append(formatCommand(SqlCommandParser.SqlCommand.HELP, "Prints the available commands."))
          .append(formatCommand(SqlCommandParser.SqlCommand.INSERT_INTO, "Inserts the results of a SQL SELECT query into a declared table sink."))
          .append(formatCommand(SqlCommandParser.SqlCommand.INSERT_OVERWRITE, "Inserts the results of a SQL SELECT query into a declared table sink and overwrite existing data."))
          .append(formatCommand(SqlCommandParser.SqlCommand.SELECT, "Executes a SQL SELECT query on the Flink cluster."))
          .append(formatCommand(SqlCommandParser.SqlCommand.SET, "Sets a session configuration property. Syntax: 'SET <key>=<value>;'. Use 'SET;' for listing all properties."))
          .append(formatCommand(SqlCommandParser.SqlCommand.SHOW_FUNCTIONS, "Shows all user-defined and built-in functions."))
          .append(formatCommand(SqlCommandParser.SqlCommand.SHOW_TABLES, "Shows all registered tables."))
          .append(formatCommand(SqlCommandParser.SqlCommand.SOURCE, "Reads a SQL SELECT query from a file and executes it on the Flink cluster."))
          .append(formatCommand(SqlCommandParser.SqlCommand.USE_CATALOG, "Sets the current catalog. The current database is set to the catalog's default one. Experimental! Syntax: 'USE CATALOG <name>;'"))
          .append(formatCommand(SqlCommandParser.SqlCommand.USE, "Sets the current default database. Experimental! Syntax: 'USE <name>;'"))
          .style(AttributedStyle.DEFAULT.underline())
          .append("\nHint")
          .style(AttributedStyle.DEFAULT)
          .append(": Make sure that a statement ends with ';' for finalizing (multi-line) statements.")
          .toAttributedString();

  public Flink110Shims(Properties properties) {
    super(properties);
  }

  @Override
  public Object createCatalogManager(Object config) {
    return new CatalogManager("default_catalog",
            new GenericInMemoryCatalog("default_catalog", "default_database"));
  }

  @Override
  public String getPyFlinkPythonPath(Properties properties) throws IOException {
    String flinkHome = System.getenv("FLINK_HOME");
    if (flinkHome != null) {
      File tmpDir = Files.createTempDirectory("zeppelin").toFile();
      List<File> depFiles = null;
      try {
        depFiles = ResourceUtil.extractBuiltInDependencies(tmpDir.getAbsolutePath(), "pyflink", true);
      } catch (InterruptedException e) {
        throw new IOException(e);
      }
      StringBuilder builder = new StringBuilder();
      for (File file : depFiles) {
        LOGGER.info("Adding extracted file to PYTHONPATH: " + file.getAbsolutePath());
        builder.append(file.getAbsolutePath() + ":");
      }
      return builder.toString();
    } else {
      throw new IOException("No FLINK_HOME is specified");
    }
  }

  @Override
  public Object getCollectStreamTableSink(InetAddress targetAddress, int targetPort, Object serializer) {
    return new CollectStreamTableSink(targetAddress, targetPort, (TypeSerializer<Tuple2<Boolean, Row >>) serializer);
  }

  @Override
  public List collectToList(Object table) throws Exception {
    return TableUtils.collectToList((Table) table);
  }

  @Override
  public void startMultipleInsert(Object tblEnv, InterpreterContext context) throws Exception {

  }

  @Override
  public void addInsertStatement(String sql, Object tblEnv, InterpreterContext context) throws Exception {
    ((TableEnvironment) tblEnv).sqlUpdate(sql);
  }

  @Override
  public boolean executeMultipleInsertInto(String jobName, Object tblEnv, InterpreterContext context) throws Exception {
    ((TableEnvironment) tblEnv).execute(jobName);
    return true;
  }

  @Override
  public boolean rowEquals(Object row1, Object row2) {
    return ((Row)row1).equals((Row) row2);
  }

  public Object fromDataSet(Object btenv, Object ds) {
    return Flink110ScalaShims.fromDataSet((BatchTableEnvironment) btenv, (DataSet) ds);
  }

  @Override
  public Object toDataSet(Object btenv, Object table) {
    return Flink110ScalaShims.toDataSet((BatchTableEnvironment) btenv, (Table) table);
  }

  @Override
  public void registerTableSink(Object stenv, String tableName, Object collectTableSink) {
    ((TableEnvironment) stenv).registerTableSink(tableName, (TableSink) collectTableSink);
  }

  @Override
  public void registerTableFunction(Object btenv, String name, Object tableFunction) {
    ((StreamTableEnvironmentImpl)(btenv)).registerFunction(name, (TableFunction) tableFunction);
  }

  @Override
  public void registerAggregateFunction(Object btenv, String name, Object aggregateFunction) {
    ((StreamTableEnvironmentImpl)(btenv)).registerFunction(name, (AggregateFunction) aggregateFunction);
  }

  @Override
  public void registerTableAggregateFunction(Object btenv, String name, Object tableAggregateFunction) {
    ((StreamTableEnvironmentImpl)(btenv)).registerFunction(name, (TableAggregateFunction) tableAggregateFunction);
  }

  @Override
  public Optional<SqlCommandParser.SqlCommandCall> parseSql(Object tableEnv, String stmt) {
    // parse
    for (SqlCommandParser.SqlCommand cmd : SqlCommandParser.SqlCommand.values()) {
      if (cmd.pattern == null){
        continue;
      }
      final Matcher matcher = cmd.pattern.matcher(stmt);
      if (matcher.matches()) {
        final String[] groups = new String[matcher.groupCount()];
        for (int i = 0; i < groups.length; i++) {
          groups[i] = matcher.group(i + 1);
        }
        final String sql = stmt;
        return cmd.operandConverter.apply(groups)
                .map((operands) -> new SqlCommandParser.SqlCommandCall(cmd, operands, sql));
      }
    }
    return Optional.empty();
  }

  @Override
  public void executeSql(Object tableEnv, String sql) {
    throw new RuntimeException("Should not be called for flink 1.10");
  }

  @Override
  public String sqlHelp() {
    return MESSAGE_HELP.toString();
  }

  @Override
  public void setCatalogManagerSchemaResolver(Object catalogManager,
                                              Object parser,
                                              Object environmentSetting) {
    // do nothing for flink 1.10
  }

  @Override
  public Object getCustomCli(Object cliFrontend, Object commandLine) {
    return ((CliFrontend)cliFrontend).getActiveCustomCommandLine((CommandLine) commandLine);
  }

  @Override
  public Map extractTableConfigOptions() {
    Map<String, ConfigOption> configOptions = new HashMap<>();
    configOptions.putAll(extractConfigOptions(ExecutionConfigOptions.class));
    configOptions.putAll(extractConfigOptions(OptimizerConfigOptions.class));
    configOptions.putAll(extractConfigOptions(PythonOptions.class));
    return configOptions;
  }

  private Map<String, ConfigOption> extractConfigOptions(Class clazz) {
    Map<String, ConfigOption> configOptions = new HashMap();
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      if (field.getType().isAssignableFrom(ConfigOption.class)) {
        try {
          ConfigOption configOption = (ConfigOption) field.get(ConfigOption.class);
          configOptions.put(configOption.key(), configOption);
        } catch (Throwable e) {
          LOGGER.warn("Fail to get ConfigOption", e);
        }
      }
    }
    return configOptions;
  }
}
