/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.ksql;

import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class BasicKSQLHttpClient implements Closeable {

  interface BasicHTTPClientResponse {
    void onMessage(int status, String message);

    void onError(int status, String message);
  }

  private final String jsonData;
  private final Map<String, Object> formData;
  private final String type;
  private final Map<String, String> headers;
  private final URL url;
  private HttpURLConnection connection;
  private final int timeout;
  private boolean connected;


  public BasicKSQLHttpClient(String url, String jsonData, Map<String, Object> formData,
                 String type, Map<String, String> headers, int timeout)
      throws IOException {
    this.url = new URL(url);
    this.jsonData = jsonData;
    this.formData = formData;
    this.type = type;
    this.headers = headers;
    this.timeout = timeout;
    this.connected = false;
  }

  @Override
  public void close() throws IOException {
    connected = false;
    if (connection != null) {
      connection.disconnect();
    }
  }

  private void writeOutput(String data) throws IOException {
    try (OutputStream os = connection.getOutputStream()) {
      byte[] input = data.getBytes(StandardCharsets.UTF_8);
      os.write(input);
    }
  }

  public String connect() throws IOException {
    int status = createConnection();
    boolean isStatusOk = isStatusOk(status);
    return IOUtils.toString(isStatusOk ?
        connection.getInputStream() : connection.getErrorStream(), StandardCharsets.UTF_8.name());
  }

  public void connectAsync(BasicHTTPClientResponse onResponse) throws IOException {
    int status = createConnection();
    boolean isStatusOk = isStatusOk(status);
    long start = System.currentTimeMillis();

    try (InputStreamReader in = new InputStreamReader(connection.getInputStream(),
            StandardCharsets.UTF_8);
         BufferedReader br = new BufferedReader(in)) {
      while (connected && (timeout == -1 || System.currentTimeMillis() - start < timeout)) {
        if (br.ready()) {
          String responseLine = br.readLine();
          if (responseLine == null || responseLine.isEmpty()) {
            continue;
          }
          if (isStatusOk) {
            onResponse.onMessage(status, responseLine.trim());
          } else {
            onResponse.onError(status, responseLine.trim());
          }
        }
      }
    }
  }

  private boolean isStatusOk(int status) {
    return status >= 200 && status < 300;
  }

  private int createConnection() throws IOException {
    this.connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod(this.type);
    this.headers.forEach((k, v) -> connection.setRequestProperty(k, v));
    connection.setDoOutput(true);
    if (jsonData != null && !jsonData.isEmpty()) {
      writeOutput(jsonData);
    } else if (formData != null && !formData.isEmpty()) {
      String queryStringParams = formData.entrySet()
          .stream()
          .map(e -> e.getKey() + "=" + e.getValue())
          .collect(Collectors.joining("&"));
      writeOutput(queryStringParams);
    }
    connected = true;
    return connection.getResponseCode();
  }

  static class Builder {
    private String url;
    private String json;
    private Map<String, Object> formData = new HashMap<>();
    private String type;
    private Map<String, String> headers = new HashMap<>();
    private int timeout = -1;

    public Builder withTimeout(int timeout) {
      this.timeout = timeout;
      return this;
    }

    public Builder withUrl(String url) {
      this.url = url;
      return this;
    }

    public Builder withJson(String json) {
      this.json = json;
      return this;
    }

    public Builder withType(String type) {
      this.type = type;
      return this;
    }

    public Builder withHeader(String header, String value) {
      this.headers.put(header, value);
      return this;
    }

    public Builder withFormData(String name, Object value) {
      this.formData.put(name, value);
      return this;
    }

    public BasicKSQLHttpClient build() throws IOException {
      return new BasicKSQLHttpClient(url, json, formData, type, headers, timeout);
    }

  }
}
