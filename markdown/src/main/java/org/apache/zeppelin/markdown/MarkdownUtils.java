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

package org.apache.zeppelin.markdown;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MarkdownUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(MarkdownUtils.class);
  public static final String WEBSEQ_URL = "https://www.websequencediagrams.com/";
  public static final String WEBSEQ_URL_RENDER = "https://www.websequencediagrams.com/index.php";

  private MarkdownUtils() {
    throw new IllegalStateException("Utility class");
  }

  public static String createWebsequenceUrl(String style, String content) {
    style = StringUtils.defaultString(style, "default").trim();

    String webSeqUrl = "";

    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpPost httpPost = new HttpPost(WEBSEQ_URL_RENDER);
      ArrayList<NameValuePair> postParameters = new ArrayList<>();
      postParameters.add(new BasicNameValuePair("style", style));
      postParameters.add(new BasicNameValuePair("apiVersion", "1"));
      postParameters.add(new BasicNameValuePair("format", "png"));
      postParameters.add(new BasicNameValuePair("message", content));
      httpPost.setEntity(new UrlEncodedFormEntity(postParameters, StandardCharsets.UTF_8));
      try (CloseableHttpResponse post = httpClient.execute(httpPost)) {
        if (post.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
          String postResponse = EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8);
          int start = postResponse.indexOf("?png=");
          int end = postResponse.indexOf("\"", start);

          if (start != -1 && end != -1) {
            webSeqUrl = WEBSEQ_URL + postResponse.substring(start, end);
          } else {
            LOGGER.error("Can't get imagecode");
          }
        } else {
          LOGGER.error("websequencediagrams post failed with {}",
              post.getStatusLine().getStatusCode());
        }
      }
    } catch (IOException e) {
      LOGGER.error("Communication with websequencediagrams broken", e);
    }
    return webSeqUrl;
  }
}
