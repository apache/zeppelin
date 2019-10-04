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

package org.apache.zeppelin.flink.sql;

import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment;
import org.apache.flink.streaming.experimental.SocketStreamIterator;
import org.apache.flink.table.api.StreamQueryConfig;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.api.scala.StreamTableEnvironment;
import org.apache.flink.table.calcite.FlinkTypeFactory;
import org.apache.flink.types.Row;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Abstract class for all kinds of stream sql job
 *
 */
public abstract class AbstractStreamSqlJob {
  private static Logger LOGGER = LoggerFactory.getLogger(AbstractStreamSqlJob.class);

  protected StreamExecutionEnvironment senv;
  protected StreamTableEnvironment stenv;
  protected InterpreterContext context;
  protected TableSchema schema;
  protected SocketStreamIterator<Tuple2<Boolean, Row>> iterator;
  protected Object resultLock = new Object();
  protected int defaultParallelism;

  public AbstractStreamSqlJob(StreamExecutionEnvironment senv,
                              StreamTableEnvironment stenv,
                              InterpreterContext context,
                              int defaultParallelism) {
    this.senv = senv;
    this.stenv = stenv;
    this.context = context;
    this.defaultParallelism = defaultParallelism;
  }

  private static TableSchema removeTimeAttributes(TableSchema schema) {
    final TableSchema.Builder builder = TableSchema.builder();
    for (int i = 0; i < schema.getFieldCount(); i++) {
      final TypeInformation<?> type = schema.getFieldTypes()[i];
      final TypeInformation<?> convertedType;
      if (FlinkTypeFactory.isTimeIndicatorType(type)) {
        convertedType = Types.SQL_TIMESTAMP;
      } else {
        convertedType = type;
      }
      builder.field(schema.getFieldNames()[i], convertedType);
    }
    return builder.build();
  }

  protected abstract String getType();

  public InterpreterResult run(String st) throws IOException {
    try {
      checkLocalProperties(context.getLocalProperties());

      int parallelism = Integer.parseInt(context.getLocalProperties()
              .getOrDefault("parallelism", defaultParallelism + ""));

      Table table = stenv.sqlQuery(st);
      this.schema = removeTimeAttributes(table.getSchema());
      checkTableSchema(schema);

      LOGGER.info("ResultTable Schema: " + this.schema);
      final RowTypeInfo outputType = new RowTypeInfo(schema.getFieldTypes(),
              schema.getFieldNames());

      // create socket stream iterator
      TypeInformation<Tuple2<Boolean, Row>> socketType = Types.TUPLE(Types.BOOLEAN, outputType);
      TypeSerializer<Tuple2<Boolean, Row>> serializer =
              socketType.createSerializer(senv.getConfig());

      // pass gateway port and address such that iterator knows where to bind to
      iterator = new SocketStreamIterator<>(0,
              InetAddress.getByName(RemoteInterpreterUtils.findAvailableHostAddress()),
              serializer);
      // create table sink
      // pass binding address and port such that sink knows where to send to
      LOGGER.debug("Collecting data at address: " + iterator.getBindAddress() +
              ":" + iterator.getPort());
      CollectStreamTableSink collectTableSink =
              new CollectStreamTableSink(iterator.getBindAddress(), iterator.getPort(), serializer);
      collectTableSink = collectTableSink.configure(
              outputType.getFieldNames(), outputType.getFieldTypes());

      // workaround, otherwise it won't find the sink properly
      String originalCatalog = stenv.getCurrentCatalog();
      String originalDatabase = stenv.getCurrentDatabase();
      try {
        stenv.useCatalog("default_catalog");
        stenv.useDatabase("default_database");
        stenv.registerTableSink(st, collectTableSink);
        table.insertInto(new StreamQueryConfig(), st);
      } finally {
        stenv.useCatalog(originalCatalog);
        stenv.useDatabase(originalDatabase);
      }

      ScheduledExecutorService refreshScheduler = Executors.newScheduledThreadPool(1);
      long delay = 1000L;
      long period = Long.parseLong(
              context.getLocalProperties().getOrDefault("refreshInterval", "3000"));
      refreshScheduler.scheduleAtFixedRate(new RefreshTask(context), delay, period, MILLISECONDS);

      ResultRetrievalThread retrievalThread = new ResultRetrievalThread(refreshScheduler);
      retrievalThread.start();

      LOGGER.info("Run job without savePointPath, " + ", parallelism: " + parallelism);
      JobExecutionResult jobExecutionResult = stenv.execute(st);
      LOGGER.info("Flink Job is finished");
      return new InterpreterResult(InterpreterResult.Code.SUCCESS);
    } catch (Exception e) {
      LOGGER.error("Fail to run stream sql job", e);
      throw new IOException("Fail to run stream sql job", e);
    }
  }

  protected void checkTableSchema(TableSchema schema) throws Exception {
  }

  protected void checkLocalProperties(Map<String, String> localProperties) throws Exception {
    List<String> validLocalProperties = getValidLocalProperties();
    for (String key : localProperties.keySet()) {
      if (!validLocalProperties.contains(key)) {
        throw new Exception("Invalid property: " + key + ", Only the following properties " +
                "are valid for stream type '" + getType() + "': " + validLocalProperties);
      }
    }
  };

  protected abstract List<String> getValidLocalProperties();

  protected void processRecord(Tuple2<Boolean, Row> change) {
    synchronized (resultLock) {
      // insert
      if (change.f0) {
        processInsert(change.f1);
      }
      // delete
      else {
        processDelete(change.f1);
      }
    }
  }

  protected abstract void processInsert(Row row);

  protected abstract void processDelete(Row row);

  private class ResultRetrievalThread extends Thread {

    private ScheduledExecutorService refreshExecutorService;
    volatile boolean isRunning = true;

    ResultRetrievalThread(ScheduledExecutorService refreshExecutorService) {
      this.refreshExecutorService = refreshExecutorService;
    }

    @Override
    public void run() {
      try {
        while (isRunning && iterator.hasNext()) {
          final Tuple2<Boolean, Row> change = iterator.next();
          processRecord(change);
        }
      } catch (Exception e) {
        // ignore socket exceptions
        LOGGER.error("Fail to process record", e);
      }

      // no result anymore
      // either the job is done or an error occurred
      isRunning = false;
      LOGGER.info("ResultRetrieval Thread is done");
      refreshExecutorService.shutdown();
    }

    public void cancel() {
      isRunning = false;
    }
  }

  protected abstract void refresh(InterpreterContext context) throws Exception;


  private class RefreshTask implements Runnable {

    private InterpreterContext context;

    RefreshTask(InterpreterContext context) {
      this.context = context;
    }

    @Override
    public void run() {
      try {
        synchronized (resultLock) {
          refresh(context);
        }
      } catch (Exception e) {
        LOGGER.error("Fail to refresh task", e);
      }
    }
  }
}
