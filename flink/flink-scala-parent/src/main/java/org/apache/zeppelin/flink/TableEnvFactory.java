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

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableConfig;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.TableException;
import org.apache.flink.table.catalog.CatalogManager;
import org.apache.flink.table.catalog.FunctionCatalog;
import org.apache.flink.table.delegation.Executor;
import org.apache.flink.table.delegation.Planner;
import org.apache.flink.table.module.ModuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;

/**
 * Factory class for creating flink table env for different purpose:
 * 1. java/scala
 * 2. stream table / batch table
 * 3. flink planner / blink planner
 *
 */
public class TableEnvFactory {

  private static Logger LOGGER = LoggerFactory.getLogger(TableEnvFactory.class);

  private FlinkVersion flinkVersion;
  private FlinkShims flinkShims;
  private org.apache.flink.api.scala.ExecutionEnvironment benv;
  private org.apache.flink.streaming.api.scala.StreamExecutionEnvironment senv;

  /***********************************************************************
  Should use different TableConfig for different kinds of table_env
  otherwise it will cause conflicts after flink 1.13
   ***********************************************************************/
  // tableConfig used for StreamTableEnvironment.
  private TableConfig streamTableConfig;
  // tableConfig used for BatchTableEnvironment.
  private TableConfig batchTableConfig;
  // tableConfig for old planner
  private TableConfig oldPlannerStreamTableConfig;
  private TableConfig oldPlannerBatchTableConfig;

  private CatalogManager catalogManager;
  private CatalogManager oldPlannerCatalogManager;
  private ModuleManager moduleManager;
  private FunctionCatalog functionCatalog;
  private FunctionCatalog oldPlannerFunctionCatalog;


  public TableEnvFactory(FlinkVersion flinkVersion,
                         FlinkShims flinkShims,
                         org.apache.flink.api.scala.ExecutionEnvironment env,
                         org.apache.flink.streaming.api.scala.StreamExecutionEnvironment senv,
                         TableConfig streamTableConfig) {

    this.flinkVersion = flinkVersion;
    this.flinkShims = flinkShims;
    this.benv = env;
    this.senv = senv;
    this.streamTableConfig = streamTableConfig;
    this.batchTableConfig = new TableConfig();
    this.batchTableConfig.getConfiguration().addAll(streamTableConfig.getConfiguration());
    flinkShims.setBatchRuntimeMode(this.batchTableConfig);
    this.oldPlannerBatchTableConfig = new TableConfig();
    this.oldPlannerBatchTableConfig.getConfiguration().addAll(streamTableConfig.getConfiguration());
    flinkShims.setOldPlanner(this.oldPlannerBatchTableConfig);
    this.oldPlannerStreamTableConfig = new TableConfig();
    this.oldPlannerStreamTableConfig.getConfiguration().addAll(streamTableConfig.getConfiguration());
    flinkShims.setOldPlanner(this.oldPlannerStreamTableConfig);

    this.catalogManager = (CatalogManager) flinkShims.createCatalogManager(streamTableConfig.getConfiguration());
    this.oldPlannerCatalogManager = (CatalogManager) flinkShims.createCatalogManager(
            this.oldPlannerStreamTableConfig.getConfiguration());

    this.moduleManager = new ModuleManager();

    this.functionCatalog = new FunctionCatalog(streamTableConfig, catalogManager, moduleManager);
    this.oldPlannerFunctionCatalog = new FunctionCatalog(
            this.oldPlannerStreamTableConfig, this.oldPlannerCatalogManager, moduleManager);
  }

  public TableEnvironment createScalaFlinkBatchTableEnvironment() {
    try {
      Class clazz = Class
                .forName("org.apache.flink.table.api.bridge.scala.internal.BatchTableEnvironmentImpl");

      Constructor constructor = clazz
              .getConstructor(
                      org.apache.flink.api.scala.ExecutionEnvironment.class,
                      TableConfig.class,
                      CatalogManager.class,
                      ModuleManager.class);

      return (TableEnvironment)
              constructor.newInstance(benv, oldPlannerBatchTableConfig, oldPlannerCatalogManager, moduleManager);
    } catch (Exception e) {
      throw new TableException("Fail to createScalaFlinkBatchTableEnvironment", e);
    }
  }

  public TableEnvironment createScalaFlinkStreamTableEnvironment(EnvironmentSettings settings, ClassLoader classLoader) {
    try {
      ImmutablePair<Object, Object> pair = flinkShims.createPlannerAndExecutor(
              classLoader, settings, senv.getJavaEnv(),
              oldPlannerStreamTableConfig, functionCatalog, catalogManager);
      Planner planner = (Planner) pair.left;
      Executor executor = (Executor) pair.right;

      Class clazz = Class
                .forName("org.apache.flink.table.api.bridge.scala.internal.StreamTableEnvironmentImpl");

      try {
        Constructor constructor = clazz
                .getConstructor(
                        CatalogManager.class,
                        ModuleManager.class,
                        FunctionCatalog.class,
                        TableConfig.class,
                        org.apache.flink.streaming.api.scala.StreamExecutionEnvironment.class,
                        Planner.class,
                        Executor.class,
                        boolean.class);
        return (TableEnvironment) constructor.newInstance(
                oldPlannerCatalogManager,
                moduleManager,
                oldPlannerFunctionCatalog,
                oldPlannerStreamTableConfig,
                senv,
                planner,
                executor,
                settings.isStreamingMode());
      } catch (NoSuchMethodException e) {
        // Flink 1.11.1 change the constructor signature, FLINK-18419
        Constructor constructor = clazz
                .getConstructor(
                        CatalogManager.class,
                        ModuleManager.class,
                        FunctionCatalog.class,
                        TableConfig.class,
                        org.apache.flink.streaming.api.scala.StreamExecutionEnvironment.class,
                        Planner.class,
                        Executor.class,
                        boolean.class,
                        ClassLoader.class);
        return (TableEnvironment) constructor.newInstance(
                oldPlannerCatalogManager,
                moduleManager,
                oldPlannerFunctionCatalog,
                oldPlannerStreamTableConfig,
                senv,
                planner,
                executor,
                settings.isStreamingMode(),
                classLoader);
      }

    } catch (Exception e) {
      throw new TableException("Fail to createScalaFlinkStreamTableEnvironment", e);
    }
  }

  public TableEnvironment createJavaFlinkBatchTableEnvironment() {
    try {
      Class<?> clazz = Class
                .forName("org.apache.flink.table.api.bridge.java.internal.BatchTableEnvironmentImpl");

      Constructor con = clazz.getConstructor(
              ExecutionEnvironment.class,
              TableConfig.class,
              CatalogManager.class,
              ModuleManager.class);

      return (TableEnvironment) con.newInstance(
              benv.getJavaEnv(),
              oldPlannerBatchTableConfig,
              oldPlannerCatalogManager,
              moduleManager);
    } catch (Throwable t) {
      throw new TableException("Create BatchTableEnvironment failed.", t);
    }
  }

  public TableEnvironment createJavaFlinkStreamTableEnvironment(EnvironmentSettings settings,
                                                                ClassLoader classLoader) {
    try {
      ImmutablePair<Object, Object> pair = flinkShims.createPlannerAndExecutor(
              classLoader, settings, senv.getJavaEnv(),
              oldPlannerBatchTableConfig, functionCatalog, catalogManager);
      Planner planner = (Planner) pair.left;
      Executor executor = (Executor) pair.right;

      Class clazz = Class
                .forName("org.apache.flink.table.api.bridge.java.internal.StreamTableEnvironmentImpl");

      try {
        Constructor constructor = clazz
                .getConstructor(
                        CatalogManager.class,
                        ModuleManager.class,
                        FunctionCatalog.class,
                        TableConfig.class,
                        org.apache.flink.streaming.api.environment.StreamExecutionEnvironment.class,
                        Planner.class,
                        Executor.class,
                        boolean.class);
        return (TableEnvironment) constructor.newInstance(
                oldPlannerCatalogManager,
                moduleManager,
                oldPlannerFunctionCatalog,
                oldPlannerStreamTableConfig,
                senv.getJavaEnv(),
                planner,
                executor,
                settings.isStreamingMode());
      } catch (NoSuchMethodException e) {
        // Flink 1.11.1 change the constructor signature, FLINK-18419
        Constructor constructor = clazz
                .getConstructor(
                        CatalogManager.class,
                        ModuleManager.class,
                        FunctionCatalog.class,
                        TableConfig.class,
                        org.apache.flink.streaming.api.environment.StreamExecutionEnvironment.class,
                        Planner.class,
                        Executor.class,
                        boolean.class,
                        ClassLoader.class);
        return (TableEnvironment) constructor.newInstance(
                oldPlannerCatalogManager,
                moduleManager,
                oldPlannerFunctionCatalog,
                oldPlannerStreamTableConfig,
                senv.getJavaEnv(),
                planner,
                executor,
                settings.isStreamingMode(),
                classLoader);
      }

    } catch (Exception e) {
      throw new TableException("Fail to createJavaFlinkStreamTableEnvironment", e);
    }
  }

  public TableEnvironment createScalaBlinkStreamTableEnvironment(EnvironmentSettings settings, ClassLoader classLoader) {

    try {
      ImmutablePair<Object, Object> pair = flinkShims.createPlannerAndExecutor(
              classLoader, settings, senv.getJavaEnv(),
              streamTableConfig, functionCatalog, catalogManager);
      Planner planner = (Planner) pair.left;
      Executor executor = (Executor) pair.right;

      Class clazz = Class
                .forName("org.apache.flink.table.api.bridge.scala.internal.StreamTableEnvironmentImpl");
      try {
        Constructor constructor = clazz
                .getConstructor(
                        CatalogManager.class,
                        ModuleManager.class,
                        FunctionCatalog.class,
                        TableConfig.class,
                        org.apache.flink.streaming.api.scala.StreamExecutionEnvironment.class,
                        Planner.class,
                        Executor.class,
                        boolean.class);
        return (TableEnvironment) constructor.newInstance(catalogManager,
                moduleManager,
                functionCatalog,
                streamTableConfig,
                senv,
                planner,
                executor,
                settings.isStreamingMode());
      } catch (NoSuchMethodException e) {
        // Flink 1.11.1 change the constructor signature, FLINK-18419
        Constructor constructor = clazz
                .getConstructor(
                        CatalogManager.class,
                        ModuleManager.class,
                        FunctionCatalog.class,
                        TableConfig.class,
                        org.apache.flink.streaming.api.scala.StreamExecutionEnvironment.class,
                        Planner.class,
                        Executor.class,
                        boolean.class,
                        ClassLoader.class);
        return (TableEnvironment) constructor.newInstance(catalogManager,
                moduleManager,
                functionCatalog,
                streamTableConfig,
                senv,
                planner,
                executor,
                settings.isStreamingMode(),
                classLoader);
      }
    } catch (Exception e) {
      throw new TableException("Fail to createScalaBlinkStreamTableEnvironment", e);
    }
  }

  public TableEnvironment createJavaBlinkStreamTableEnvironment(EnvironmentSettings settings, ClassLoader classLoader) {
    try {
      ImmutablePair<Object, Object> pair = flinkShims.createPlannerAndExecutor(
              classLoader, settings, senv.getJavaEnv(),
              streamTableConfig, functionCatalog, catalogManager);
      Planner planner = (Planner) pair.left;
      Executor executor = (Executor) pair.right;

      Class clazz = Class
                .forName("org.apache.flink.table.api.bridge.java.internal.StreamTableEnvironmentImpl");

      try {
        Constructor constructor = clazz
                .getConstructor(
                        CatalogManager.class,
                        ModuleManager.class,
                        FunctionCatalog.class,
                        TableConfig.class,
                        org.apache.flink.streaming.api.environment.StreamExecutionEnvironment.class,
                        Planner.class,
                        Executor.class,
                        boolean.class);
        return (TableEnvironment) constructor.newInstance(catalogManager,
                moduleManager,
                functionCatalog,
                streamTableConfig,
                senv.getJavaEnv(),
                planner,
                executor,
                settings.isStreamingMode());
      } catch (NoSuchMethodException e) {
        // Flink 1.11.1 change the constructor signature, FLINK-18419
        Constructor constructor = clazz
                .getConstructor(
                        CatalogManager.class,
                        ModuleManager.class,
                        FunctionCatalog.class,
                        TableConfig.class,
                        org.apache.flink.streaming.api.environment.StreamExecutionEnvironment.class,
                        Planner.class,
                        Executor.class,
                        boolean.class,
                        ClassLoader.class);
        return (TableEnvironment) constructor.newInstance(catalogManager,
                moduleManager,
                functionCatalog,
                streamTableConfig,
                senv.getJavaEnv(),
                planner,
                executor,
                settings.isStreamingMode(),
                classLoader);
      }
    } catch (Exception e) {
      throw new TableException("Fail to createJavaBlinkStreamTableEnvironment", e);
    }
  }

  public TableEnvironment createJavaBlinkBatchTableEnvironment(
          EnvironmentSettings settings, ClassLoader classLoader) {
    try {
      ImmutablePair<Object, Object> pair = flinkShims.createPlannerAndExecutor(
              classLoader, settings, senv.getJavaEnv(),
              batchTableConfig, functionCatalog, catalogManager);
      Planner planner = (Planner) pair.left;
      Executor executor = (Executor) pair.right;

      Class clazz = Class
                .forName("org.apache.flink.table.api.bridge.java.internal.StreamTableEnvironmentImpl");
      try {
        Constructor constructor = clazz.getConstructor(
                CatalogManager.class,
                ModuleManager.class,
                FunctionCatalog.class,
                TableConfig.class,
                StreamExecutionEnvironment.class,
                Planner.class,
                Executor.class,
                boolean.class);
        return (TableEnvironment) constructor.newInstance(
                catalogManager,
                moduleManager,
                functionCatalog,
                batchTableConfig,
                senv.getJavaEnv(),
                planner,
                executor,
                settings.isStreamingMode());
      } catch (NoSuchMethodException e) {
        // Flink 1.11.1 change the constructor signature, FLINK-18419
        Constructor constructor = clazz.getConstructor(
                CatalogManager.class,
                ModuleManager.class,
                FunctionCatalog.class,
                TableConfig.class,
                StreamExecutionEnvironment.class,
                Planner.class,
                Executor.class,
                boolean.class,
                ClassLoader.class);
        return (TableEnvironment) constructor.newInstance(
                catalogManager,
                moduleManager,
                functionCatalog,
                batchTableConfig,
                senv.getJavaEnv(),
                planner,
                executor,
                settings.isStreamingMode(),
                classLoader);
      }
    } catch (Exception e) {
      LOGGER.info(ExceptionUtils.getStackTrace(e));
      throw new TableException("Fail to createJavaBlinkBatchTableEnvironment", e);
    }
  }

  public void createStreamPlanner(EnvironmentSettings settings) {
    ImmutablePair<Object, Object> pair = flinkShims.createPlannerAndExecutor(
            Thread.currentThread().getContextClassLoader(), settings, senv.getJavaEnv(),
            streamTableConfig, functionCatalog, catalogManager);
    Planner planner = (Planner) pair.left;
    this.flinkShims.setCatalogManagerSchemaResolver(catalogManager, planner.getParser(), settings);
  }
}
