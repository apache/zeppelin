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
package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket;


import java.io.IOException;
import java.net.HttpCookie;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.listener.ZeppelinhubWebsocket;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.protocol.ZeppelinHubOp;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.protocol.ZeppelinhubMessage;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.scheduler.SchedulerService;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.scheduler.ZeppelinHubHeartbeat;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.session.ZeppelinhubSession;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.utils.ZeppelinhubUtils;
import org.apache.zeppelin.notebook.socket.Message;
import org.apache.zeppelin.notebook.socket.Message.OP;
import org.apache.zeppelin.ticket.TicketContainer;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Manage a zeppelinhub websocket connection.
 */
public class ZeppelinhubClient {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinhubClient.class);

  private final WebSocketClient client;
  private final URI zeppelinhubWebsocketUrl;
  private final String zeppelinhubToken;

  private static final String TOKEN_HEADER = "X-Zeppelin-Token";
  private static final long CONNECTION_IDLE_TIME = TimeUnit.SECONDS.toMillis(30);
  private static ZeppelinhubClient instance = null;
  private static Gson gson;
  
  private SchedulerService schedulerService;
  private Map<String, ZeppelinhubSession> sessionMap = 
      new ConcurrentHashMap<String, ZeppelinhubSession>();

  public static ZeppelinhubClient initialize(String zeppelinhubUrl, String token) {
    if (instance == null) {
      instance = new ZeppelinhubClient(zeppelinhubUrl, token);
    }
    return instance;
  }

  public static ZeppelinhubClient getInstance() {
    return instance;
  }

  private ZeppelinhubClient(String url, String token) {
    zeppelinhubWebsocketUrl = URI.create(url);
    client = createNewWebsocketClient();
    zeppelinhubToken = token;
    schedulerService = SchedulerService.create(10);
    gson = new Gson();
    LOG.info("Initialized ZeppelinHub websocket client on {}", zeppelinhubWebsocketUrl);
  }

  public void start() {
    try {
      client.start();
      addRoutines();
    } catch (Exception e) {
      LOG.error("Cannot connect to zeppelinhub via websocket", e);
    }
  }
  
  public void initUser(String token) {
    
  }

  public void stop() {
    LOG.info("Stopping Zeppelinhub websocket client");
    try {
      schedulerService.close();
      client.stop();
    } catch (Exception e) {
      LOG.error("Cannot stop zeppelinhub websocket client", e);
    }
  }

  public void stopUser(String token) {
    removeSession(token);
  }

  public String getToken() {
    return this.zeppelinhubToken;
  }
  
  public void send(String msg, String token) {
    ZeppelinhubSession zeppelinhubSession = getSession(token);
    if (!isConnectedToZeppelinhub(zeppelinhubSession)) {
      LOG.info("Zeppelinhub connection is not open, opening it");
      zeppelinhubSession = connect(token);
      if (zeppelinhubSession == ZeppelinhubSession.EMPTY) {
        LOG.warn("While connecting to ZeppelinHub received empty session, cannot send the message");
        return;
      }
    }
    zeppelinhubSession.sendByFuture(msg);
  }
  
  private boolean isConnectedToZeppelinhub(ZeppelinhubSession zeppelinhubSession) {
    return (zeppelinhubSession != null && zeppelinhubSession.isSessionOpen());
  }

  private ZeppelinhubSession connect(String token) {
    if (StringUtils.isBlank(token)) {
      LOG.debug("Can't connect with empty token");
      return ZeppelinhubSession.EMPTY;
    }
    ZeppelinhubSession zeppelinhubSession;
    try {
      ZeppelinhubWebsocket ws = ZeppelinhubWebsocket.newInstance(token);
      ClientUpgradeRequest request = getConnectionRequest(token);
      Future<Session> future = client.connect(ws, zeppelinhubWebsocketUrl, request);
      Session session = future.get();
      zeppelinhubSession = ZeppelinhubSession.createInstance(session, token);
      setSession(token, zeppelinhubSession);
    } catch (IOException | InterruptedException | ExecutionException e) {
      LOG.info("Couldnt connect to zeppelinhub", e);
      zeppelinhubSession = ZeppelinhubSession.EMPTY;
    }
    return zeppelinhubSession;
  }

  private void setSession(String token, ZeppelinhubSession session) {
    sessionMap.put(token, session);
  }

  private ZeppelinhubSession getSession(String token) {
    return sessionMap.get(token);
  }

  public void removeSession(String token) {
    ZeppelinhubSession zeppelinhubSession = getSession(token);
    if (zeppelinhubSession == null) {
      return;
    }
    zeppelinhubSession.close();
    sessionMap.remove(token);
  }

  private ClientUpgradeRequest getConnectionRequest(String token) {
    ClientUpgradeRequest request = new ClientUpgradeRequest();
    request.setCookies(Lists.newArrayList(new HttpCookie(TOKEN_HEADER, token)));
    return request;
  }
  
  private WebSocketClient createNewWebsocketClient() {
    SslContextFactory sslContextFactory = new SslContextFactory();
    WebSocketClient client = new WebSocketClient(sslContextFactory);
    client.setMaxTextMessageBufferSize(Client.getMaxNoteSize());
    client.getPolicy().setMaxTextMessageSize(Client.getMaxNoteSize());
    client.setMaxIdleTimeout(CONNECTION_IDLE_TIME);
    return client;
  }
  
  private void addRoutines() {
    schedulerService.add(ZeppelinHubHeartbeat.newInstance(this), 10, 23);
  }

  public void handleMsgFromZeppelinHub(String message) {
    ZeppelinhubMessage hubMsg = ZeppelinhubMessage.fromJson(message);
    if (hubMsg.equals(ZeppelinhubMessage.EMPTY)) {
      LOG.error("Cannot handle ZeppelinHub message is empty");
      return;
    }
    String op = StringUtils.EMPTY;
    if (hubMsg.op instanceof String) {
      op = (String) hubMsg.op;
    } else {
      LOG.error("Message OP from ZeppelinHub isn't string {}", hubMsg.op);
      return;
    }
    if (ZeppelinhubUtils.isZeppelinHubOp(op)) {
      handleZeppelinHubOpMsg(ZeppelinhubUtils.toZeppelinHubOp(op), hubMsg, message);
    } else if (ZeppelinhubUtils.isZeppelinOp(op)) {
      forwardToZeppelin(ZeppelinhubUtils.toZeppelinOp(op), hubMsg);
    }
  }

  private void handleZeppelinHubOpMsg(ZeppelinHubOp op, ZeppelinhubMessage hubMsg, String msg) {
    if (op == null || msg.equals(ZeppelinhubMessage.EMPTY)) {
      LOG.error("Cannot handle empty op or msg");
      return;
    }
    switch (op) {
      case RUN_NOTEBOOK:
        runAllParagraph(hubMsg.meta.get("noteId"), msg);
        break;
      default:
        LOG.debug("Received {} from ZeppelinHub, not handled", op);
        break;
    }
  }

  @SuppressWarnings("unchecked")
  private void forwardToZeppelin(Message.OP op, ZeppelinhubMessage hubMsg) {
    Message zeppelinMsg = new Message(op);
    if (!(hubMsg.data instanceof Map)) {
      LOG.error("Data field of message from ZeppelinHub isn't in correct Map format");
      return;
    }
    zeppelinMsg.data = (Map<String, Object>) hubMsg.data;
    zeppelinMsg.principal = hubMsg.meta.get("owner");
    zeppelinMsg.ticket = TicketContainer.instance.getTicket(zeppelinMsg.principal);
    Client client = Client.getInstance();
    if (client == null) {
      LOG.warn("Base client isn't initialized, returning");
      return;
    }
    client.relayToZeppelin(zeppelinMsg, hubMsg.meta.get("noteId"));
  }

  boolean runAllParagraph(String noteId, String hubMsg) {
    LOG.info("Running paragraph with noteId {}", noteId);
    try {
      JSONObject data = new JSONObject(hubMsg);
      if (data.equals(JSONObject.NULL) || !(data.get("data") instanceof JSONArray)) {
        LOG.error("Wrong \"data\" format for RUN_NOTEBOOK");
        return false;
      }
      Client client = Client.getInstance();
      if (client == null) {
        LOG.warn("Base client isn't initialized, returning");
        return false;
      }
      Message zeppelinMsg = new Message(OP.RUN_PARAGRAPH);

      JSONArray paragraphs = data.getJSONArray("data");
      String principal = data.getJSONObject("meta").getString("owner");
      for (int i = 0; i < paragraphs.length(); i++) {
        if (!(paragraphs.get(i) instanceof JSONObject)) {
          LOG.warn("Wrong \"paragraph\" format for RUN_NOTEBOOK");
          continue;
        }
        zeppelinMsg.data = gson.fromJson(paragraphs.getString(i), 
            new TypeToken<Map<String, Object>>(){}.getType());
        zeppelinMsg.principal = principal;
        zeppelinMsg.ticket = TicketContainer.instance.getTicket(principal);
        client.relayToZeppelin(zeppelinMsg, noteId);
        LOG.info("\nSending RUN_PARAGRAPH message to Zeppelin ");
      }
    } catch (JSONException e) {
      LOG.error("Failed to parse RUN_NOTEBOOK message from ZeppelinHub ", e);
      return false;
    }
    return true;
  }

}
