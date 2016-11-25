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
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.notebook.repo.zeppelinhub.model.Instance;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * REST API handler.
 *
 */
public class ZeppelinhubRestApiHandler {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinhubRestApiHandler.class);
  public static final String ZEPPELIN_TOKEN_HEADER = "X-Zeppelin-Token";
  private static final String USER_SESSION_HEADER = "X-User-Session";
  private static final String DEFAULT_API_PATH = "/api/v1/zeppelin";
  private static boolean PROXY_ON = false;
  private static String PROXY_HOST;
  private static int PROXY_PORT;

  private final HttpClient client;
  private final String zepelinhubUrl;

  public static ZeppelinhubRestApiHandler newInstance(String zeppelinhubUrl, String token) {
    return new ZeppelinhubRestApiHandler(zeppelinhubUrl, token);
  }

  private ZeppelinhubRestApiHandler(String zeppelinhubUrl, String token) {
    this.zepelinhubUrl = zeppelinhubUrl + DEFAULT_API_PATH + "/";

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

  /**
   * Fetch zeppelin instances for a given user.
   * @param ticket
   * @return
   * @throws IOException
   */
  public List<Instance> getInstances(String ticket) throws IOException {
    InputStreamResponseListener listener = new InputStreamResponseListener();
    Response response;
    String url = zepelinhubUrl + "instances";
    String data;

    Request request = client.newRequest(url).header(USER_SESSION_HEADER, ticket);
    request.send(listener);

    try {
      response = listener.get(30, TimeUnit.SECONDS);
    } catch (InterruptedException | TimeoutException | ExecutionException e) {
      LOG.error("Cannot perform request to ZeppelinHub", e);
      throw new IOException("Cannot perform  GET request to ZeppelinHub", e);
    }

    int code = response.getStatus();
    if (code == 200) {
      try (InputStream responseContent = listener.getInputStream()) {
        data = IOUtils.toString(responseContent, "UTF-8");
      }
    } else {
      LOG.error("ZeppelinHub GET {} returned with status {} ", url, code);
      throw new IOException("Cannot perform  GET request to ZeppelinHub");
    }
    Type listType = new TypeToken<ArrayList<Instance>>() {}.getType();
    return new Gson().fromJson(data, listType);
  }

  public String get(String token, String argument) throws IOException {
    String url = zepelinhubUrl + argument;
    return sendToZeppelinHub(HttpMethod.GET, url, StringUtils.EMPTY, token, true);
  }
  
  public String putWithResponseBody(String token, String url, String json) throws IOException {
    if (StringUtils.isBlank(url) || StringUtils.isBlank(json)) {
      LOG.error("Empty note, cannot send it to zeppelinHub");
      throw new IOException("Cannot send emtpy note to zeppelinHub");
    }
    return sendToZeppelinHub(HttpMethod.PUT, zepelinhubUrl + url, json, token, true);
  }
  
  public void put(String token, String jsonNote) throws IOException {
    if (StringUtils.isBlank(jsonNote)) {
      LOG.error("Cannot save empty note/string to ZeppelinHub");
      return;
    }
    sendToZeppelinHub(HttpMethod.PUT, zepelinhubUrl, jsonNote, token, false);
  }

  public void del(String token, String argument) throws IOException {
    if (StringUtils.isBlank(argument)) {
      LOG.error("Cannot delete empty note from ZeppelinHub");
      return;
    }
    sendToZeppelinHub(HttpMethod.DELETE, zepelinhubUrl + argument, StringUtils.EMPTY, token, false);
  }
  
  private String sendToZeppelinHub(HttpMethod method,
                                   String url,
                                   String json,
                                   String token,
                                   boolean withResponse)
      throws IOException {
    Request request = client.newRequest(url).method(method).header(ZEPPELIN_TOKEN_HEADER, token);
    if ((method.equals(HttpMethod.PUT) || method.equals(HttpMethod.POST))
        && !StringUtils.isBlank(json)) {
      request.content(new StringContentProvider(json, "UTF-8"), "application/json;charset=UTF-8");
    }
    return withResponse ?
        sendToZeppelinHub(request) : sendToZeppelinHubWithoutResponseBody(request);
  }
  
  private String sendToZeppelinHubWithoutResponseBody(Request request) throws IOException {
    request.send(new Response.CompleteListener() {
      @Override
      public void onComplete(Result result) {
        Request req = result.getRequest();
        LOG.info("ZeppelinHub {} {} returned with status {}: {}", req.getMethod(),
            req.getURI(), result.getResponse().getStatus(), result.getResponse().getReason());
      }
    });
    return StringUtils.EMPTY;
  }
  
  private String sendToZeppelinHub(final Request request) throws IOException {
    InputStreamResponseListener listener = new InputStreamResponseListener();
    Response response;
    String data;
    request.send(listener);
    try {
      response = listener.get(30, TimeUnit.SECONDS);
    } catch (InterruptedException | TimeoutException | ExecutionException e) {
      String method = request.getMethod();
      LOG.error("Cannot perform {} request to ZeppelinHub", method, e);
      throw new IOException("Cannot perform " + method + " request to ZeppelinHub", e);
    }

    int code = response.getStatus();
    if (code == 200) {
      try (InputStream responseContent = listener.getInputStream()) {
        data = IOUtils.toString(responseContent, "UTF-8");
      }
    } else {
      String method = response.getRequest().getMethod();
      String url = response.getRequest().getURI().toString();
      LOG.error("ZeppelinHub {} {} returned with status {} ", method, url, code);
      throw new IOException("Cannot perform " + method + " request to ZeppelinHub");
    }
    return data;
  }

  public void close() {
    try {
      client.stop();
    } catch (Exception e) {
      LOG.info("Couldn't stop ZeppelinHub client properly", e);
    }
  }
}
