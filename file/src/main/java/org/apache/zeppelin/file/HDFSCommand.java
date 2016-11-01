/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.file;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.NameScope;
import org.slf4j.Logger;

import javax.ws.rs.core.UriBuilder;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Definition and HTTP invocation methods for all WebHDFS commands
 *
 */
public class HDFSCommand {

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
  public Op CreateWriteFile = new Op("CREATE", HttpType.PUT, 0);
  public Op DeleteFile = new Op("DELETE", HttpType.DELETE, 0);
  public Op RenameFile = new Op("RENAME", HttpType.PUT, 0);

  public HDFSCommand(String url, String user, Logger logger, int maxLength) {
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
                args.length != op.minArgs)))
    {
      String a = "";
      a = (op != null) ? a + op.op + "\n" : a;
      a = (path != null) ? a + path + "\n" : a;
      a = (args != null) ? a + args + "\n" : a;
      return a;
    }
    return null;
  }


  public String runCommand(Op op, String path, Arg[] args) throws  Exception {
    return runCommand(op, path, null, null, null, args);
  }

  public String runCommand(Op op, String path, FileObject argFile, Arg[] args) throws  Exception {
    return runCommand(op, path, null, null, argFile, args);
  }

  public String runCommand(Op op, String path, FileObject noteDir, String charsetName,
                           Arg[] args) throws Exception {
    return runCommand(op, path, noteDir, charsetName, null, args);
  }

  // The operator that runs all commands
  public String runCommand(Op op, String path, FileObject noteDir, String charsetName,
                           FileObject argFile, Arg[] args) throws Exception {

    // Check arguments
    String error = checkArgs(op, path, args);
    if (error != null) {
      logger.error("Bad arguments to command: " + error);
      return "ERROR: BAD ARGS";
    }

    // Build URI
    UriBuilder builder = UriBuilder
        .fromPath(url)
        .path(path)
        .queryParam("op", op.op);

    if (args != null) {
      for (Arg a : args) {
        builder = builder.queryParam(a.key, a.value);
      }
    }
    java.net.URI uri = builder.build();

    // Connect and get response string
    URL hdfsUrl = uri.toURL();
    HttpURLConnection con = (HttpURLConnection) hdfsUrl.openConnection();
    FileObject noteJson;
    OutputStream out = null;

    if (op.cmd == HttpType.GET) {
      con.setRequestMethod("GET");
      con.setFollowRedirects(true);

      if ("OPEN".equals(op.op)) {
        noteJson = noteDir.resolveFile("note.json", NameScope.CHILD);
        out = noteJson.getContent().getOutputStream(false);
      }

      String result = getReceivedResponse(con, HttpType.GET, hdfsUrl);

      if ("OPEN".equals(op.op)) {
        out.write(result.getBytes());
        out.close();
      }

      return result;
    }
    else if (op.cmd == HttpType.PUT) {
      con.setRequestMethod("PUT");
      con.setFollowRedirects(false);
      int responseCode = con.getResponseCode();
      String result = getReceivedResponse(con, HttpType.PUT, hdfsUrl);

      if (responseCode == 307 && ("CREATE".equals(op.op) || "APPEND".equals(op.op))) {
        String location = con.getHeaderField("Location");
        logger.debug("Redirect Location: " + location);

        hdfsUrl = new URL(location);
        con = (HttpURLConnection) hdfsUrl.openConnection();

        File file = new File(argFile.getURL().toURI());
        FileInputStream fi = new FileInputStream(file);

        con.setRequestMethod("PUT");
        con.setRequestProperty("Content-Type", "application/octet-stream");
        con.setRequestProperty("Transfer-Encoding", "chunked");
        con.setDoOutput(true);

        DataOutputStream outputStream = new DataOutputStream(con.getOutputStream());

        int bytesAvailable = fi.available();
        int maxBufferSize = 1024;
        int bufferSize = Math.min(bytesAvailable, maxBufferSize);
        byte[] buffer = new byte[bufferSize];

        int bytesRead = fi.read(buffer, 0, bufferSize);
        while (bytesRead > 0) {
          outputStream.write(buffer, 0, bufferSize);
          bytesAvailable = fi.available();
          bufferSize = Math.min(bytesAvailable, maxBufferSize);
          bytesRead = fi.read(buffer, 0, bufferSize);
        }

        fi.close();
        outputStream.flush();

        result = getReceivedResponse(con, HttpType.PUT, hdfsUrl);
      }

      return result;
    }
    else if (op.cmd == HttpType.DELETE) {
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
    }
    else {
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
      if (i >= maxLength)
        break;
    }
    in.close();

    return response.toString();
  }
}
