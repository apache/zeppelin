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

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Kylin interpreter for Zeppelin. (http://kylin.apache.org) */
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
  static final Pattern KYLIN_TABLE_FORMAT_REGEX_RESULTS =
      Pattern.compile("\"results\":\\[\\[(.*?)]]");

  public KylinInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {}

  @Override
  public void close() {}

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
  public void cancel(InterpreterContext context) {}

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public List<InterpreterCompletion> completion(
      String buf, int cursor, InterpreterContext interpreterContext) {
    return null;
  }

  public HttpResponse prepareRequest(String sql) throws IOException {
    String kylinProject = getProject(sql);
    String kylinSql = getSQL(sql);

    logger.info("project:" + kylinProject);
    logger.info("sql:" + kylinSql);
    logger.info("acceptPartial:" + getProperty(KYLIN_QUERY_ACCEPT_PARTIAL));
    logger.info("limit:" + getProperty(KYLIN_QUERY_LIMIT));
    logger.info("offset:" + getProperty(KYLIN_QUERY_OFFSET));
    byte[] encodeBytes =
        Base64.encodeBase64(
            new String(getProperty(KYLIN_USERNAME) + ":" + getProperty(KYLIN_PASSWORD))
                .getBytes("UTF-8"));

    String postContent =
        new String(
            "{\"project\":"
                + "\""
                + kylinProject
                + "\""
                + ","
                + "\"sql\":"
                + "\""
                + kylinSql
                + "\""
                + ","
                + "\"acceptPartial\":"
                + "\""
                + getProperty(KYLIN_QUERY_ACCEPT_PARTIAL)
                + "\""
                + ","
                + "\"offset\":"
                + "\""
                + getProperty(KYLIN_QUERY_OFFSET)
                + "\""
                + ","
                + "\"limit\":"
                + "\""
                + getProperty(KYLIN_QUERY_LIMIT)
                + "\""
                + "}");
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
    boolean isFirstLineProject = cmd.startsWith("(");

    if (isFirstLineProject) {
      int projectStartIndex = cmd.indexOf("(");
      int projectEndIndex = cmd.indexOf(")");
      if (projectStartIndex != -1 && projectEndIndex != -1) {
        return cmd.substring(projectStartIndex + 1, projectEndIndex);
      } else {
        return getProperty(KYLIN_QUERY_PROJECT);
      }
    } else {
      return getProperty(KYLIN_QUERY_PROJECT);
    }
  }

  public String getSQL(String cmd) {
    boolean isFirstLineProject = cmd.startsWith("(");

    if (isFirstLineProject) {
      int projectStartIndex = cmd.indexOf("(");
      int projectEndIndex = cmd.indexOf(")");
      if (projectStartIndex != -1 && projectEndIndex != -1) {
        return cmd.substring(projectEndIndex + 1);
      } else {
        return cmd;
      }
    } else {
      return cmd;
    }
  }

  private InterpreterResult executeQuery(String sql) throws IOException {
    HttpResponse response = prepareRequest(sql);
    String result;

    try {
      int code = response.getStatusLine().getStatusCode();
      result = IOUtils.toString(response.getEntity().getContent(), "UTF-8");

      if (code != 200) {
        StringBuilder errorMessage = new StringBuilder("Failed : HTTP error code " + code + " .");
        logger.error("Failed to execute query: " + result);

        KylinErrorResponse kylinErrorResponse = KylinErrorResponse.fromJson(result);
        if (kylinErrorResponse == null) {
          logger.error("Cannot get json from string: " + result);
          // when code is 401, the response is html, not json
          if (code == 401) {
            errorMessage.append(
                " Error message: Unauthorized. This request requires "
                    + "HTTP authentication. Please make sure your have set your credentials "
                    + "correctly.");
          } else {
            errorMessage.append(" Error message: " + result + " .");
          }
        } else {
          String exception = kylinErrorResponse.getException();
          logger.error("The exception is " + exception);
          errorMessage.append(" Error message: " + exception + " .");
        }

        return new InterpreterResult(InterpreterResult.Code.ERROR, errorMessage.toString());
      }
    } catch (NullPointerException | IOException e) {
      throw new IOException(e);
    }

    return new InterpreterResult(InterpreterResult.Code.SUCCESS, formatResult(result));
  }

  String formatResult(String msg) {
    StringBuilder res = new StringBuilder("%table ");

    Matcher ml = KYLIN_TABLE_FORMAT_REGEX_LABEL.matcher(msg);
    while (!ml.hitEnd() && ml.find()) {
      res.append(ml.group(1) + " \t");
    }
    res.append(" \n");

    Matcher mr = KYLIN_TABLE_FORMAT_REGEX_RESULTS.matcher(msg);
    String table = null;
    while (!mr.hitEnd() && mr.find()) {
      table = mr.group(1);
    }

    if (table != null && !table.isEmpty()) {
      String[] row = table.split("],\\[");
      for (int i = 0; i < row.length; i++) {
        String[] col = row[i].split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
        for (int j = 0; j < col.length; j++) {
          if (col[j] != null) {
            col[j] = col[j].replaceAll("^\"|\"$", "");
          }
          res.append(col[j] + " \t");
        }
        res.append(" \n");
      }
    }
    return res.toString();
  }
}
