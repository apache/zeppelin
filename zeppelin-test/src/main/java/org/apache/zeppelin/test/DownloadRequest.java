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

package org.apache.zeppelin.test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

public class DownloadRequest {
  private final URL url;
  private final Optional<URL> alternativeUrl;
  private final int retries;

  public static final int DEFAULT_RETRIES = 3;

  public DownloadRequest(String url, int retries) throws MalformedURLException {
    this(url, null, retries);
  }

  public DownloadRequest(String url, String alternativeUrl) throws MalformedURLException {
    this(url, alternativeUrl, DEFAULT_RETRIES);
  }

  public DownloadRequest(String url, String alternativeUrl, int retries)
      throws MalformedURLException {
    if (alternativeUrl != null) {
      this.url = new URL(url);
      this.alternativeUrl = Optional.of(new URL(alternativeUrl));
      this.retries = retries;
    } else {
      this.url = new URL(url);
      this.alternativeUrl = Optional.empty();
      this.retries = retries;
    }
  }

  public DownloadRequest(URL url, int retries) {
    this(url, null, retries);
  }

  public DownloadRequest(URL url, URL alternativeUrl) {
    this(url, alternativeUrl, DEFAULT_RETRIES);
  }

  public DownloadRequest(URL url, URL alternativeUrl, int retries) {
    this.url = url;
    this.alternativeUrl = Optional.of(alternativeUrl);
    this.retries = retries;
  }

  public URL getUrl() {
    return url;
  }

  public Optional<URL> getAlternativeUrl() {
    return alternativeUrl;
  }

  public int getRetries() {
    return retries;
  }
}
