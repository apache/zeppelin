package com.nflabs.zeppelin.socket;

import java.io.IOException;

import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import java.util.concurrent.ExecutorService;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.SSLSocketChannel2;

/**
 * Extension of the java_websocket library's DefaultSSLWebSocketServerFactory
 * to require client side authentication during the SSL handshake
 */
public class SSLWebSocketServerFactory extends DefaultSSLWebSocketServerFactory {

  protected boolean needClientAuth;

  public SSLWebSocketServerFactory(SSLContext sslcontext) {
    super(sslcontext);
    initAttributes();
  }

  public SSLWebSocketServerFactory(SSLContext sslcontext, ExecutorService exec) {
    super(sslcontext, exec);
    initAttributes();
  }

  protected void initAttributes() {
    this.needClientAuth = false;
  }

  @Override
  public ByteChannel wrapChannel( SocketChannel channel, SelectionKey key ) throws IOException {
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
