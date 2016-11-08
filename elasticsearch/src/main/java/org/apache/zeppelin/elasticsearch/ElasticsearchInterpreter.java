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

package org.apache.zeppelin.elasticsearch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


/**
 * Elasticsearch Interpreter for Zeppelin.
 */
public class ElasticsearchInterpreter extends Interpreter {

  private static Logger logger = LoggerFactory.getLogger(ElasticsearchInterpreter.class);


  protected static final List<String> COMMANDS = Arrays.asList(
      "count", "delete", "get", "help", "index", "search");

  private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

  private ElasticsearchConnector connector;

  public ElasticsearchInterpreter(Properties property) {
    super(property);
    // should be called after set parent's props
    Properties merged = getProperty();
    connector = Elasticsearch2Connector.create(merged);
  }

  @Override
  public void open() {
    connector.connect(getProperty());
  }

  @Override
  public void close() {
    connector.release();
  }

  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext interpreterContext) {
    logger.info("Run Elasticsearch command '" + cmd + "'");

    if (StringUtils.isEmpty(cmd) || StringUtils.isEmpty(cmd.trim())) {
      return new InterpreterResult(InterpreterResult.Code.SUCCESS);
    }

    if (null == connector) {
      return new InterpreterResult(InterpreterResult.Code.ERROR,
          "Problem with the Elasticsearch client, " +
              "please check your configuration (host, port,...)");
    }

    int currentResultSize = connector.getResultSize();
    String[] items = StringUtils.split(cmd.trim(), " ", 3);

    // Process some specific commands (help, size, ...)
    if ("help".equalsIgnoreCase(items[0])) {
      return processHelp(InterpreterResult.Code.SUCCESS, null);
    }

    if ("size".equalsIgnoreCase(items[0])) {
      // In this case, the line with size must be followed by a search,
      // so we will continue with the next lines
      final String[] lines = StringUtils.split(cmd.trim(), "\n", 2);

      if (lines.length < 2) {
        return processHelp(InterpreterResult.Code.ERROR,
            "Size cmd must be followed by a search");
      }

      final String[] sizeLine = StringUtils.split(lines[0], " ", 2);
      if (sizeLine.length != 2) {
        return processHelp(InterpreterResult.Code.ERROR,
            "Right format is : size <value>");
      }
      currentResultSize = Integer.parseInt(sizeLine[1]);
      items = StringUtils.split(lines[1].trim(), " ", 3);
    }

    if (items.length < 2) {
      return processHelp(InterpreterResult.Code.ERROR, "Arguments missing");
    }

    final String method = items[0];
    final String url = items[1];
    final String data = items.length > 2 ? items[2].trim() : null;
    final String[] urlItems = StringUtils.split(url.trim(), "/");

    return handleElasticsearchCommands(method, urlItems, data, currentResultSize);
  }

  private InterpreterResult handleElasticsearchCommands(String method, String[] urlItems,
                                                        String data, int currentResultSize) {
    try {
      if ("get".equalsIgnoreCase(method)) {
        return processGet(urlItems);
      } else if ("count".equalsIgnoreCase(method)) {
        return processCount(urlItems, data);
      } else if ("index".equalsIgnoreCase(method)) {
        return processIndex(urlItems, data);
      } else if ("delete".equalsIgnoreCase(method)) {
        return processDelete(urlItems);
      } else if ("search".equalsIgnoreCase(method)) {
        return processSearch(urlItems, data, currentResultSize);
      }

      return processHelp(InterpreterResult.Code.ERROR, "Unknown command");
    } catch (Exception e) {
      return new InterpreterResult(InterpreterResult.Code.ERROR, "Error : " + e.getMessage());
    }
  }

  @Override
  public void cancel(InterpreterContext interpreterContext) {
    // Nothing to do
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext interpreterContext) {
    return 0;
  }

  @Override
  public List<InterpreterCompletion> completion(String s, int i) {
    final List suggestions = new ArrayList<>();

    for (String cmd : COMMANDS) {
      if (cmd.toLowerCase().contains(s)) {
        suggestions.add(new InterpreterCompletion(cmd, cmd));
      }
    }
    return suggestions;
  }

  private InterpreterResult processHelp(InterpreterResult.Code code, String additionalMessage) {
    String message = ElasticsearchConnector.getHelpString(additionalMessage);
    return new InterpreterResult(code, InterpreterResult.Type.TEXT, message);
  }

  private InterpreterResult processGet(String[] urlItems) {
    String result = connector.executeGetQuery(urlItems);
    return new InterpreterResult(
        InterpreterResult.Code.SUCCESS,
        InterpreterResult.Type.TEXT,
        result);
  }

  private InterpreterResult processCount(String[] urlItems, String data) {
    return new InterpreterResult(
        InterpreterResult.Code.SUCCESS,
        InterpreterResult.Type.TEXT,
        connector.executeCountQuery(urlItems, data));
  }

  /**
   * Processes a "search" request.
   *
   * @param urlItems Items of the URL
   * @param data     May contains the JSON of the request
   * @param size     Limit of output set
   * @return Result of the search request, it contains a tab-formatted string of the matching hits
   */
  private InterpreterResult processSearch(String[] urlItems, String data, int size) {
    TypedElasticConnectorResult result = connector
        .executeSearchQuery(urlItems, data, size);

    return new InterpreterResult(
        InterpreterResult.Code.SUCCESS,
        result.type,
        result.output);
  }

  /**
   * Processes a "index" request.
   *
   * @param urlItems Items of the URL
   * @param data     JSON to be indexed
   * @return Result of the index request, it contains the id of the document
   */
  private InterpreterResult processIndex(String[] urlItems, String data) {
    return new InterpreterResult(
        InterpreterResult.Code.SUCCESS,
        InterpreterResult.Type.TEXT,
        connector.executeIndexQuery(urlItems, data));
  }

  /**
   * Processes a "delete" request.
   *
   * @param urlItems Items of the URL
   * @return Result of the delete request, it contains the id of the deleted document
   */
  private InterpreterResult processDelete(String[] urlItems) {
    return new InterpreterResult(
        InterpreterResult.Code.SUCCESS,
        InterpreterResult.Type.TEXT,
        connector.executeDeleteQuery(urlItems));
  }
}
