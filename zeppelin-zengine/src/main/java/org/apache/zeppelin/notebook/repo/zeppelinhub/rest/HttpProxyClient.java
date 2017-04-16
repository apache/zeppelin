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
package org.apache.zeppelin.notebook.repo.zeppelinhub.rest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLContext;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ssl.BrowserCompatHostnameVerifier;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.nio.conn.NoopIOSessionStrategy;
import org.apache.http.nio.conn.SchemeIOSessionStrategy;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is http client class for the case of proxy usage
 * jetty-client has issue with https over proxy for 9.2.x
 *   https://github.com/eclipse/jetty.project/issues/408
 *   https://github.com/eclipse/jetty.project/issues/827
 *    
 */

public class HttpProxyClient {
  private static final Logger LOG = LoggerFactory.getLogger(HttpProxyClient.class);
  public static final String ZEPPELIN_TOKEN_HEADER = "X-Zeppelin-Token";
  
  private CloseableHttpAsyncClient client;
  private URI proxyUri;
  
  public static HttpProxyClient newInstance(URI proxyUri) {
    return new HttpProxyClient(proxyUri);
  }
  
  private HttpProxyClient(URI uri) {
    this.proxyUri = uri;
    
    client = getAsyncProxyHttpClient(proxyUri);
    client.start();
  }
  
  public URI getProxyUri() {
    return proxyUri;
  }
  
  private CloseableHttpAsyncClient getAsyncProxyHttpClient(URI proxyUri) {
    LOG.info("Creating async proxy http client");
    PoolingNHttpClientConnectionManager cm = getAsyncConnectionManager();
    HttpHost proxy = new HttpHost(proxyUri.getHost(), proxyUri.getPort());
    
    HttpAsyncClientBuilder clientBuilder = HttpAsyncClients.custom();
    if (cm != null) {
      clientBuilder = clientBuilder.setConnectionManager(cm);
    }

    if (proxy != null) {
      clientBuilder = clientBuilder.setProxy(proxy);
    }
    clientBuilder = setRedirects(clientBuilder);
    return clientBuilder.build();
  }
  
  private PoolingNHttpClientConnectionManager getAsyncConnectionManager() {
    ConnectingIOReactor ioReactor = null;
    PoolingNHttpClientConnectionManager cm = null;
    try {
      ioReactor = new DefaultConnectingIOReactor();
      // ssl setup
      SSLContext sslcontext = SSLContexts.createSystemDefault();
      X509HostnameVerifier hostnameVerifier = new BrowserCompatHostnameVerifier();
      @SuppressWarnings("deprecation")
      Registry<SchemeIOSessionStrategy> sessionStrategyRegistry = RegistryBuilder
          .<SchemeIOSessionStrategy>create()
          .register("http", NoopIOSessionStrategy.INSTANCE)
          .register("https", new SSLIOSessionStrategy(sslcontext, hostnameVerifier))
          .build();

      cm = new PoolingNHttpClientConnectionManager(ioReactor, sessionStrategyRegistry);
    } catch (IOReactorException e) {
      LOG.error("Couldn't initialize multi-threaded async client ", e);
      return null;
    }
    return cm;
  }
  
  private HttpAsyncClientBuilder setRedirects(HttpAsyncClientBuilder clientBuilder) {
    clientBuilder.setRedirectStrategy(new DefaultRedirectStrategy() {
      /** Redirectable methods. */
      private String[] REDIRECT_METHODS = new String[] { 
        HttpGet.METHOD_NAME, HttpPost.METHOD_NAME, 
        HttpPut.METHOD_NAME, HttpDelete.METHOD_NAME, HttpHead.METHOD_NAME 
      };

      @Override
      protected boolean isRedirectable(String method) {
        for (String m : REDIRECT_METHODS) {
          if (m.equalsIgnoreCase(method)) {
            return true;
          }
        }
        return false;
      }
    });
    return clientBuilder;
  }
  
  public String sendToZeppelinHub(HttpRequestBase request,
      boolean withResponse) throws IOException {
    return withResponse ?
        sendAndGetResponse(request) : sendWithoutResponseBody(request);
  }
  

  private String sendWithoutResponseBody(HttpRequestBase request) throws IOException {
    FutureCallback<HttpResponse> callback = getCallback(request);
    client.execute(request, callback);
    return StringUtils.EMPTY;
  }
  
  private String sendAndGetResponse(HttpRequestBase request) throws IOException {
    String data = StringUtils.EMPTY;
    try {
      HttpResponse response = client.execute(request, null).get(30, TimeUnit.SECONDS);
      int code = response.getStatusLine().getStatusCode();
      if (code == 200) {
        try (InputStream responseContent = response.getEntity().getContent()) {
          data = IOUtils.toString(responseContent, "UTF-8");
        }
      } else {
        LOG.error("ZeppelinHub {} {} returned with status {} ", request.getMethod(),
            request.getURI(), code);
        throw new IOException("Cannot perform " + request.getMethod() + " request to ZeppelinHub");
      }
    } catch (InterruptedException | ExecutionException | TimeoutException
        | NullPointerException e) {
      throw new IOException(e);
    }
    return data;
  }
  
  private FutureCallback<HttpResponse> getCallback(final HttpRequestBase request) {
    return new FutureCallback<HttpResponse>() {

      public void completed(final HttpResponse response) {
        request.releaseConnection();
        LOG.info("Note {} completed with {} status", request.getMethod(),
            response.getStatusLine());
      }

      public void failed(final Exception ex) {
        request.releaseConnection();
        LOG.error("Note {} failed with {} message", request.getMethod(),
            ex.getMessage());
      }

      public void cancelled() {
        request.releaseConnection();
        LOG.info("Note {} was canceled", request.getMethod());
      }
    };
  }
  
  public void stop() {
    try {
      client.close();
    } catch (Exception e) {
      LOG.error("Failed to close proxy client ", e);
    }
  }
}
