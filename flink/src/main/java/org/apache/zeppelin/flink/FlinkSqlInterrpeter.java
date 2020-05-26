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

import com.google.common.collect.Lists;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.configuration.ConfigOption;
import org.apache.flink.core.execution.JobClient;
import org.apache.flink.core.execution.JobListener;
import org.apache.flink.python.PythonOptions;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.api.config.ExecutionConfigOptions;
import org.apache.flink.table.api.config.OptimizerConfigOptions;
import org.apache.zeppelin.flink.sql.SqlCommandParser;
import org.apache.zeppelin.flink.sql.SqlCommandParser.SqlCommand;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.util.SqlSplitter;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public abstract class FlinkSqlInterrpeter extends Interpreter {

  protected static final Logger LOGGER = LoggerFactory.getLogger(FlinkSqlInterrpeter.class);

  public static final AttributedString MESSAGE_HELP = new AttributedStringBuilder()
          .append("The following commands are available:\n\n")
          .append(formatCommand(SqlCommand.CREATE_TABLE, "Create table under current catalog and database."))
          .append(formatCommand(SqlCommand.DROP_TABLE, "Drop table with optional catalog and database. Syntax: 'DROP TABLE [IF EXISTS] <name>;'"))
          .append(formatCommand(SqlCommand.CREATE_VIEW, "Creates a virtual table from a SQL query. Syntax: 'CREATE VIEW <name> AS <query>;'"))
          .append(formatCommand(SqlCommand.DESCRIBE, "Describes the schema of a table with the given name."))
          .append(formatCommand(SqlCommand.DROP_VIEW, "Deletes a previously created virtual table. Syntax: 'DROP VIEW <name>;'"))
          .append(formatCommand(SqlCommand.EXPLAIN, "Describes the execution plan of a query or table with the given name."))
          .append(formatCommand(SqlCommand.HELP, "Prints the available commands."))
          .append(formatCommand(SqlCommand.INSERT_INTO, "Inserts the results of a SQL SELECT query into a declared table sink."))
          .append(formatCommand(SqlCommand.INSERT_OVERWRITE, "Inserts the results of a SQL SELECT query into a declared table sink and overwrite existing data."))
          .append(formatCommand(SqlCommand.SELECT, "Executes a SQL SELECT query on the Flink cluster."))
          .append(formatCommand(SqlCommand.SET, "Sets a session configuration property. Syntax: 'SET <key>=<value>;'. Use 'SET;' for listing all properties."))
          .append(formatCommand(SqlCommand.SHOW_FUNCTIONS, "Shows all user-defined and built-in functions."))
          .append(formatCommand(SqlCommand.SHOW_TABLES, "Shows all registered tables."))
          .append(formatCommand(SqlCommand.SOURCE, "Reads a SQL SELECT query from a file and executes it on the Flink cluster."))
          .append(formatCommand(SqlCommand.USE_CATALOG, "Sets the current catalog. The current database is set to the catalog's default one. Experimental! Syntax: 'USE CATALOG <name>;'"))
          .append(formatCommand(SqlCommand.USE, "Sets the current default database. Experimental! Syntax: 'USE <name>;'"))
          .style(AttributedStyle.DEFAULT.underline())
          .append("\nHint")
          .style(AttributedStyle.DEFAULT)
          .append(": Make sure that a statement ends with ';' for finalizing (multi-line) statements.")
          .toAttributedString();

  protected FlinkInterpreter flinkInterpreter;
  protected TableEnvironment tbenv;
  private SqlSplitter sqlSplitter;
  private int defaultSqlParallelism;
  private ReentrantReadWriteLock.WriteLock lock = new ReentrantReadWriteLock().writeLock();
  // all the available sql config options. see
  // https://ci.apache.org/projects/flink/flink-docs-release-1.10/dev/table/config.html
  private Map<String, ConfigOption> tableConfigOptions;
  // represent paragraph's tableConfig
  // paragraphId --> tableConfig
  private Map<String, Map<String, String>> paragraphTableConfigMap = new HashMap<>();

  public FlinkSqlInterrpeter(Properties properties) {
    super(properties);
  }

  protected abstract boolean isBatch();

  @Override
  public void open() throws InterpreterException {
    flinkInterpreter =
            getInterpreterInTheSameSessionByClassName(FlinkInterpreter.class);
    this.sqlSplitter = new SqlSplitter();
    JobListener jobListener = new JobListener() {
      @Override
      public void onJobSubmitted(@Nullable JobClient jobClient, @Nullable Throwable throwable) {
        if (lock.isHeldByCurrentThread()) {
          lock.unlock();
          LOGGER.info("UnLock JobSubmitLock");
        }
      }

      @Override
      public void onJobExecuted(@Nullable JobExecutionResult jobExecutionResult, @Nullable Throwable throwable) {

      }
    };

    flinkInterpreter.getExecutionEnvironment().getJavaEnv().registerJobListener(jobListener);
    flinkInterpreter.getStreamExecutionEnvironment().getJavaEnv().registerJobListener(jobListener);
    this.defaultSqlParallelism = flinkInterpreter.getDefaultSqlParallelism();
    this.tableConfigOptions = extractTableConfigOptions();
  }

  private Map<String, ConfigOption> extractTableConfigOptions() {
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

  @Override
  public InterpreterResult interpret(String st,
                                     InterpreterContext context) throws InterpreterException {
    LOGGER.debug("Interpret code: " + st);
    flinkInterpreter.getZeppelinContext().setInterpreterContext(context);
    flinkInterpreter.getZeppelinContext().setNoteGui(context.getNoteGui());
    flinkInterpreter.getZeppelinContext().setGui(context.getGui());

    // set ClassLoader of current Thread to be the ClassLoader of Flink scala-shell,
    // otherwise codegen will fail to find classes defined in scala-shell
    ClassLoader originClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(flinkInterpreter.getFlinkScalaShellLoader());
      flinkInterpreter.setParallelismIfNecessary(context);
      flinkInterpreter.setSavePointIfNecessary(context);
      return runSqlList(st, context);
    } finally {
      Thread.currentThread().setContextClassLoader(originClassLoader);
    }
  }

  private InterpreterResult runSqlList(String st, InterpreterContext context) {
    // clear current paragraph's tableConfig before running any sql statements
    Map<String, String> tableConfig = paragraphTableConfigMap.getOrDefault(context.getParagraphId(), new HashMap<>());
    tableConfig.clear();
    paragraphTableConfigMap.put(context.getParagraphId(), tableConfig);

    try {
      List<String> sqls = sqlSplitter.splitSql(st);
      for (String sql : sqls) {
        Optional<SqlCommandParser.SqlCommandCall> sqlCommand = SqlCommandParser.parse(sql);
        if (!sqlCommand.isPresent()) {
          try {
            context.out.write("%text Invalid Sql statement: " + sql + "\n");
            context.out.write(MESSAGE_HELP.toString());
          } catch (IOException e) {
            return new InterpreterResult(InterpreterResult.Code.ERROR, e.toString());
          }
          return new InterpreterResult(InterpreterResult.Code.ERROR);
        }
        try {
          callCommand(sqlCommand.get(), context);
          context.out.flush();
        } catch (Throwable e) {
          LOGGER.error("Fail to run sql:" + sql, e);
          try {
            context.out.write("%text Fail to run sql command: " +
                    sql + "\n" + ExceptionUtils.getStackTrace(e) + "\n");
          } catch (IOException ex) {
            LOGGER.warn("Unexpected exception:", ex);
            return new InterpreterResult(InterpreterResult.Code.ERROR,
                    ExceptionUtils.getStackTrace(e));
          }
          return new InterpreterResult(InterpreterResult.Code.ERROR);
        }
      }

      boolean runAsOne = Boolean.parseBoolean(context.getStringLocalProperty("runAsOne", "false"));
      if (runAsOne) {
        try {
          lock.lock();
          this.tbenv.execute(st);
          context.out.write("Insertion successfully.\n");
        } catch (Exception e) {
          LOGGER.error("Fail to execute sql as one job", e);
          return new InterpreterResult(InterpreterResult.Code.ERROR, ExceptionUtils.getStackTrace(e));
        } finally {
          if (lock.isHeldByCurrentThread()) {
            lock.unlock();
          }
        }
      }
    } finally {
      // reset parallelism
      this.tbenv.getConfig().getConfiguration()
              .set(ExecutionConfigOptions.TABLE_EXEC_RESOURCE_DEFAULT_PARALLELISM,
                      defaultSqlParallelism);
      // reset table config
      for (ConfigOption configOption: tableConfigOptions.values()) {
        // some may has no default value, e.g. ExecutionConfigOptions#TABLE_EXEC_DISABLED_OPERATORS
        if (configOption.defaultValue() != null) {
          this.tbenv.getConfig().getConfiguration().set(configOption, configOption.defaultValue());
        }
      }
      this.tbenv.getConfig().getConfiguration().addAll(flinkInterpreter.getFlinkConfiguration());
    }

    return new InterpreterResult(InterpreterResult.Code.SUCCESS);
  }

  private void callCommand(SqlCommandParser.SqlCommandCall cmdCall,
                                        InterpreterContext context) throws Exception {
    switch (cmdCall.command) {
      case HELP:
        callHelp(context);
        break;
      case SHOW_CATALOGS:
        callShowCatalogs(context);
        break;
      case SHOW_DATABASES:
        callShowDatabases(context);
        break;
      case SHOW_TABLES:
        callShowTables(context);
        break;
      case SOURCE:
        callSource(cmdCall.operands[0], context);
        break;
      case SHOW_FUNCTIONS:
        callShowFunctions(context);
        break;
      case SHOW_MODULES:
        callShowModules(context);
        break;
      case USE_CATALOG:
        callUseCatalog(cmdCall.operands[0], context);
        break;
      case USE:
        callUseDatabase(cmdCall.operands[0], context);
        break;
      case DESCRIBE:
        callDescribe(cmdCall.operands[0], context);
        break;
      case EXPLAIN:
        callExplain(cmdCall.operands[0], context);
        break;
      case SELECT:
        callSelect(cmdCall.operands[0], context);
        break;
      case SET:
        callSet(cmdCall.operands[0], cmdCall.operands[1], context);
        break;
      case INSERT_INTO:
      case INSERT_OVERWRITE:
        callInsertInto(cmdCall.operands[0], context);
        break;
      case CREATE_TABLE:
        callCreateTable(cmdCall.operands[0], context);
        break;
      case DROP_TABLE:
        callDropTable(cmdCall.operands[0], context);
        break;
      case CREATE_VIEW:
        callCreateView(cmdCall.operands[0], cmdCall.operands[1], context);
        break;
      case DROP_VIEW:
        callDropView(cmdCall.operands[0], context);
        break;
      case CREATE_DATABASE:
        callCreateDatabase(cmdCall.operands[0], context);
        break;
      case DROP_DATABASE:
        callDropDatabase(cmdCall.operands[0], context);
        break;
      case ALTER_DATABASE:
        callAlterDatabase(cmdCall.operands[0], context);
        break;
      case ALTER_TABLE:
        callAlterTable(cmdCall.operands[0], context);
        break;
      default:
        throw new Exception("Unsupported command: " + cmdCall.command);
    }
  }

  private void callAlterTable(String sql, InterpreterContext context) throws IOException {
    try {
      lock.lock();
      this.tbenv.sqlUpdate(sql);
    } finally {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
    context.out.write("Table has been modified.\n");
  }

  private void callAlterDatabase(String sql, InterpreterContext context) throws IOException {
    try {
      lock.lock();
      this.tbenv.sqlUpdate(sql);
    } finally {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
    context.out.write("Database has been modified.\n");
  }

  private void callDropDatabase(String sql, InterpreterContext context) throws IOException {
    try {
      this.tbenv.sqlUpdate(sql);
    } finally {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
    context.out.write("Database has been dropped.\n");
  }

  private void callCreateDatabase(String sql, InterpreterContext context) throws IOException {
    try {
      this.tbenv.sqlUpdate(sql);
    } finally {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
    context.out.write("Database has been created.\n");
  }

  private void callDropView(String view, InterpreterContext context) throws IOException {
    this.tbenv.dropTemporaryView(view);
    context.out.write("View has been dropped.\n");
  }

  private void callCreateView(String name, String query, InterpreterContext context) throws IOException {
    try {
      lock.lock();
      this.tbenv.createTemporaryView(name, tbenv.sqlQuery(query));
    } finally {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
    context.out.write("View has been created.\n");
  }

  private void callCreateTable(String sql, InterpreterContext context) throws IOException {
    try {
      lock.lock();
      this.tbenv.sqlUpdate(sql);
    } finally {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
    context.out.write("Table has been created.\n");
  }

  private void callDropTable(String sql, InterpreterContext context) throws IOException {
    try {
      lock.lock();
      this.tbenv.sqlUpdate(sql);
    } finally {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
    context.out.write("Table has been dropped.\n");
  }

  private void callUseCatalog(String catalog, InterpreterContext context) {
    this.tbenv.useCatalog(catalog);
  }

  private void callShowModules(InterpreterContext context) throws IOException {
    String[] modules = this.tbenv.listModules();
    context.out.write("%table module\n" + StringUtils.join(modules, "\n") + "\n");
  }

  private void callHelp(InterpreterContext context) throws IOException {
    context.out.write(MESSAGE_HELP.toString());
  }

  private void callShowCatalogs(InterpreterContext context) throws IOException {
    String[] catalogs = this.tbenv.listCatalogs();
    context.out.write("%table catalog\n" + StringUtils.join(catalogs, "\n") + "\n");
  }

  private void callShowDatabases(InterpreterContext context) throws IOException {
    String[] databases = this.tbenv.listDatabases();
    context.out.write(
            "%table database\n" + StringUtils.join(databases, "\n") + "\n");
  }

  private void callShowTables(InterpreterContext context) throws IOException {
    List<String> tables =
            Lists.newArrayList(this.tbenv.listTables()).stream()
                    .filter(tbl -> !tbl.startsWith("UnnamedTable")).collect(Collectors.toList());
    context.out.write(
            "%table table\n" + StringUtils.join(tables, "\n") + "\n");
  }

  private void callSource(String sqlFile, InterpreterContext context) throws IOException {
    String sql = IOUtils.toString(new FileInputStream(sqlFile));
    runSqlList(sql, context);
  }

  private void callShowFunctions(InterpreterContext context) throws IOException {
    String[] functions = this.tbenv.listUserDefinedFunctions();
    context.out.write(
            "%table function\n" + StringUtils.join(functions, "\n") + "\n");
  }

  private void callUseDatabase(String databaseName,
                               InterpreterContext context) throws IOException {
    tbenv.useDatabase(databaseName);
  }

  private void callDescribe(String name, InterpreterContext context) throws IOException {
    TableSchema schema = tbenv.scan(name).getSchema();
    StringBuilder builder = new StringBuilder();
    builder.append("Column\tType\n");
    for (int i = 0; i < schema.getFieldCount(); ++i) {
      builder.append(schema.getFieldName(i).get() + "\t" + schema.getFieldDataType(i).get() + "\n");
    }
    context.out.write("%table\n" + builder.toString());
  }

  private void callExplain(String sql, InterpreterContext context) throws IOException {
    try {
      lock.lock();
      Table table = this.tbenv.sqlQuery(sql);
      context.out.write(this.tbenv.explain(table) + "\n");
    } finally {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
  }

  public void callSelect(String sql, InterpreterContext context) throws IOException {
    try {
      lock.lock();
      // set table config from set statement until now.
      Map<String, String> paragraphTableConfig = paragraphTableConfigMap.get(context.getParagraphId());
      for (Map.Entry<String, String> entry : paragraphTableConfig.entrySet()) {
        this.tbenv.getConfig().getConfiguration().setString(entry.getKey(), entry.getValue());
      }

      callInnerSelect(sql, context);
    } finally {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
  }

  public abstract void callInnerSelect(String sql, InterpreterContext context) throws IOException;

  public void callSet(String key, String value, InterpreterContext context) throws IOException {
    if (!tableConfigOptions.containsKey(key)) {
      throw new IOException(key + " is not a valid table/sql config, please check link: " +
              "https://ci.apache.org/projects/flink/flink-docs-release-1.10/dev/table/config.html");
    }
    paragraphTableConfigMap.get(context.getParagraphId()).put(key, value);
  }

  public void callInsertInto(String sql,
                              InterpreterContext context) throws IOException {
     if (!isBatch()) {
       context.getLocalProperties().put("flink.streaming.insert_into", "true");
     }
     try {
       lock.lock();

       // set table config from set statement until now.
       Map<String, String> paragraphTableConfig = paragraphTableConfigMap.get(context.getParagraphId());
       for (Map.Entry<String, String> entry : paragraphTableConfig.entrySet()) {
         this.tbenv.getConfig().getConfiguration().setString(entry.getKey(), entry.getValue());
       }

       this.tbenv.sqlUpdate(sql);
       boolean runAsOne = Boolean.parseBoolean(context.getStringLocalProperty("runAsOne", "false"));
       if (!runAsOne) {
         this.tbenv.execute(sql);
         context.out.write("Insertion successfully.\n");
       }
     } catch (Exception e) {
       throw new IOException(e);
     } finally {
       if (lock.isHeldByCurrentThread()) {
         lock.unlock();
       }
     }
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    this.flinkInterpreter.cancel(context);
  }

  private static AttributedString formatCommand(SqlCommand cmd, String description) {
    return new AttributedStringBuilder()
            .style(AttributedStyle.DEFAULT.bold())
            .append(cmd.toString())
            .append("\t\t")
            .style(AttributedStyle.DEFAULT)
            .append(description)
            .append('\n')
            .toAttributedString();
  }
}
