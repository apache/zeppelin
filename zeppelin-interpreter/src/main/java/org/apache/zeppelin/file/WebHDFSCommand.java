/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.file;

import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Definition and HTTP invocation methods for all WebHDFS commands
 */
public class WebHDFSCommand {

  /**
   * Type of HTTP request
   */
  public enum HttpType {
    GET,
    PUT,
    DELETE
  }

  /**
   * Definition of WebHDFS operator
   */
  public class Op {
    public String op;
    public HttpType cmd;
    public int minArgs;

    public Op(String op, HttpType cmd, int minArgs) {
      this.op = op;
      this.cmd = cmd;
      this.minArgs = minArgs;
    }
  }

  /**
   * Definition of argument to an operator
   */
  public class Arg {
    public String key;
    public String value;

    public Arg(String key, String value) {
      this.key = key;
      this.value = value;
    }
  }

  // How to connect to WebHDFS
  String url = null;
  String user = null;
  int maxLength = 0;
  Logger logger;

  // Define all the commands available
  public Op getFileStatus = new Op("GETFILESTATUS", HttpType.GET, 0);
  public Op listStatus = new Op("LISTSTATUS", HttpType.GET, 0);
  public Op openFile = new Op("OPEN", HttpType.GET, 0);
  public Op makeDirectory = new Op("MKDIRS", HttpType.PUT, 0);
  public Op createWriteFile = new Op("CREATE", HttpType.PUT, 0);
  public Op deleteFile = new Op("DELETE", HttpType.DELETE, 0);
  public Op renameFile = new Op("RENAME", HttpType.PUT, 0);

  public WebHDFSCommand(String url, String user, Logger logger, int maxLength) {
    super();
    this.url = url;
    this.user = user;
    this.maxLength = maxLength;
    this.logger = logger;
  }

  public String checkArgs(Op op, String path, Arg[] args) throws Exception {
    if (op == null ||
        path == null ||
        (op.minArgs > 0 &&
            (args == null ||
                args.length != op.minArgs))) {
      String a = "";
      a = (op != null) ? a + op.op + "\n" : a;
      a = (path != null) ? a + path + "\n" : a;
      a = (args != null) ? a + args + "\n" : a;
      return a;
    }
    return null;
  }


  public String runCommand(Op op, String path, Arg[] args) throws Exception {
    return runCommand(op, path, null, args);
  }

  public String runCommand(Op op, String path, byte[] argFile, Arg[] args) throws Exception {
    // Check arguments
    String error = checkArgs(op, path, args);
    if (error != null) {
      logger.error("Bad arguments to command: " + error);
      return "ERROR: BAD ARGS";
    }


    // Build URI
    String finalUrl = url;
    if (url.endsWith("/") && path.startsWith("/"))
      finalUrl += path.substring(1);
    else
      finalUrl += path;

    URIBuilder uriBuilder = new URIBuilder(finalUrl)
        .addParameter("op", op.op)
        .addParameter("user", this.user);

    if (args != null) {
      boolean isUserName = false;
      for (Arg a : args) {
        uriBuilder.addParameter(a.key, a.value);
        if ("user.name".equals(a.key)) {
          isUserName = true;
        }
      }
      if (!isUserName) {
        uriBuilder.addParameter("user.name", this.user);
      }
    } else {
      uriBuilder.addParameter("user.name", this.user);
    }
    java.net.URI uri = uriBuilder.build();
    // Connect and get response string
    URL hdfsUrl = uri.toURL();
    HttpURLConnection con = (HttpURLConnection) hdfsUrl.openConnection();

    if (op.cmd == HttpType.GET) {
      con.setRequestMethod("GET");
      con.setInstanceFollowRedirects(true);

      String result = getReceivedResponse(con, HttpType.GET, hdfsUrl);
      return result;
    } else if (op.cmd == HttpType.PUT) {
      con.setRequestMethod("PUT");
      con.setInstanceFollowRedirects(false);
      int responseCode = con.getResponseCode();
      String result = getReceivedResponse(con, HttpType.PUT, hdfsUrl);

      if (responseCode == 307 && ("CREATE".equals(op.op) || "APPEND".equals(op.op))) {
        String location = con.getHeaderField("Location");
        logger.debug("Redirect Location: " + location);

        hdfsUrl = new URL(location);
        con = (HttpURLConnection) hdfsUrl.openConnection();

        con.setRequestMethod("PUT");
        con.setRequestProperty("Content-Type", "application/octet-stream");
        con.setRequestProperty("Transfer-Encoding", "chunked");
        con.setDoOutput(true);

        DataOutputStream outputStream = new DataOutputStream(con.getOutputStream());
        outputStream.write(argFile);
        outputStream.flush();

        result = getReceivedResponse(con, HttpType.PUT, hdfsUrl);
      }

      return result;
    } else if (op.cmd == HttpType.DELETE) {
      con.setRequestMethod("DELETE");
      con.setDoInput(true);
      con.setInstanceFollowRedirects(false);
      return getReceivedResponse(con, HttpType.DELETE, hdfsUrl);
    }
    return null;
  }

  private String getReceivedResponse(HttpURLConnection con,
                                     HttpType type, URL url) throws IOException {
    int responseCode = con.getResponseCode();

    BufferedReader in;
    if (responseCode == 200 || responseCode == 201 || responseCode == 307) {
      logger.debug("Sending '{}' request to URL : {}", type.toString(), url);
      logger.debug("Response Code : " + responseCode);
      logger.debug("response message: " + con.getResponseMessage());
      in = new BufferedReader(new InputStreamReader(con.getInputStream()));
    } else {
      logger.info("Sending '{}' request to URL : {}", type.toString(), url);
      logger.info("Response Code : " + responseCode);
      logger.info("response message: " + con.getResponseMessage());
      in = new BufferedReader(new InputStreamReader(con.getErrorStream()));
    }
    String inputLine;
    StringBuffer response = new StringBuffer();
    int i = 0;
    while ((inputLine = in.readLine()) != null) {
      if (inputLine.length() < maxLength) {
        response.append(inputLine);
      }
      i++;
      if (i >= maxLength) {
        logger.warn("Input stream's length(" + inputLine.length()
            + ") is greater than or equal to hdfs.maxlength(" + maxLength
            + "). Please increase hdfs.maxlength in interpreter setting");
        break;
      }
    }
    in.close();

    return response.toString();
  }
}
