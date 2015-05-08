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
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.java_websocket.SSLSocketChannel2;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;

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

