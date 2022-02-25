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


import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.util.List;
import java.util.Properties;

/**
 * This is abstract class for anything that is api incompatible between different flink versions. It will
 * load the correct version of FlinkShims based on the version of flink.
 */
public abstract class FlinkShims {

  private static final Logger LOGGER = LoggerFactory.getLogger(FlinkShims.class);

  private static FlinkShims flinkShims;

  protected Properties properties;
  protected FlinkVersion flinkVersion;
  protected FlinkSqlContext flinkSqlContext;

  public FlinkShims(FlinkVersion flinkVersion, Properties properties) {
    this.flinkVersion = flinkVersion;
    this.properties = properties;
  }

  private static FlinkShims loadShims(FlinkVersion flinkVersion,
                                      Properties properties)
      throws Exception {
    Class<?> flinkShimsClass;
    if (flinkVersion.getMajorVersion() == 1 && flinkVersion.getMinorVersion() == 12) {
      LOGGER.info("Initializing shims for Flink 1.12");
      flinkShimsClass = Class.forName("org.apache.zeppelin.flink.Flink112Shims");
    } else if (flinkVersion.getMajorVersion() == 1 && flinkVersion.getMinorVersion() == 13) {
      LOGGER.info("Initializing shims for Flink 1.13");
      flinkShimsClass = Class.forName("org.apache.zeppelin.flink.Flink113Shims");
    } else if (flinkVersion.getMajorVersion() == 1 && flinkVersion.getMinorVersion() == 14) {
      LOGGER.info("Initializing shims for Flink 1.14");
      flinkShimsClass = Class.forName("org.apache.zeppelin.flink.Flink114Shims");
    } else if (flinkVersion.getMajorVersion() == 1 && flinkVersion.getMinorVersion() == 15) {
      LOGGER.info("Initializing shims for Flink 1.15");
      flinkShimsClass = Class.forName("org.apache.zeppelin.flink.Flink115Shims");
    } else {
      throw new Exception("Flink version: '" + flinkVersion + "' is not supported yet");
    }

    Constructor c = flinkShimsClass.getConstructor(FlinkVersion.class, Properties.class);
    return (FlinkShims) c.newInstance(flinkVersion, properties);
  }

  /**
   *
   * @param flinkVersion
   * @param properties
   * @return
   */
  public static FlinkShims getInstance(FlinkVersion flinkVersion,
                                       Properties properties) throws Exception {
    if (flinkShims == null) {
      flinkShims = loadShims(flinkVersion, properties);
    }
    return flinkShims;
  }

  public FlinkVersion getFlinkVersion() {
    return flinkVersion;
  }

  public abstract void initInnerBatchSqlInterpreter(FlinkSqlContext flinkSqlContext);

  public abstract void initInnerStreamSqlInterpreter(FlinkSqlContext flinkSqlContext);

  public abstract void disableSysoutLogging(Object batchConfig, Object streamConfig);

  public abstract Object createStreamExecutionEnvironmentFactory(Object streamExecutionEnvironment);

  public abstract Object createCatalogManager(Object config);

  public abstract String getPyFlinkPythonPath(Properties properties) throws IOException;

  public abstract Object getCollectStreamTableSink(InetAddress targetAddress,
                                                   int targetPort,
                                                   Object serializer);

  public abstract List collectToList(Object table) throws Exception;

  public abstract boolean rowEquals(Object row1, Object row2);

  public abstract Object fromDataSet(Object btenv, Object ds);

  public abstract Object toDataSet(Object btenv, Object table);

  public abstract void registerScalarFunction(Object btenv, String name, Object scalarFunction);

  public abstract void registerTableFunction(Object btenv, String name, Object tableFunction);

  public abstract void registerAggregateFunction(Object btenv, String name, Object aggregateFunction);

  public abstract void registerTableAggregateFunction(Object btenv, String name, Object tableAggregateFunction);

  public abstract void registerTableSink(Object stenv, String tableName, Object collectTableSink);

  public abstract void setCatalogManagerSchemaResolver(Object catalogManager,
                                                       Object parser,
                                                       Object environmentSetting);

  public abstract Object updateEffectiveConfig(Object cliFrontend, Object commandLine, Object executorConfig);

  public void setBatchRuntimeMode(Object tableConfig) {
    // only needed after flink 1.13
  }

  public void setOldPlanner(Object tableConfig) {
    // only needed after flink 1.13
  }

  public abstract String[] rowToString(Object row, Object table, Object tableConfig);

  public abstract boolean isTimeIndicatorType(Object type);

  public abstract ImmutablePair<Object, Object> createPlannerAndExecutor(
          ClassLoader classLoader, Object environmentSettings, Object sEnv,
          Object tableConfig, Object functionCatalog, Object catalogManager);

  public abstract InterpreterResult runSqlList(String st, InterpreterContext context, boolean isBatch);
}
