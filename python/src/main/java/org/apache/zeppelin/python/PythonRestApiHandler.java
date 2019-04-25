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

package org.apache.zeppelin.python;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.serving.RestApiHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * PythonRestApiHandler.
 */
public class PythonRestApiHandler extends RestApiHandler {
  private static Logger LOGGER = LoggerFactory.getLogger(PythonRestApiHandler.class);

  private final String endpoint;
  private final ConcurrentLinkedQueue<PythonRestApiRequestResponseMessage> restApiReqResQueue;
  private Gson gson = new Gson();

  public PythonRestApiHandler(
          String endpoint,
          ConcurrentLinkedQueue<PythonRestApiRequestResponseMessage> restApiReqResQueue) {
    this.endpoint = endpoint;
    this.restApiReqResQueue = restApiReqResQueue;
  }

  @Override
  protected void handle(HttpServletRequest request, HttpServletResponse response)
          throws IOException {
    BufferedReader reader = request.getReader();
    Object requestObject;
    String contentType = request.getContentType();
    try {
      if (contentType != null && contentType.startsWith("application/json")) {
        requestObject = gson.fromJson(reader, new TypeToken<Map>() {}.getType());
      } else {
        requestObject = IOUtils.toString(reader);
      }
    } catch (Exception e) {
      LOGGER.error("Bad request", e);
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    PythonRestApiRequestResponseMessage message = new PythonRestApiRequestResponseMessage(
            endpoint,
            requestObject,
            request,
            response);

    synchronized (restApiReqResQueue) {
      restApiReqResQueue.add(message);
      restApiReqResQueue.notifyAll();
    }

    synchronized (message) {
      long start = System.currentTimeMillis();
      while (!message.isResponseSet() && System.currentTimeMillis() - start <= 60 * 1000) {
        try {
          message.wait();
        } catch (InterruptedException e) {
          continue;
        }
      }
    }

    if (!message.isResponseSet()) {
      response.setStatus(HttpServletResponse.SC_REQUEST_TIMEOUT);
    } else {
      response.setStatus(HttpServletResponse.SC_OK);
      Object headers = message.getResponseHeader();
      if (headers != null && headers instanceof Map) {
        ((Map) headers).forEach((k, v) -> response.setHeader(k.toString(), v.toString()));
      }

      PrintWriter responseWriter = response.getWriter();
      gson.toJson(message.getResponseBody(), responseWriter);
    }
  }
}
