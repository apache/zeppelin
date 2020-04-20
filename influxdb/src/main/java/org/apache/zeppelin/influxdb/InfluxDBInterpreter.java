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

import java.util.Properties;
import java.util.StringJoiner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import com.influxdb.LogLevel;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.InfluxDBClientOptions;
import com.influxdb.client.QueryApi;
import org.apache.zeppelin.interpreter.AbstractInterpreter;
import org.apache.zeppelin.interpreter.ZeppelinContext;
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

    logger.debug("Run Flux command '{}'", query);
    query = query.trim();

    QueryApi queryService = getInfluxDBClient(context);

    final int[] actualIndex = {-1};

    AtomicReference<InterpreterResult> resultRef = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);

    StringBuilder result = new StringBuilder();
    queryService.query(
        query,

        //process record
        (cancellable, fluxRecord) -> {

          Integer tableIndex = fluxRecord.getTable();
          if (actualIndex[0] != tableIndex) {
            result.append(NEWLINE);
            result.append(TABLE_MAGIC_TAG);
            actualIndex[0] = tableIndex;

            //add column names to table header
            StringJoiner joiner = new StringJoiner(TAB);
            fluxRecord.getValues().keySet().forEach(c -> joiner.add(replaceReservedChars(c)));
            result.append(joiner.toString());
            result.append(NEWLINE);
          }

          StringJoiner rowsJoiner = new StringJoiner(TAB);
          for (Object value : fluxRecord.getValues().values()) {
            if (value == null) {
              value = EMPTY_COLUMN_VALUE;
            }
            rowsJoiner.add(replaceReservedChars(value.toString()));
          }
          result.append(rowsJoiner.toString());
          result.append(NEWLINE);
        },

        throwable -> {

          logger.error(throwable.getMessage(), throwable);
          resultRef.set(new InterpreterResult(InterpreterResult.Code.ERROR,
              throwable.getMessage()));

          countDownLatch.countDown();

        }, () -> {
          //on complete
          InterpreterResult intpResult = new InterpreterResult(InterpreterResult.Code.SUCCESS);
          intpResult.add(result.toString());
          resultRef.set(intpResult);
          countDownLatch.countDown();
        }
    );
    try {
      countDownLatch.await();
    } catch (InterruptedException e) {
      throw new InterpreterException(e);
    }

    return resultRef.get();
  }


  private QueryApi getInfluxDBClient(InterpreterContext context) {
    if (queryApi == null) {
      queryApi = this.client.getQueryApi();
    }
    return queryApi;
  }


  @Override
  public void open() throws InterpreterException {

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
  public void close() throws InterpreterException {
    if (this.client != null) {
      this.client.close();
      this.client = null;
    }
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {

  }

  @Override
  public FormType getFormType() throws InterpreterException {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
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
