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
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.flink.api.common.JobStatus;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.scala.DataSet;
import org.apache.flink.client.cli.CliFrontend;
import org.apache.flink.client.cli.CustomCommandLine;
import org.apache.flink.configuration.ConfigOption;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.core.execution.JobClient;
import org.apache.flink.python.PythonOptions;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironmentFactory;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.StatementSet;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableConfig;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.TableException;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.bridge.java.internal.StreamTableEnvironmentImpl;
import org.apache.flink.table.api.bridge.scala.BatchTableEnvironment;
import org.apache.flink.table.api.config.ExecutionConfigOptions;
import org.apache.flink.table.api.config.OptimizerConfigOptions;
import org.apache.flink.table.api.config.TableConfigOptions;
import org.apache.flink.table.api.internal.TableEnvironmentInternal;
import org.apache.flink.table.api.internal.CatalogTableSchemaResolver;
import org.apache.flink.table.catalog.CatalogManager;
import org.apache.flink.table.catalog.FunctionCatalog;
import org.apache.flink.table.catalog.GenericInMemoryCatalog;
import org.apache.flink.table.delegation.Executor;
import org.apache.flink.table.delegation.ExecutorFactory;
import org.apache.flink.table.delegation.Parser;
import org.apache.flink.table.delegation.Planner;
import org.apache.flink.table.delegation.PlannerFactory;
import org.apache.flink.table.factories.ComponentFactoryService;
import org.apache.flink.table.functions.AggregateFunction;
import org.apache.flink.table.functions.ScalarFunction;
import org.apache.flink.table.functions.TableAggregateFunction;
import org.apache.flink.table.functions.TableFunction;
import org.apache.flink.table.operations.CatalogSinkModifyOperation;
import org.apache.flink.table.operations.DescribeTableOperation;
import org.apache.flink.table.operations.ExplainOperation;
import org.apache.flink.table.operations.Operation;
import org.apache.flink.table.operations.QueryOperation;
import org.apache.flink.table.operations.ShowCatalogsOperation;
import org.apache.flink.table.operations.ShowDatabasesOperation;
import org.apache.flink.table.operations.ShowFunctionsOperation;
import org.apache.flink.table.operations.ShowTablesOperation;
import org.apache.flink.table.operations.UseCatalogOperation;
import org.apache.flink.table.operations.UseDatabaseOperation;
import org.apache.flink.table.operations.ddl.AlterCatalogFunctionOperation;
import org.apache.flink.table.operations.ddl.AlterDatabaseOperation;
import org.apache.flink.table.operations.ddl.AlterTableOperation;
import org.apache.flink.table.operations.ddl.CreateCatalogFunctionOperation;
import org.apache.flink.table.operations.ddl.CreateCatalogOperation;
import org.apache.flink.table.operations.ddl.CreateDatabaseOperation;
import org.apache.flink.table.operations.ddl.CreateTableOperation;
import org.apache.flink.table.operations.ddl.CreateTempSystemFunctionOperation;
import org.apache.flink.table.operations.ddl.CreateViewOperation;
import org.apache.flink.table.operations.ddl.DropCatalogFunctionOperation;
import org.apache.flink.table.operations.ddl.DropCatalogOperation;
import org.apache.flink.table.operations.ddl.DropDatabaseOperation;
import org.apache.flink.table.operations.ddl.DropTableOperation;
import org.apache.flink.table.operations.ddl.DropTempSystemFunctionOperation;
import org.apache.flink.table.operations.ddl.DropViewOperation;
import org.apache.flink.table.planner.calcite.FlinkTypeFactory;
import org.apache.flink.table.sinks.TableSink;
import org.apache.flink.table.utils.PrintUtils;
import org.apache.flink.types.Row;
import org.apache.flink.types.RowKind;
import org.apache.flink.util.FlinkException;
import org.apache.zeppelin.flink.shims112.CollectStreamTableSink;
import org.apache.zeppelin.flink.shims112.Flink112ScalaShims;
import org.apache.zeppelin.flink.sql.SqlCommandParser;
import org.apache.zeppelin.flink.sql.SqlCommandParser.SqlCommand;
import org.apache.zeppelin.flink.sql.SqlCommandParser.SqlCommandCall;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;


/**
 * Shims for flink 1.12
 */
public class Flink112Shims extends FlinkShims {

  private static final Logger LOGGER = LoggerFactory.getLogger(Flink112Shims.class);
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

  private Map<String, StatementSet> statementSetMap = new ConcurrentHashMap<>();

  public Flink112Shims(FlinkVersion flinkVersion, Properties properties) {
    super(flinkVersion, properties);
  }
  @Override
  public void disableSysoutLogging(Object batchConfig, Object streamConfig) {
    // do nothing
  }


  @Override
  public Object createStreamExecutionEnvironmentFactory(Object streamExecutionEnvironment) {
    return new StreamExecutionEnvironmentFactory() {
      @Override
      public StreamExecutionEnvironment createExecutionEnvironment(Configuration configuration) {
        return (StreamExecutionEnvironment) streamExecutionEnvironment;
      }
    };
  }

  @Override
  public Object createCatalogManager(Object config) {
    return CatalogManager.newBuilder()
            .classLoader(Thread.currentThread().getContextClassLoader())
            .config((ReadableConfig) config)
            .defaultCatalog("default_catalog",
                    new GenericInMemoryCatalog("default_catalog", "default_database"))
            .build();
  }

  @Override
  public String getPyFlinkPythonPath(Properties properties) throws IOException {
    String mode = properties.getProperty("flink.execution.mode");
    if ("yarn-application".equalsIgnoreCase(mode)) {
      // for yarn application mode, FLINK_HOME is container working directory
      String flinkHome = new File(".").getAbsolutePath();
      return getPyFlinkPythonPath(flinkHome + "/lib/python");
    }

    String flinkHome = System.getenv("FLINK_HOME");
    if (StringUtils.isNotBlank(flinkHome)) {
      return getPyFlinkPythonPath(flinkHome + "/opt/python");
    } else {
      throw new IOException("No FLINK_HOME is specified");
    }
  }

  private String getPyFlinkPythonPath(String pyFlinkFolder) {
    LOGGER.info("Getting pyflink lib from {}", pyFlinkFolder);
    List<File> depFiles = Arrays.asList(new File(pyFlinkFolder).listFiles());
    StringBuilder builder = new StringBuilder();
    for (File file : depFiles) {
      LOGGER.info("Adding extracted file {} to PYTHONPATH", file.getAbsolutePath());
      builder.append(file.getAbsolutePath() + ":");
    }
    return builder.toString();
  }

  @Override
  public Object getCollectStreamTableSink(InetAddress targetAddress, int targetPort, Object serializer) {
    return new CollectStreamTableSink(targetAddress, targetPort, (TypeSerializer<Tuple2<Boolean, Row>>) serializer);
  }

  @Override
  public List collectToList(Object table) throws Exception {
    return Lists.newArrayList(((Table) table).execute().collect());
  }

  @Override
  public void startMultipleInsert(Object tblEnv, InterpreterContext context) throws Exception {
    StatementSet statementSet = ((TableEnvironment) tblEnv).createStatementSet();
    statementSetMap.put(context.getParagraphId(), statementSet);
  }

  @Override
  public void addInsertStatement(String sql, Object tblEnv, InterpreterContext context) throws Exception {
    statementSetMap.get(context.getParagraphId()).addInsertSql(sql);
  }

  @Override
  public boolean executeMultipleInsertInto(String jobName, Object tblEnv, InterpreterContext context) throws Exception {
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

  @Override
  public boolean rowEquals(Object row1, Object row2) {
    Row r1 = (Row) row1;
    Row r2 = (Row) row2;
    r1.setKind(RowKind.INSERT);
    r2.setKind(RowKind.INSERT);
    return r1.equals(r2);
  }

  @Override
  public Object fromDataSet(Object btenv, Object ds) {
    return Flink112ScalaShims.fromDataSet((BatchTableEnvironment) btenv, (DataSet) ds);
  }

  @Override
  public Object toDataSet(Object btenv, Object table) {
    return Flink112ScalaShims.toDataSet((BatchTableEnvironment) btenv, (Table) table);
  }

  @Override
  public void registerTableSink(Object stenv, String tableName, Object collectTableSink) {
    ((org.apache.flink.table.api.internal.TableEnvironmentInternal) stenv)
            .registerTableSinkInternal(tableName, (TableSink) collectTableSink);
  }

  @Override
  public void registerScalarFunction(Object btenv, String name, Object scalarFunction) {
    ((StreamTableEnvironmentImpl)(btenv)).createTemporarySystemFunction(name, (ScalarFunction) scalarFunction);
  }

  @Override
  public void registerTableFunction(Object btenv, String name, Object tableFunction) {
    ((StreamTableEnvironmentImpl) (btenv)).registerFunction(name, (TableFunction) tableFunction);
  }

  @Override
  public void registerAggregateFunction(Object btenv, String name, Object aggregateFunction) {
    ((StreamTableEnvironmentImpl) (btenv)).registerFunction(name, (AggregateFunction) aggregateFunction);
  }

  @Override
  public void registerTableAggregateFunction(Object btenv, String name, Object tableAggregateFunction) {
    ((StreamTableEnvironmentImpl) (btenv)).registerFunction(name, (TableAggregateFunction) tableAggregateFunction);
  }

  /**
   * Parse it via flink SqlParser first, then fallback to regular expression matching.
   *
   * @param tableEnv
   * @param stmt
   * @return
   */
  @Override
  public Optional<SqlCommandParser.SqlCommandCall> parseSql(Object tableEnv, String stmt) {
    Parser sqlParser = ((TableEnvironmentInternal) tableEnv).getParser();
    SqlCommandCall sqlCommandCall = null;
    try {
      // parse statement via regex matching first
      Optional<SqlCommandCall> callOpt = parseByRegexMatching(stmt);
      if (callOpt.isPresent()) {
        sqlCommandCall = callOpt.get();
      } else {
        sqlCommandCall = parseBySqlParser(sqlParser, stmt);
      }
    } catch (Exception e) {
      return Optional.empty();
    }
    return Optional.of(sqlCommandCall);

  }

  private SqlCommandCall parseBySqlParser(Parser sqlParser, String stmt) throws Exception {
    List<Operation> operations;
    try {
      operations = sqlParser.parse(stmt);
    } catch (Throwable e) {
      throw new Exception("Invalidate SQL statement.", e);
    }
    if (operations.size() != 1) {
      throw new Exception("Only single statement is supported now.");
    }

    final SqlCommand cmd;
    String[] operands = new String[]{stmt};
    Operation operation = operations.get(0);
    if (operation instanceof CatalogSinkModifyOperation) {
      boolean overwrite = ((CatalogSinkModifyOperation) operation).isOverwrite();
      cmd = overwrite ? SqlCommand.INSERT_OVERWRITE : SqlCommand.INSERT_INTO;
    } else if (operation instanceof CreateTableOperation) {
      cmd = SqlCommand.CREATE_TABLE;
    } else if (operation instanceof DropTableOperation) {
      cmd = SqlCommand.DROP_TABLE;
    } else if (operation instanceof AlterTableOperation) {
      cmd = SqlCommand.ALTER_TABLE;
    } else if (operation instanceof CreateViewOperation) {
      cmd = SqlCommand.CREATE_VIEW;
    } else if (operation instanceof DropViewOperation) {
      cmd = SqlCommand.DROP_VIEW;
    } else if (operation instanceof CreateDatabaseOperation) {
      cmd = SqlCommand.CREATE_DATABASE;
    } else if (operation instanceof DropDatabaseOperation) {
      cmd = SqlCommand.DROP_DATABASE;
    } else if (operation instanceof AlterDatabaseOperation) {
      cmd = SqlCommand.ALTER_DATABASE;
    } else if (operation instanceof CreateCatalogOperation) {
      cmd = SqlCommand.CREATE_CATALOG;
    } else if (operation instanceof DropCatalogOperation) {
      cmd = SqlCommand.DROP_CATALOG;
    } else if (operation instanceof UseCatalogOperation) {
      cmd = SqlCommand.USE_CATALOG;
      operands = new String[]{((UseCatalogOperation) operation).getCatalogName()};
    } else if (operation instanceof UseDatabaseOperation) {
      cmd = SqlCommand.USE;
      operands = new String[]{((UseDatabaseOperation) operation).getDatabaseName()};
    } else if (operation instanceof ShowCatalogsOperation) {
      cmd = SqlCommand.SHOW_CATALOGS;
      operands = new String[0];
    } else if (operation instanceof ShowDatabasesOperation) {
      cmd = SqlCommand.SHOW_DATABASES;
      operands = new String[0];
    } else if (operation instanceof ShowTablesOperation) {
      cmd = SqlCommand.SHOW_TABLES;
      operands = new String[0];
    } else if (operation instanceof ShowFunctionsOperation) {
      cmd = SqlCommand.SHOW_FUNCTIONS;
      operands = new String[0];
    } else if (operation instanceof CreateCatalogFunctionOperation ||
            operation instanceof CreateTempSystemFunctionOperation) {
      cmd = SqlCommand.CREATE_FUNCTION;
    } else if (operation instanceof DropCatalogFunctionOperation ||
            operation instanceof DropTempSystemFunctionOperation) {
      cmd = SqlCommand.DROP_FUNCTION;
    } else if (operation instanceof AlterCatalogFunctionOperation) {
      cmd = SqlCommand.ALTER_FUNCTION;
    } else if (operation instanceof ExplainOperation) {
      cmd = SqlCommand.EXPLAIN;
    } else if (operation instanceof DescribeTableOperation) {
      cmd = SqlCommand.DESCRIBE;
      operands = new String[]{((DescribeTableOperation) operation).getSqlIdentifier().asSerializableString()};
    } else if (operation instanceof QueryOperation) {
      cmd = SqlCommand.SELECT;
    } else {
      throw new Exception("Unknown operation: " + operation.asSummaryString());
    }

    return new SqlCommandCall(cmd, operands, stmt);
  }

  private static Optional<SqlCommandCall> parseByRegexMatching(String stmt) {
    // parse statement via regex matching
    for (SqlCommand cmd : SqlCommand.values()) {
      if (cmd.pattern != null) {
        final Matcher matcher = cmd.pattern.matcher(stmt);
        if (matcher.matches()) {
          final String[] groups = new String[matcher.groupCount()];
          for (int i = 0; i < groups.length; i++) {
            groups[i] = matcher.group(i + 1);
          }
          return cmd.operandConverter.apply(groups)
                  .map((operands) -> {
                    String[] newOperands = operands;
                    if (cmd == SqlCommand.EXPLAIN) {
                      // convert `explain xx` to `explain plan for xx`
                      // which can execute through executeSql method
                      newOperands = new String[]{"EXPLAIN PLAN FOR " + operands[0] + " " + operands[1]};
                    }
                    return new SqlCommandCall(cmd, newOperands, stmt);
                  });
        }
      }
    }
    return Optional.empty();
  }

  @Override
  public void executeSql(Object tableEnv, String sql) {
    ((TableEnvironment) tableEnv).executeSql(sql);
  }

  @Override
  public String explain(Object tableEnv, String sql) {
    TableResult tableResult = ((TableEnvironment) tableEnv).executeSql(sql);
    return tableResult.collect().next().getField(0).toString();
  }

  @Override
  public String sqlHelp() {
    return MESSAGE_HELP.toString();
  }

  /**
   * Flink 1.11 bind CatalogManager with parser which make blink and flink could not share the same CatalogManager.
   * This is a workaround which always reset CatalogTableSchemaResolver before running any flink code.
   * @param catalogManager
   * @param parserObject
   * @param environmentSetting
   */
  @Override
  public void setCatalogManagerSchemaResolver(Object catalogManager,
                                              Object parserObject,
                                              Object environmentSetting) {
    ((CatalogManager) catalogManager).setCatalogTableSchemaResolver(
            new CatalogTableSchemaResolver((Parser)parserObject,
                    ((EnvironmentSettings)environmentSetting).isStreamingMode()));
  }

  @Override
  public Object updateEffectiveConfig(Object cliFrontend, Object commandLine, Object effectiveConfig) {
    CustomCommandLine customCommandLine = ((CliFrontend)cliFrontend).validateAndGetActiveCommandLine((CommandLine) commandLine);
    try {
       ((Configuration) effectiveConfig).addAll(customCommandLine.toConfiguration((CommandLine) commandLine));
       return effectiveConfig;
    } catch (FlinkException e) {
      throw new RuntimeException("Fail to call addAll", e);
    }
  }

  @Override
  public Map extractTableConfigOptions() {
    Map<String, ConfigOption> configOptions = new HashMap<>();
    configOptions.putAll(extractConfigOptions(ExecutionConfigOptions.class));
    configOptions.putAll(extractConfigOptions(OptimizerConfigOptions.class));
    try {
      configOptions.putAll(extractConfigOptions(PythonOptions.class));
    } catch (NoClassDefFoundError e) {
      LOGGER.warn("No pyflink jars found");
    }
    configOptions.putAll(extractConfigOptions(TableConfigOptions.class));
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
  public String[] rowToString(Object row, Object table, Object tableConfig) {
    return PrintUtils.rowToString((Row) row);
  }

  public boolean isTimeIndicatorType(Object type) {
    return FlinkTypeFactory.isTimeIndicatorType((TypeInformation<?>) type);
  }

  private Object lookupExecutor(ClassLoader classLoader,
                                Object settings,
                                Object sEnv) {
    try {
      Map<String, String> executorProperties = ((EnvironmentSettings) settings).toExecutorProperties();
      ExecutorFactory executorFactory = ComponentFactoryService.find(ExecutorFactory.class, executorProperties);
      Method createMethod = executorFactory.getClass()
              .getMethod("create", Map.class, StreamExecutionEnvironment.class);

      return (Executor) createMethod.invoke(
              executorFactory,
              executorProperties,
              (StreamExecutionEnvironment) sEnv);
    } catch (Exception e) {
      throw new TableException(
              "Could not instantiate the executor. Make sure a planner module is on the classpath",
              e);
    }
  }

  @Override
  public ImmutablePair<Object, Object> createPlannerAndExecutor(
          ClassLoader classLoader, Object environmentSettings, Object sEnv,
          Object tableConfig, Object functionCatalog, Object catalogManager) {
    EnvironmentSettings settings = (EnvironmentSettings) environmentSettings;
    Executor executor = (Executor) lookupExecutor(classLoader, settings, sEnv);
    Map<String, String> plannerProperties = settings.toPlannerProperties();
    Planner planner = ComponentFactoryService.find(PlannerFactory.class, plannerProperties)
            .create(plannerProperties, executor, (TableConfig) tableConfig,
                    (FunctionCatalog) functionCatalog,
                    (CatalogManager) catalogManager);
    return ImmutablePair.of(planner, executor);
  }
}
