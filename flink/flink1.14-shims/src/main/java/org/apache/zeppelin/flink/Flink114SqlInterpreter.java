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
import org.apache.flink.configuration.PipelineOptions;
import org.apache.flink.core.execution.JobClient;
import org.apache.flink.core.execution.JobListener;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.SqlParserException;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.internal.TableEnvironmentInternal;
import org.apache.flink.table.delegation.Parser;
import org.apache.flink.table.operations.*;
import org.apache.flink.table.operations.command.HelpOperation;
import org.apache.flink.table.operations.command.SetOperation;
import org.apache.flink.table.operations.ddl.*;
import org.apache.flink.table.utils.EncodingUtils;
import org.apache.flink.types.Row;
import org.apache.flink.util.CloseableIterator;
import org.apache.flink.util.CollectionUtil;
import org.apache.flink.util.Preconditions;
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
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static org.apache.flink.util.Preconditions.checkNotNull;
import static org.apache.flink.util.Preconditions.checkState;

public class Flink114SqlInterpreter {

  protected static final Logger LOGGER = LoggerFactory.getLogger(Flink114SqlInterpreter.class);

  private static final String CMD_DESC_DELIMITER = "\t\t";

  /**
   * SQL Client HELP command helper class.
   */
  private static final class SQLCliCommandsDescriptions {
    private int commandMaxLength;
    private final Map<String, String> commandsDescriptions;

    public SQLCliCommandsDescriptions() {
      this.commandsDescriptions = new LinkedHashMap<>();
      this.commandMaxLength = -1;
    }

    public SQLCliCommandsDescriptions commandDescription(String command, String description) {
      Preconditions.checkState(
              StringUtils.isNotBlank(command), "content of command must not be empty.");
      Preconditions.checkState(
              StringUtils.isNotBlank(description),
              "content of command's description must not be empty.");
      this.updateMaxCommandLength(command.length());
      this.commandsDescriptions.put(command, description);
      return this;
    }

    private void updateMaxCommandLength(int newLength) {
      Preconditions.checkState(newLength > 0);
      if (this.commandMaxLength < newLength) {
        this.commandMaxLength = newLength;
      }
    }

    public AttributedString build() {
      AttributedStringBuilder attributedStringBuilder = new AttributedStringBuilder();
      if (!this.commandsDescriptions.isEmpty()) {
        this.commandsDescriptions.forEach(
                (cmd, cmdDesc) -> {
                  attributedStringBuilder
                          .style(AttributedStyle.DEFAULT.bold())
                          .append(
                                  String.format(
                                          String.format("%%-%ds", commandMaxLength), cmd))
                          .append(CMD_DESC_DELIMITER)
                          .style(AttributedStyle.DEFAULT)
                          .append(cmdDesc)
                          .append('\n');
                });
      }
      return attributedStringBuilder.toAttributedString();
    }
  }

  private static final AttributedString SQL_CLI_COMMANDS_DESCRIPTIONS =
          new SQLCliCommandsDescriptions()
                  .commandDescription("HELP", "Prints the available commands.")
                  .commandDescription(
                          "SET",
                          "Sets a session configuration property. Syntax: \"SET '<key>'='<value>';\". Use \"SET;\" for listing all properties.")
                  .commandDescription(
                          "RESET",
                          "Resets a session configuration property. Syntax: \"RESET '<key>';\". Use \"RESET;\" for reset all session properties.")
                  .commandDescription(
                          "INSERT INTO",
                          "Inserts the results of a SQL SELECT query into a declared table sink.")
                  .commandDescription(
                          "INSERT OVERWRITE",
                          "Inserts the results of a SQL SELECT query into a declared table sink and overwrite existing data.")
                  .commandDescription(
                          "SELECT", "Executes a SQL SELECT query on the Flink cluster.")
                  .commandDescription(
                          "EXPLAIN",
                          "Describes the execution plan of a query or table with the given name.")
                  .commandDescription(
                          "BEGIN STATEMENT SET",
                          "Begins a statement set. Syntax: \"BEGIN STATEMENT SET;\"")
                  .commandDescription("END", "Ends a statement set. Syntax: \"END;\"")
                  // (TODO) zjffdu, ADD/REMOVE/SHOW JAR
                  .build();

  // --------------------------------------------------------------------------------------------

  public static final AttributedString MESSAGE_HELP =
          new AttributedStringBuilder()
                  .append("The following commands are available:\n\n")
                  .append(SQL_CLI_COMMANDS_DESCRIPTIONS)
                  .style(AttributedStyle.DEFAULT.underline())
                  .append("\nHint")
                  .style(AttributedStyle.DEFAULT)
                  .append(
                          ": Make sure that a statement ends with \";\" for finalizing (multi-line) statements.")
                  // About Documentation Link.
                  .style(AttributedStyle.DEFAULT)
                  .append(
                          "\nYou can also type any Flink SQL statement, please visit https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/table/sql/overview/ for more details.")
                  .toAttributedString();

  private static final String MESSAGE_NO_STATEMENT_IN_STATEMENT_SET = "No statement in the statement set, skip submit.";

  private FlinkSqlContext flinkSqlContext;
  private TableEnvironment tbenv;
  private ZeppelinContext z;
  private Parser sqlParser;
  private SqlSplitter sqlSplitter;
  // paragraphId -> list of ModifyOperation, used for statement set in 2 syntax:
  // 1. runAsOne= true
  // 2. begin statement set;
  //    ...
  //    end;
  private Map<String, List<ModifyOperation>> statementOperationsMap = new HashMap<>();
  private boolean isBatch;
  private ReentrantReadWriteLock.WriteLock lock = new ReentrantReadWriteLock().writeLock();


  public Flink114SqlInterpreter(FlinkSqlContext flinkSqlContext, boolean isBatch) {
    this.flinkSqlContext = flinkSqlContext;
    this.isBatch = isBatch;
    if (isBatch) {
      this.tbenv = (TableEnvironment) flinkSqlContext.getBtenv();
    } else {
      this.tbenv = (TableEnvironment) flinkSqlContext.getStenv();
    }
    this.z = (ZeppelinContext) flinkSqlContext.getZeppelinContext();
    this.sqlParser = ((TableEnvironmentInternal) tbenv).getParser();
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
      if (runAsOne) {
        statementOperationsMap.put(context.getParagraphId(), new ArrayList<>());
      }

      String jobName = context.getLocalProperties().get("jobName");
      if (StringUtils.isNotBlank(jobName)) {
        tbenv.getConfig().getConfiguration().set(PipelineOptions.NAME, jobName);
      }

      List<String> sqls = sqlSplitter.splitSql(st).stream().map(String::trim).collect(Collectors.toList());
      for (String sql : sqls) {
        List<Operation> operations = null;
        try {
          operations = sqlParser.parse(sql);
        } catch (SqlParserException e) {
          context.out.write("%text Invalid Sql statement: " + sql + "\n");
          context.out.write(MESSAGE_HELP.toString());
          return new InterpreterResult(InterpreterResult.Code.ERROR, e.toString());
        }

        try {
          callOperation(sql, operations.get(0), context);
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

      if (runAsOne && !statementOperationsMap.getOrDefault(context.getParagraphId(), new ArrayList<>()).isEmpty()) {
        try {
          lock.lock();
          List<ModifyOperation> modifyOperations = statementOperationsMap.getOrDefault(context.getParagraphId(), new ArrayList<>());
          if (!modifyOperations.isEmpty()) {
            callInserts(modifyOperations, context);
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
      statementOperationsMap.remove(context.getParagraphId());
    }

    return new InterpreterResult(InterpreterResult.Code.SUCCESS);
  }

  private void callOperation(String sql, Operation operation, InterpreterContext context) throws IOException {
    if (operation instanceof HelpOperation) {
      // HELP
      callHelp(context);
    } else if (operation instanceof SetOperation) {
      // SET
      callSet((SetOperation) operation, context);
    } else if (operation instanceof CatalogSinkModifyOperation) {
      // INSERT INTO/OVERWRITE
      callInsert((CatalogSinkModifyOperation) operation, context);
    } else if (operation instanceof QueryOperation) {
      // SELECT
      callSelect(sql, (QueryOperation) operation, context);
    } else if (operation instanceof ExplainOperation) {
      // EXPLAIN
      callExplain((ExplainOperation) operation, context);
    } else if (operation instanceof BeginStatementSetOperation) {
      // BEGIN STATEMENT SET
      callBeginStatementSet(context);
    } else if (operation instanceof EndStatementSetOperation) {
      // END
      callEndStatementSet(context);
    } else if (operation instanceof ShowCreateTableOperation) {
      // SHOW CREATE TABLE
      callShowCreateTable((ShowCreateTableOperation) operation, context);
    } else if (operation instanceof ShowCatalogsOperation) {
      callShowCatalogs(context);
    } else if (operation instanceof ShowCurrentCatalogOperation) {
      callShowCurrentCatalog(context);
    } else if (operation instanceof UseCatalogOperation) {
      callUseCatalog(((UseCatalogOperation) operation).getCatalogName(), context);
    } else if (operation instanceof CreateCatalogOperation) {
      callDDL(sql, context, "Catalog has been created.");
    } else if (operation instanceof DropCatalogOperation) {
      callDDL(sql, context, "Catalog has been dropped.");
    } else if (operation instanceof UseDatabaseOperation) {
      UseDatabaseOperation useDBOperation = (UseDatabaseOperation) operation;
      callUseDatabase(useDBOperation.getDatabaseName(), context);
    } else if (operation instanceof CreateDatabaseOperation) {
      callDDL(sql, context, "Database has been created.");
    } else if (operation instanceof DropDatabaseOperation) {
      callDDL(sql, context, "Database has been removed.");
    } else if (operation instanceof AlterDatabaseOperation) {
      callDDL(sql, context, "Alter database succeeded!");
    } else if (operation instanceof ShowDatabasesOperation) {
      callShowDatabases(context);
    } else if (operation instanceof ShowCurrentDatabaseOperation) {
      callShowCurrentDatabase(context);
    } else if (operation instanceof CreateTableOperation || operation instanceof CreateTableASOperation) {
      callDDL(sql, context, "Table has been created.");
    } else if (operation instanceof AlterTableOperation) {
      callDDL(sql, context, "Alter table succeeded!");
    } else if (operation instanceof DropTableOperation) {
      callDDL(sql, context, "Table has been dropped.");
    } else if (operation instanceof DescribeTableOperation) {
      DescribeTableOperation describeTableOperation = (DescribeTableOperation) operation;
      callDescribe(describeTableOperation.getSqlIdentifier().getObjectName(), context);
    } else if (operation instanceof ShowTablesOperation) {
      callShowTables(context);
    } else if (operation instanceof CreateViewOperation) {
      callDDL(sql, context, "View has been created.");
    } else if (operation instanceof DropViewOperation) {
      callDDL(sql, context, "View has been dropped.");
    } else if (operation instanceof AlterViewOperation) {
      callDDL(sql, context, "Alter view succeeded!");
    } else if (operation instanceof CreateCatalogFunctionOperation || operation instanceof CreateTempSystemFunctionOperation) {
      callDDL(sql, context, "Function has been created.");
    } else if (operation instanceof DropCatalogFunctionOperation || operation instanceof DropTempSystemFunctionOperation) {
      callDDL(sql, context, "Function has been removed.");
    } else if (operation instanceof AlterCatalogFunctionOperation) {
      callDDL(sql, context, "Alter function succeeded!");
    } else if (operation instanceof ShowFunctionsOperation) {
      callShowFunctions(context);
    } else if (operation instanceof ShowModulesOperation) {
      callShowModules(context);
    } else if (operation instanceof ShowPartitionsOperation) {
      ShowPartitionsOperation showPartitionsOperation = (ShowPartitionsOperation) operation;
      callShowPartitions(showPartitionsOperation.asSummaryString(), context);
    } else {
      throw new IOException(operation.getClass().getName() + " is not supported");
    }
  }


  private void callHelp(InterpreterContext context) throws IOException {
    context.out.write(MESSAGE_HELP.toString() + "\n");
  }

  private void callInsert(CatalogSinkModifyOperation operation, InterpreterContext context) throws IOException {
    if (statementOperationsMap.containsKey(context.getParagraphId())) {
      List<ModifyOperation> modifyOperations = statementOperationsMap.get(context.getParagraphId());
      modifyOperations.add(operation);
    } else {
      callInserts(Collections.singletonList(operation), context);
    }
  }

  private void callInserts(List<ModifyOperation> operations, InterpreterContext context) throws IOException {
    if (!isBatch) {
      context.getLocalProperties().put("flink.streaming.insert_into", "true");
    }
    TableResult tableResult = ((TableEnvironmentInternal) tbenv).executeInternal(operations);
    checkState(tableResult.getJobClient().isPresent());
    try {
      tableResult.await();
      JobClient jobClient = tableResult.getJobClient().get();
      if (jobClient.getJobStatus().get() == JobStatus.FINISHED) {
        context.out.write("Insertion successfully.\n");
      } else {
        throw new IOException("Job is failed, " + jobClient.getJobExecutionResult().get().toString());
      }
    } catch (InterruptedException e) {
      throw new IOException("Flink job is interrupted", e);
    } catch (ExecutionException e) {
      throw new IOException("Flink job is failed", e);
    }
  }

  private void callShowCreateTable(ShowCreateTableOperation showCreateTableOperation, InterpreterContext context) throws IOException {
    try {
      lock.lock();
      TableResult tableResult = ((TableEnvironmentInternal) tbenv).executeInternal(showCreateTableOperation);
      String explanation =
              Objects.requireNonNull(tableResult.collect().next().getField(0)).toString();
      context.out.write(explanation + "\n");
    } finally {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
  }

  private void callExplain(ExplainOperation explainOperation, InterpreterContext context) throws IOException {
    try {
      lock.lock();
      TableResult tableResult = ((TableEnvironmentInternal) tbenv).executeInternal(explainOperation);
      String explanation =
              Objects.requireNonNull(tableResult.collect().next().getField(0)).toString();
      context.out.write(explanation + "\n");
    } finally {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
  }

  public void callSelect(String sql, QueryOperation queryOperation, InterpreterContext context) throws IOException {
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

  public void callSet(SetOperation setOperation, InterpreterContext context) throws IOException {
    if (setOperation.getKey().isPresent() && setOperation.getValue().isPresent()) {
      // set a property
      String key = setOperation.getKey().get().trim();
      String value = setOperation.getValue().get().trim();
      this.tbenv.getConfig().getConfiguration().setString(key, value);
      LOGGER.info("Set table config: {}={}", key, value);
    } else {
      // show all properties
      final Map<String, String> properties = this.tbenv.getConfig().getConfiguration().toMap();
      List<String> prettyEntries = new ArrayList<>();
      for (String key : properties.keySet()) {
        prettyEntries.add(
                String.format(
                        "'%s' = '%s'",
                        EncodingUtils.escapeSingleQuotes(key),
                        EncodingUtils.escapeSingleQuotes(properties.get(key))));
      }
      prettyEntries.sort(String::compareTo);
      prettyEntries.forEach(entry -> {
        try {
          context.out.write(entry + "\n");
        } catch (IOException e) {
          LOGGER.warn("Fail to write output", e);
        }
      });
    }
  }

  private void callBeginStatementSet(InterpreterContext context) throws IOException {
    statementOperationsMap.put(context.getParagraphId(), new ArrayList<>());
  }

  private void callEndStatementSet(InterpreterContext context) throws IOException {
    List<ModifyOperation> modifyOperations = statementOperationsMap.get(context.getParagraphId());
    if (modifyOperations != null && !modifyOperations.isEmpty()) {
      callInserts(modifyOperations, context);
    } else {
      context.out.write(MESSAGE_NO_STATEMENT_IN_STATEMENT_SET);
    }
  }

  private void callUseCatalog(String catalog, InterpreterContext context) throws IOException {
    tbenv.executeSql("USE CATALOG `" + catalog + "`");
  }

  private void callUseDatabase(String databaseName,
                               InterpreterContext context) throws IOException {
    this.tbenv.executeSql("USE `" + databaseName + "`");
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
    String catalog = tableResult.collect().next().getField(0).toString();
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
    String database = tableResult.collect().next().getField(0).toString();
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
    List<String> partions = CollectionUtil.iteratorToList(tableResult.collect()).stream()
            .map(r -> checkNotNull(r.getField(0)).toString())
            .collect(Collectors.toList());
    context.out.write(
            "%table partitions\n" + StringUtils.join(partions, "\n") + "\n");
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
}
