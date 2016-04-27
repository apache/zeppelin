package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.session;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zeppelinhub session.
 */
public class ZeppelinhubSession {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinhubSession.class);
  private Session session;
  private final String token;
  
  public static final ZeppelinhubSession EMPTY = new ZeppelinhubSession(null, StringUtils.EMPTY);
  
  public static ZeppelinhubSession createInstance(Session session, String token) {
    return new ZeppelinhubSession(session, token);
  }
  
  private ZeppelinhubSession(Session session, String token) {
    this.session = session;
    this.token = token;
  }
  
  public boolean isSessionOpen() {
    return ((session != null) && (session.isOpen()));
  }
  
  public void close() {
    if (isSessionOpen()) {
      session.close();
    }
  }
  
  public void sendByFuture(String msg) {
    if (StringUtils.isBlank(msg)) {
      LOG.error("Cannot send event to Zeppelinhub, msg is empty");
    }
    if (isSessionOpen()) {
      session.getRemote().sendStringByFuture(msg);
    } else {
      LOG.error("Cannot send event to Zeppelinhub, session is close");
    }
  }
}
