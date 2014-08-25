package com.nflabs.zeppelin.socket;

import java.net.InetSocketAddress;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Notebook extends WebSocketServer {
  
  private static final Logger LOG = LoggerFactory.getLogger(Notebook.class);

  private static final int DEFAULT_PORT=8282;
  
  private static void creatingwebSocketServerLog(int port) {
    LOG.info("Create zeppeling websocket on port {}", port);
  }
  
  public Notebook() {
    super(new InetSocketAddress(DEFAULT_PORT));
    creatingwebSocketServerLog(DEFAULT_PORT);
  }
  
  public Notebook(int port) {
    super(new InetSocketAddress(port));
    creatingwebSocketServerLog(port);
  }

  @Override
  public void onClose(WebSocket conn, int code, String reason, boolean remote) {
    LOG.info("Closed connection to " + conn.getRemoteSocketAddress().getAddress().getHostAddress());
    
  }

  @Override
  public void onError(WebSocket conn, Exception message) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void onMessage(WebSocket conn, String arg1) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void onOpen(WebSocket conn, ClientHandshake handshake) {
    LOG.info("New connection from " + conn.getRemoteSocketAddress().getAddress().getHostAddress());
    
  }
  
}
