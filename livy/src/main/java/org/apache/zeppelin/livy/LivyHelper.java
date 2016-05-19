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

package org.apache.zeppelin.livy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;


/***
 * Livy helper class
 */
public class LivyHelper {
  Logger LOGGER = LoggerFactory.getLogger(LivyHelper.class);
  Gson gson = new GsonBuilder().setPrettyPrinting().create();
  HashMap<String, Object> paragraphHttpMap = new HashMap<>();
  Properties property;
  Integer MAX_NOS_RETRY = 60;

  LivyHelper(Properties property) {
    this.property = property;
  }

  public Integer createSession(InterpreterContext context, String kind) throws Exception {
    try {
      String json = executeHTTP(property.getProperty("zeppelin.livy.url") + "/sessions",
          "POST",
          "{" +
              "\"kind\": \"" + kind + "\", " +
              "\"master\": \"" + property.getProperty("zeppelin.livy.master") + "\", " +
              "\"proxyUser\": \"" + context.getAuthenticationInfo().getUser() + "\"" +
              "}",
          context.getParagraphId()
      );
      if (json.contains("CreateInteractiveRequest[\\\"master\\\"]")) {
        json = executeHTTP(property.getProperty("zeppelin.livy.url") + "/sessions",
            "POST",
            "{" +
                "\"kind\": \"" + kind + "\", " +
                "\"conf\":{\"spark.master\": \""
                + property.getProperty("zeppelin.livy.master") + "\"}," +
                "\"proxyUser\": \"" + context.getAuthenticationInfo().getUser() + "\"" +
                "}",
            context.getParagraphId()
        );
      }
      Map jsonMap = (Map<Object, Object>) gson.fromJson(json,
          new TypeToken<Map<Object, Object>>() {
          }.getType());
      Integer sessionId = ((Double) jsonMap.get("id")).intValue();
      if (!jsonMap.get("state").equals("idle")) {
        Integer nosRetry = MAX_NOS_RETRY;

        while (nosRetry >= 0) {
          LOGGER.error(String.format("sessionId:%s state is %s",
              jsonMap.get("id"), jsonMap.get("state")));
          Thread.sleep(1000);
          json = executeHTTP(property.getProperty("zeppelin.livy.url") + "/sessions/" + sessionId,
              "GET", null,
              context.getParagraphId());
          jsonMap = (Map<Object, Object>) gson.fromJson(json,
              new TypeToken<Map<Object, Object>>() {
              }.getType());
          if (jsonMap.get("state").equals("idle")) {
            break;
          } else if (jsonMap.get("state").equals("error")) {
            json = executeHTTP(property.getProperty("zeppelin.livy.url") + "/sessions/" +
                    sessionId + "/log",
                "GET", null,
                context.getParagraphId());
            jsonMap = (Map<Object, Object>) gson.fromJson(json,
                new TypeToken<Map<Object, Object>>() {
                }.getType());
            String logs = StringUtils.join((ArrayList<String>) jsonMap.get("log"), '\n');
            LOGGER.error(String.format("Cannot start  %s.\n%s", kind, logs));
            throw new Exception(String.format("Cannot start  %s.\n%s", kind, logs));
          }
          nosRetry--;
        }
        if (nosRetry <= 0) {
          LOGGER.error("Error getting session for user within 60Sec.");
          throw new Exception(String.format("Cannot start  %s.", kind));
        }
      }
      return sessionId;
    } catch (Exception e) {
      LOGGER.error("Error getting session for user", e);
      throw e;
    }
  }

  protected void initializeSpark(final InterpreterContext context,
                                 final Map<String, Integer> userSessionMap) throws Exception {
    interpret("val sqlContext= new org.apache.spark.sql.SQLContext(sc)\n" +
        "import sqlContext.implicits._", context, userSessionMap);
  }

  public InterpreterResult interpretInput(String stringLines,
                                          final InterpreterContext context,
                                          final Map<String, Integer> userSessionMap,
                                          LivyOutputStream out) {
    try {
      String[] lines = stringLines.split("\n");
      String[] linesToRun = new String[lines.length + 1];
      for (int i = 0; i < lines.length; i++) {
        linesToRun[i] = lines[i];
      }
      linesToRun[lines.length] = "print(\"\")";

      out.setInterpreterOutput(context.out);
      context.out.clear();
      Code r = null;
      String incomplete = "";
      boolean inComment = false;

      for (int l = 0; l < linesToRun.length; l++) {
        String s = linesToRun[l];
        // check if next line starts with "." (but not ".." or "./") it is treated as an invocation
        //for spark
        if (l + 1 < linesToRun.length) {
          String nextLine = linesToRun[l + 1].trim();
          boolean continuation = false;
          if (nextLine.isEmpty()
              || nextLine.startsWith("//")         // skip empty line or comment
              || nextLine.startsWith("}")
              || nextLine.startsWith("object")) {  // include "} object" for Scala companion object
            continuation = true;
          } else if (!inComment && nextLine.startsWith("/*")) {
            inComment = true;
            continuation = true;
          } else if (inComment && nextLine.lastIndexOf("*/") >= 0) {
            inComment = false;
            continuation = true;
          } else if (nextLine.length() > 1
              && nextLine.charAt(0) == '.'
              && nextLine.charAt(1) != '.'     // ".."
              && nextLine.charAt(1) != '/') {  // "./"
            continuation = true;
          } else if (inComment) {
            continuation = true;
          }
          if (continuation) {
            incomplete += s + "\n";
            continue;
          }
        }

        InterpreterResult res;
        try {
          res = interpret(incomplete + s, context, userSessionMap);
        } catch (Exception e) {
          LOGGER.error("Interpreter exception", e);
          return new InterpreterResult(Code.ERROR, InterpreterUtils.getMostRelevantMessage(e));
        }

        r = res.code();

        if (r == Code.ERROR) {
          out.setInterpreterOutput(null);
          return res;
        } else if (r == Code.INCOMPLETE) {
          incomplete += s + "\n";
        } else {
          out.write((res.message() + "\n").getBytes(Charset.forName("UTF-8")));
          incomplete = "";
        }
      }

      if (r == Code.INCOMPLETE) {
        out.setInterpreterOutput(null);
        return new InterpreterResult(r, "Incomplete expression");
      } else {
        out.setInterpreterOutput(null);
        return new InterpreterResult(Code.SUCCESS);
      }

    } catch (Exception e) {
      LOGGER.error("error in interpretInput", e);
      return new InterpreterResult(Code.ERROR, e.getMessage());
    }
  }

  public InterpreterResult interpret(String stringLines,
                                     final InterpreterContext context,
                                     final Map<String, Integer> userSessionMap)
      throws Exception {
    stringLines = stringLines
        //for "\n" present in string
        .replaceAll("\\\\n", "\\\\\\\\n")
        //for new line present in string
        .replaceAll("\\n", "\\\\n")
        // for \" present in string
        .replaceAll("\\\\\"", "\\\\\\\\\"")
        // for " present in string
        .replaceAll("\"", "\\\\\"");

    if (stringLines.trim().equals("")) {
      return new InterpreterResult(Code.SUCCESS, "");
    }
    Map jsonMap = executeCommand(stringLines, context, userSessionMap);
    Integer id = ((Double) jsonMap.get("id")).intValue();
    InterpreterResult res = getResultFromMap(jsonMap);
    if (res != null) {
      return res;
    }

    while (true) {
      Thread.sleep(1000);
      if (paragraphHttpMap.get(context.getParagraphId()) == null) {
        return new InterpreterResult(Code.INCOMPLETE, "");
      }
      jsonMap = getStatusById(context, userSessionMap, id);
      InterpreterResult interpreterResult = getResultFromMap(jsonMap);
      if (interpreterResult != null) {
        return interpreterResult;
      }
    }
  }

  private InterpreterResult getResultFromMap(Map jsonMap) {
    if (jsonMap.get("state").equals("available")) {
      if (((Map) jsonMap.get("output")).get("status").equals("error")) {
        StringBuilder errorMessage = new StringBuilder((String) ((Map) jsonMap
            .get("output")).get("evalue"));
        if (errorMessage.toString().equals("incomplete statement")
            || errorMessage.toString().contains("EOF")) {
          return new InterpreterResult(Code.INCOMPLETE, "");
        }
        String traceback = gson.toJson(((Map) jsonMap.get("output")).get("traceback"));
        if (!traceback.equals("[]")) {
          errorMessage
              .append("\n")
              .append("traceback: \n")
              .append(traceback);
        }

        return new InterpreterResult(Code.ERROR, errorMessage.toString());
      }
      if (((Map) jsonMap.get("output")).get("status").equals("ok")) {
        String result = (String) ((Map) ((Map) jsonMap.get("output"))
            .get("data")).get("text/plain");
        if (result != null) {
          result = result.trim();
          if (result.startsWith("<link")
              || result.startsWith("<script")
              || result.startsWith("<style")
              || result.startsWith("<div")) {
            result = "%html " + result;
          }
        }
        return new InterpreterResult(Code.SUCCESS, result);
      }
    }
    return null;
  }

  private Map executeCommand(String lines, InterpreterContext context,
                             Map<String, Integer> userSessionMap) throws Exception {
    String json = executeHTTP(property.get("zeppelin.livy.url") + "/sessions/"
            + userSessionMap.get(context.getAuthenticationInfo().getUser())
            + "/statements",
        "POST",
        "{\"code\": \"" + lines + "\" }",
        context.getParagraphId());
    if (json.matches("^(\")?Session (\'[0-9]\' )?not found(.?\"?)$")) {
      throw new Exception("Exception: Session not found, Livy server would have restarted, " +
          "or lost session.");
    }
    try {
      Map jsonMap = gson.fromJson(json,
          new TypeToken<Map>() {
          }.getType());
      return jsonMap;
    } catch (Exception e) {
      LOGGER.error("Error executeCommand", e);
      throw e;
    }
  }

  private Map getStatusById(InterpreterContext context,
                            Map<String, Integer> userSessionMap, Integer id) throws Exception {
    String json = executeHTTP(property.getProperty("zeppelin.livy.url") + "/sessions/"
            + userSessionMap.get(context.getAuthenticationInfo().getUser())
            + "/statements/" + id,
        "GET", null, context.getParagraphId());
    try {
      Map jsonMap = gson.fromJson(json,
          new TypeToken<Map>() {
          }.getType());
      return jsonMap;
    } catch (Exception e) {
      LOGGER.error("Error getStatusById", e);
      throw e;
    }
  }

  protected String executeHTTP(String targetURL, String method, String jsonData, String paragraphId)
      throws Exception {
    HttpClient client = HttpClientBuilder.create().build();
    HttpResponse response = null;
    if (method.equals("POST")) {
      HttpPost request = new HttpPost(targetURL);
      request.addHeader("Content-Type", "application/json");
      StringEntity se = new StringEntity(jsonData);
      request.setEntity(se);
      response = client.execute(request);
      paragraphHttpMap.put(paragraphId, request);
    } else if (method.equals("GET")) {
      HttpGet request = new HttpGet(targetURL);
      request.addHeader("Content-Type", "application/json");
      response = client.execute(request);
      paragraphHttpMap.put(paragraphId, request);
    } else if (method.equals("DELETE")) {
      HttpDelete request = new HttpDelete(targetURL);
      request.addHeader("Content-Type", "application/json");
      response = client.execute(request);
    }

    if (response == null) {
      return null;
    }

    if (response.getStatusLine().getStatusCode() == 200
        || response.getStatusLine().getStatusCode() == 201
        || response.getStatusLine().getStatusCode() == 404) {
      return getResponse(response);
    } else {
      String responseString = getResponse(response);
      if (responseString.contains("CreateInteractiveRequest[\\\"master\\\"]")) {
        return responseString;
      }
      LOGGER.error(String.format("Error with %s StatusCode: %s",
          response.getStatusLine().getStatusCode(), responseString));
      throw new Exception(String.format("Error with %s StatusCode: %s",
          response.getStatusLine().getStatusCode(), responseString));
    }
  }

  private String getResponse(HttpResponse response) throws Exception {
    BufferedReader rd = new BufferedReader(
        new InputStreamReader(response.getEntity().getContent()));

    StringBuffer result = new StringBuffer();
    String line = "";
    while ((line = rd.readLine()) != null) {
      result.append(line);
    }
    return result.toString();
  }

  public void cancelHTTP(String paragraphId) {
    if (paragraphHttpMap.get(paragraphId).getClass().getName().contains("HttpPost")) {
      ((HttpPost) paragraphHttpMap.get(paragraphId)).abort();
    } else {
      ((HttpGet) paragraphHttpMap.get(paragraphId)).abort();
    }
    paragraphHttpMap.put(paragraphId, null);
  }

  public void closeSession(Map<String, Integer> userSessionMap) {
    for (Map.Entry<String, Integer> entry : userSessionMap.entrySet()) {
      try {
        executeHTTP(property.getProperty("zeppelin.livy.url") + "/sessions/"
                + entry.getValue(),
            "DELETE", null, null);
      } catch (Exception e) {
        LOGGER.error(String.format("Error closing session for user with session ID: %s",
            entry.getValue()), e);
      }
    }
  }
}
