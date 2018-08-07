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
package org.apache.zeppelin.helium;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * This registry reads helium package json data
 * from specified url.
 *
 * File should be look like
 * [
 *    "packageName": {
 *       "0.0.1": json serialized HeliumPackage class,
 *       "0.0.2": json serialized HeliumPackage class,
 *       ...
 *    },
 *    ...
 * ]
 */
public class HeliumOnlineRegistry extends HeliumRegistry {
  private Logger logger = LoggerFactory.getLogger(HeliumOnlineRegistry.class);
  private final Gson gson;
  private final File registryCacheFile;

  public HeliumOnlineRegistry(String name, String uri, File registryCacheDir) {
    super(name, uri);
    registryCacheDir.mkdirs();
    UUID registryCacheFileUuid = UUID.nameUUIDFromBytes(uri.getBytes());
    this.registryCacheFile = new File(registryCacheDir, registryCacheFileUuid.toString());

    gson = new Gson();
  }

  @Override
  public synchronized List<HeliumPackage> getAll() throws IOException {
    HttpClient client = HttpClientBuilder.create()
        .setUserAgent("ApacheZeppelin/" + Util.getVersion())
        .setProxy(getProxy(uri()))
        .build();
    HttpGet get = new HttpGet(uri());
    HttpResponse response;
    try {
      ZeppelinConfiguration cfg = ZeppelinConfiguration.create();
      if ((get.getURI().getHost().equals(cfg.getS3Endpoint()))) {
        if (cfg.getS3Timeout() != null) {
          int timeout = Integer.valueOf(cfg.getS3Timeout());
          RequestConfig requestCfg = RequestConfig.custom()
                  .setConnectTimeout(timeout)
                  .setSocketTimeout(timeout)
                  .build();
          get.setConfig(requestCfg);
        }
      }
      response = client.execute(get);
    } catch (Exception e) {
      logger.error(e.getMessage());
      return readFromCache();
    }

    if (response.getStatusLine().getStatusCode() != 200) {
      // try read from cache
      logger.error(uri() + " returned " + response.getStatusLine().toString());
      return readFromCache();
    } else {
      List<HeliumPackage> packageList = new LinkedList<>();

      BufferedReader reader;
      reader = new BufferedReader(
          new InputStreamReader(response.getEntity().getContent()));

      List<Map<String, Map<String, HeliumPackage>>> packages = gson.fromJson(
          reader,
          new TypeToken<List<Map<String, Map<String, HeliumPackage>>>>() {
          }.getType());
      reader.close();

      for (Map<String, Map<String, HeliumPackage>> pkg : packages) {
        for (Map<String, HeliumPackage> versions : pkg.values()) {
          packageList.addAll(versions.values());
        }
      }

      writeToCache(packageList);
      return packageList;
    }
  }

  private HttpHost getProxy(String uri) {
    String httpProxy = StringUtils.isBlank(System.getenv("http_proxy")) ?
            System.getenv("HTTP_PROXY") : System.getenv("http_proxy");

    String httpsProxy = StringUtils.isBlank(System.getenv("https_proxy")) ?
            System.getenv("HTTPS_PROXY") : System.getenv("https_proxy");

    try {
      String scheme = new URI(uri).getScheme();
      if (scheme.toLowerCase().startsWith("https") && StringUtils.isNotBlank(httpsProxy)) {
        URI httpsProxyUri = new URI(httpsProxy);
        return new HttpHost(httpsProxyUri.getHost(),
                httpsProxyUri.getPort(), httpsProxyUri.getScheme());
      }
      else if (scheme.toLowerCase().startsWith("http") && StringUtils.isNotBlank(httpProxy)){
        URI httpProxyUri = new URI(httpProxy);
        return new HttpHost(httpProxyUri.getHost(),
                httpProxyUri.getPort(), httpProxyUri.getScheme());
      }
      else return null;
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
      return null;
    }
  }

  private List<HeliumPackage> readFromCache() {
    synchronized (registryCacheFile) {
      if (registryCacheFile.isFile()) {
        try {
          return gson.fromJson(
              new FileReader(registryCacheFile),
              new TypeToken<List<HeliumPackage>>() {
              }.getType());
        } catch (FileNotFoundException e) {
          logger.error(e.getMessage(), e);
          return new LinkedList<>();
        }
      } else {
        return new LinkedList<>();
      }
    }
  }

  private void writeToCache(List<HeliumPackage> pkg) throws IOException {
    synchronized (registryCacheFile) {
      if (registryCacheFile.exists()) {
        registryCacheFile.delete();
      }
      String jsonToCache = gson.toJson(pkg);
      FileUtils.writeStringToFile(registryCacheFile, jsonToCache);
    }
  }
}
