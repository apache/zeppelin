package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.listener;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.ZeppelinClient;
import org.apache.zeppelin.notebook.socket.Message;
import org.apache.zeppelin.notebook.socket.Message.OP;
import org.apache.zeppelin.notebook.socket.WatcherMessage;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * 
 *
 */
public class WatcherWebsocket implements WebSocketListener {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinWebsocket.class);
  private static final Gson GSON = new Gson();
  public Session connection;
  
  public static WatcherWebsocket createInstace() {
    return new WatcherWebsocket();
  }
  
  @Override
  public void onWebSocketBinary(byte[] payload, int offset, int len) {
  }

  @Override
  public void onWebSocketClose(int code, String reason) {
    LOG.info("WatcherWebsocket connection closed with code: {}, message: {}", code, reason);
  }

  @Override
  public void onWebSocketConnect(Session session) {
    LOG.info("WatcherWebsocket connection opened");
    this.connection = session;
    session.getRemote().sendStringByFuture(GSON.toJson(new Message(OP.WATCHER)));
  }

  @Override
  public void onWebSocketError(Throwable cause) {
    LOG.warn("WatcherWebsocket socket connection error ", cause);
  }

  @Override
  public void onWebSocketText(String message) {
    LOG.debug("WatcherWebsocket client received Message: " + message);
    WatcherMessage watcherMsg = GSON.fromJson(message, WatcherMessage.class);
    if (StringUtils.isBlank(watcherMsg.noteId)) {
      return;
    }
    try {
      ZeppelinClient zeppelinClient = ZeppelinClient.getInstance();
      if (zeppelinClient != null) {
        zeppelinClient.handleMsgFromZeppelin(watcherMsg.message, watcherMsg.noteId);
      }
    } catch (Exception e) {
      LOG.error("Failed to send message to ZeppelinHub: ", e);
    }
  }

}
