package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.mock;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockEventSocket extends WebSocketAdapter {
  private static final Logger LOG = LoggerFactory.getLogger(MockEventServlet.class);
  private Session session;

  @Override
  public void onWebSocketConnect(Session session) {
    super.onWebSocketConnect(session);
    this.session = session;
    LOG.info("Socket Connected: " + session);
  }

  @Override
  public void onWebSocketText(String message) {
    super.onWebSocketText(message);
    session.getRemote().sendStringByFuture(message);
    LOG.info("Received TEXT message: {}", message);
    
  }

  @Override
  public void onWebSocketClose(int statusCode, String reason) {
    super.onWebSocketClose(statusCode, reason);
    LOG.info("Socket Closed: [{}] {}", statusCode, reason);
  }

  @Override
  public void onWebSocketError(Throwable cause) {
    super.onWebSocketError(cause);
    LOG.error("Websocket error: {}", cause);
  }
}