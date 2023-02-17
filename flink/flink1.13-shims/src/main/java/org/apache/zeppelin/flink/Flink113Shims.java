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
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.scala.DataSet;
import org.apache.flink.client.cli.CliFrontend;
import org.apache.flink.client.cli.CustomCommandLine;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.ExecutionOptions;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironmentFactory;
import org.apache.flink.table.api.*;
import org.apache.flink.table.api.bridge.java.internal.StreamTableEnvironmentImpl;
import org.apache.flink.table.api.bridge.scala.BatchTableEnvironment;
import org.apache.flink.table.api.config.TableConfigOptions;
import org.apache.flink.table.catalog.CatalogManager;
import org.apache.flink.table.catalog.FunctionCatalog;
import org.apache.flink.table.catalog.GenericInMemoryCatalog;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.table.delegation.Executor;
import org.apache.flink.table.delegation.ExecutorFactory;
import org.apache.flink.table.delegation.Planner;
import org.apache.flink.table.delegation.PlannerFactory;
import org.apache.flink.table.factories.ComponentFactoryService;
import org.apache.flink.table.functions.AggregateFunction;
import org.apache.flink.table.functions.ScalarFunction;
import org.apache.flink.table.functions.TableAggregateFunction;
import org.apache.flink.table.functions.TableFunction;
import org.apache.flink.table.module.ModuleManager;
import org.apache.flink.table.planner.calcite.FlinkTypeFactory;
import org.apache.flink.table.sinks.TableSink;
import org.apache.flink.table.utils.PrintUtils;
import org.apache.flink.types.Row;
import org.apache.flink.types.RowKind;
import org.apache.flink.util.FlinkException;
import org.apache.zeppelin.flink.shims113.CollectStreamTableSink;
import org.apache.zeppelin.flink.shims113.Flink113ScalaShims;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.URL;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;


/**
 * Shims for flink 1.13
 */
public class Flink113Shims extends FlinkShims {

  private static final Logger LOGGER = LoggerFactory.getLogger(Flink113Shims.class);

  private Flink113SqlInterpreter batchSqlInterpreter;
  private Flink113SqlInterpreter streamSqlInterpreter;

  public Flink113Shims(FlinkVersion flinkVersion, Properties properties) {
    super(flinkVersion, properties);
  }

  public void initInnerBatchSqlInterpreter(FlinkSqlContext flinkSqlContext) {
    this.batchSqlInterpreter = new Flink113SqlInterpreter(flinkSqlContext, true);
  }

  public void initInnerStreamSqlInterpreter(FlinkSqlContext flinkSqlContext) {
    this.streamSqlInterpreter = new Flink113SqlInterpreter(flinkSqlContext, false);
  }

  @Override
  public Object createResourceManager(List<URL> jars, Object tableConfig) {
    return null;
  }

  @Override
  public Object createFunctionCatalog(Object tableConfig, Object catalogManager, Object moduleManager, List<URL> jars) {
    return new FunctionCatalog((TableConfig) tableConfig, (CatalogManager) catalogManager, (ModuleManager) moduleManager);
  }

  @Override
  public void disableSysoutLogging(Object batchConfig, Object streamConfig) {
    // do nothing
  }

  @Override
  public Object createScalaBlinkStreamTableEnvironment(Object environmentSettingsObj,
                                                       Object senvObj,
                                                       Object tableConfigObj,
                                                       Object moduleManagerObj,
                                                       Object functionCatalogObj,
                                                       Object catalogManagerObj,
                                                       List<URL> jars,
                                                       ClassLoader classLoader) {
    EnvironmentSettings environmentSettings = (EnvironmentSettings) environmentSettingsObj;
    StreamExecutionEnvironment senv = (StreamExecutionEnvironment) senvObj;
    TableConfig tableConfig = (TableConfig) tableConfigObj;
    ModuleManager moduleManager = (ModuleManager) moduleManagerObj;
    FunctionCatalog functionCatalog = (FunctionCatalog) functionCatalogObj;
    CatalogManager catalogManager = (CatalogManager) catalogManagerObj;
    ImmutablePair<Object, Object> pair = createPlannerAndExecutor(
            classLoader, environmentSettings, senv,
            tableConfig, moduleManager, functionCatalog, catalogManager);
    Planner planner = (Planner) pair.left;
    Executor executor = (Executor) pair.right;

    return new org.apache.flink.table.api.bridge.scala.internal.StreamTableEnvironmentImpl(catalogManager,
            moduleManager,
            functionCatalog, tableConfig, new org.apache.flink.streaming.api.scala.StreamExecutionEnvironment(senv),
            planner, executor, environmentSettings.isStreamingMode(), classLoader);
  }

  @Override
  public Object createJavaBlinkStreamTableEnvironment(Object environmentSettingsObj,
                                                      Object senvObj,
                                                      Object tableConfigObj,
                                                      Object moduleManagerObj,
                                                      Object functionCatalogObj,
                                                      Object catalogManagerObj,
                                                      List<URL> jars,
                                                      ClassLoader classLoader) {
    EnvironmentSettings environmentSettings = (EnvironmentSettings) environmentSettingsObj;
    StreamExecutionEnvironment senv = (StreamExecutionEnvironment) senvObj;
    TableConfig tableConfig = (TableConfig) tableConfigObj;
    ModuleManager moduleManager = (ModuleManager) moduleManagerObj;
    FunctionCatalog functionCatalog = (FunctionCatalog) functionCatalogObj;
    CatalogManager catalogManager = (CatalogManager) catalogManagerObj;
    ImmutablePair<Object, Object> pair = createPlannerAndExecutor(
            classLoader, environmentSettings, senv,
            tableConfig, moduleManager, functionCatalog, catalogManager);
    Planner planner = (Planner) pair.left;
    Executor executor = (Executor) pair.right;

    return new StreamTableEnvironmentImpl(catalogManager, moduleManager,
            functionCatalog, tableConfig, senv, planner, executor, environmentSettings.isStreamingMode(), classLoader);
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
      return getPyFlinkPythonPath(new File(flinkHome + "/lib/python"));
    }

    String flinkHome = System.getenv("FLINK_HOME");
    if (StringUtils.isNotBlank(flinkHome)) {
      return getPyFlinkPythonPath(new File(flinkHome + "/opt/python"));
    } else {
      throw new IOException("No FLINK_HOME is specified");
    }
  }

  private String getPyFlinkPythonPath(File pyFlinkFolder) throws IOException {
    LOGGER.info("Getting pyflink lib from {}", pyFlinkFolder);
    if (!pyFlinkFolder.exists() || !pyFlinkFolder.isDirectory()) {
      throw new IOException(String.format("PyFlink folder %s does not exist or is not a folder",
              pyFlinkFolder.getAbsolutePath()));
    }
    List<File> depFiles = Arrays.asList(pyFlinkFolder.listFiles());
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
  public boolean rowEquals(Object row1, Object row2) {
    Row r1 = (Row) row1;
    Row r2 = (Row) row2;
    r1.setKind(RowKind.INSERT);
    r2.setKind(RowKind.INSERT);
    return r1.equals(r2);
  }

  @Override
  public Object fromDataSet(Object btenv, Object ds) {
    return Flink113ScalaShims.fromDataSet((BatchTableEnvironment) btenv, (DataSet) ds);
  }

  @Override
  public Object toDataSet(Object btenv, Object table) {
    return Flink113ScalaShims.toDataSet((BatchTableEnvironment) btenv, (Table) table);
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
  public void setBatchRuntimeMode(Object tableConfig) {
    ((TableConfig) tableConfig).getConfiguration()
            .set(ExecutionOptions.RUNTIME_MODE, RuntimeExecutionMode.BATCH);
  }

  @Override
  public void setOldPlanner(Object tableConfig) {
    ((TableConfig) tableConfig).getConfiguration()
            .set(TableConfigOptions.TABLE_PLANNER, PlannerType.OLD);
  }

  @Override
  public String[] rowToString(Object row, Object table, Object tableConfig) {
    final String zone = ((TableConfig) tableConfig).getConfiguration()
            .get(TableConfigOptions.LOCAL_TIME_ZONE);
    ZoneId zoneId = TableConfigOptions.LOCAL_TIME_ZONE.defaultValue().equals(zone)
            ? ZoneId.systemDefault()
            : ZoneId.of(zone);

    ResolvedSchema resolvedSchema = ((Table) table).getResolvedSchema();
    return PrintUtils.rowToString((Row) row, resolvedSchema, zoneId);
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

      return createMethod.invoke(
              executorFactory,
              executorProperties,
              sEnv);
    } catch (Exception e) {
      throw new TableException(
              "Could not instantiate the executor. Make sure a planner module is on the classpath",
              e);
    }
  }

  @Override
  public ImmutablePair<Object, Object> createPlannerAndExecutor(
          ClassLoader classLoader, Object environmentSettings, Object sEnv,
          Object tableConfig, Object moduleManager, Object functionCatalog, Object catalogManager) {
    EnvironmentSettings settings = (EnvironmentSettings) environmentSettings;
    Executor executor = (Executor) lookupExecutor(classLoader, settings, sEnv);
    Map<String, String> plannerProperties = settings.toPlannerProperties();
    Planner planner = ComponentFactoryService.find(PlannerFactory.class, plannerProperties)
            .create(plannerProperties, executor, (TableConfig) tableConfig,
                    (FunctionCatalog) functionCatalog,
                    (CatalogManager) catalogManager);
    return ImmutablePair.of(planner, executor);
  }

  @Override
  public Object createBlinkPlannerEnvSettingBuilder() {
    return EnvironmentSettings.newInstance().useBlinkPlanner();
  }

  @Override
  public Object createOldPlannerEnvSettingBuilder() {
    return EnvironmentSettings.newInstance().useOldPlanner();
  }

  @Override
  public InterpreterResult runSqlList(String st, InterpreterContext context, boolean isBatch) {
    if (isBatch) {
      return batchSqlInterpreter.runSqlList(st, context);
    } else {
      return streamSqlInterpreter.runSqlList(st, context);
    }
  }
}
