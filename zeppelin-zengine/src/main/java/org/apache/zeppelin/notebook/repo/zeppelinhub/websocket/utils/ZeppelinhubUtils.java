package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.utils;

import java.util.HashMap;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.protocol.ZeppelinHubOp;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.protocol.ZeppelinhubMessage;
import org.apache.zeppelin.notebook.socket.Message;
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
             .newMessage(ZeppelinHubOp.LIVE, data, new HashMap<String, String>())
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

  public static ZeppelinHubOp stringToHubOp(String text) {
    ZeppelinHubOp hubOp = null;
    try {
      hubOp = ZeppelinHubOp.valueOf(text);
    } catch (IllegalArgumentException e) {
      // in case of non Hub op
    }
    return hubOp;
  }

  public static boolean isHubOp(String text) {
    return (stringToHubOp(text) != null); 
  }

  public static Message.OP stringToZeppelinOp(String text) {
    Message.OP zeppelinOp = null;
    try {
      zeppelinOp = Message.OP.valueOf(text);
    } catch (IllegalArgumentException e) {
      // in case of non Hub op
    }
    return zeppelinOp;
  }

  public static boolean isZeppelinOp(String text) {
    return (stringToZeppelinOp(text) != null); 
  }
}
