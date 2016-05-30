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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.notebook.repo.zeppelinhub.ZeppelinHubRepo;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.listener.ZeppelinhubWebsocket;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.protocol.ZeppelinHubOp;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.protocol.ZeppelinhubMessage;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.scheduler.SchedulerService;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.scheduler.ZeppelinHubHeartbeat;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.session.ZeppelinhubSession;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.utils.ZeppelinhubUtils;
import org.apache.zeppelin.notebook.socket.Message;
import org.apache.zeppelin.notebook.socket.Message.OP;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
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
  private final ClientUpgradeRequest conectionRequest;
  private final String zeppelinhubToken;
  
  private static final int MB = 1048576;
  private static final int MAXIMUN_TEXT_SIZE = 64 * MB;
  private static final long CONNECTION_IDLE_TIME = TimeUnit.SECONDS.toMillis(30);
  private static ZeppelinhubClient instance = null;
  private static Gson gson;
  
  private SchedulerService schedulerService;
  private ZeppelinhubSession zeppelinhubSession;

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
    conectionRequest = setConnectionrequest(token);
    zeppelinhubToken = token;
    schedulerService = SchedulerService.create(10);
    gson = new Gson();
    LOG.info("Initialized ZeppelinHub websocket client on {}", zeppelinhubWebsocketUrl);
  }

  public void start() {
    try {
      client.start();
      zeppelinhubSession = connect();
      addRoutines();
    } catch (Exception e) {
      LOG.error("Cannot connect to zeppelinhub via websocket", e);
    }
  }
  
  public void stop() {
    LOG.info("Stopping Zeppelinhub websocket client");
    try {
      zeppelinhubSession.close();
      schedulerService.close();
      client.stop();
    } catch (Exception e) {
      LOG.error("Cannot stop zeppelinhub websocket client", e);
    }
  }

  public String getToken() {
    return this.zeppelinhubToken;
  }
  
  public void send(String msg) {
    if (!isConnectedToZeppelinhub()) {
      LOG.info("Zeppelinhub connection is not open, opening it");
      zeppelinhubSession = connect();
      if (zeppelinhubSession == ZeppelinhubSession.EMPTY) {
        LOG.warn("While connecting to ZeppelinHub received empty session, cannot send the message");
        return;
      }
    }
    zeppelinhubSession.sendByFuture(msg);
  }
  
  private boolean isConnectedToZeppelinhub() {
    return (zeppelinhubSession != null && zeppelinhubSession.isSessionOpen());
  }

  private ZeppelinhubSession connect() {
    ZeppelinhubSession zeppelinSession;
    try {
      ZeppelinhubWebsocket ws = ZeppelinhubWebsocket.newInstance(zeppelinhubToken);
      Future<Session> future = client.connect(ws, zeppelinhubWebsocketUrl, conectionRequest);
      Session session = future.get();
      zeppelinSession = ZeppelinhubSession.createInstance(session, zeppelinhubToken);
    } catch (IOException | InterruptedException | ExecutionException e) {
      LOG.info("Couldnt connect to zeppelinhub", e);
      zeppelinSession = ZeppelinhubSession.EMPTY;
    }
    return zeppelinSession;
  }
  
  private ClientUpgradeRequest setConnectionrequest(String token) {
    ClientUpgradeRequest request = new ClientUpgradeRequest();
    request.setCookies(Lists.newArrayList(new HttpCookie(ZeppelinHubRepo.TOKEN_HEADER, token)));
    return request;
  }
  
  private WebSocketClient createNewWebsocketClient() {
    SslContextFactory sslContextFactory = new SslContextFactory();
    WebSocketClient client = new WebSocketClient(sslContextFactory);
    client.setMaxTextMessageBufferSize(MAXIMUN_TEXT_SIZE);
    client.setMaxIdleTimeout(CONNECTION_IDLE_TIME);
    return client;
  }
  
  private void addRoutines() {
    schedulerService.add(ZeppelinHubHeartbeat.newInstance(this), 10, 23);
  }

  public void handleMsgFromZeppelinHub(String message) {
    ZeppelinhubMessage hubMsg = ZeppelinhubMessage.deserialize(message);
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
          LOG.warn("Received {} from ZeppelinHub, not handled", op);
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
      for (int i = 0; i < paragraphs.length(); i++) {
        if (!(paragraphs.get(i) instanceof JSONObject)) {
          LOG.warn("Wrong \"paragraph\" format for RUN_NOTEBOOK");
          continue;
        }
        zeppelinMsg.data = gson.fromJson(paragraphs.getString(i), 
            new TypeToken<Map<String, Object>>(){}.getType());
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
