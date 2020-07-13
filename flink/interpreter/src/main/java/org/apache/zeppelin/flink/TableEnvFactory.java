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
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableConfig;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.TableException;
import org.apache.flink.table.catalog.CatalogManager;
import org.apache.flink.table.catalog.FunctionCatalog;
import org.apache.flink.table.delegation.Executor;
import org.apache.flink.table.delegation.ExecutorFactory;
import org.apache.flink.table.delegation.Planner;
import org.apache.flink.table.delegation.PlannerFactory;
import org.apache.flink.table.factories.ComponentFactoryService;
import org.apache.flink.table.module.ModuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;

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
  private Executor executor;
  private org.apache.flink.api.scala.ExecutionEnvironment benv;
  private org.apache.flink.streaming.api.scala.StreamExecutionEnvironment senv;
  private TableConfig tblConfig;
  private CatalogManager catalogManager;
  private ModuleManager moduleManager;
  private FunctionCatalog flinkFunctionCatalog;
  private FunctionCatalog blinkFunctionCatalog;

  public TableEnvFactory(FlinkVersion flinkVersion,
                         FlinkShims flinkShims,
                         org.apache.flink.api.scala.ExecutionEnvironment env,
                         org.apache.flink.streaming.api.scala.StreamExecutionEnvironment senv,
                         TableConfig tblConfig,
                         CatalogManager catalogManager,
                         ModuleManager moduleManager,
                         FunctionCatalog flinkFunctionCatalog,
                         FunctionCatalog blinkFunctionCatalog) {
    this.flinkVersion = flinkVersion;
    this.flinkShims = flinkShims;
    this.benv = env;
    this.senv = senv;
    this.tblConfig = tblConfig;
    this.catalogManager = catalogManager;
    this.moduleManager = moduleManager;
    this.flinkFunctionCatalog = flinkFunctionCatalog;
    this.blinkFunctionCatalog = blinkFunctionCatalog;
  }

  public TableEnvironment createScalaFlinkBatchTableEnvironment() {
    try {
      Class clazz = null;
      if (flinkVersion.isFlink110()) {
        clazz = Class
                .forName("org.apache.flink.table.api.scala.internal.BatchTableEnvironmentImpl");
      } else {
        clazz = Class
                .forName("org.apache.flink.table.api.bridge.scala.internal.BatchTableEnvironmentImpl");
      }
      Constructor constructor = clazz
              .getConstructor(
                      org.apache.flink.api.scala.ExecutionEnvironment.class,
                      TableConfig.class,
                      CatalogManager.class,
                      ModuleManager.class);

      return (TableEnvironment)
              constructor.newInstance(benv, tblConfig, catalogManager, moduleManager);
    } catch (Exception e) {
      throw new TableException("Fail to createScalaFlinkBatchTableEnvironment", e);
    }
  }

  public TableEnvironment createScalaFlinkStreamTableEnvironment(EnvironmentSettings settings) {
    try {
      Map<String, String> executorProperties = settings.toExecutorProperties();
      Executor executor = lookupExecutor(executorProperties, senv.getJavaEnv());

      Map<String, String> plannerProperties = settings.toPlannerProperties();
      Planner planner = ComponentFactoryService.find(PlannerFactory.class, plannerProperties)
              .create(
                      plannerProperties,
                      executor,
                      tblConfig,
                      flinkFunctionCatalog,
                      catalogManager);

      Class clazz = null;
      if (flinkVersion.isFlink110()) {
        clazz = Class
                .forName("org.apache.flink.table.api.scala.internal.StreamTableEnvironmentImpl");
      } else {
        clazz = Class
                .forName("org.apache.flink.table.api.bridge.scala.internal.StreamTableEnvironmentImpl");
      }
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
              flinkFunctionCatalog,
              tblConfig,
              senv,
              planner,
              executor,
              settings.isStreamingMode());

    } catch (Exception e) {
      throw new TableException("Fail to createScalaFlinkStreamTableEnvironment", e);
    }
  }

  public TableEnvironment createJavaFlinkBatchTableEnvironment() {
    try {
      Class<?> clazz = null;
      if (flinkVersion.isFlink110()) {
        clazz = Class
                .forName("org.apache.flink.table.api.java.internal.BatchTableEnvironmentImpl");
      } else {
        clazz = Class
                .forName("org.apache.flink.table.api.bridge.java.internal.BatchTableEnvironmentImpl");
      }

      Constructor con = clazz.getConstructor(
              ExecutionEnvironment.class,
              TableConfig.class,
              CatalogManager.class,
              ModuleManager.class);
      return (TableEnvironment) con.newInstance(
              benv.getJavaEnv(),
              tblConfig,
              catalogManager,
              moduleManager);
    } catch (Throwable t) {
      throw new TableException("Create BatchTableEnvironment failed.", t);
    }
  }

  public TableEnvironment createJavaFlinkStreamTableEnvironment(EnvironmentSettings settings) {

    try {
      Map<String, String> executorProperties = settings.toExecutorProperties();
      Executor executor = lookupExecutor(executorProperties, senv.getJavaEnv());

      Map<String, String> plannerProperties = settings.toPlannerProperties();
      Planner planner = ComponentFactoryService.find(PlannerFactory.class, plannerProperties)
              .create(plannerProperties, executor, tblConfig, flinkFunctionCatalog, catalogManager);

      Class clazz = null;
      if (flinkVersion.isFlink110()) {
        clazz = Class
                .forName("org.apache.flink.table.api.java.internal.StreamTableEnvironmentImpl");
      } else {
        clazz = Class
                .forName("org.apache.flink.table.api.bridge.java.internal.StreamTableEnvironmentImpl");
      }
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
              flinkFunctionCatalog,
              tblConfig,
              senv.getJavaEnv(),
              planner,
              executor,
              settings.isStreamingMode());

    } catch (Exception e) {
      throw new TableException("Fail to createJavaFlinkStreamTableEnvironment", e);
    }
  }

  public TableEnvironment createScalaBlinkStreamTableEnvironment(EnvironmentSettings settings) {

    try {
      Map<String, String> executorProperties = settings.toExecutorProperties();
      Executor executor = lookupExecutor(executorProperties, senv.getJavaEnv());

      Map<String, String> plannerProperties = settings.toPlannerProperties();
      Planner planner = ComponentFactoryService.find(PlannerFactory.class, plannerProperties)
              .create(
                      plannerProperties,
                      executor,
                      tblConfig,
                      blinkFunctionCatalog,
                      catalogManager);


      Class clazz = null;
      if (flinkVersion.isFlink110()) {
        clazz = Class
                .forName("org.apache.flink.table.api.scala.internal.StreamTableEnvironmentImpl");
      } else {
        clazz = Class
                .forName("org.apache.flink.table.api.bridge.scala.internal.StreamTableEnvironmentImpl");
      }
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
              blinkFunctionCatalog,
              tblConfig,
              senv,
              planner,
              executor,
              settings.isStreamingMode());
    } catch (Exception e) {
      throw new TableException("Fail to createScalaBlinkStreamTableEnvironment", e);
    }
  }

  public TableEnvironment createJavaBlinkStreamTableEnvironment(EnvironmentSettings settings) {

    try {
      Map<String, String> executorProperties = settings.toExecutorProperties();
      Executor executor = lookupExecutor(executorProperties, senv.getJavaEnv());

      Map<String, String> plannerProperties = settings.toPlannerProperties();
      Planner planner = ComponentFactoryService.find(PlannerFactory.class, plannerProperties)
              .create(plannerProperties, executor, tblConfig, blinkFunctionCatalog, catalogManager);

      Class clazz = null;
      if (flinkVersion.isFlink110()) {
        clazz = Class
                .forName("org.apache.flink.table.api.java.internal.StreamTableEnvironmentImpl");
      } else {
        clazz = Class
                .forName("org.apache.flink.table.api.bridge.java.internal.StreamTableEnvironmentImpl");
      }
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
              blinkFunctionCatalog,
              tblConfig,
              senv.getJavaEnv(),
              planner,
              executor,
              settings.isStreamingMode());
    } catch (Exception e) {
      throw new TableException("Fail to createJavaBlinkStreamTableEnvironment", e);
    }
  }

  public TableEnvironment createJavaBlinkBatchTableEnvironment(
          EnvironmentSettings settings) {
    try {
      final Map<String, String> executorProperties = settings.toExecutorProperties();
      executor = lookupExecutor(executorProperties, senv.getJavaEnv());
      final Map<String, String> plannerProperties = settings.toPlannerProperties();
      final Planner planner = ComponentFactoryService.find(PlannerFactory.class, plannerProperties)
              .create(plannerProperties, executor, tblConfig, blinkFunctionCatalog, catalogManager);

      Class clazz = null;
      if (flinkVersion.isFlink110()) {
        clazz = Class
                .forName("org.apache.flink.table.api.java.internal.StreamTableEnvironmentImpl");
      } else {
        clazz = Class
                .forName("org.apache.flink.table.api.bridge.java.internal.StreamTableEnvironmentImpl");
      }
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
              blinkFunctionCatalog,
              tblConfig,
              senv.getJavaEnv(),
              planner,
              executor,
              settings.isStreamingMode());
    } catch (Exception e) {
      LOGGER.info(ExceptionUtils.getStackTrace(e));
      throw new TableException("Fail to createJavaBlinkBatchTableEnvironment", e);
    }
  }


  public void createPlanner(EnvironmentSettings settings) {
    Map<String, String> executorProperties = settings.toExecutorProperties();
    Executor executor = lookupExecutor(executorProperties, senv.getJavaEnv());

    Map<String, String> plannerProperties = settings.toPlannerProperties();
    Planner planner = ComponentFactoryService.find(PlannerFactory.class, plannerProperties)
            .create(
                    plannerProperties,
                    executor,
                    tblConfig,
                    blinkFunctionCatalog,
                    catalogManager);
    this.flinkShims.setCatalogManagerSchemaResolver(catalogManager, planner.getParser(), settings);
  }

  private static Executor lookupExecutor(
          Map<String, String> executorProperties,
          StreamExecutionEnvironment executionEnvironment) {
    try {
      ExecutorFactory executorFactory = ComponentFactoryService.find(ExecutorFactory.class, executorProperties);
      Method createMethod = executorFactory.getClass()
              .getMethod("create", Map.class, StreamExecutionEnvironment.class);

      return (Executor) createMethod.invoke(
              executorFactory,
              executorProperties,
              executionEnvironment);
    } catch (Exception e) {
      throw new TableException(
              "Could not instantiate the executor. Make sure a planner module is on the classpath",
              e);
    }
  }
}
