package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.listener;

import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.utils.ZeppelinhubUtils;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zeppelinhub websocket handler.
 */
public class ZeppelinhubWebsocket implements WebSocketListener {
  private Logger LOG = LoggerFactory.getLogger(ZeppelinhubWebsocket.class);
  private Session zeppelinHubSession;
  private final String token;
  
  private ZeppelinhubWebsocket(String token) {
    super();
    this.token = token;
  }

  public static ZeppelinhubWebsocket newInstance(String token) {
    return new ZeppelinhubWebsocket(token);
  }
  
  @Override
  public void onWebSocketBinary(byte[] payload, int offset, int len) {}

  @Override
  public void onWebSocketClose(int statusCode, String reason) {
    LOG.info("Closing websocket connection [{}] : {}", statusCode, reason);
    send(ZeppelinhubUtils.deadMessage(token));
    this.zeppelinHubSession = null;
  }

  @Override
  public void onWebSocketConnect(Session session) {
    LOG.info("Opening a new session to Zeppelinhub {}", session.hashCode());
    this.zeppelinHubSession = session;
    send(ZeppelinhubUtils.liveMessage(token));
  }

  @Override
  public void onWebSocketError(Throwable cause) {
    LOG.info("Got error", cause);
  }

  @Override
  public void onWebSocketText(String message) {
    LOG.info("Got msg {}", message);
    if (isSessionOpen()) {
      // do something.
    }
  }

  private boolean isSessionOpen() {
    return ((zeppelinHubSession != null) && (zeppelinHubSession.isOpen())) ? true : false;
  }
  
  private void send(String msg) {
    if (isSessionOpen()) {
      zeppelinHubSession.getRemote().sendStringByFuture(msg);
    }
  }
}
