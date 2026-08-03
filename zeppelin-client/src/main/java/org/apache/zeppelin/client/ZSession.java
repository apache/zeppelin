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
import org.apache.zeppelin.common.Message;
import org.apache.zeppelin.common.SessionInfo;
import org.apache.zeppelin.client.websocket.MessageHandler;
import org.apache.zeppelin.client.websocket.StatementMessageHandler;
import org.apache.zeppelin.client.websocket.ZeppelinWebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Each ZSession represent one interpreter process, you can start/stop it, and execute/submit/cancel code.
 * There's no Zeppelin concept(like note/paragraph) in ZSession.
 *
 */
public class ZSession implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(ZSession.class);

  /** Authenticates an isolated client before reconnecting an existing Zeppelin session. */
  @FunctionalInterface
  public interface ClientAuthenticator {
    void authenticate(ZeppelinClient client) throws Exception;
  }

  private ZeppelinClient zeppelinClient;
  private String interpreter;
  private Map<String, String> intpProperties;
  // max number of retained statements, each statement represent one paragraph.
  private int maxStatement;

  private SessionInfo sessionInfo;

  private ZeppelinWebSocketClient webSocketClient;
  private boolean closed;
  private boolean remoteSessionStopped;
  private boolean webSocketStopped;

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

  private ZSession(ClientConfig clientConfig,
                   String interpreter,
                   String sessionId) throws Exception {
    this.zeppelinClient = new ZeppelinClient(clientConfig);
    this.interpreter = interpreter;
    this.sessionInfo = new SessionInfo(sessionId);
  }

  public void login(String userName, String password) throws Exception {
    this.zeppelinClient.login(userName, password);
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
    this.sessionInfo = zeppelinClient.newSession(interpreter);

    // inline configuration
    StringBuilder builder = new StringBuilder("%" + interpreter + ".conf\n");
    if (intpProperties != null) {
      for (Map.Entry<String, String> entry : intpProperties.entrySet()) {
        builder.append(entry.getKey() + " " + entry.getValue() + "\n");
      }
    }
    String paragraphId = zeppelinClient.addParagraph(getNoteId(), "Session Configuration", builder.toString());
    ParagraphResult paragraphResult = zeppelinClient.executeParagraph(getNoteId(), paragraphId, getSessionId());
    if (paragraphResult.getStatus() != Status.FINISHED) {
      throw new Exception("Fail to configure session, " + paragraphResult.getResultInText());
    }

    // start session
    // add local properties (init) to avoid skip empty paragraph.
    paragraphId = zeppelinClient.addParagraph(getNoteId(), "Session Init", "%" + interpreter + "(init=true)");
    paragraphResult = zeppelinClient.executeParagraph(getNoteId(), paragraphId, getSessionId());
    if (paragraphResult.getStatus() != Status.FINISHED) {
      throw new Exception("Fail to init session, " + paragraphResult.getResultInText());
    }
    this.sessionInfo = zeppelinClient.getSession(getSessionId());

    if (messageHandler != null) {
      connectWebSocket(messageHandler);
    }
  }

  /**
   * Stop this underlying interpreter process.
   *
   * @throws Exception
   */
  public synchronized void stop() throws Exception {
    if (closed) {
      return;
    }
    Exception failure = null;
    if (!remoteSessionStopped) {
      if (getSessionId() == null) {
        remoteSessionStopped = true;
      } else {
        try {
          zeppelinClient.stopSession(getSessionId());
          remoteSessionStopped = true;
        } catch (Exception e) {
          failure = e;
        }
      }
    }
    if (!webSocketStopped) {
      if (webSocketClient == null) {
        webSocketStopped = true;
      } else {
        try {
          webSocketClient.stop();
          webSocketStopped = true;
        } catch (Exception e) {
          if (failure == null) {
            failure = e;
          } else {
            failure.addSuppressed(e);
          }
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
    zeppelinClient.close();
    closed = true;
  }

  @Override
  public void close() throws Exception {
    stop();
  }

  /**
   * Session has been started in ZeppelinServer, this method is just to reconnect it.
   * This method is used for connect to an existing session in ZeppelinServer, instead of
   * start it from ZSession.
   * @throws Exception
   */
  public static ZSession createFromExistingSession(ClientConfig clientConfig,
                                                   String interpreter,
                                                   String sessionId) throws Exception {
    return createFromExistingSession(clientConfig, interpreter, sessionId, null);
  }

  /**
   * Session has been started in ZeppelinServer, this method is just to reconnect it.
   * This method is used for connect to an existing session in ZeppelinServer, instead of
   * start it from ZSession.
   * @throws Exception
   */
  public static ZSession createFromExistingSession(ClientConfig clientConfig,
                                                   String interpreter,
                                                   String sessionId,
                                                   MessageHandler messageHandler) throws Exception {
    return createFromExistingSession(
        clientConfig, interpreter, sessionId, client -> { }, messageHandler);
  }

  /**
   * Reconnect an existing session after authenticating its isolated REST/WebSocket client.
   *
   * <p>For Shiro, the authenticator can call {@code client.login(user, password)}. It is invoked
   * before both the protected session lookup and WebSocket upgrade.
   */
  public static ZSession createFromExistingSession(
      ClientConfig clientConfig,
      String interpreter,
      String sessionId,
      ClientAuthenticator authenticator,
      MessageHandler messageHandler) throws Exception {
    ZSession session = new ZSession(clientConfig, interpreter, sessionId);
    try {
      authenticator.authenticate(session.zeppelinClient);
      session.reconnect(messageHandler);
      return session;
    } catch (Exception e) {
      try {
        session.closeLocalResources();
      } catch (Exception cleanupFailure) {
        e.addSuppressed(cleanupFailure);
      }
      throw e;
    }
  }

  private void closeLocalResources() throws Exception {
    try {
      if (webSocketClient != null) {
        webSocketClient.stop();
      }
    } finally {
      zeppelinClient.close();
      closed = true;
    }
  }

  private void reconnect(MessageHandler messageHandler) throws Exception {
    this.sessionInfo = this.zeppelinClient.getSession(getSessionId());
    if (!sessionInfo.getState().equalsIgnoreCase("Running")) {
      throw new Exception("Session " + getSessionId() + " is not running, state: " + sessionInfo.getState());
    }

    if (messageHandler != null) {
      connectWebSocket(messageHandler);
    }
  }

  private void connectWebSocket(MessageHandler messageHandler) throws Exception {
    this.webSocketClient = new ZeppelinWebSocketClient(messageHandler);
    URI webSocketUri = toWebSocketUri(zeppelinClient.getClientConfig().getZeppelinRestUrl());
    this.webSocketClient.connect(webSocketUri.toString(),
            zeppelinClient.getSessionCookieHeader(webSocketUri));

    // call GET_NOTE to establish websocket connection between this session and zeppelin-server
    Message msg = new Message(Message.OP.GET_NOTE);
    msg.put("id", getNoteId());
    this.webSocketClient.send(msg);
  }

  static URI toWebSocketUri(String zeppelinRestUrl) throws URISyntaxException {
    URI restUri = new URI(zeppelinRestUrl);
    String webSocketScheme;
    if ("https".equalsIgnoreCase(restUri.getScheme())) {
      webSocketScheme = "wss";
    } else if ("http".equalsIgnoreCase(restUri.getScheme())) {
      webSocketScheme = "ws";
    } else {
      throw new IllegalArgumentException("Unsupported Zeppelin REST URL scheme: "
              + restUri.getScheme());
    }
    if (restUri.getRawAuthority() == null) {
      throw new IllegalArgumentException("Zeppelin REST URL must contain an authority");
    }
    String rawBasePath = restUri.getRawPath();
    if (rawBasePath != null && rawBasePath.length() > 1 && rawBasePath.endsWith("/")) {
      rawBasePath = rawBasePath.substring(0, rawBasePath.length() - 1);
    }
    String webSocketPath = rawBasePath == null || rawBasePath.isEmpty() || "/".equals(rawBasePath)
            ? "/ws" : rawBasePath + "/ws";
    return new URI(webSocketScheme + "://" + restUri.getRawAuthority() + webSocketPath);
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
   * @param localProperties
   * @return
   * @throws Exception
   */
  public ExecuteResult execute(String code, Map<String, String> localProperties) throws Exception {
    return execute("", localProperties, code);
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
      List<String> propertyString = localProperties.entrySet().stream()
              .map(entry -> ("\"" + entry.getKey() + "\"=\"" + entry.getValue() + "\""))
              .collect(Collectors.toList());
      builder.append(StringUtils.join(propertyString, ","));
      builder.append(")");
    }
    builder.append(" " + code);

    String text = builder.toString();

    String nextParagraphId = zeppelinClient.nextSessionParagraph(getNoteId(), maxStatement);
    zeppelinClient.updateParagraph(getNoteId(), nextParagraphId, "", text);

    if (messageHandler != null) {
      webSocketClient.addStatementMessageHandler(nextParagraphId, messageHandler);
    }
    ParagraphResult paragraphResult = zeppelinClient.executeParagraph(getNoteId(), nextParagraphId, getSessionId());
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
      List<String> propertyString = localProperties.entrySet().stream()
              .map(entry -> (entry.getKey() + "=\"" + entry.getValue() + "\""))
              .collect(Collectors.toList());
      builder.append(StringUtils.join(propertyString, ","));
      builder.append(")");
    }
    builder.append(" " + code);

    String text = builder.toString();
    String nextParagraphId = zeppelinClient.nextSessionParagraph(getNoteId(), maxStatement);
    zeppelinClient.updateParagraph(getNoteId(), nextParagraphId, "", text);
    if (messageHandler != null) {
      webSocketClient.addStatementMessageHandler(nextParagraphId, messageHandler);
    }
    ParagraphResult paragraphResult = zeppelinClient.submitParagraph(getNoteId(), nextParagraphId, getSessionId());
    return new ExecuteResult(paragraphResult);
  }

  /**
   *
   * @param statementId
   * @throws Exception
   */
  public void cancel(String statementId) throws Exception {
    zeppelinClient.cancelParagraph(getNoteId(), statementId);
  }

  /**
   *
   * @param statementId
   * @return
   * @throws Exception
   */
  public ExecuteResult queryStatement(String statementId) throws Exception {
    ParagraphResult paragraphResult = zeppelinClient.queryParagraphResult(getNoteId(), statementId);
    return new ExecuteResult(paragraphResult);
  }

  /**
   *
   * @param statementId
   * @return
   * @throws Exception
   */
  public ExecuteResult waitUntilFinished(String statementId) throws Exception {
    ParagraphResult paragraphResult = zeppelinClient.waitUtilParagraphFinish(getNoteId(), statementId);
    return new ExecuteResult(paragraphResult);
  }

  /**
   *
   * @param statementId
   * @return
   * @throws Exception
   */
  public ExecuteResult waitUntilRunning(String statementId) throws Exception {
    ParagraphResult paragraphResult = zeppelinClient.waitUtilParagraphRunning(getNoteId(), statementId);
    return new ExecuteResult(paragraphResult);
  }

  public String getNoteId() {
    if (this.sessionInfo != null) {
      return this.sessionInfo.getNoteId();
    } else {
      return null;
    }
  }

  public String getWeburl() {
    if (this.sessionInfo != null) {
      return sessionInfo.getWeburl();
    } else {
      return null;
    }
  }

  public String getSessionId() {
    if (this.sessionInfo != null) {
      return this.sessionInfo.getSessionId();
    } else {
      return null;
    }
  }

  public String getInterpreter() {
    return interpreter;
  }

  public ZeppelinClient getZeppelinClient() {
    return zeppelinClient;
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

    public Builder setMaxStatement(int maxStatement) {
      this.maxStatement = maxStatement;
      return this;
    }

    public ZSession build() throws Exception {
      return new ZSession(clientConfig, interpreter, intpProperties, maxStatement);
    }
  }

  public static void main(String[] args) throws Exception {

    ClientConfig clientConfig = new ClientConfig("http://localhost:8080", 1000);
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

    ZSession sparkSession = ZSession.createFromExistingSession(clientConfig, "hive", "hive_1598418780469");
    ExecuteResult executeResult = sparkSession.execute("show databases");
    System.out.println(executeResult);

//    ExecuteResult executeResult = sparkSession.submit("sql", "show tables");
//    executeResult = sparkSession.waitUntilFinished(executeResult.getStatementId());
//    System.out.println(executeResult.toString());
//
//    executeResult = sparkSession.submit("sql", "select eid, count(1) from employee group by eid");
//    executeResult = sparkSession.waitUntilFinished(executeResult.getStatementId());
//    System.out.println(executeResult.toString());
  }
}
