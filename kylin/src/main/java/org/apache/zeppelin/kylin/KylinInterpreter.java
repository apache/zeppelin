/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.kylin;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kylin interpreter for Zeppelin. (http://kylin.apache.org)
 */
public class KylinInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(KylinInterpreter.class);

  static final String KYLIN_QUERY_API_URL = "kylin.api.url";
  static final String KYLIN_USERNAME = "kylin.api.user";
  static final String KYLIN_PASSWORD = "kylin.api.password";
  static final String KYLIN_QUERY_PROJECT = "kylin.query.project";
  static final String KYLIN_QUERY_OFFSET = "kylin.query.offset";
  static final String KYLIN_QUERY_LIMIT = "kylin.query.limit";
  static final String KYLIN_QUERY_ACCEPT_PARTIAL = "kylin.query.ispartial";
  static final Pattern KYLIN_TABLE_FORMAT_REGEX_LABEL = Pattern.compile("\"label\":\"(.*?)\"");
  static final Pattern KYLIN_TABLE_FORMAT_REGEX = Pattern.compile("\"results\":\\[\\[\"(.*?)\"]]");

  public KylinInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {

  }

  @Override
  public void close() {

  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    try {
      return executeQuery(st);
    } catch (IOException e) {
      logger.error("failed to query data in kylin ", e);
      return new InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage());
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

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor) {
    return null;
  }

  public HttpResponse prepareRequest(String sql) throws IOException {
    String kylinProject = getProject(KYLIN_QUERY_PROJECT);

    logger.info("project:" + kylinProject);
    logger.info("sql:" + sql);
    logger.info("acceptPartial:" + getProperty(KYLIN_QUERY_ACCEPT_PARTIAL));
    logger.info("limit:" + getProperty(KYLIN_QUERY_LIMIT));
    logger.info("offset:" + getProperty(KYLIN_QUERY_OFFSET));
    byte[] encodeBytes = Base64.encodeBase64(new String(getProperty(KYLIN_USERNAME)
        + ":" + getProperty(KYLIN_PASSWORD)).getBytes("UTF-8"));

    String postContent = new String("{\"project\":" + "\"" + kylinProject + "\""
        + "," + "\"sql\":" + "\"" + sql + "\""
        + "," + "\"acceptPartial\":" + "\"" + getProperty(KYLIN_QUERY_ACCEPT_PARTIAL) + "\""
        + "," + "\"offset\":" + "\"" + getProperty(KYLIN_QUERY_OFFSET) + "\""
        + "," + "\"limit\":" + "\"" + getProperty(KYLIN_QUERY_LIMIT) + "\"" + "}");
    logger.info("post:" + postContent);
    postContent = postContent.replaceAll("[\u0000-\u001f]", " ");
    StringEntity entity = new StringEntity(postContent, "UTF-8");
    entity.setContentType("application/json; charset=UTF-8");

    logger.info("post url:" + getProperty(KYLIN_QUERY_API_URL));

    HttpPost postRequest = new HttpPost(getProperty(KYLIN_QUERY_API_URL));
    postRequest.setEntity(entity);
    postRequest.addHeader("Authorization", "Basic " + new String(encodeBytes));
    postRequest.addHeader("Accept-Encoding", "UTF-8");

    HttpClient httpClient = HttpClientBuilder.create().build();
    return httpClient.execute(postRequest);
  }

  public String getProject(String cmd) {
    boolean firstLineIndex = cmd.startsWith("(");

    if (firstLineIndex) {
      int configStartIndex = cmd.indexOf("(");
      int configLastIndex = cmd.indexOf(")");
      if (configStartIndex != -1 && configLastIndex != -1) {
        return cmd.substring(configStartIndex + 1, configLastIndex);
      } else {
        return getProperty(KYLIN_QUERY_PROJECT);
      }
    } else {
      return getProperty(KYLIN_QUERY_PROJECT);
    }
  }

  private InterpreterResult executeQuery(String sql) throws IOException {

    HttpResponse response = prepareRequest(sql);

    if (response.getStatusLine().getStatusCode() != 200) {
      logger.error("failed to execute query: " + response.getEntity().getContent().toString());
      return new InterpreterResult(InterpreterResult.Code.ERROR,
          "Failed : HTTP error code " + response.getStatusLine().getStatusCode());
    }

    BufferedReader br = new BufferedReader(
        new InputStreamReader((response.getEntity().getContent())));
    StringBuilder sb = new StringBuilder();

    String output;
    logger.info("Output from Server .... \n");
    while ((output = br.readLine()) != null) {
      logger.info(output);
      sb.append(output).append('\n');
    }
    InterpreterResult rett = new InterpreterResult(InterpreterResult.Code.SUCCESS, 
        formatResult(sb.toString()));
    return rett;
  }

  private String formatResult(String msg) {
    StringBuilder res = new StringBuilder("%table ");
    
    Matcher ml = KYLIN_TABLE_FORMAT_REGEX_LABEL.matcher(msg);
    while (!ml.hitEnd() && ml.find()) {
      res.append(ml.group(1) + " \t");
    } 
    res.append(" \n");
    
    Matcher mr = KYLIN_TABLE_FORMAT_REGEX.matcher(msg);
    String table = null;
    while (!mr.hitEnd() && mr.find()) {
      table = mr.group(1);
    }

    String[] row = table.split("\"],\\[\"");
    for (int i = 0; i < row.length; i++) {
      String[] col = row[i].split("\",\"");
      for (int j = 0; j < col.length; j++) {
        res.append(col[j] + " \t");
      }
      res.append(" \n");
    }
    return res.toString();
  }

}
