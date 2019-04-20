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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * PythonRestApiHandler.
 */
public class PythonRestApiHandler extends RestApiHandler {
  private final String endpoint;
  private final ConcurrentLinkedQueue restApiReqResQueue;
  private boolean isResponseSet = false;
  private Object responseObject;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private Gson gson = new Gson();

  public PythonRestApiHandler(String endpoint, ConcurrentLinkedQueue restApiReqResQueue) {
    this.endpoint = endpoint;
    this.restApiReqResQueue = restApiReqResQueue;
  }

  @Override
  protected void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
    synchronized (restApiReqResQueue) {
      restApiReqResQueue.add(this);
      restApiReqResQueue.notifyAll();
    }

    this.request = request;
    this.response = response;

    synchronized (this) {
      long start = System.currentTimeMillis();
      while (!isResponseSet && System.currentTimeMillis() - start <= 60 * 1000) {
        try {
          this.wait();
        } catch (InterruptedException e) {
        }
      }
    }

    if (!isResponseSet) {
      response.setStatus(HttpServletResponse.SC_REQUEST_TIMEOUT);
    } else {
      response.setStatus(HttpServletResponse.SC_OK);
      PrintWriter responseWriter = response.getWriter();
      gson.toJson(responseObject, responseWriter);
    }
  }

  /**
   * Invoke from python
   */
  public Object getRequestBody() throws IOException {
    BufferedReader reader = request.getReader();
    String body = IOUtils.toString(reader);

    // first try parse as a json
    try {
      Map param = gson.fromJson(body, new TypeToken<Map>() {}.getType());
      return param;
    } catch (Exception e) {
      // if request body is not json, thread it as a string
      return body;
    }
  }

  /**
   * Invoke from python
   */
  public void setResponse(Object responseObject) {
    synchronized (this) {
      isResponseSet = true;
      this.responseObject = responseObject;
      this.notify();
    }
  }

  /**
   * Invoke from python
   */
  public String getEndpoint() {
    return endpoint;
  }
}
