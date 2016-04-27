package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket;

import org.apache.zeppelin.notebook.socket.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO(xxx): Add description
 * 
 */
public class Client {
  private static final Logger LOG = LoggerFactory.getLogger(Client.class);
  private final ZeppelinhubClient zeppelinhubClient;
  private final ZeppelinClient zeppelinClient;
  private static Client instance = null;

  public static Client initialize(String zeppelinUri, String zeppelinhubUri, String token) {
    if (instance == null) {
      instance = new Client(zeppelinUri, zeppelinhubUri, token);
    }
    return instance;
  }

  public static Client getInstance() {
    return instance;
  }

  private Client(String zeppelinUri, String zeppelinhubUri, String token) {
    LOG.debug("Init Client");
    zeppelinhubClient = ZeppelinhubClient.initialize(zeppelinhubUri, token);
    zeppelinClient = ZeppelinClient.initialize(zeppelinUri, token);
  }

  public void start() {
    if (zeppelinhubClient != null) {
      zeppelinhubClient.start();
      if (zeppelinClient != null) {
        zeppelinClient.start();
      }
    }
  }

  public void stop() {
    if (zeppelinhubClient != null) {
      zeppelinhubClient.stop();
    }
    if (zeppelinClient != null) {
      zeppelinClient.stop();
    }
  }

  public void relayToHub(String message) {
    zeppelinhubClient.send(message);
  }

  public void relayToZeppelin(Message message, String noteId) {
    zeppelinClient.send(message, noteId);
  }
}
