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

package org.apache.zeppelin.spark;

import org.apache.commons.lang3.StringUtils;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

public class SparkConnectUtils {

  static final int DEFAULT_TRUNCATE_LENGTH = 256;

  private SparkConnectUtils() {
  }

  /**
   * Build the Spark Connect connection string from interpreter properties.
   * Format: sc://hostname:port[/;param1=val1;param2=val2]
   *
   * Supports: token, use_ssl, user_id, and any extra params already in the URI.
   * Examples:
   *   sc://localhost:15002
   *   sc://localhost:15002/;use_ssl=true;token=abc123;user_id=alice
   *   sc://ranking-cluster-m:8080
   */
  public static String buildConnectionString(Properties properties) {
    return buildConnectionString(properties, null);
  }

  /**
   * Build the Spark Connect connection string, including user_id so the Spark Connect
   * server can attribute the session to the correct user in its own UI.
   *
   * @param properties interpreter properties
   * @param userName   the authenticated Zeppelin username; ignored if blank
   */
  public static String buildConnectionString(Properties properties, String userName) {
    String remote = properties.getProperty("spark.remote", "sc://localhost:15002");
    StringBuilder params = new StringBuilder();

    String token = properties.getProperty("spark.connect.token", "");
    if (StringUtils.isNotBlank(token)) {
      params.append(";token=").append(token);
    }

    boolean useSsl = Boolean.parseBoolean(
        properties.getProperty("spark.connect.use_ssl", "false"));
    if (useSsl) {
      params.append(";use_ssl=true");
    }

    if (StringUtils.isNotBlank(userName) && !remote.contains("user_id=")) {
      params.append(";user_id=").append(userName);
    }

    if (params.length() > 0) {
      if (remote.contains(";")) {
        remote = remote + params;
      } else {
        remote = remote + "/" + params;
      }
    }
    return remote;
  }

  /**
   * Convert a Dataset<Row> to Zeppelin's %table format string.
   * Applies limit before collecting to prevent OOM on the driver.
   * Truncates cell values to avoid excessively wide output.
   */
  public static String showDataFrame(Dataset<Row> df, int maxResult) {
    return showDataFrame(df, maxResult, DEFAULT_TRUNCATE_LENGTH);
  }

  public static String showDataFrame(Dataset<Row> df, int maxResult, int truncateLength) {
    StructType schema = df.schema();
    StructField[] fields = schema.fields();

    int effectiveLimit = Math.max(1, Math.min(maxResult, 100_000));

    int estimatedRowSize = Math.max(fields.length * 20, 100);
    int estimatedTotalBytes = estimatedRowSize * effectiveLimit;
    StringBuilder sb = new StringBuilder(Math.min(estimatedTotalBytes, 10 * 1024 * 1024));
    sb.append("%table ");

    for (int i = 0; i < fields.length; i++) {
      if (i > 0) {
        sb.append('\t');
      }
      sb.append(replaceReservedChars(fields[i].name()));
    }
    sb.append('\n');

    List<Row> rows = df.limit(effectiveLimit).collectAsList();
    for (Row row : rows) {
      for (int i = 0; i < row.length(); i++) {
        if (i > 0) {
          sb.append('\t');
        }
        Object value = row.get(i);
        String cellStr = value == null ? "null" : value.toString();
        if (truncateLength > 0 && cellStr.length() > truncateLength) {
          cellStr = cellStr.substring(0, truncateLength) + "...";
        }
        sb.append(replaceReservedChars(cellStr));
      }
      sb.append('\n');
    }

    return sb.toString();
  }

  /**
   * Stream a Dataset<Row> as Zeppelin %table format directly to an OutputStream,
   * avoiding building the entire result in memory.
   * Preferred for large result sets.
   */
  public static void streamDataFrame(Dataset<Row> df, int maxResult, OutputStream out)
      throws IOException {
    streamDataFrame(df, maxResult, DEFAULT_TRUNCATE_LENGTH, out);
  }

  public static void streamDataFrame(Dataset<Row> df, int maxResult,
      int truncateLength, OutputStream out) throws IOException {
    StructType schema = df.schema();
    StructField[] fields = schema.fields();

    int effectiveLimit = Math.max(1, Math.min(maxResult, 100_000));

    StringBuilder header = new StringBuilder("%table ");
    for (int i = 0; i < fields.length; i++) {
      if (i > 0) {
        header.append('\t');
      }
      header.append(replaceReservedChars(fields[i].name()));
    }
    header.append('\n');
    out.write(header.toString().getBytes(StandardCharsets.UTF_8));

    Iterator<Row> it = df.limit(effectiveLimit).toLocalIterator();
    StringBuilder rowBuf = new StringBuilder(256);
    while (it.hasNext()) {
      rowBuf.setLength(0);
      Row row = it.next();
      for (int i = 0; i < row.length(); i++) {
        if (i > 0) {
          rowBuf.append('\t');
        }
        Object value = row.get(i);
        String cellStr = value == null ? "null" : value.toString();
        if (truncateLength > 0 && cellStr.length() > truncateLength) {
          cellStr = cellStr.substring(0, truncateLength) + "...";
        }
        rowBuf.append(replaceReservedChars(cellStr));
      }
      rowBuf.append('\n');
      out.write(rowBuf.toString().getBytes(StandardCharsets.UTF_8));
    }
    out.flush();
  }

  static String replaceReservedChars(String str) {
    if (str == null) {
      return "null";
    }
    return str.replace('\t', ' ').replace('\n', ' ');
  }
}
