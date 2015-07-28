package org.apache.zeppelin.socket;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.websocket.WebSocket;

/**
 * Notebook websocket
 */
public class NotebookSocket implements WebSocket.OnTextMessage{

  private Connection connection;
  private NotebookSocketListener listener;
  private HttpServletRequest request;
  private String protocol;


  public NotebookSocket(HttpServletRequest req, String protocol,
      NotebookSocketListener listener) {
    this.listener = listener;
    this.request = req;
    this.protocol = protocol;
  }

  @Override
  public void onClose(int closeCode, String message) {
    listener.onClose(this, closeCode, message);
  }

  @Override
  public void onOpen(Connection connection) {
    this.connection = connection;
    listener.onOpen(this);
  }

  @Override
  public void onMessage(String message) {
    listener.onMessage(this, message);
  }
  
  
  public HttpServletRequest getRequest() {
    return request;
  }

  public String getProtocol() {
    return protocol;
  }

  public void send(String serializeMessage) throws IOException {
    connection.sendMessage(serializeMessage);
  }

  
}
