/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.zeppelin.influxdb;

import com.influxdb.query.FluxRecord;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import com.influxdb.LogLevel;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.InfluxDBClientOptions;
import com.influxdb.client.QueryApi;
import java.util.stream.Collectors;
import org.apache.zeppelin.interpreter.AbstractInterpreter;
import org.apache.zeppelin.interpreter.ZeppelinContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;

/**
 * <a href="https://v2.docs.influxdata.com/v2.0/">InfluxDB 2.0</a> interpreter for Zeppelin.
 * It uses /v2/query API, query is written in Flux Language.
 * <p>
 * How to use: <br/>
 * <pre>
 * {@code
 * %influxdb
 * from(bucket: "my-bucket")
 *   |> range(start: -5m)
 *   |> filter(fn: (r) => r._measurement == "cpu")
 *   |> filter(fn: (r) => r._field == "usage_user")
 *   |> filter(fn: (r) => r.cpu == "cpu-total")
 * }
 * </pre>
 * </p>
 */
public class InfluxDBInterpreter extends AbstractInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(InfluxDBInterpreter.class);

  private static final String INFLUXDB_API_URL_PROPERTY = "influxdb.url";
  private static final String INFLUXDB_TOKEN_PROPERTY = "influxdb.token";
  private static final String INFLUXDB_ORG_PROPERTY = "influxdb.org";
  private static final String INFLUXDB_LOGLEVEL_PROPERTY = "influxdb.logLevel";

  private static final String TABLE_MAGIC_TAG = "%table ";
  private static final String WHITESPACE = " ";
  private static final String NEWLINE = "\n";
  private static final String TAB = "\t";
  private static final String EMPTY_COLUMN_VALUE = "";

  private volatile InfluxDBClient client;
  private volatile QueryApi queryApi;

  public InfluxDBInterpreter(Properties properties) {
    super(properties);
  }

  @Override
  public ZeppelinContext getZeppelinContext() {
    return null;
  }

  @Override
  protected InterpreterResult internalInterpret(String query, InterpreterContext context)
      throws InterpreterException {

    LOGGER.debug("Run Flux command '{}'", query);
    query = query.trim();

    QueryApi queryService = getQueryApi();

    final int[] currentTableIndex = {-1};

    AtomicReference<InterpreterResult> resultRef = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);

    StringBuilder resultBuilder = new StringBuilder();
    queryService.query(
        query,

        //process record
        (cancellable, fluxRecord) -> handleRecord(fluxRecord, currentTableIndex, resultBuilder),
        throwable -> handleError(throwable, resultRef, countDownLatch),
        () -> handleComplete(resultBuilder, resultRef, countDownLatch)
    );

    awaitLatch(countDownLatch);

    return resultRef.get();
  }

  private void handleRecord(FluxRecord fluxRecord, int[] currentTableIndex,
      StringBuilder resultBuilder) {
    Integer tableIndex = fluxRecord.getTable();
    if (currentTableIndex[0] != tableIndex) {
      appendTableHeader(fluxRecord, resultBuilder);
      currentTableIndex[0] = tableIndex;
    }

    appendTableRow(fluxRecord, resultBuilder);
  }

  private void appendTableHeader(FluxRecord fluxRecord, StringBuilder resultBuilder) {
    resultBuilder.append(NEWLINE).append(TABLE_MAGIC_TAG);
    String headerLine = fluxRecord.getValues().keySet().stream()
        .map(this::replaceReservedChars)
        .collect(Collectors.joining(TAB));
    resultBuilder.append(headerLine).append(NEWLINE);
  }

  private void appendTableRow(FluxRecord fluxRecord, StringBuilder resultBuilder) {
    String rowLine = fluxRecord.getValues().values().stream()
        .map(v -> v == null ? EMPTY_COLUMN_VALUE : v.toString())
        .map(this::replaceReservedChars)
        .collect(Collectors.joining(TAB));
    resultBuilder.append(rowLine).append(NEWLINE);
  }

  private static void handleError(Throwable throwable, AtomicReference<InterpreterResult> resultRef,
      CountDownLatch countDownLatch) {
    LOGGER.error(throwable.getMessage(), throwable);
    resultRef.set(new InterpreterResult(InterpreterResult.Code.ERROR,
        throwable.getMessage()));

    countDownLatch.countDown();
  }

  private static void handleComplete(StringBuilder resultBuilder,
      AtomicReference<InterpreterResult> resultRef,
      CountDownLatch countDownLatch) {
    InterpreterResult intpResult = new InterpreterResult(InterpreterResult.Code.SUCCESS);
    intpResult.add(resultBuilder.toString());
    resultRef.set(intpResult);
    countDownLatch.countDown();
  }

  private static void awaitLatch(CountDownLatch countDownLatch) throws InterpreterException {
    try {
      countDownLatch.await();
    } catch (InterruptedException e) {
      throw new InterpreterException(e);
    }
  }


  private QueryApi getQueryApi() {
    if (queryApi == null) {
      queryApi = this.client.getQueryApi();
    }
    return queryApi;
  }


  @Override
  public void open() {

    if (this.client == null) {
      InfluxDBClientOptions opt = InfluxDBClientOptions.builder()
          .url(getProperty(INFLUXDB_API_URL_PROPERTY))
          .authenticateToken(getProperty(INFLUXDB_TOKEN_PROPERTY).toCharArray())
          .logLevel(LogLevel.valueOf(
              getProperty(INFLUXDB_LOGLEVEL_PROPERTY, LogLevel.NONE.toString())))
          .org(getProperty(INFLUXDB_ORG_PROPERTY))
          .build();

      this.client = InfluxDBClientFactory.create(opt);
    }
  }

  @Override
  public void close() {
    if (this.client != null) {
      this.client.close();
      this.client = null;
    }
  }

  @Override
  public void cancel(InterpreterContext context) {

  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  /**
   * For %table response replace Tab and Newline.
   */
  private String replaceReservedChars(String str) {
    if (str == null) {
      return EMPTY_COLUMN_VALUE;
    }
    return str.replace(TAB, WHITESPACE).replace(NEWLINE, WHITESPACE);
  }

}
