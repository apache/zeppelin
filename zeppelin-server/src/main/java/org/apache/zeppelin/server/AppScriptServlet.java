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

package org.apache.zeppelin.server;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.util.resource.Resource;

/**
 * Simple servlet to dynamically set the Websocket port
 * in the JavaScript sent to the client
 */
public class AppScriptServlet extends DefaultServlet {

  // Hash containing the possible scripts that contain the getPort()
  // function originally defined in app.js
  private static Set<String> scriptPaths = new HashSet<String>(
    Arrays.asList(
      "/scripts/scripts.js",
      "/scripts/app.js"
    )
  );

  private int websocketPort;

  public AppScriptServlet(int websocketPort) {
    this.websocketPort = websocketPort;
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException,
          IOException {

    // Process all requests not for the app script to the parent
    // class
    String uri = request.getRequestURI();
    if (!scriptPaths.contains(uri)) {
      super.doGet(request, response);
      return;
    }

    // Read the script file chunk by chunk
    Resource scriptFile = getResource(uri);
    InputStream is = scriptFile.getInputStream();
    StringBuffer script = new StringBuffer();
    byte[] buffer = new byte[1024];
    while (is.available() > 0) {
      int numRead = is.read(buffer);
      if (numRead <= 0) {
        break;
      }
      script.append(new String(buffer, 0, numRead, "UTF-8"));
    }

    // Replace the string "function getPort(){...}" to return
    // the proper value
    int startIndex = script.indexOf("function getPort()");
    int endIndex = script.indexOf("}", startIndex);

    if (startIndex >= 0 && endIndex >= 0) {
      String replaceString = "function getPort(){return " + websocketPort + "}";
      script.replace(startIndex, endIndex + 1, replaceString);
    }

    response.getWriter().println(script.toString());
  }
}

