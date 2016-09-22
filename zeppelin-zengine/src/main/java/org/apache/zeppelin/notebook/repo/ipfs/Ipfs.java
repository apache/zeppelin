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

package org.apache.zeppelin.notebook.repo.ipfs;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;


/**
 * Ipfs HttpClient
 */
public class Ipfs {
  private static final Logger LOG = LoggerFactory.getLogger(Ipfs.class);
  private CloseableHttpClient client;
  private String baseUrl;

  public Ipfs(String url) {
    baseUrl = url;
    client = HttpClients.createDefault();
  }

  public String pinLs() {
    String pinnedJsonString = null;
    HttpGet request = new HttpGet(baseUrl + "pin/ls?type=recursive&quiet=true");
    CloseableHttpResponse response = null;
    try {
      response = client.execute(request);
      int statusCode = response.getStatusLine().getStatusCode();
      HttpEntity resEntity = response.getEntity();
      if (statusCode == HttpStatus.SC_OK && resEntity != null) {
        pinnedJsonString = EntityUtils.toString(resEntity, "UTF-8");
      } else {
        LOG.error("Failed to get pinned objects with unexpected statuscode " + statusCode);
      }
    } catch (IOException e) {
      LOG.error("Http request failed to send to " + baseUrl, e);
    } finally {
      try {
        response.close();
      } catch (IOException | NullPointerException e) {
        LOG.error("Failed to close response", e);
      }
    }
    return pinnedJsonString;
  }

  public boolean pinRm(String hash) {
    boolean removed = false;
    HttpGet request = new HttpGet(baseUrl + "pin/rm?recursive=true&arg=" + hash);
    CloseableHttpResponse response = null;
    try {
      response = client.execute(request);
      int statusCode = response.getStatusLine().getStatusCode();
      HttpEntity resEntity = response.getEntity();
      String responseMessage;
      if (statusCode == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
        responseMessage = EntityUtils.toString(resEntity, "UTF-8");
        Type type = new TypeToken<Map<String, String>>() {
        }.getType();
        Map<String, String> responseMap = new Gson().fromJson(responseMessage, type);
        String errormessage = responseMap.get("Message");
        if (errormessage.equals("note pinned")) {
          removed = true;
        }

        LOG.error("response Message = " + responseMessage);
          /*
           * HTTP/1.1 500 Internal Server Error
           * Content-Type: application/json
           * responseMessage = {"Message":"invalid ipfs ref path","Code":0}
           * or not pinned
           * HTTP/1.1 500 Internal Server Error
           * responseMessage = {"Message":"not pinned","Code":0}
           *
           **/
      } else if (statusCode == HttpStatus.SC_OK && resEntity != null) {
        responseMessage = EntityUtils.toString(resEntity, "UTF-8");
        removed = true;
        LOG.info("response Message = " + responseMessage);
      } else {
        LOG.error("Failed to remove pinned object with unexpected statuscode " + statusCode);
      }
    } catch (IOException e) {
      LOG.error("Http request failed to send to " + baseUrl, e);
    } finally {
      try {
        response.close();
      } catch (IOException | NullPointerException e) {
        LOG.error("Failed to close response", e);
      }
    }
    return removed;
  }

  public String cat(String hash) {
    HttpGet request = new HttpGet(baseUrl + "cat/" + hash);
    String ipfsContent = null;
    RequestConfig config = RequestConfig.custom()
        .setSocketTimeout(555000)
        .build();
    request.setConfig(config);
    CloseableHttpResponse response = null;
    try {
      response = client.execute(request);
      int statusCode = response.getStatusLine().getStatusCode();
      HttpEntity resEntity = response.getEntity();
      if (statusCode == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
        String responseMessage = EntityUtils.toString(resEntity, "UTF-8");
        LOG.error("response Message = " + responseMessage);
          /*
           * HTTP/1.1 500 Internal Server Error
           * Content-Type: application/json
           * responseMessage = {"Message":"invalid ipfs ref path","Code":0}
           *
           **/
      } else if (statusCode == HttpStatus.SC_OK && resEntity != null) {
        String responseMessage = EntityUtils.toString(resEntity, "UTF-8");
        return responseMessage;
      } else {
        LOG.error("Failed to get ipfs object with unexpected statuscode " + statusCode);
      }
    } catch (IOException e) {
      LOG.error("Http request failed to send to " + baseUrl, e);
    } finally {
      try {
        response.close();
      } catch (IOException | NullPointerException e) {
        LOG.error("Failed to close response", e);
      }
    }
    return ipfsContent;
  }

  public String add(String content) {
    String filename = "note.json";
    String responseMessage = null;
    if (content == null) {
      return null;
    }
    HttpPost request = new HttpPost(baseUrl + "add?stream-channels=true&progress=false");
    HttpEntity entity = MultipartEntityBuilder.create()
        .addBinaryBody("file", content.getBytes(), ContentType
            .APPLICATION_OCTET_STREAM, filename)
        .build();
    request.setEntity(entity);
    CloseableHttpResponse response = null;
    try {
      response = client.execute(request);
      int statusCode = response.getStatusLine().getStatusCode();
      HttpEntity resEntity = response.getEntity();
      if (statusCode == HttpStatus.SC_OK && resEntity != null) {
        responseMessage = EntityUtils.toString(resEntity, "UTF-8");
      } else {
        LOG.error("Failed to add ipfs object with unexpected statuscode " + statusCode);
      }
    } catch (IOException e) {
      LOG.error("Http request failed to send to " + baseUrl, e);
    } finally {
      try {
        response.close();
      } catch (IOException | NullPointerException e) {
        LOG.error("Failed to close response", e);
      }
    }
    return responseMessage;
  }

  public String version() {
    String version = null;
    HttpGet request = new HttpGet(baseUrl + "/version");
    CloseableHttpResponse response = null;
    try {
      response = client.execute(request);
      int statusCode = response.getStatusLine().getStatusCode();
      HttpEntity resEntity = response.getEntity();
      if (statusCode == HttpStatus.SC_OK && resEntity != null) {
        version = EntityUtils.toString(resEntity, "UTF-8");
      } else {
        LOG.error("Failed to get ipfs version with unexpected statuscode " + statusCode);
      }
    } catch (IOException e) {
      LOG.error("Http request failed to send to " + baseUrl, e);
    } finally {
      try {
        response.close();
      } catch (IOException | NullPointerException e) {
        LOG.error("Failed to close response", e);
      }
    }
    return version;
  }

  public CloseableHttpClient getClient() {
    return client;
  }
}


