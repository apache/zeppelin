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
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.NotebookAuthorization;
import org.apache.zeppelin.notebook.repo.zeppelinhub.model.UserTokenContainer;
import org.apache.zeppelin.notebook.repo.zeppelinhub.security.Authentication;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.listener.WatcherWebsocket;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.listener.ZeppelinWebsocket;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.protocol.ZeppelinhubMessage;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.scheduler.SchedulerService;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.scheduler.ZeppelinHeartbeat;
import org.apache.zeppelin.notebook.socket.Message;
import org.apache.zeppelin.notebook.socket.Message.OP;
import org.apache.zeppelin.util.WatcherSecurityKey;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Zeppelin websocket client.
 *
 */
public class ZeppelinClient {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinClient.class);
  private final URI zeppelinWebsocketUrl;
  private final WebSocketClient wsClient;
  // Keep track of current open connection per notebook.
  private ConcurrentHashMap<String, Session> notesConnection;
  // Listen to every note actions.
  private static Session watcherSession;
  private static ZeppelinClient instance = null;
  private SchedulerService schedulerService;
  private Authentication authModule;
  private static final int MIN = 60;
  private static final String ORIGIN = "Origin";

  private static final Set<String> actionable = new  HashSet<String>(Arrays.asList(
      // running events
      "ANGULAR_OBJECT_UPDATE",
      "PROGRESS",
      "NOTE",
      "PARAGRAPH",
      "PARAGRAPH_UPDATE_OUTPUT",
      "PARAGRAPH_APPEND_OUTPUT",
      "PARAGRAPH_CLEAR_OUTPUT",
      "PARAGRAPH_REMOVE",
      // run or stop events
      "RUN_PARAGRAPH",
      "CANCEL_PARAGRAPH"));

  public static ZeppelinClient initialize(String zeppelinUrl, String token, 
      ZeppelinConfiguration conf) {
    if (instance == null) {
      instance = new ZeppelinClient(zeppelinUrl, token, conf);
    }
    return instance;
  }

  public static ZeppelinClient getInstance() {
    return instance;
  }

  private ZeppelinClient(String zeppelinUrl, String token, ZeppelinConfiguration conf) {
    zeppelinWebsocketUrl = URI.create(zeppelinUrl);
    wsClient = createNewWebsocketClient();
    notesConnection = new ConcurrentHashMap<>();
    schedulerService = SchedulerService.getInstance();
    authModule = Authentication.initialize(token, conf);
    if (authModule != null) {
      SchedulerService.getInstance().addOnce(authModule, 10);
    }
    LOG.info("Initialized Zeppelin websocket client on {}", zeppelinWebsocketUrl);
  }

  private WebSocketClient createNewWebsocketClient() {
    SslContextFactory sslContextFactory = new SslContextFactory();
    WebSocketClient client = new WebSocketClient(sslContextFactory);
    client.setMaxIdleTimeout(5 * MIN * 1000);
    client.setMaxTextMessageBufferSize(Client.getMaxNoteSize());
    client.getPolicy().setMaxTextMessageSize(Client.getMaxNoteSize());
    //TODO(khalid): other client settings
    return client;
  }

  public void start() {
    try {
      if (wsClient != null) {
        wsClient.start();
        addRoutines();
      } else {
        LOG.warn("Cannot start zeppelin websocket client - isn't initialized");
      }
    } catch (Exception e) {
      LOG.error("Cannot start Zeppelin websocket client", e);
    }
  }

  private void addRoutines() {
    schedulerService.add(ZeppelinHeartbeat.newInstance(this), 10, 1 * MIN);
    new Timer().schedule(new java.util.TimerTask() {
      @Override
      public void run() {
        int time = 0;
        while (time < 5 * MIN) {
          watcherSession = openWatcherSession();
          if (watcherSession == null) {
            try {
              Thread.sleep(5000);
              time += 5;
            } catch (InterruptedException e) {
              //continue
            }
          } else {
            break;
          }
        }
      }
    }, 5000);
  }

  public void stop() {
    try {
      if (wsClient != null) {
        removeAllConnections();
        wsClient.stop();
      } else {
        LOG.warn("Cannot stop zeppelin websocket client - isn't initialized");
      }
      if (watcherSession != null) {
        watcherSession.close();
      }
    } catch (Exception e) {
      LOG.error("Cannot stop Zeppelin websocket client", e);
    }
  }

  public String serialize(Message zeppelinMsg) {
    if (credentialsAvailable()) {
      zeppelinMsg.principal = authModule.getPrincipal();
      zeppelinMsg.ticket = authModule.getTicket();
      zeppelinMsg.roles = authModule.getRoles();
    }
    String msg = zeppelinMsg.toJson();
    return msg;
  }

  private boolean credentialsAvailable() {
    return Authentication.getInstance() != null && Authentication.getInstance().isAuthenticated();
  }

  public Message deserialize(String zeppelinMessage) {
    if (StringUtils.isBlank(zeppelinMessage)) {
      return null;
    }
    try {
      return Message.fromJson(zeppelinMessage);
    } catch (Exception e) {
      LOG.error("Fail to parse zeppelinMessage", e);
      return null;
    }
  }
  
  private Session openWatcherSession() {
    ClientUpgradeRequest request = new ClientUpgradeRequest();
    request.setHeader(WatcherSecurityKey.HTTP_HEADER, WatcherSecurityKey.getKey());
    request.setHeader(ORIGIN, "*");
    WatcherWebsocket socket = WatcherWebsocket.createInstace();
    Future<Session> future = null;
    Session session = null;
    try {
      future = wsClient.connect(socket, zeppelinWebsocketUrl, request);
      session = future.get();
    } catch (IOException | InterruptedException | ExecutionException e) {
      LOG.error("Couldn't establish websocket connection to Zeppelin ", e);
      return session;
    }
    return session;
  }

  public void send(Message msg, String noteId) {
    Session noteSession = getZeppelinConnection(noteId, msg.principal, msg.ticket);
    if (!isSessionOpen(noteSession)) {
      LOG.error("Cannot open websocket connection to Zeppelin note {}", noteId);
      return;
    }
    noteSession.getRemote().sendStringByFuture(serialize(msg));
  }
  
  public Session getZeppelinConnection(String noteId, String principal, String ticket) {
    if (StringUtils.isBlank(noteId)) {
      LOG.warn("Cannot get Websocket session with blanck noteId");
      return null;
    }
    return getNoteSession(noteId, principal, ticket);
  }
  
/*
  private Message zeppelinGetNoteMsg(String noteId) {
    Message getNoteMsg = new Message(Message.OP.GET_NOTE);
    HashMap<String, Object> data = new HashMap<>();
    data.put("id", noteId);
    getNoteMsg.data = data;
    return getNoteMsg;
  }
  */

  private Session getNoteSession(String noteId, String principal, String ticket) {
    LOG.info("Getting Note websocket connection for note {}", noteId);
    Session session = notesConnection.get(noteId);
    if (!isSessionOpen(session)) {
      LOG.info("No open connection for note {}, opening one", noteId);
      notesConnection.remove(noteId);
      session = openNoteSession(noteId, principal, ticket);
    }
    return session;
  }
  
  private Session openNoteSession(String noteId, String principal, String ticket) {
    ClientUpgradeRequest request = new ClientUpgradeRequest();
    request.setHeader(ORIGIN, "*");
    ZeppelinWebsocket socket = new ZeppelinWebsocket(noteId);
    Future<Session> future = null;
    Session session = null;
    try {
      future = wsClient.connect(socket, zeppelinWebsocketUrl, request);
      session = future.get();
    } catch (IOException | InterruptedException | ExecutionException e) {
      LOG.error("Couldn't establish websocket connection to Zeppelin ", e);
      return session;
    }

    if (notesConnection.containsKey(noteId)) {
      session.close();
      session = notesConnection.get(noteId);
    } else {
      String getNote = serialize(zeppelinGetNoteMsg(noteId, principal, ticket));
      session.getRemote().sendStringByFuture(getNote);
      notesConnection.put(noteId, session);
    }
    return session;
  }
  
  private boolean isSessionOpen(Session session) {
    return (session != null) && (session.isOpen());
  }

  private Message zeppelinGetNoteMsg(String noteId, String principal, String ticket) {
    Message getNoteMsg = new Message(Message.OP.GET_NOTE);
    HashMap<String, Object> data = new HashMap<String, Object>();
    data.put("id", noteId);
    getNoteMsg.data = data;
    getNoteMsg.principal = principal;
    getNoteMsg.ticket = ticket;
    return getNoteMsg;
  }

  public void handleMsgFromZeppelin(String message, String noteId) {
    Map<String, String> meta = new HashMap<>();
    //TODO(khalid): don't use zeppelinhubToken in this class, decouple
    meta.put("noteId", noteId);
    Message zeppelinMsg = deserialize(message);
    if (zeppelinMsg == null) {
      return;
    }
    String token;
    if (!isActionable(zeppelinMsg.op)) {
      return;
    }
    
    token = UserTokenContainer.getInstance().getUserToken(zeppelinMsg.principal);
    Client client = Client.getInstance();
    if (client == null) {
      LOG.warn("Client isn't initialized yet");
      return;
    }
    ZeppelinhubMessage hubMsg = ZeppelinhubMessage.newMessage(zeppelinMsg, meta);
    if (StringUtils.isEmpty(token)) {
      relayToAllZeppelinHub(hubMsg, noteId);
    } else {
      client.relayToZeppelinHub(hubMsg.toJson(), token);
    }

  }

  private void relayToAllZeppelinHub(ZeppelinhubMessage hubMsg, String noteId) {
    if (StringUtils.isBlank(noteId)) {
      return;
    }
    NotebookAuthorization noteAuth = NotebookAuthorization.getInstance();
    Map<String, String> userTokens = UserTokenContainer.getInstance().getAllUserTokens();
    Client client = Client.getInstance();
    Set<String> userAndRoles;
    String token;
    for (String user: userTokens.keySet()) {
      userAndRoles = noteAuth.getRoles(user);
      userAndRoles.add(user);
      if (noteAuth.isReader(noteId, userAndRoles)) {
        token = userTokens.get(user);
        hubMsg.meta.put("token", token);
        client.relayToZeppelinHub(hubMsg.toJson(), token);
      }
    }
  }

  private boolean isActionable(OP action) {
    if (action == null) {
      return false;
    }
    return actionable.contains(action.name());
  }
  
  public void removeNoteConnection(String noteId) {
    if (StringUtils.isBlank(noteId)) {
      LOG.error("Cannot remove session for empty noteId");
      return;
    }
    if (notesConnection.containsKey(noteId)) {
      Session connection = notesConnection.get(noteId);
      if (connection.isOpen()) {
        connection.close();
      }
      notesConnection.remove(noteId);
    }
    LOG.info("Removed note websocket connection for note {}", noteId);
  }
  
  private void removeAllConnections() {
    if (watcherSession != null && watcherSession.isOpen()) {
      watcherSession.close();
    }

    Session noteSession = null;
    for (Map.Entry<String, Session> note: notesConnection.entrySet()) {
      noteSession = note.getValue();
      if (isSessionOpen(noteSession)) {
        noteSession.close();
      }
    }
    notesConnection.clear();
  }

  public void ping() {
    if (watcherSession == null) {
      LOG.debug("Cannot send PING event, no watcher found");
      return;
    }
    watcherSession.getRemote().sendStringByFuture(serialize(new Message(OP.PING)));
  }
  
  /**
   * Only used in test.
   */
  public int countConnectedNotes() {
    return notesConnection.size();
  }
}
