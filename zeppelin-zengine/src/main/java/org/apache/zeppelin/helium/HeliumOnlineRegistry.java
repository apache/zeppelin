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
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.zeppelin.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
  Logger logger = LoggerFactory.getLogger(HeliumOnlineRegistry.class);
  private final Gson gson;

  public HeliumOnlineRegistry(String name, String uri) {
    super(name, uri);
    gson = new Gson();
  }

  @Override
  public List<HeliumPackage> getAll() throws IOException {
    HttpClient client = HttpClientBuilder.create()
        .setUserAgent("ApacheZeppelin/" + Util.getVersion())
        .build();
    HttpGet get = new HttpGet(uri());
    HttpResponse response = client.execute(get);
    if (response.getStatusLine().getStatusCode() != 200) {
      throw new IOException(uri() + " returned " + response.getStatusLine().toString());
    }

    BufferedReader reader = new BufferedReader(
        new InputStreamReader(response.getEntity().getContent()));
    List<Map<String, Map<String, HeliumPackage>>> packages = gson.fromJson(
        reader,
        new TypeToken<List<Map<String, Map<String, HeliumPackage>>>>() {
        }.getType());
    reader.close();

    List<HeliumPackage> packageList = new LinkedList<>();

    for (Map<String, Map<String, HeliumPackage>> pkg : packages) {
      for (Map<String, HeliumPackage> versions : pkg.values()) {
        packageList.addAll(versions.values());
      }
    }
    return packageList;
  }
}
