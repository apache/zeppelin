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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API handler.
 *
 */
public class ZeppelinhubRestApiHandler {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinhubRestApiHandler.class);
  public static final String ZEPPELIN_TOKEN_HEADER = "X-Zeppelin-Token";
  private static final String DEFAULT_API_PATH = "/api/v1/zeppelin";
  private static boolean PROXY_ON = false;
  private static String PROXY_HOST;
  private static int PROXY_PORT;

  private final HttpClient client;
  private final String zepelinhubUrl;
  private final String token;

  public static ZeppelinhubRestApiHandler newInstance(String zeppelinhubUrl,
      String token) {
    return new ZeppelinhubRestApiHandler(zeppelinhubUrl, token);
  }

  private ZeppelinhubRestApiHandler(String zeppelinhubUrl, String token) {
    this.zepelinhubUrl = zeppelinhubUrl + DEFAULT_API_PATH + "/";
    this.token = token;

    //TODO(khalid):to make proxy conf consistent with Zeppelin confs
    //readProxyConf();
    client = getAsyncClient();

    try {
      client.start();
    } catch (Exception e) {
      LOG.error("Cannot initialize ZeppelinHub REST async client", e);
    }

  }

  private void readProxyConf() {
    //try reading http_proxy
    String proxyHostString = StringUtils.isBlank(System.getenv("http_proxy")) ?
        System.getenv("HTTP_PROXY") : System.getenv("http_proxy");
    if (StringUtils.isBlank(proxyHostString)) {
      //try https_proxy if no http_proxy
      proxyHostString = StringUtils.isBlank(System.getenv("https_proxy")) ?
          System.getenv("HTTPS_PROXY") : System.getenv("https_proxy");
    }

    if (StringUtils.isBlank(proxyHostString)) {
      PROXY_ON = false;
    } else {
      // host format - http://domain:port/
      String[] parts = proxyHostString.replaceAll("/", "").split(":");
      if (parts.length != 3) {
        LOG.warn("Proxy host format is incorrect {}, e.g. http://domain:port/", proxyHostString);
        PROXY_ON = false;
        return;
      }
      PROXY_HOST = parts[1];
      PROXY_PORT = Integer.parseInt(parts[2]);
      LOG.info("Proxy protocol: {}, domain: {}, port: {}", parts[0], parts[1], parts[2]);
      PROXY_ON = true;
    }
  }

  private HttpClient getAsyncClient() {
    SslContextFactory sslContextFactory = new SslContextFactory();
    HttpClient httpClient = new HttpClient(sslContextFactory);

    // Configure HttpClient
    httpClient.setFollowRedirects(false);
    httpClient.setMaxConnectionsPerDestination(100);
    // Config considerations
    //TODO(khalid): consider using proxy
    //TODO(khalid): consider whether require to follow redirects
    //TODO(khalid): consider multi-threaded connection manager case

    return httpClient;
  }

  public String asyncGet(String argument) throws IOException {
    String note = StringUtils.EMPTY;

    InputStreamResponseListener listener = new InputStreamResponseListener();
    client.newRequest(zepelinhubUrl + argument)
          .header(ZEPPELIN_TOKEN_HEADER, token)
          .send(listener);

    // Wait for the response headers to arrive
    Response response;
    try {
      response = listener.get(30, TimeUnit.SECONDS);
    } catch (InterruptedException | TimeoutException | ExecutionException e) {
      LOG.error("Cannot perform Get request to ZeppelinHub", e);
      throw new IOException("Cannot load note from ZeppelinHub", e);
    }

    int code = response.getStatus();
    if (code == 200) {
      try (InputStream responseContent = listener.getInputStream()) {
        note = IOUtils.toString(responseContent, "UTF-8");
      }
    } else {
      LOG.error("ZeppelinHub Get {} returned with status {} ", zepelinhubUrl + argument, code);
      throw new IOException("Cannot load note from ZeppelinHub");
    }
    return note;
  }

  public void asyncPut(String jsonNote) throws IOException {
    if (StringUtils.isBlank(jsonNote)) {
      LOG.error("Cannot save empty note/string to ZeppelinHub");
      return;
    }

    client.newRequest(zepelinhubUrl).method(HttpMethod.PUT)
        .header(ZEPPELIN_TOKEN_HEADER, token)
        .content(new StringContentProvider(jsonNote, "UTF-8"), "application/json;charset=UTF-8")
        .send(new BufferingResponseListener() {

          @Override
          public void onComplete(Result res) {
            if (!res.isFailed() && res.getResponse().getStatus() == 200) {
              LOG.info("Successfully saved note to ZeppelinHub with {}",
                  res.getResponse().getStatus());
            } else {
              LOG.warn("Failed to save note to ZeppelinHub with HttpStatus {}",
                  res.getResponse().getStatus());
            }
          }

          @Override
          public void onFailure(Response response, Throwable failure) {
            LOG.error("Failed to save note to ZeppelinHub: {}", response.getReason(), failure);
          }
        });
  }

  public void asyncDel(String argument) {
    if (StringUtils.isBlank(argument)) {
      LOG.error("Cannot delete empty note from ZeppelinHub");
      return;
    }
    client.newRequest(zepelinhubUrl + argument)
        .method(HttpMethod.DELETE)
        .header(ZEPPELIN_TOKEN_HEADER, token)
        .send(new BufferingResponseListener() {

          @Override
          public void onComplete(Result res) {
            if (!res.isFailed() && res.getResponse().getStatus() == 200) {
              LOG.info("Successfully removed note from ZeppelinHub with {}",
                  res.getResponse().getStatus());
            } else {
              LOG.warn("Failed to remove note from ZeppelinHub with HttpStatus {}",
                  res.getResponse().getStatus());
            }
          }

          @Override
          public void onFailure(Response response, Throwable failure) {
            LOG.error("Failed to remove note from ZeppelinHub: {}", response.getReason(), failure);
          }
        });
  }

  public void close() {
    try {
      client.stop();
    } catch (Exception e) {
      LOG.info("Couldn't stop ZeppelinHub client properly", e);
    }
  }
}
