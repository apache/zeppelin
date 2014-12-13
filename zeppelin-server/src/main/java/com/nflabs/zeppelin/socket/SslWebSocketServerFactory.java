package com.nflabs.zeppelin.socket;

import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.SSLSocketChannel2;

import java.io.IOException;

import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import java.util.concurrent.ExecutorService;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

/**
 * Extension of the java_websocket library's DefaultSslWebSocketServerFactory
 * to require client side authentication during the SSL handshake
 */
public class SslWebSocketServerFactory 
    extends DefaultSSLWebSocketServerFactory {

  protected boolean needClientAuth;

  public SslWebSocketServerFactory(SSLContext sslcontext) {
    super(sslcontext);
    initAttributes();
  }

  public SslWebSocketServerFactory(
      SSLContext sslcontext,
      ExecutorService exec) {

    super(sslcontext, exec);
    initAttributes();
  }

  protected void initAttributes() {
    this.needClientAuth = false;
  }

  @Override
  public ByteChannel wrapChannel(SocketChannel channel, SelectionKey key)
      throws IOException {

    SSLEngine sslEngine = sslcontext.createSSLEngine();
    sslEngine.setUseClientMode(false);
    sslEngine.setNeedClientAuth(needClientAuth);
    return new SSLSocketChannel2( channel, sslEngine, exec, key );
  }

  public boolean getNeedClientAuth() {
    return needClientAuth;
  }

  public void setNeedClientAuth(boolean needClientAuth) {
    this.needClientAuth = needClientAuth;
  }
}

