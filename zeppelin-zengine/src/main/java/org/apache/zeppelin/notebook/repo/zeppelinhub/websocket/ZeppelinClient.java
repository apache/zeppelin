package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.listener.ZeppelinWebsocket;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.protocol.ZeppelinhubMessage;
import org.apache.zeppelin.notebook.socket.Message;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * Zeppelin websocket client.
 *
 */
public class ZeppelinClient {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinClient.class);
  private final URI zeppelinWebsocketUrl;
  private final String zeppelinhubToken;
  private final WebSocketClient wsClient;
  private static Gson gson;
  private ConcurrentHashMap<String, Session> zeppelinConnectionMap;
  private static ZeppelinClient instance = null;

  public static ZeppelinClient initialize(String zeppelinUrl, String token) {
    if (instance == null) {
      instance = new ZeppelinClient(zeppelinUrl, token);
    }
    return instance;
  }

  public static ZeppelinClient getInstance() {
    return instance;
  }

  private ZeppelinClient(String zeppelinUrl, String token) {
    zeppelinWebsocketUrl = URI.create(zeppelinUrl);
    zeppelinhubToken = token;
    wsClient = createNewWebsocketClient();
    gson = new Gson();
    zeppelinConnectionMap = new ConcurrentHashMap<>();
    LOG.info("Initialized Zeppelin websocket client on {}", zeppelinWebsocketUrl);
  }

  private WebSocketClient createNewWebsocketClient() {
    SslContextFactory sslContextFactory = new SslContextFactory();
    WebSocketClient client = new WebSocketClient(sslContextFactory);
    //TODO(khalid): other client settings
    return client;
  }

  public void start() {
    try {
      if (wsClient != null) {
        wsClient.start();
      } else {
        LOG.warn("Cannot start zeppelin websocket client - isn't initialized");
      }
    } catch (Exception e) {
      LOG.error("Cannot start Zeppelin websocket client", e);
    }
  }

  public void stop() {
    try {
      if (wsClient != null) {
        wsClient.stop();
      } else {
        LOG.warn("Cannot stop zeppelin websocket client - isn't initialized");
      }
    } catch (Exception e) {
      LOG.error("Cannot stop Zeppelin websocket client", e);
    }
  }

  public String serialize(Message zeppelinMsg) {
    // TODO(khalid): handle authentication
    String msg = gson.toJson(zeppelinMsg);
    return msg;
  }

  public Message deserialize(String zeppelinMessage) {
    if (StringUtils.isBlank(zeppelinMessage)) {
      return null;
    }
    Message msg;
    try {
      msg = gson.fromJson(zeppelinMessage, Message.class);
    } catch (JsonSyntaxException ex) {
      LOG.error("Cannot deserialize zeppelin message", ex);
      msg = null;
    }
    return msg;
  }

  public void send(Message msg, String noteId) {
    Session noteSession = getZeppelinConnection(noteId);
    if (!isSessionOpen(noteSession)) {
      LOG.error("Cannot open websocket connection to Zeppelin note {}", noteId);
      return;
    }
    noteSession.getRemote().sendStringByFuture(serialize(msg));
  }

  private boolean isSessionOpen(Session session) {
    return (session != null) && (session.isOpen());
  }

  /* per notebook based ws connection, returns null if can't connect */
  public Session getZeppelinConnection(String noteId) {
    if (StringUtils.isBlank(noteId)) {
      LOG.warn("Cannot return websocket connection for blank noteId");
      return null;
    }
    // return existing connection
    if (zeppelinConnectionMap.containsKey(noteId)) {
      LOG.info("Connection for {} exists in map", noteId);
      return zeppelinConnectionMap.get(noteId);
    }

    // create connection
    ClientUpgradeRequest request = new ClientUpgradeRequest();
    ZeppelinWebsocket socket = new ZeppelinWebsocket(noteId);
    Future<Session> future = null;
    Session session = null;
    try {
      future = wsClient.connect(socket, zeppelinWebsocketUrl, request);
      session = future.get();
    } catch (IOException | InterruptedException | ExecutionException e) {
      LOG.error("Couldn't establish websocket connection to Zeppelin ", e);
      return null;
    }

    if (zeppelinConnectionMap.containsKey(noteId)) {
      session.close();
      session = zeppelinConnectionMap.get(noteId);
    } else {
      String getNote = serialize(zeppelinGetNoteMsg(noteId));
      //TODO(khalid): may need to check return whether successful
      session.getRemote().sendStringByFuture(getNote);
      zeppelinConnectionMap.put(noteId, session);
    }
    //TODO(khalid): clean log later
    LOG.info("Create Zeppelin websocket connection {} {}", zeppelinWebsocketUrl, noteId);
    return session;
  }

  private Message zeppelinGetNoteMsg(String noteId) {
    Message getNoteMsg = new Message(Message.OP.GET_NOTE);
    HashMap<String, Object> data = new HashMap<String, Object>();
    data.put("id", noteId);
    getNoteMsg.data = data;
    return getNoteMsg;
  }

  public void handleMsgFromZeppelin(String message, String noteId) {
    Map<String, String> meta = new HashMap<String, String>();
    meta.put("token", zeppelinhubToken);
    meta.put("noteId", noteId);
    Message zeppelinMsg = deserialize(message);
    if (zeppelinMsg == null) {
      return;
    }
    ZeppelinhubMessage hubMsg = ZeppelinhubMessage.newMessage(zeppelinMsg, meta);
    Client client = Client.getInstance();
    if (client == null) {
      LOG.warn("Client isn't initialized yet");
      return;
    }
    client.relayToHub(hubMsg.serialize());
  }

  /**
   * Close and remove ZeppelinConnection
   */
  public void removeZeppelinConnection(String noteId) {
    if (zeppelinConnectionMap.containsKey(noteId)) {
      Session conn = zeppelinConnectionMap.get(noteId);
      if (conn.isOpen()) {
        conn.close();
      }
      zeppelinConnectionMap.remove(noteId);
    }
    // TODO(khalid): clean log later
    LOG.info("Removed Zeppelin ws connection for the following note {}", noteId);
  }

}
