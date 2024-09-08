package org.apache.zeppelin.shell.terminal.websocket;

import javax.websocket.server.ServerEndpointConfig.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerminalSessionConfigurator  extends Configurator {
  private static final Logger LOGGER = LoggerFactory.getLogger(TerminalSessionConfigurator.class);

  @Override
  public boolean checkOrigin(String originHeaderValue) {
    LOGGER.info("TerminalSessionConfigurator checkOrigin: " + originHeaderValue);
    return super.checkOrigin(originHeaderValue);
  }
}
