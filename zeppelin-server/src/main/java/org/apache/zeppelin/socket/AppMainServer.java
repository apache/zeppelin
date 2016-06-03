/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.socket;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.servlet.http.HttpServletRequest;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.socket.Message;
import org.apache.zeppelin.ticket.TicketContainer;
import org.apache.zeppelin.utils.SecurityUtils;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zeppelin websocket service.
 */
public class AppMainServer extends WebSocketServlet implements
    WebSocketListener, WebSocketServer {
  private static final Logger LOG = LoggerFactory.getLogger(AppMainServer.class);
  Gson gson = new GsonBuilder()
          .setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").create();

  final Map<String, WebSocketServer> subWebSocketServer = new HashMap<>();

  final Map<String, List<WebAppSocket>> userWebSocketMap = new HashMap<>();
  final Queue<WebAppSocket> connectedSockets = new ConcurrentLinkedQueue<>();


  @Override
  public void configure(WebSocketServletFactory factory) {
    factory.setCreator(new WebAppSocketCreator(this));
  }

  public boolean checkOrigin(HttpServletRequest request, String origin) {
    try {
      return SecurityUtils.isValidOrigin(origin, ZeppelinConfiguration.create());
    } catch (UnknownHostException e) {
      LOG.error(e.toString(), e);
    } catch (URISyntaxException e) {
      LOG.error(e.toString(), e);
    }
    return false;
  }

  public WebAppSocket doWebSocketConnect(HttpServletRequest req, String protocol) {
    return new WebAppSocket(req, protocol, this);
  }

  public void setSubWebSocketServer(String key, WebSocketServer server) {
    synchronized (subWebSocketServer) {
      subWebSocketServer.remove(key);
      subWebSocketServer.put(key, server);
    }
  }

  @Override
  public void onOpen(WebAppSocket conn) {
    LOG.info("New connection from {} : {}", conn.getRequest().getRemoteAddr(),
        conn.getRequest().getRemotePort());
    connectedSockets.add(conn);
  }

  @Override
  public void onMessage(WebAppSocket conn, String msg) {
    try {
      Message messagereceived = deserializeMessage(msg);
      LOG.debug("RECEIVE << " + messagereceived.op);
      LOG.debug("RECEIVE PRINCIPAL << " + messagereceived.principal);
      LOG.debug("RECEIVE TICKET << " + messagereceived.ticket);
      LOG.debug("RECEIVE ROLES << " + messagereceived.roles);

      if (LOG.isTraceEnabled()) {
        LOG.trace("RECEIVE MSG = " + messagereceived);
      }

      WebSocketServer processServer = subWebSocketServer.get(messagereceived.target);
      if (processServer != null) {
        LOG.debug("server {} received.", messagereceived.target);
        processServer.onMessage(conn, msg);
      } else {
        LOG.debug("server {} received.", AppMainServer.class.toString());
      }

      /** Lets be elegant here */
      switch (messagereceived.op) {
          case PING:
            break; //do nothing
          default:
            break;
      }
    } catch (Exception e) {
      LOG.error("Can't handle message", e);
    }
  }

  @Override
  public void onClose(WebAppSocket conn, int code, String reason) {
    LOG.info("Closed connection to {} : {}. ({}) {}", conn.getRequest()
        .getRemoteAddr(), conn.getRequest().getRemotePort(), code, reason);
    removeConnectionFromAllKey(conn);
    connectedSockets.remove(conn);
  }

  protected Message deserializeMessage(String msg) {
    return gson.fromJson(msg, Message.class);
  }

  protected String serializeMessage(Message m) {
    return gson.toJson(m);
  }

  protected void addConnectionToKey(String key, WebAppSocket socket) {
    synchronized (userWebSocketMap) {
      removeConnectionFromAllKey(socket); // make sure a socket relates only a
      // single key.
      List<WebAppSocket> socketList = userWebSocketMap.get(key);
      if (socketList == null) {
        socketList = new LinkedList<>();
        userWebSocketMap.put(key, socketList);
      }
      if (!socketList.contains(socket)) {
        socketList.add(socket);
      }
    }
  }

  protected void removeConnectionFromKey(String key, WebAppSocket socket) {
    synchronized (userWebSocketMap) {
      List<WebAppSocket> socketList = userWebSocketMap.get(key);
      if (socketList != null) {
        socketList.remove(socket);
      }
    }
  }

  protected void removeKey(String key) {
    synchronized (userWebSocketMap) {
      List<WebAppSocket> socketList = userWebSocketMap.remove(key);
    }
  }

  protected void removeConnectionFromAllKey(WebAppSocket socket) {
    synchronized (userWebSocketMap) {
      Set<String> keys = userWebSocketMap.keySet();
      for (String keyValue : keys) {
        removeConnectionFromKey(keyValue, socket);
      }
    }
  }

  protected String getOpenKey(WebAppSocket socket) {
    String key = null;
    synchronized (userWebSocketMap) {
      Set<String> keys = userWebSocketMap.keySet();
      for (String keyValue : keys) {
        List<WebAppSocket> sockets = userWebSocketMap.get(keyValue);
        if (sockets.contains(socket)) {
          key = keyValue;
        }
      }
    }

    return key;
  }

  protected void broadcast(String key, Message m) {
    synchronized (userWebSocketMap) {
      List<WebAppSocket> socketLists = userWebSocketMap.get(key);
      if (socketLists == null || socketLists.size() == 0) {
        return;
      }
      LOG.debug("SEND >> " + m.op);
      for (WebAppSocket conn : socketLists) {
        try {
          conn.send(serializeMessage(m));
        } catch (IOException e) {
          LOG.error("socket error", e);
          removeConnectionFromAllKey(conn);
        }
      }
    }
  }

  protected void broadcastExcept(String key, Message m, WebAppSocket exclude) {
    synchronized (userWebSocketMap) {
      List<WebAppSocket> socketLists = userWebSocketMap.get(key);
      if (socketLists == null || socketLists.size() == 0) {
        return;
      }
      LOG.debug("SEND >> " + m.op);
      for (WebAppSocket conn : socketLists) {
        if (exclude.equals(conn)) {
          continue;
        }
        try {
          conn.send(serializeMessage(m));
        } catch (IOException e) {
          LOG.error("socket error", e);
        }
      }
    }
  }

  protected void broadcastAll(Message m) {
    for (WebAppSocket conn : connectedSockets) {
      try {
        conn.send(serializeMessage(m));
      } catch (IOException e) {
        LOG.error("socket error", e);
      }
    }
  }

  protected void unicast(Message m, WebAppSocket conn) {
    try {
      conn.send(serializeMessage(m));
    } catch (IOException e) {
      LOG.error("socket error", e);
    }
  }
}

