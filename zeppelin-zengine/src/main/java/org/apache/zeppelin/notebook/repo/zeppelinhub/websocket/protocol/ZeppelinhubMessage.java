package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.protocol;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.Client;
import org.apache.zeppelin.notebook.socket.Message.OP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * Zeppelinhub message class.
 *
 */
public class ZeppelinhubMessage {
  private static final Gson gson = new Gson();
  private static final Logger LOG = LoggerFactory.getLogger(Client.class);
  public static final ZeppelinhubMessage EMPTY = new ZeppelinhubMessage();

  public OP op;
  public Object data;
  public Map<String, String> meta = Maps.newHashMap();
  
  private ZeppelinhubMessage() {
    this.op = OP.LIST_NOTES;
    this.data = null;
  }
  
  private ZeppelinhubMessage(OP op, Object data, Map<String, String> meta) {
    this.op = op;
    this.data = data;
    this.meta = meta;
  }
  
  public static ZeppelinhubMessage newMessage(OP op, Object data, Map<String, String> meta) {
    return new ZeppelinhubMessage(op, data, meta);
  }
  
  public String serialize() {
    return gson.toJson(this, ZeppelinhubMessage.class);
  }
  
  public static ZeppelinhubMessage deserialize(String zeppelinhubMessage) {
    if (StringUtils.isBlank(zeppelinhubMessage)) {
      return EMPTY;
    }
    ZeppelinhubMessage msg;
    try {
      msg = gson.fromJson(zeppelinhubMessage, ZeppelinhubMessage.class);
    } catch(JsonSyntaxException ex) {
      LOG.error("Cannot deserialize zeppelinhub message", ex);
      msg = EMPTY;
    }
    return msg;
  }
  
}
