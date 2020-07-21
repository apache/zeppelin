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


import org.apache.zeppelin.flink.sql.SqlCommandParser;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * This is abstract class for anything that is api incompatible between different flink versions. It will
 * load the correct version of FlinkShims based on the version of flink.
 */
public abstract class FlinkShims {

  private static final Logger LOGGER = LoggerFactory.getLogger(FlinkShims.class);

  private static FlinkShims flinkShims;

  protected Properties properties;

  public FlinkShims(Properties properties) {
    this.properties = properties;
  }

  private static FlinkShims loadShims(FlinkVersion flinkVersion, Properties properties)
      throws Exception {
    Class<?> flinkShimsClass;
    if (flinkVersion.getMajorVersion() == 1 && flinkVersion.getMinorVersion() == 10) {
      LOGGER.info("Initializing shims for Flink 1.10");
      flinkShimsClass = Class.forName("org.apache.zeppelin.flink.Flink110Shims");
    } else if (flinkVersion.getMajorVersion() == 1 && flinkVersion.getMinorVersion() >= 11) {
      LOGGER.info("Initializing shims for Flink 1.11");
      flinkShimsClass = Class.forName("org.apache.zeppelin.flink.Flink111Shims");
    } else {
      throw new Exception("Flink version: '" + flinkVersion + "' is not supported yet");
    }

    Constructor c = flinkShimsClass.getConstructor(Properties.class);
    return (FlinkShims) c.newInstance(properties);
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

  protected static AttributedString formatCommand(SqlCommandParser.SqlCommand cmd, String description) {
    return new AttributedStringBuilder()
            .style(AttributedStyle.DEFAULT.bold())
            .append(cmd.toString())
            .append("\t\t")
            .style(AttributedStyle.DEFAULT)
            .append(description)
            .append('\n')
            .toAttributedString();
  }

  public abstract Object createCatalogManager(Object config);

  public abstract String getPyFlinkPythonPath(Properties properties) throws IOException;

  public abstract Object getCollectStreamTableSink(InetAddress targetAddress,
                                                   int targetPort,
                                                   Object serializer);

  public abstract List collectToList(Object table) throws Exception;

  public abstract void startMultipleInsert(Object tblEnv, InterpreterContext context) throws Exception;

  public abstract void addInsertStatement(String sql, Object tblEnv, InterpreterContext context) throws Exception;

  public abstract boolean executeMultipleInsertInto(String jobName, Object tblEnv, InterpreterContext context) throws Exception;

  public abstract boolean rowEquals(Object row1, Object row2);

  public abstract Object fromDataSet(Object btenv, Object ds);

  public abstract Object toDataSet(Object btenv, Object table);

  public abstract void registerTableFunction(Object btenv, String name, Object tableFunction);

  public abstract void registerAggregateFunction(Object btenv, String name, Object aggregateFunction);

  public abstract void registerTableAggregateFunction(Object btenv, String name, Object tableAggregateFunction);

  public abstract void registerTableSink(Object stenv, String tableName, Object collectTableSink);

  public abstract Optional<SqlCommandParser.SqlCommandCall> parseSql(Object tableEnv, String stmt);

  public abstract void executeSql(Object tableEnv, String sql);

  public abstract String sqlHelp();

  public abstract void setCatalogManagerSchemaResolver(Object catalogManager,
                                                       Object parser,
                                                       Object environmentSetting);

  public abstract Object getCustomCli(Object cliFrontend, Object commandLine);

  public abstract Map extractTableConfigOptions();
}
