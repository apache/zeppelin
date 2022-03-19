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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.JobStatus;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.core.execution.JobClient;
import org.apache.flink.core.execution.JobListener;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.StatementSet;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.internal.TableEnvironmentInternal;
import org.apache.flink.table.utils.EncodingUtils;
import org.apache.flink.types.Row;
import org.apache.flink.util.CloseableIterator;
import org.apache.flink.util.CollectionUtil;
import org.apache.zeppelin.flink.shims112.SqlCommandParser;
import org.apache.zeppelin.flink.shims112.SqlCommandParser.SqlCommand;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.ZeppelinContext;
import org.apache.zeppelin.interpreter.util.SqlSplitter;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static org.apache.flink.util.Preconditions.checkNotNull;

public class Flink112SqlInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(Flink112SqlInterpreter.class);
  private static final AttributedString MESSAGE_HELP =
          new AttributedStringBuilder()
                  .append("The following commands are available:\n\n")
                  .append(
                          formatCommand(
                                  SqlCommand.CREATE_TABLE,
                                  "Create table under current catalog and database."))
                  .append(
                          formatCommand(
                                  SqlCommand.DROP_TABLE,
                                  "Drop table with optional catalog and database. Syntax: 'DROP TABLE [IF EXISTS] <name>;'"))
                  .append(
                          formatCommand(
                                  SqlCommand.CREATE_VIEW,
                                  "Creates a virtual table from a SQL query. Syntax: 'CREATE VIEW <name> AS <query>;'"))
                  .append(
                          formatCommand(
                                  SqlCommand.DESCRIBE,
                                  "Describes the schema of a table with the given name."))
                  .append(
                          formatCommand(
                                  SqlCommand.DROP_VIEW,
                                  "Deletes a previously created virtual table. Syntax: 'DROP VIEW <name>;'"))
                  .append(
                          formatCommand(
                                  SqlCommand.EXPLAIN,
                                  "Describes the execution plan of a query or table with the given name."))
                  .append(formatCommand(SqlCommand.HELP, "Prints the available commands."))
                  .append(
                          formatCommand(
                                  SqlCommand.INSERT_INTO,
                                  "Inserts the results of a SQL SELECT query into a declared table sink."))
                  .append(
                          formatCommand(
                                  SqlCommand.INSERT_OVERWRITE,
                                  "Inserts the results of a SQL SELECT query into a declared table sink and overwrite existing data."))
                  .append(
                          formatCommand(
                                  SqlCommand.SELECT,
                                  "Executes a SQL SELECT query on the Flink cluster."))
                  .append(
                          formatCommand(
                                  SqlCommand.SET,
                                  "Sets a session configuration property. Syntax: 'SET <key>=<value>;'. Use 'SET;' for listing all properties."))
                  .append(
                          formatCommand(
                                  SqlCommand.SHOW_FUNCTIONS,
                                  "Shows all user-defined and built-in functions."))
                  .append(formatCommand(SqlCommand.SHOW_TABLES, "Shows all registered tables."))
                  .append(
                          formatCommand(
                                  SqlCommand.USE_CATALOG,
                                  "Sets the current catalog. The current database is set to the catalog's default one. Experimental! Syntax: 'USE CATALOG <name>;'"))
                  .append(
                          formatCommand(
                                  SqlCommand.USE,
                                  "Sets the current default database. Experimental! Syntax: 'USE <name>;'"))
                  .style(AttributedStyle.DEFAULT.underline())
                  .append("\nHint")
                  .style(AttributedStyle.DEFAULT)
                  .append(
                          ": Make sure that a statement ends with ';' for finalizing (multi-line) statements.")
                  .toAttributedString();

  private static AttributedString formatCommand(SqlCommandParser.SqlCommand cmd, String description) {
    return new AttributedStringBuilder()
            .style(AttributedStyle.DEFAULT.bold())
            .append(cmd.toString())
            .append("\t\t")
            .style(AttributedStyle.DEFAULT)
            .append(description)
            .append('\n')
            .toAttributedString();
  }

  private FlinkSqlContext flinkSqlContext;
  private TableEnvironment tbenv;
  private ZeppelinContext z;
  private SqlCommandParser sqlCommandParser;
  private SqlSplitter sqlSplitter;
  private boolean isBatch;
  private ReentrantReadWriteLock.WriteLock lock = new ReentrantReadWriteLock().writeLock();
  // paragraphId -> StatementSet
  private Map<String, StatementSet> statementSetMap = new ConcurrentHashMap<>();


  public Flink112SqlInterpreter(FlinkSqlContext flinkSqlContext, boolean isBatch) {
    this.flinkSqlContext = flinkSqlContext;
    this.isBatch = isBatch;
    if (isBatch) {
      this.tbenv = (TableEnvironment) flinkSqlContext.getBtenv();
    } else {
      this.tbenv = (TableEnvironment) flinkSqlContext.getStenv();
    }
    this.z = (ZeppelinContext) flinkSqlContext.getZeppelinContext();
    this.sqlCommandParser = new SqlCommandParser((TableEnvironmentInternal) tbenv);
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

    ((ExecutionEnvironment) flinkSqlContext.getBenv()).registerJobListener(jobListener);
    ((StreamExecutionEnvironment) flinkSqlContext.getSenv()).registerJobListener(jobListener);
  }

  public InterpreterResult runSqlList(String st, InterpreterContext context) {
    try {
      boolean runAsOne = Boolean.parseBoolean(context.getStringLocalProperty("runAsOne", "false"));
      List<String> sqls = sqlSplitter.splitSql(st).stream().map(String::trim).collect(Collectors.toList());
      boolean isFirstInsert = true;
      boolean hasInsert = false;
      for (String sql : sqls) {
        SqlCommandParser.SqlCommandCall sqlCommand = null;
        try {
          sqlCommand = sqlCommandParser.parse(sql);
        } catch (Exception e1) {
          try {
            context.out.write("%text Invalid Sql statement: " + sql + "\n");
            context.out.write(e1.toString());
            context.out.write(MESSAGE_HELP.toString());
          } catch (IOException e2) {
            return new InterpreterResult(InterpreterResult.Code.ERROR, e2.toString());
          }
          return new InterpreterResult(InterpreterResult.Code.ERROR);
        }

        try {
          if (sqlCommand.command == SqlCommand.INSERT_INTO ||
                  sqlCommand.command == SqlCommand.INSERT_OVERWRITE) {
            hasInsert = true;
            if (isFirstInsert && runAsOne) {
              startMultipleInsert(context);
              isFirstInsert = false;
            }
          }
          callCommand(sqlCommand, sql, context);
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

      if (runAsOne && hasInsert) {
        try {
          lock.lock();
          String jobName = context.getStringLocalProperty("jobName", st);
          if (executeMultipleInsertInto(jobName, context)) {
            context.out.write("Insertion successfully.\n");
          }
        } catch (Exception e) {
          LOGGER.error("Fail to execute sql as one job", e);
          return new InterpreterResult(InterpreterResult.Code.ERROR, ExceptionUtils.getStackTrace(e));
        } finally {
          if (lock.isHeldByCurrentThread()) {
            lock.unlock();
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("Fail to execute sql", e);
      return new InterpreterResult(InterpreterResult.Code.ERROR, ExceptionUtils.getStackTrace(e));
    } finally {
      statementSetMap.remove(context.getParagraphId());
    }

    return new InterpreterResult(InterpreterResult.Code.SUCCESS);
  }

  private void callCommand(SqlCommandParser.SqlCommandCall cmdCall,
                           String sql,
                           InterpreterContext context) throws Exception {
    switch (cmdCall.command) {
      case SET:
        callSet(cmdCall, context);
        break;
      case HELP:
        callHelp(context);
        break;
      case SHOW_CATALOGS:
        callShowCatalogs(context);
        break;
      case SHOW_CURRENT_CATALOG:
        callShowCurrentCatalog(context);
        break;
      case SHOW_DATABASES:
        callShowDatabases(context);
        break;
      case SHOW_CURRENT_DATABASE:
        callShowCurrentDatabase(context);
        break;
      case SHOW_TABLES:
        callShowTables(context);
        break;
      case SHOW_FUNCTIONS:
        callShowFunctions(context);
        break;
      case SHOW_MODULES:
        callShowModules(context);
        break;
      case SHOW_PARTITIONS:
        callShowPartitions(sql, context);
        break;
      case USE_CATALOG:
        callUseCatalog(cmdCall.operands[0], context);
        break;
      case USE:
        callUseDatabase(cmdCall.operands[0], context);
        break;
      case DESC:
      case DESCRIBE:
        callDescribe(cmdCall.operands[0], context);
        break;
      case EXPLAIN:
        callExplain(cmdCall.operands[0], context);
        break;
      case SELECT:
        callSelect(cmdCall.operands[0], context);
        break;
      case INSERT_INTO:
      case INSERT_OVERWRITE:
        callInsertInto(cmdCall.operands[0], context);
        break;
      case CREATE_TABLE:
        callDDL(sql, context, "Table has been created.");
        break;
      case DROP_TABLE:
        callDDL(sql, context, "Table has been dropped.");
        break;
      case ALTER_TABLE:
        callDDL(sql, context, "Alter table succeeded!");
        break;
      case CREATE_VIEW:
        callDDL(sql, context, "View has been created.");
        break;
      case DROP_VIEW:
        callDDL(sql, context, "View has been dropped.");
        break;
      case ALTER_VIEW:
        callDDL(sql, context, "Alter view succeeded!");
        break;
      case CREATE_FUNCTION:
        callDDL(sql, context, "Function has been created.");
        break;
      case DROP_FUNCTION:
        callDDL(sql, context, "Function has been removed.");
        break;
      case ALTER_FUNCTION:
        callDDL(sql, context, "Alter function succeeded!");
        break;
      case CREATE_DATABASE:
        callDDL(sql, context, "Database has been created.");
        break;
      case DROP_DATABASE:
        callDDL(sql, context, "Database has been removed.");
        break;
      case ALTER_DATABASE:
        callDDL(sql, context, "Alter database succeeded!");
        break;
      case CREATE_CATALOG:
        callDDL(sql, context, "Catalog has been created.");
        break;
      case DROP_CATALOG:
        callDDL(sql, context, "Catalog has been dropped.");
        break;
      default:
        throw new Exception("Unsupported command: " + cmdCall.command);
    }
  }

  private void callDDL(String sql, InterpreterContext context, String message) throws IOException {
    try {
      lock.lock();
      this.tbenv.executeSql(sql);
    } finally {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
    context.out.write(message + "\n");
  }

  private void callUseCatalog(String catalog, InterpreterContext context) throws IOException {
    tbenv.executeSql("USE CATALOG `" + catalog + "`");
  }

  private void callHelp(InterpreterContext context) throws IOException {
    context.out.write(MESSAGE_HELP.toString() + "\n");
  }

  private void callShowCatalogs(InterpreterContext context) throws IOException {
    TableResult tableResult = this.tbenv.executeSql("SHOW Catalogs");
    List<String> catalogs = CollectionUtil.iteratorToList(tableResult.collect()).stream()
            .map(r -> checkNotNull(r.getField(0)).toString())
            .collect(Collectors.toList());
    context.out.write("%table catalog\n" + StringUtils.join(catalogs, "\n") + "\n");
  }

  private void callShowCurrentCatalog(InterpreterContext context) throws IOException {
    TableResult tableResult = this.tbenv.executeSql("SHOW Current Catalog");
    String catalog = tableResult.collect().next().toString();
    context.out.write("%text current catalog: " + catalog + "\n");
  }

  private void callShowDatabases(InterpreterContext context) throws IOException {
    TableResult tableResult = this.tbenv.executeSql("SHOW Databases");
    List<String> databases = CollectionUtil.iteratorToList(tableResult.collect()).stream()
            .map(r -> checkNotNull(r.getField(0)).toString())
            .collect(Collectors.toList());
    context.out.write(
            "%table database\n" + StringUtils.join(databases, "\n") + "\n");
  }

  private void callShowCurrentDatabase(InterpreterContext context) throws IOException {
    TableResult tableResult = this.tbenv.executeSql("SHOW Current Database");
    String database = tableResult.collect().next().toString();
    context.out.write("%text current database: " + database + "\n");
  }

  private void callShowTables(InterpreterContext context) throws IOException {
    TableResult tableResult = this.tbenv.executeSql("SHOW Tables");
    List<String> tables = CollectionUtil.iteratorToList(tableResult.collect()).stream()
            .map(r -> checkNotNull(r.getField(0)).toString())
            .filter(tbl -> !tbl.startsWith("UnnamedTable"))
            .collect(Collectors.toList());
    context.out.write(
            "%table table\n" + StringUtils.join(tables, "\n") + "\n");
  }

  private void callShowFunctions(InterpreterContext context) throws IOException {
    TableResult tableResult = this.tbenv.executeSql("SHOW Functions");
    List<String> functions = CollectionUtil.iteratorToList(tableResult.collect()).stream()
            .map(r -> checkNotNull(r.getField(0)).toString())
            .collect(Collectors.toList());
    context.out.write(
            "%table function\n" + StringUtils.join(functions, "\n") + "\n");
  }

  private void callShowModules(InterpreterContext context) throws IOException {
    String[] modules = this.tbenv.listModules();
    context.out.write("%table modules\n" + StringUtils.join(modules, "\n") + "\n");
  }

  private void callShowPartitions(String sql, InterpreterContext context) throws IOException {
    TableResult tableResult = this.tbenv.executeSql(sql);
    List<String> functions = CollectionUtil.iteratorToList(tableResult.collect()).stream()
            .map(r -> checkNotNull(r.getField(0)).toString())
            .collect(Collectors.toList());
    context.out.write(
            "%table partitions\n" + StringUtils.join(functions, "\n") + "\n");
  }

  public void startMultipleInsert(InterpreterContext context) throws Exception {
    StatementSet statementSet = tbenv.createStatementSet();
    statementSetMap.put(context.getParagraphId(), statementSet);
  }

  public void addInsertStatement(String sql, InterpreterContext context) throws Exception {
    statementSetMap.get(context.getParagraphId()).addInsertSql(sql);
  }

  public boolean executeMultipleInsertInto(String jobName, InterpreterContext context) throws Exception {
    JobClient jobClient = statementSetMap.get(context.getParagraphId()).execute().getJobClient().get();
    while (!jobClient.getJobStatus().get().isTerminalState()) {
      LOGGER.debug("Wait for job to finish");
      Thread.sleep(1000 * 5);
    }
    if (jobClient.getJobStatus().get() == JobStatus.CANCELED) {
      context.out.write("Job is cancelled.\n");
      return false;
    }
    return true;
  }

  private void callUseDatabase(String databaseName,
                               InterpreterContext context) throws IOException {
    this.tbenv.executeSql("USE `" + databaseName + "`");
  }

  private void callDescribe(String name, InterpreterContext context) throws IOException {
    TableResult tableResult = null;
    try {
      tableResult = tbenv.executeSql("DESCRIBE " + name);
    } catch (Exception e) {
      throw new IOException("Fail to describe table: " + name, e);
    }
    CloseableIterator<Row> result = tableResult.collect();
    StringBuilder builder = new StringBuilder();
    builder.append("Column\tType\n");
    while (result.hasNext()) {
      Row row = result.next();
      builder.append(row.getField(0) + "\t" + row.getField(1) + "\n");
    }
    context.out.write("%table\n" + builder.toString());
  }

  private void callExplain(String sql, InterpreterContext context) throws IOException {
    try {
      lock.lock();
      TableResult tableResult = tbenv.executeSql(sql);
      String result = tableResult.collect().next().getField(0).toString();
      context.out.write(result + "\n");
    } finally {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
  }

  public void callSelect(String sql, InterpreterContext context) throws IOException {
    try {
      lock.lock();
      if (isBatch) {
        callBatchInnerSelect(sql, context);
      } else {
        callStreamInnerSelect(sql, context);
      }
    } finally {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
  }

  public void callBatchInnerSelect(String sql, InterpreterContext context) throws IOException {
    Table table = this.tbenv.sqlQuery(sql);
    String result = z.showData(table);
    context.out.write(result);
  }

  public void callStreamInnerSelect(String sql, InterpreterContext context) throws IOException {
    flinkSqlContext.getStreamSqlSelectConsumer().accept(sql);
  }

  private String removeSingleQuote(String value) {
    value = value.trim();
    if (value.startsWith("'")) {
      value = value.substring(1);
    }
    if (value.endsWith("'")) {
      value = value.substring(0, value.length() - 1);
    }
    return value;
  }

  public void callSet(SqlCommandParser.SqlCommandCall sqlCommand, InterpreterContext context) throws Exception {
    if (sqlCommand.operands.length == 0) {
      // show all properties
      final Map<String, String> properties = this.tbenv.getConfig().getConfiguration().toMap();
      List<String> prettyEntries = new ArrayList<>();
      for (String k : properties.keySet()) {
        prettyEntries.add(
                String.format(
                        "'%s' = '%s'",
                        EncodingUtils.escapeSingleQuotes(k),
                        EncodingUtils.escapeSingleQuotes(properties.get(k))));
      }
      prettyEntries.sort(String::compareTo);
      prettyEntries.forEach(entry -> {
        try {
          context.out.write(entry + "\n");
        } catch (IOException e) {
          LOGGER.warn("Fail to write output", e);
        }
      });
    } else {
      String key = removeSingleQuote(sqlCommand.operands[0]);
      String value = removeSingleQuote(sqlCommand.operands[1]);
      if ("execution.runtime-mode".equals(key)) {
        throw new UnsupportedOperationException("execution.runtime-mode is not supported to set, " +
                "you can use %flink.ssql & %flink.bsql to switch between streaming mode and batch mode");
      }
      LOGGER.info("Set table config: {}={}", key, value);
      this.tbenv.getConfig().getConfiguration().setString(key, value);
    }
  }

  public void callInsertInto(String sql,
                             InterpreterContext context) throws IOException {
    if (!isBatch) {
      context.getLocalProperties().put("flink.streaming.insert_into", "true");
    }
    try {
      lock.lock();
      boolean runAsOne = Boolean.parseBoolean(context.getStringLocalProperty("runAsOne", "false"));
      if (!runAsOne) {
        this.tbenv.sqlUpdate(sql);
        String jobName = context.getStringLocalProperty("jobName", sql);
        this.tbenv.execute(jobName);
        context.out.write("Insertion successfully.\n");
      } else {
        addInsertStatement(sql, context);
      }
    } catch (Exception e) {
      throw new IOException(e);
    } finally {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
  }
}
