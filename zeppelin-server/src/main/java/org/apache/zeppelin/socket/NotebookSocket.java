package org.apache.zeppelin.socket;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.utils.ServerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

import javax.websocket.Session;

/**
 * Notebook websocket.
 */
public class NotebookSocket {
  private static final Logger LOGGER = LoggerFactory.getLogger(NotebookSocket.class);

  private Session session;
  private Map<String, Object> headers;
  private String user;

  public NotebookSocket(Session session, Map<String, Object> headers) {
    this.session = session;
    this.headers = headers;
    this.user = StringUtils.EMPTY;
    LOGGER.debug("NotebookSocket created for session: {}", session.getId());
  }

  public String getHeader(String key) {
    String headerValue = String.valueOf(headers.get(key));
    LOGGER.debug("Getting header: {} with value: {}", key, headerValue);
    return headerValue;
  }

  public void send(String serializeMessage) throws IOException {
    session.getAsyncRemote().sendText(serializeMessage, result -> {
      if (result.getException() != null) {
        LOGGER.error("Failed to send async message", result.getException());
      }
    });
  }

  public String getUser() {
    LOGGER.debug("Getting user: {}", user);
    return user;
  }

  public void setUser(String user) {
    LOGGER.debug("Setting user: {}", user);
    this.user = user;
  }

  @Override
  public String toString() {
    return ServerUtils.getRemoteAddress(session);
  }
}