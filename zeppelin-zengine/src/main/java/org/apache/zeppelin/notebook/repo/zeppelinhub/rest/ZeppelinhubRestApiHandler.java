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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
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
  //TODO(xxx): possibly switch to jetty-client > 9.3.12 when adopt jvm 1.8
  private static HttpProxyClient proxyClient;
  private final HttpClient client;
  private String zepelinhubUrl;

  public static ZeppelinhubRestApiHandler newInstance(String zeppelinhubUrl) {
    return new ZeppelinhubRestApiHandler(zeppelinhubUrl);
  }

  private ZeppelinhubRestApiHandler(String zeppelinhubUrl) {
    this.zepelinhubUrl = zeppelinhubUrl + DEFAULT_API_PATH + "/";

    readProxyConf();
    client = getAsyncClient();

    try {
      client.start();
    } catch (Exception e) {
      LOG.error("Cannot initialize ZeppelinHub REST async client", e);
    }
  }
  
  private void readProxyConf() {
    //try reading https_proxy
    String proxyHostString = StringUtils.isBlank(System.getenv("https_proxy")) ?
        System.getenv("HTTPS_PROXY") : System.getenv("https_proxy");
    if (StringUtils.isBlank(proxyHostString)) {
      //try http_proxy if no https_proxy
      proxyHostString = StringUtils.isBlank(System.getenv("http_proxy")) ?
          System.getenv("HTTP_PROXY") : System.getenv("http_proxy");
    }

    if (!StringUtils.isBlank(proxyHostString)) {
      URI uri = null;
      try {
        uri = new URI(proxyHostString);
      } catch (URISyntaxException e) {
        LOG.warn("Proxy uri doesn't follow correct syntax", e);
      }
      if (uri != null) {
        PROXY_ON = true;
        proxyClient = HttpProxyClient.newInstance(uri);
      }
    }
  }

  private HttpClient getAsyncClient() {
    SslContextFactory sslContextFactory = new SslContextFactory();
    HttpClient httpClient = new HttpClient(sslContextFactory);
    // Configure HttpClient
    httpClient.setFollowRedirects(false);
    httpClient.setMaxConnectionsPerDestination(100);

    // Config considerations
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
    if (StringUtils.isBlank(token)) {
      return StringUtils.EMPTY;
    }
    String url = zepelinhubUrl + argument;
    if (PROXY_ON) {
      return sendToZeppelinHubViaProxy(new HttpGet(url), StringUtils.EMPTY, token, true);
    } else {
      return sendToZeppelinHub(HttpMethod.GET, url, StringUtils.EMPTY, token, true);
    }
  }
  
  public String putWithResponseBody(String token, String url, String json) throws IOException {
    if (StringUtils.isBlank(url) || StringUtils.isBlank(json)) {
      LOG.error("Empty note, cannot send it to zeppelinHub");
      throw new IOException("Cannot send emtpy note to zeppelinHub");
    }
    if (PROXY_ON) {
      return sendToZeppelinHubViaProxy(new HttpPut(zepelinhubUrl + url), json, token, true);
    } else {
      return sendToZeppelinHub(HttpMethod.PUT, zepelinhubUrl + url, json, token, true);
    }
  }
  
  public void put(String token, String jsonNote) throws IOException {
    if (StringUtils.isBlank(jsonNote)) {
      LOG.error("Cannot save empty note/string to ZeppelinHub");
      return;
    }
    if (PROXY_ON) {
      sendToZeppelinHubViaProxy(new HttpPut(zepelinhubUrl), jsonNote, token, false);
    } else {
      sendToZeppelinHub(HttpMethod.PUT, zepelinhubUrl, jsonNote, token, false);
    }
  }

  public void del(String token, String argument) throws IOException {
    if (StringUtils.isBlank(argument)) {
      LOG.error("Cannot delete empty note from ZeppelinHub");
      return;
    }
    if (PROXY_ON) {
      sendToZeppelinHubViaProxy(new HttpDelete(zepelinhubUrl + argument), StringUtils.EMPTY, token,
          false);
    } else {
      sendToZeppelinHub(HttpMethod.DELETE, zepelinhubUrl + argument, StringUtils.EMPTY, token,
          false);
    }
  }
  
  private String sendToZeppelinHubViaProxy(HttpRequestBase request, 
                                           String json, 
                                           String token,
                                           boolean withResponse) throws IOException {
    request.setHeader(ZEPPELIN_TOKEN_HEADER, token);
    if (request.getMethod().equals(HttpPost.METHOD_NAME)) {
      HttpPost post = (HttpPost) request;
      StringEntity content = new StringEntity(json, "application/json;charset=UTF-8");
      post.setEntity(content);
    }
    if (request.getMethod().equals(HttpPut.METHOD_NAME)) {
      HttpPut put = (HttpPut) request;
      StringEntity content = new StringEntity(json, "application/json;charset=UTF-8");
      put.setEntity(content);
    }
    String body = StringUtils.EMPTY;
    if (proxyClient != null) {
      body = proxyClient.sendToZeppelinHub(request, withResponse);
    } else {
      LOG.warn("Proxy client request was submitted while not correctly initialized");
    }
    return body; 
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
      if (proxyClient != null) {
        proxyClient.stop();
      }
    } catch (Exception e) {
      LOG.info("Couldn't stop ZeppelinHub client properly", e);
    }
  }
}
