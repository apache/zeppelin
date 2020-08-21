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

package org.apache.zeppelin.client;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Each ZSession represent one interpreter process, you can start/stop it, and execute/submit/cancel code.
 * There's no Zeppelin concept(like note/paragraph) in ZSession.
 *
 */
public class ZSession {
  private static final Logger LOGGER = LoggerFactory.getLogger(ZSession.class);

  private ZeppelinClient zeppelinClient;
  private String interpreter;
  private Map<String, String> intpProperties;
  // max number of retained statements, each statement represent one paragraph.
  private int maxStatement;

  private String sessionId;
  private String noteId;
  private String weburl;

  private ZeppelinWebSocketClient webSocketClient;

  public ZSession(ClientConfig clientConfig,
                  String interpreter) throws Exception {
    this(clientConfig, interpreter, new HashMap<>(), 100);
  }

  public ZSession(ClientConfig clientConfig,
                  String interpreter,
                  Map<String, String> intpProperties,
                  int maxStatement) throws Exception {
    this.zeppelinClient = new ZeppelinClient(clientConfig);
    this.interpreter = interpreter;
    this.intpProperties = intpProperties;
    this.maxStatement = maxStatement;
  }

  /**
   * Start this ZSession, underneath it would create a note for this ZSession and
   * start a dedicated interpreter group.
   * This method won't establish websocket connection.
   *
   * @throws Exception
   */
  public void start() throws Exception {
    start(null);
  }

  /**
   * Start this ZSession, underneath it would create a note for this ZSession and
   * start a dedicated interpreter process.
   *
   * @param messageHandler
   * @throws Exception
   */
  public void start(MessageHandler messageHandler) throws Exception {
    this.sessionId = zeppelinClient.newSession(interpreter);

    this.noteId = zeppelinClient.createNote("/_ZSession/" + interpreter + "/" + sessionId);
    // inline configuration
    StringBuilder builder = new StringBuilder("%" + interpreter + ".conf\n");
    if (intpProperties != null) {
      for (Map.Entry<String, String> entry : intpProperties.entrySet()) {
        builder.append(entry.getKey() + " " + entry.getValue() + "\n");
      }
    }
    String paragraphId = zeppelinClient.addParagraph(noteId, "Session Configuration", builder.toString());
    ParagraphResult paragraphResult = zeppelinClient.executeParagraph(noteId, paragraphId, sessionId);
    if (paragraphResult.getStatus() != Status.FINISHED) {
      throw new Exception("Fail to configure session, " + paragraphResult.getMessage());
    }

    // start session
    paragraphId = zeppelinClient.addParagraph(noteId, "Session Init", "%" + interpreter);
    paragraphResult = zeppelinClient.executeParagraph(noteId, paragraphId, sessionId);
    if (paragraphResult.getStatus() != Status.FINISHED) {
      throw new Exception("Fail to init session, " + paragraphResult.getMessage());
    }
    this.weburl = zeppelinClient.getSessionWebUrl(sessionId);

    if (messageHandler != null) {
      this.webSocketClient = new ZeppelinWebSocketClient(messageHandler);
      this.webSocketClient.connect(zeppelinClient.getClientConfig().getZeppelinRestUrl()
              .replace("https", "ws").replace("http", "ws") + "/ws");

      // call GET_NOTE to establish websocket connection between this session and zeppelin-server
      Message msg = new Message(Message.OP.GET_NOTE);
      msg.put("id", this.noteId);
      this.webSocketClient.send(msg);
    }
  }

  /**
   * Stop this underlying interpreter process.
   *
   * @throws Exception
   */
  public void stop() throws Exception {
    if (sessionId != null) {
      zeppelinClient.stopSession(interpreter, sessionId);
    }
    if (webSocketClient != null) {
      webSocketClient.stop();
    }
  }

  /**
   * Run code in non-blocking way.
   *
   * @param code
   * @return
   * @throws Exception
   */
  public ExecuteResult execute(String code) throws Exception {
    return execute("", code);
  }

  /**
   * Run code in non-blocking way.
   *
   * @param code
   * @param messageHandler
   * @return
   * @throws Exception
   */
  public ExecuteResult execute(String code, StatementMessageHandler messageHandler) throws Exception {
    return execute("", code, messageHandler);
  }

  /**
   *
   * @param subInterpreter
   * @param code
   * @return
   * @throws Exception
   */
  public ExecuteResult execute(String subInterpreter, String code) throws Exception {
    return execute(subInterpreter, new HashMap<>(), code);
  }

  /**
   *
   * @param subInterpreter
   * @param code
   * @param messageHandler
   * @return
   * @throws Exception
   */
  public ExecuteResult execute(String subInterpreter, String code, StatementMessageHandler messageHandler) throws Exception {
    return execute(subInterpreter, new HashMap<>(), code, messageHandler);
  }

  /**
   *
   * @param subInterpreter
   * @param localProperties
   * @param code
   * @return
   * @throws Exception
   */
  public ExecuteResult execute(String subInterpreter,
                               Map<String, String> localProperties,
                               String code) throws Exception {
    return execute(subInterpreter, localProperties, code, null);
  }

  /**
   *
   * @param subInterpreter
   * @param localProperties
   * @param code
   * @param messageHandler
   * @return
   * @throws Exception
   */
  public ExecuteResult execute(String subInterpreter,
                               Map<String, String> localProperties,
                               String code,
                               StatementMessageHandler messageHandler) throws Exception {
    StringBuilder builder = new StringBuilder("%" + interpreter);
    if (!StringUtils.isBlank(subInterpreter)) {
      builder.append("." + subInterpreter);
    }
    if (localProperties != null && !localProperties.isEmpty()) {
      builder.append("(");
      for (Map.Entry<String, String> entry : localProperties.entrySet()) {
        builder.append(entry.getKey() + "=\"" + entry.getValue() + "\"");
      }
      builder.append(")");
    }
    builder.append("\n" + code);

    String text = builder.toString();

    String nextParagraphId = zeppelinClient.nextSessionParagraph(noteId, maxStatement);
    zeppelinClient.updateParagraph(noteId, nextParagraphId, "", text);

    if (messageHandler != null) {
      webSocketClient.addStatementMessageHandler(nextParagraphId, messageHandler);
    }
    ParagraphResult paragraphResult = zeppelinClient.executeParagraph(noteId, nextParagraphId, sessionId);
    return new ExecuteResult(paragraphResult);
  }

  /**
   *
   * @param code
   * @return
   * @throws Exception
   */
  public ExecuteResult submit(String code) throws Exception {
    return submit("", code);
  }

  /**
   *
   * @param code
   * @param messageHandler
   * @return
   * @throws Exception
   */
  public ExecuteResult submit(String code, StatementMessageHandler messageHandler) throws Exception {
    return submit("", code, messageHandler);
  }

  /**
   *
   * @param subInterpreter
   * @param code
   * @return
   * @throws Exception
   */
  public ExecuteResult submit(String subInterpreter, String code) throws Exception {
    return submit(subInterpreter, new HashMap<>(), code);
  }

  /**
   *
   * @param subInterpreter
   * @param code
   * @param messageHandler
   * @return
   * @throws Exception
   */
  public ExecuteResult submit(String subInterpreter, String code, StatementMessageHandler messageHandler) throws Exception {
    return submit(subInterpreter, new HashMap<>(), code, messageHandler);
  }

  /**
   *
   * @param subInterpreter
   * @param code
   * @return
   * @throws Exception
   */
  public ExecuteResult submit(String subInterpreter, Map<String, String> localProperties, String code) throws Exception {
    return submit(subInterpreter, localProperties, code, null);
  }

  /**
   *
   * @param subInterpreter
   * @param code
   * @param messageHandler
   * @return
   * @throws Exception
   */
  public ExecuteResult submit(String subInterpreter,
                              Map<String, String> localProperties,
                              String code,
                              StatementMessageHandler messageHandler) throws Exception {
    StringBuilder builder = new StringBuilder("%" + interpreter);
    if (!StringUtils.isBlank(subInterpreter)) {
      builder.append("." + subInterpreter);
    }
    if (localProperties != null && !localProperties.isEmpty()) {
      builder.append("(");
      for (Map.Entry<String, String> entry : localProperties.entrySet()) {
        builder.append(entry.getKey() + "=\"" + entry.getValue() + "\"");
      }
      builder.append(")");
    }
    builder.append("\n" + code);

    String text = builder.toString();
    String nextParagraphId = zeppelinClient.nextSessionParagraph(noteId, maxStatement);
    zeppelinClient.updateParagraph(noteId, nextParagraphId, "", text);
    if (messageHandler != null) {
      webSocketClient.addStatementMessageHandler(nextParagraphId, messageHandler);
    }
    ParagraphResult paragraphResult = zeppelinClient.submitParagraph(noteId, nextParagraphId, sessionId);
    return new ExecuteResult(paragraphResult);
  }

  /**
   *
   * @param statementId
   * @throws Exception
   */
  public void cancel(String statementId) throws Exception {
    zeppelinClient.cancelParagraph(noteId, statementId);
  }

  /**
   *
   * @param statementId
   * @return
   * @throws Exception
   */
  public ExecuteResult queryStatement(String statementId) throws Exception {
    ParagraphResult paragraphResult = zeppelinClient.queryParagraphResult(noteId, statementId);
    return new ExecuteResult(paragraphResult);
  }

  /**
   *
   * @param statementId
   * @return
   * @throws Exception
   */
  public ExecuteResult waitUntilFinished(String statementId) throws Exception {
    ParagraphResult paragraphResult = zeppelinClient.waitUtilParagraphFinish(noteId, statementId);
    return new ExecuteResult(paragraphResult);
  }

  /**
   *
   * @param statementId
   * @return
   * @throws Exception
   */
  public ExecuteResult waitUntilRunning(String statementId) throws Exception {
    ParagraphResult paragraphResult = zeppelinClient.waitUtilParagraphRunning(noteId, statementId);
    return new ExecuteResult(paragraphResult);
  }

  public String getNoteId() {
    return noteId;
  }

  public String getWeburl() {
    return weburl;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private ClientConfig clientConfig;
    private String interpreter;
    private Map<String, String> intpProperties;
    private int maxStatement = 100;

    public Builder setClientConfig(ClientConfig clientConfig) {
      this.clientConfig = clientConfig;
      return this;
    }

    public Builder setInterpreter(String interpreter) {
      this.interpreter = interpreter;
      return this;
    }

    public Builder setIntpProperties(Map<String, String> intpProperties) {
      this.intpProperties = intpProperties;
      return this;
    }

    public ZSession build() throws Exception {
      return new ZSession(clientConfig, interpreter, intpProperties, maxStatement);
    }
  }

  public static void main(String[] args) throws Exception {

    ClientConfig clientConfig = new ClientConfig("http://localhost:18086", 1000);
//    ZSession hiveSession = new ZSession(clientConfig, "hive", new HashMap<>(), 100);
//    hiveSession.start();
//
//    ExecuteResult executeResult = hiveSession.submit("show tables");
//    executeResult = hiveSession.waitUntilFinished(executeResult.getStatementId());
//    System.out.println(executeResult.toString());
//
//    executeResult = hiveSession.submit("select eid, count(1) from employee group by eid");
//    executeResult = hiveSession.waitUntilFinished(executeResult.getStatementId());
//    System.out.println(executeResult.toString());

    ZSession sparkSession = new ZSession(clientConfig, "sh", new HashMap<>(), 100);
    sparkSession.start();

//    ExecuteResult executeResult = sparkSession.submit("sql", "show tables");
//    executeResult = sparkSession.waitUntilFinished(executeResult.getStatementId());
//    System.out.println(executeResult.toString());
//
//    executeResult = sparkSession.submit("sql", "select eid, count(1) from employee group by eid");
//    executeResult = sparkSession.waitUntilFinished(executeResult.getStatementId());
//    System.out.println(executeResult.toString());
  }
}
