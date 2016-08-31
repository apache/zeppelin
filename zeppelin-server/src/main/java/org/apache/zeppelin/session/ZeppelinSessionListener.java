package org.apache.zeppelin.session;

import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

public class ZeppelinSessionListener implements HttpSessionListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(ZeppelinSessionListener.class);

  @Override
  public void sessionCreated(HttpSessionEvent httpSessionEvent) {
    LOGGER.info("Session Created: " + httpSessionEvent.getSession().getId());
  }

  @Override
  public void sessionDestroyed(HttpSessionEvent httpSessionEvent) {
    AuthenticationInfo authenticationInfo = (AuthenticationInfo) httpSessionEvent.getSession().getAttribute(ZeppelinSessions.ZEPPELIN_AUTH_USER_KEY);
    if (authenticationInfo != null) {
      ZeppelinSessions.components.remove(authenticationInfo.getUser());
      LOGGER.info("Session ID="  + httpSessionEvent.getSession().getId() + " destroyed for authenticationInfo=" + authenticationInfo.getUser());
    }
  }

}
