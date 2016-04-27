package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.scheduler;

import org.apache.zeppelin.notebook.repo.zeppelinhub.ZeppelinhubRestApiHandler;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.ZeppelinhubClient;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.utils.ZeppelinhubUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Routine that send PING event to zeppelinhub.
 *
 */
public class ZeppelinHubHeartbeat implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinhubRestApiHandler.class);
  private ZeppelinhubClient client;
  
  public static ZeppelinHubHeartbeat newInstance(ZeppelinhubClient client) {
    return new ZeppelinHubHeartbeat(client);
  }
  
  private ZeppelinHubHeartbeat(ZeppelinhubClient client) {
    this.client = client;
  }
  
  @Override
  public void run() {
    LOG.debug("Sending PING to zeppelinhub");
    client.send(ZeppelinhubUtils.pingMessage(client.getToken()));
  }  
}
