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
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.kerberos.client.KerberosRestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.Charset;
import java.util.*;
import java.util.Map.Entry;


/***
 * Livy helper class
 */
public class LivyHelper {
  Logger LOGGER = LoggerFactory.getLogger(LivyHelper.class);
  Gson gson = new GsonBuilder().setPrettyPrinting().create();
  HashMap<String, Object> paragraphHttpMap = new HashMap<>();
  Properties property;

  LivyHelper(Properties property) {
    this.property = property;
  }

  public Integer createSession(InterpreterContext context, String kind) throws Exception {
    try {
      Map<String, String> conf = new HashMap<>();

      Iterator<Entry<Object, Object>> it = property.entrySet().iterator();
      while (it.hasNext()) {
        Entry<Object, Object> pair = it.next();
        if (pair.getKey().toString().startsWith("livy.spark.") &&
            !pair.getValue().toString().isEmpty())
          conf.put(pair.getKey().toString().substring(5), pair.getValue().toString());
      }

      String confData = gson.toJson(conf);
      String user = context.getAuthenticationInfo().getUser();
      LOGGER.debug("Try to create session for user:" + user);
      String json = executeHTTP(property.getProperty("zeppelin.livy.url") + "/sessions", "POST",
          "{" +
              "\"kind\": \"" + kind + "\", " +
              "\"conf\": " + confData + ", " +
              "\"proxyUser\": " + (context.getAuthenticationInfo().isAnonymous() ? null : "\"" +
              user + "\"") +
          "}",
          context.getParagraphId()
      );

      Map jsonMap = (Map<Object, Object>) gson.fromJson(json,
          new TypeToken<Map<Object, Object>>() {
          }.getType());
      Integer sessionId = ((Double) jsonMap.get("id")).intValue();
      if (!jsonMap.get("state").equals("idle")) {
        Integer retryCount = 60;

        try {
          retryCount = Integer.valueOf(
              property.getProperty("zeppelin.livy.create.session.retries"));
        } catch (Exception e) {
          LOGGER.info("zeppelin.livy.create.session.retries property is not configured." +
              " Using default retry count.");
        }

        while (retryCount >= 0) {
          LOGGER.error(String.format("sessionId:%s state is %s",
              jsonMap.get("id"), jsonMap.get("state")));
          Thread.sleep(1000);
          json = executeHTTP(property.getProperty("zeppelin.livy.url") + "/sessions/" +
              sessionId, "GET", null, context.getParagraphId());
          jsonMap = (Map<Object, Object>) gson.fromJson(json,
              new TypeToken<Map<Object, Object>>() {
              }.getType());
          if (jsonMap.get("state").equals("idle")) {
            break;
          } else if (jsonMap.get("state").equals("error") || jsonMap.get("state").equals("dead")) {
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
          retryCount--;
        }
        if (retryCount <= 0) {
          LOGGER.error("Error getting session for user within the configured number of retries.");
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
    interpret("val sqlContext = new org.apache.spark.sql.SQLContext(sc)\n" +
        "import sqlContext.implicits._", context, userSessionMap);
  }

  public InterpreterResult interpretInput(String stringLines,
                                          final InterpreterContext context,
                                          final Map<String, Integer> userSessionMap,
                                          LivyOutputStream out,
                                          String appId,
                                          String webUI,
                                          boolean displayAppInfo) {
    try {
      out.setInterpreterOutput(context.out);
      context.out.clear();
      String incomplete = "";
      boolean inComment = false;
      String[] lines = stringLines.split("\n");
      String[] linesToRun = new String[lines.length + 1];
      for (int i = 0; i < lines.length; i++) {
        linesToRun[i] = lines[i];
      }
      linesToRun[lines.length] = "print(\"\")";
      Code r = null;
      StringBuilder outputBuilder = new StringBuilder();
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
          outputBuilder.append(res.message() + "\n");
          incomplete = "";
        }
      }

      if (r == Code.INCOMPLETE) {
        out.setInterpreterOutput(null);
        return new InterpreterResult(r, "Incomplete expression");
      } else {
        if (displayAppInfo) {
          out.write("%angular ");
          out.write("<pre><code>");
          out.write(outputBuilder.toString());
          out.write("</code></pre>");
          out.write("<hr/>");
          out.write("Spark Application Id:" + appId + "<br/>");
          out.write("Spark WebUI: <a href=" + webUI + ">" + webUI + "</a>");
        } else {
          out.write(outputBuilder.toString());
        }
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
        "{\"code\": \"" + StringEscapeUtils.escapeJson(lines) + "\"}",
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
    LOGGER.debug("statement {} response: {}", id, json);
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

  private RestTemplate getRestTemplate() {
    String keytabLocation = property.getProperty("zeppelin.livy.keytab");
    String principal = property.getProperty("zeppelin.livy.principal");
    if (StringUtils.isNotEmpty(keytabLocation) && StringUtils.isNotEmpty(principal)) {
      return new KerberosRestTemplate(keytabLocation, principal);
    }
    return new RestTemplate();
  }

  protected String executeHTTP(String targetURL, String method, String jsonData, String paragraphId)
      throws Exception {
    LOGGER.debug("Call rest api in {}, method: {}, jsonData: {}", targetURL, method, jsonData);
    RestTemplate restTemplate = getRestTemplate();
    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/json");
    headers.add("X-Requested-By", "zeppelin");
    ResponseEntity<String> response = null;
    try {
      if (method.equals("POST")) {
        HttpEntity<String> entity = new HttpEntity<>(jsonData, headers);

        response = restTemplate.exchange(targetURL, HttpMethod.POST, entity, String.class);
        paragraphHttpMap.put(paragraphId, response);
      } else if (method.equals("GET")) {
        HttpEntity<String> entity = new HttpEntity<>(headers);
        response = restTemplate.exchange(targetURL, HttpMethod.GET, entity, String.class);
        paragraphHttpMap.put(paragraphId, response);
      } else if (method.equals("DELETE")) {
        HttpEntity<String> entity = new HttpEntity<>(headers);
        response = restTemplate.exchange(targetURL, HttpMethod.DELETE, entity, String.class);
      }
    } catch (HttpClientErrorException e) {
      response = new ResponseEntity(e.getResponseBodyAsString(), e.getStatusCode());
      LOGGER.error(String.format("Error with %s StatusCode: %s",
          response.getStatusCode().value(), e.getResponseBodyAsString()));
    }
    if (response == null) {
      return null;
    }

    if (response.getStatusCode().value() == 200
            || response.getStatusCode().value() == 201
            || response.getStatusCode().value() == 404) {
      return response.getBody();
    } else {
      String responseString = response.getBody();
      if (responseString.contains("CreateInteractiveRequest[\\\"master\\\"]")) {
        return responseString;
      }
      LOGGER.error(String.format("Error with %s StatusCode: %s",
              response.getStatusCode().value(), responseString));
      throw new Exception(String.format("Error with %s StatusCode: %s",
              response.getStatusCode().value(), responseString));
    }
  }

  public void cancelHTTP(String paragraphId) {
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
