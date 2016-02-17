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
import com.google.gson.reflect.TypeToken;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.apache.zeppelin.livy.RequestHelper.executeHTTP;

/**
 * Livy interpreter for Zeppelin.
 */
public class LivyHelper {
  Logger LOGGER = LoggerFactory.getLogger(LivyHelper.class);


  Gson gson = new Gson();

  protected Integer createSession(String user, String kind) {
    try {
      String json = executeHTTP(System.getProperty("zeppelin.livy.url") + "/sessions",
          "POST",
          "{\"kind\": \"" + kind + "\", \"proxyUser\": \"" + user + "\"}"
      );
      Map jsonMap = (Map<String, Object>) gson.fromJson(json,
          new TypeToken<Map<String, Object>>() {
          }.getType());
      return ((Double) jsonMap.get("id")).intValue();
    } catch (Exception e) {
      LOGGER.error("Error getting session for user", e);
    }
    return 0;
  }

  public InterpreterResult interpret(String[] lines, InterpreterContext context,
                                     Map<String, Integer> userSessionMap) {
    synchronized (this) {
      InterpreterResult res = interpretInput(lines, context, userSessionMap);
      return res;
    }
  }

  public InterpreterResult interpretInput(String[] lines, InterpreterContext context,
                                          Map<String, Integer> userSessionMap) {
    String[] linesToRun = new String[lines.length + 1];
    for (int i = 0; i < lines.length; i++) {
      linesToRun[i] = lines[i];
    }
    linesToRun[lines.length] = "print(\"\")";

    String incomplete = "";
    Code r = null;

    for (int l = 0; l < linesToRun.length; l++) {
      String s = linesToRun[l];
      // check if next line starts with "." (but not ".." or "./") it is treated as an invocation
      if (l + 1 < linesToRun.length) {
        String nextLine = linesToRun[l + 1].trim();
        if (nextLine.startsWith(".") && !nextLine.startsWith("..") && !nextLine.startsWith("./")) {
          incomplete += s + "\n";
          continue;
        }
      }

      try {
        return new InterpreterResult(InterpreterResult.Code.SUCCESS,
            executeCommand(incomplete + s, context, userSessionMap));
      } catch (Exception e) {
        return new InterpreterResult(Code.ERROR, e.getMessage());
      }

    }
    if (r == Code.INCOMPLETE) {
      return new InterpreterResult(r, "Incomplete expression");
    } else {
      return new InterpreterResult(Code.SUCCESS);
    }
  }

  private String executeCommand(String lines, InterpreterContext context,
                                Map<String, Integer> userSessionMap) throws Exception {
    String json = executeHTTP(System.getProperty("zeppelin.livy.url") + "/sessions/"
            + userSessionMap.get(context.getAuthenticationInfo().getUser())
            + "/statements",
        "POST",
        "{\"code\": \"" + lines + "\" }");
    Map jsonMap = (Map<String, Object>) gson.fromJson(json,
        new TypeToken<Map<String, Object>>() {
        }.getType());

    return null;
  }

}
