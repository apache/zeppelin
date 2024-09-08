package org.apache.zeppelin.shell.terminal.websocket;

import javax.websocket.server.ServerEndpointConfig.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerminalSessionConfigurator  extends Configurator {
  private static final Logger LOGGER = LoggerFactory.getLogger(TerminalSessionConfigurator.class);
  private String allowedOrigin;

  public TerminalSessionConfigurator(String allowedOrigin) {
    this.allowedOrigin = allowedOrigin;
  }

  @Override
  public boolean checkOrigin(String originHeaderValue) {
    boolean allowed = allowedOrigin.equals(originHeaderValue);
    LOGGER.info("Checking origin for TerminalSessionConfigurator: " + originHeaderValue + " allowed: " + allowed);
    return allowed;
  }
}
