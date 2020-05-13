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

import org.apache.commons.compress.utils.Lists;
import org.apache.flink.api.common.JobStatus;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.scala.DataSet;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.core.execution.JobClient;
import org.apache.flink.table.api.StatementSet;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.bridge.java.internal.StreamTableEnvironmentImpl;
import org.apache.flink.table.api.bridge.scala.BatchTableEnvironment;
import org.apache.flink.table.catalog.CatalogManager;
import org.apache.flink.table.catalog.GenericInMemoryCatalog;
import org.apache.flink.table.functions.AggregateFunction;
import org.apache.flink.table.functions.TableAggregateFunction;
import org.apache.flink.table.functions.TableFunction;
import org.apache.flink.table.sinks.TableSink;
import org.apache.flink.types.Row;
import org.apache.flink.types.RowKind;
import org.apache.zeppelin.flink.shims111.CollectStreamTableSink;
import org.apache.zeppelin.flink.shims111.Flink111ScalaShims;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Shims for flink 1.11
 */
public class Flink111Shims extends FlinkShims {

  private static final Logger LOGGER = LoggerFactory.getLogger(Flink111Shims.class);

  private Map<String, StatementSet> statementSetMap = new ConcurrentHashMap<>();

  public Flink111Shims(Properties properties) {
    super(properties);
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
    String flinkHome = System.getenv("FLINK_HOME");
    if (flinkHome != null) {
      List<File> depFiles = null;
      depFiles = Arrays.asList(new File(flinkHome + "/opt/python").listFiles());
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
  public boolean executeMultipleInsertInto(String sql, Object tblEnv, InterpreterContext context) throws Exception {
    JobClient jobClient = statementSetMap.get(context.getParagraphId()).execute().getJobClient().get();
    while(!jobClient.getJobStatus().get().isTerminalState()) {
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
    return Flink111ScalaShims.fromDataSet((BatchTableEnvironment) btenv, (DataSet) ds);
  }

  @Override
  public Object toDataSet(Object btenv, Object table) {
    return Flink111ScalaShims.toDataSet((BatchTableEnvironment) btenv, (Table) table);
  }

  @Override
  public void registerTableSink(Object stenv, String tableName, Object collectTableSink) {
    ((org.apache.flink.table.api.internal.TableEnvironmentInternal) stenv)
            .registerTableSinkInternal(tableName, (TableSink) collectTableSink);
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

}
