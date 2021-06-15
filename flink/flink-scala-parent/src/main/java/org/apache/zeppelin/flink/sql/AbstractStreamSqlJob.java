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


import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment;
import org.apache.flink.streaming.experimental.SocketStreamIterator;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.calcite.FlinkTypeFactory;
import org.apache.flink.table.sinks.RetractStreamTableSink;
import org.apache.flink.types.Row;
import org.apache.zeppelin.flink.FlinkShims;
import org.apache.zeppelin.flink.JobManager;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.apache.zeppelin.tabledata.TableDataUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Abstract class for all kinds of stream sql job
 *
 */
public abstract class AbstractStreamSqlJob {
  private static Logger LOGGER = LoggerFactory.getLogger(AbstractStreamSqlJob.class);

  private static AtomicInteger SQL_INDEX = new AtomicInteger(0);
  protected StreamExecutionEnvironment senv;
  protected TableEnvironment stenv;
  private Table table;
  protected JobManager jobManager;
  protected InterpreterContext context;
  protected TableSchema schema;
  protected SocketStreamIterator<Tuple2<Boolean, Row>> iterator;
  protected Object resultLock = new Object();
  protected volatile boolean enableToRefresh = true;
  protected int defaultParallelism;
  protected FlinkShims flinkShims;
  protected ScheduledExecutorService refreshScheduler = Executors.newScheduledThreadPool(1);

  public AbstractStreamSqlJob(StreamExecutionEnvironment senv,
                              TableEnvironment stenv,
                              JobManager jobManager,
                              InterpreterContext context,
                              int defaultParallelism,
                              FlinkShims flinkShims) {
    this.senv = senv;
    this.stenv = stenv;
    this.jobManager = jobManager;
    this.context = context;
    this.defaultParallelism = defaultParallelism;
    this.flinkShims = flinkShims;
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

  public String run(String st) throws IOException {
    this.table = stenv.sqlQuery(st);
    String tableName = "UnnamedTable_" +
            "_" + SQL_INDEX.getAndIncrement();
    return run(table, tableName);
  }

  public String run(Table table, String tableName) throws IOException {
    try {
      int parallelism = Integer.parseInt(context.getLocalProperties()
              .getOrDefault("parallelism", defaultParallelism + ""));
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
      RetractStreamTableSink collectTableSink =
              (RetractStreamTableSink) flinkShims.getCollectStreamTableSink(iterator.getBindAddress(), iterator.getPort(), serializer);
             // new CollectStreamTableSink(iterator.getBindAddress(), iterator.getPort(), serializer);
      collectTableSink = (RetractStreamTableSink) collectTableSink.configure(
              outputType.getFieldNames(), outputType.getFieldTypes());

      // workaround, otherwise it won't find the sink properly
      String originalCatalog = stenv.getCurrentCatalog();
      String originalDatabase = stenv.getCurrentDatabase();
      try {
        stenv.useCatalog("default_catalog");
        stenv.useDatabase("default_database");
        flinkShims.registerTableSink(stenv, tableName, collectTableSink);
        table.insertInto(tableName);
      } finally {
        stenv.useCatalog(originalCatalog);
        stenv.useDatabase(originalDatabase);
      }

      long delay = 1000L;
      long period = Long.parseLong(
              context.getLocalProperties().getOrDefault("refreshInterval", "3000"));
      refreshScheduler.scheduleAtFixedRate(new RefreshTask(context), delay, period, MILLISECONDS);

      ResultRetrievalThread retrievalThread = new ResultRetrievalThread(refreshScheduler);
      retrievalThread.start();

      LOGGER.info("Run job: " + tableName + ", parallelism: " + parallelism);
      String jobName = context.getStringLocalProperty("jobName", tableName);
      stenv.execute(jobName);
      LOGGER.info("Flink Job is finished, jobName: " + jobName);
      // wait for retrieve thread consume all data
      LOGGER.info("Waiting for retrieve thread to be done");
      retrievalThread.join();
      refresh(context);
      String finalResult = buildResult();
      LOGGER.info("Final Result: " + finalResult);
      return finalResult;
    } catch (Exception e) {
      LOGGER.error("Fail to run stream sql job", e);
      throw new IOException("Fail to run stream sql job", e);
    } finally {
      refreshScheduler.shutdownNow();
    }
  }

  protected void checkTableSchema(TableSchema schema) throws Exception {
  }

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

  protected abstract String buildResult();

  protected String tableToString(List<Row> rows) {
    StringBuilder builder = new StringBuilder();
    for (Row row : rows) {
      String[] fields = flinkShims.rowToString(row, table, stenv.getConfig());
      String rowString = Arrays.stream(fields)
              .map(TableDataUtils::normalizeColumn)
              .collect(Collectors.joining("\t"));
      builder.append(rowString);
      builder.append("\n");
    }
    return builder.toString();
  }

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
      } catch (Throwable e) {
        // ignore socket exceptions
        LOGGER.error("Fail to process record", e);
      }

      // no result anymore
      // either the job is done or an error occurred
      isRunning = false;
      LOGGER.info("ResultRetrieval Thread is done, isRunning={}, hasNext={}",
              isRunning, iterator.hasNext());
      LOGGER.info("Final Result: " + buildResult());
      refreshExecutorService.shutdownNow();
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
          if (!enableToRefresh) {
            resultLock.wait();
          }
          LOGGER.debug("Refresh result of paragraph: " + context.getParagraphId());
          refresh(context);
        }
      } catch (Exception e) {
        LOGGER.error("Fail to refresh task", e);
      }
    }
  }
}
