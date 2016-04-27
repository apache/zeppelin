package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO(xxx): Add description
 * 
 */
public class Client {
  private Logger LOG = LoggerFactory.getLogger(Client.class);
  
  private final ZeppelinhubClient zeppelinhubClient;
  
  public Client(String zeppelinUri, String zeppelinhub, String token) {
    LOG.debug("Init Client");
    zeppelinhubClient = ZeppelinhubClient.newInstance(zeppelinhub, token);
  }
  
  public void start() {
    if (zeppelinhubClient != null) {
      zeppelinhubClient.start();
    }
  }
  
  public void stop() {
    if (zeppelinhubClient != null) {
      zeppelinhubClient.stop();
    }
  }
}
