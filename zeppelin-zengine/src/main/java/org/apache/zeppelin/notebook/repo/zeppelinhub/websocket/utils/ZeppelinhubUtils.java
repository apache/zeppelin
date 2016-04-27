package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.utils;

import java.util.HashMap;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.protocol.ZeppelinHubOp;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.protocol.ZeppelinhubMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class.
 *
 */
public class ZeppelinhubUtils {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinhubUtils.class);

  public static String liveMessage(String token) {
    if (StringUtils.isBlank(token)) {
      LOG.error("Cannot create Live message: token is null or empty");
      return ZeppelinhubMessage.EMPTY.serialize();
    }
    HashMap<String, Object> data = new HashMap<String, Object>();
    data.put("token", token);
    return ZeppelinhubMessage
             .newMessage(ZeppelinHubOp.ALIVE, data, new HashMap<String, String>())
             .serialize();
  }
  
  public static String deadMessage(String token) {
    if (StringUtils.isBlank(token)) {
      LOG.error("Cannot create Dead message: token is null or empty");
      return ZeppelinhubMessage.EMPTY.serialize();
    }
    HashMap<String, Object> data = new HashMap<String, Object>();
    data.put("token", token);
    return ZeppelinhubMessage
             .newMessage(ZeppelinHubOp.DEAD, data, new HashMap<String, String>())
             .serialize();
  }
  
  public static String pingMessage(String token) {
    if (StringUtils.isBlank(token)) {
      LOG.error("Cannot create Ping message: token is null or empty");
      return ZeppelinhubMessage.EMPTY.serialize();
    }
    HashMap<String, Object> data = new HashMap<String, Object>();
    data.put("token", token);
    return ZeppelinhubMessage
             .newMessage(ZeppelinHubOp.PING, data, new HashMap<String, String>())
             .serialize();
  }
}
