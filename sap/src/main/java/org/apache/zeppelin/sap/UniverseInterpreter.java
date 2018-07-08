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

package org.apache.zeppelin.sap;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.sap.universe.*;


import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * SAP Universe interpreter for Zeppelin.
 */
public class UniverseInterpreter extends Interpreter {

  public UniverseInterpreter(Properties properties) {
    super(properties);
  }

  private UniverseClient client;
  private UniverseUtil universeUtil;
  private UniverseCompleter universeCompleter;

  private static final String EMPTY_COLUMN_VALUE = StringUtils.EMPTY;
  private static final char WHITESPACE = ' ';
  private static final char NEWLINE = '\n';
  private static final char TAB = '\t';
  private static final String TABLE_MAGIC_TAG = "%table ";

  @Override
  public void open() throws InterpreterException {
    String user = getProperty("universe.user");
    String password = getProperty("universe.password");
    String apiUrl = getProperty("universe.api.url");
    String authType = getProperty("universe.authType");
    this.client = new UniverseClient(user, password, apiUrl, authType);
    this.universeUtil = new UniverseUtil();
  }

  @Override
  public void close() throws InterpreterException {
    try {
      client.close();
    } catch (Exception e) {
      throw new InterpreterException(e.getCause());
    }
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context)
      throws InterpreterException {
    try {
      InterpreterResult interpreterResult = new InterpreterResult(InterpreterResult.Code.SUCCESS);
      String paragraphId = context.getParagraphId();
      String token = client.getToken(paragraphId);
      client.loadUniverses(token);
      UniverseQuery universeQuery = universeUtil.convertQuery(st, client, token);
      String queryId = client.createQuery(token, universeQuery);
      // process parameters
      List<UniverseQueryPrompt> parameters = client.getParameters(token, queryId);

      for (UniverseQueryPrompt parameter : parameters) {
        Object value = context.getGui().getParams().get(parameter.getName());
        if (value != null) {
          parameter.setValue(value.toString());
        }
        context.getGui().textbox(parameter.getName(), StringUtils.EMPTY);
      }

      if (!parameters.isEmpty() && parameters.size() != context.getGui().getParams().size()) {
        client.deleteQuery(token, queryId);
        interpreterResult.add("Set parameters");
        return interpreterResult;
      }

      if (!parameters.isEmpty()) {
        client.setParametersValues(token, queryId, parameters);
      }

      // get results
      List<List<String>> results = client.getResults(token, queryId);
      String table = formatResults(results);
      // remove query
      client.deleteQuery(token, queryId);
      interpreterResult.add(table);
      return interpreterResult;
    } catch (Exception e) {
      throw new InterpreterException(e.getMessage(), e);
    } finally {
      try {
        client.closeSession(context.getParagraphId());
      } catch (Exception e) {
        logger.error("Error close SAP session", e );
      }
    }
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    try {
      client.closeSession(context.getParagraphId());
    } catch (Exception e) {
      logger.error("Error close SAP session", e );
    }
  }

  @Override
  public FormType getFormType() throws InterpreterException {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return 0;
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor,
                                                InterpreterContext interpreterContext)
      throws InterpreterException {
    List<InterpreterCompletion> candidates = new ArrayList<>();

    try {
      universeCompleter = createOrUpdateUniverseCompleter(interpreterContext, buf, cursor);
      universeCompleter.complete(buf, cursor, candidates);
    } catch (UniverseException e) {
      logger.error("Error update completer", e );
    }

    return candidates;
  }

  private String formatResults(List<List<String>> results) {
    StringBuilder msg = new StringBuilder();
    if (results != null) {
      msg.append(TABLE_MAGIC_TAG);
      for (int i = 0; i < results.size(); i++) {
        List<String> items = results.get(i);
        for (int j = 0; j < items.size(); j++) {
          if (j > 0) {
            msg.append(TAB);
          }
          msg.append(replaceReservedChars(items.get(j)));
        }
        msg.append(NEWLINE);
      }
    }

    return msg.toString();
  }

  private String replaceReservedChars(String str) {
    if (str == null) {
      return EMPTY_COLUMN_VALUE;
    }
    return str.replace(TAB, WHITESPACE).replace(NEWLINE, WHITESPACE);
  }

  private UniverseCompleter createOrUpdateUniverseCompleter(InterpreterContext interpreterContext,
                                                            final String buf, final int cursor)
      throws UniverseException {
    final UniverseCompleter completer;
    if (universeCompleter == null) {
      completer = new UniverseCompleter(3600);
    } else {
      completer = universeCompleter;
    }
    try {
      final String token = client.getToken(interpreterContext.getParagraphId());
      ExecutorService executorService = Executors.newFixedThreadPool(1);
      executorService.execute(new Runnable() {
        @Override
        public void run() {
          completer.createOrUpdate(client, token, buf, cursor);
        }
      });

      executorService.shutdown();

      executorService.awaitTermination(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      logger.warn("Completion timeout", e);
    } finally {
      try {
        client.closeSession(interpreterContext.getParagraphId());
      } catch (Exception e) {
        logger.error("Error close SAP session", e );
      }
    }
    return completer;
  }
}
