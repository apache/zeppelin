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

import com.google.gson.GsonBuilder;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Properties;

import static org.mockito.Mockito.doReturn;

/**
 * Created for org.apache.zeppelin.livy on 22/04/16.
 */

@RunWith(MockitoJUnitRunner.class)
public class LivyHelperTest {

  @Rule
  public ErrorCollector collector = new ErrorCollector();

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private static LivyPySparkInterpreter interpreter;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private InterpreterContext interpreterContext;

  @Mock(answer = Answers.CALLS_REAL_METHODS)
  private LivyHelper livyHelper;

  @Before
  public void prepareContext() throws Exception {
    interpreter.userSessionMap = new HashMap<>();
    interpreter.userSessionMap.put(null, 1);

    Properties properties = new Properties();
    properties.setProperty("zeppelin.livy.url", "http://localhost:8998");
    livyHelper.property = properties;
    livyHelper.paragraphHttpMap = new HashMap<>();
    livyHelper.gson = new GsonBuilder().setPrettyPrinting().create();


    doReturn("{\"id\":1,\"state\":\"idle\",\"kind\":\"spark\",\"proxyUser\":\"null\",\"log\":[]}")
        .when(livyHelper)
        .executeHTTP(
            livyHelper.property.getProperty("zeppelin.livy.url") + "/sessions",
            "POST",
            "{\"kind\": \"spark\", \"proxyUser\": \"null\"}",
            null
        );

    doReturn("{\"id\":1,\"state\":\"available\",\"output\":{\"status\":\"ok\"," +
        "\"execution_count\":1,\"data\":{\"text/plain\":\"1\"}}}")
        .when(livyHelper)
        .executeHTTP(
            livyHelper.property.getProperty("zeppelin.livy.url") + "/sessions/1/statements",
            "POST",
            "{\"code\": \"print(1)\" }",
            null
        );

  }


  @Test
  public void checkCreateSession() {
    try {
      Integer sessionId = livyHelper.createSession(interpreterContext, "spark");

      collector.checkThat("check sessionId", 1, CoreMatchers.equalTo(sessionId));

    } catch (Exception e) {
      collector.addError(e);
    }
  }

  @Test
  public void checkInterpret() {
    try {
      InterpreterResult result = livyHelper.interpret("print(1)", interpreterContext, interpreter.userSessionMap);

      collector.checkThat("check sessionId", InterpreterResult.Code.SUCCESS, CoreMatchers.equalTo(result.code()));

    } catch (Exception e) {
      collector.addError(e);
    }
  }

}
