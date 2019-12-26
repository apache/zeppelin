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

package org.apache.zeppelin.spark;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.r.ShinyInterpreterTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SparkShinyInterpreterTest extends ShinyInterpreterTest {

  private SparkInterpreter sparkInterpreter;

  @Before
  public void setUp() throws InterpreterException {
    Properties properties = new Properties();
    properties.setProperty("master", "local[*]");
    properties.setProperty("spark.app.name", "test");

    InterpreterContext context = getInterpreterContext();
    InterpreterContext.set(context);
    interpreter = new SparkShinyInterpreter(properties);

    InterpreterGroup interpreterGroup = new InterpreterGroup();
    interpreterGroup.addInterpreterToSession(new LazyOpenInterpreter(interpreter), "session_1");
    interpreter.setInterpreterGroup(interpreterGroup);

    sparkInterpreter = new SparkInterpreter(properties);
    interpreterGroup.addInterpreterToSession(new LazyOpenInterpreter(sparkInterpreter), "session_1");
    sparkInterpreter.setInterpreterGroup(interpreterGroup);

    interpreter.open();
  }

  @After
  public void tearDown() throws InterpreterException {
    if (interpreter != null) {
      interpreter.close();
    }
  }
  
  @Test
  public void testSparkShinyApp() throws IOException, InterpreterException, InterruptedException, UnirestException {
    /****************** Launch Shiny app with default app name *****************************/
    InterpreterContext context = getInterpreterContext();
    context.getLocalProperties().put("type", "ui");
    InterpreterResult result =
            interpreter.interpret(IOUtils.toString(getClass().getResource("/spark_ui.R")), context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    context = getInterpreterContext();
    context.getLocalProperties().put("type", "server");
    result = interpreter.interpret(IOUtils.toString(getClass().getResource("/spark_server.R")), context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    final InterpreterContext context2 = getInterpreterContext();
    context2.getLocalProperties().put("type", "run");
    Thread thread = new Thread(() -> {
      try {
        interpreter.interpret("", context2);
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
    thread.start();
    // wait for the shiny app start
    Thread.sleep(5 * 1000);
    // extract shiny url
    List<InterpreterResultMessage> resultMessages = context2.out.toInterpreterResultMessage();
    assertEquals(1, resultMessages.size());
    assertEquals(InterpreterResult.Type.HTML, resultMessages.get(0).getType());
    String resultMessageData = resultMessages.get(0).getData();
    assertTrue(resultMessageData, resultMessageData.contains("<iframe"));
    Pattern urlPattern = Pattern.compile(".*src=\"(http\\S*)\".*", Pattern.DOTALL);
    Matcher matcher = urlPattern.matcher(resultMessageData);
    if (!matcher.matches()) {
      fail("Unable to extract url: " + resultMessageData);
    }
    String shinyURL = matcher.group(1);

    // verify shiny app via calling its rest api
    HttpResponse<String> response = Unirest.get(shinyURL).asString();
    if (sparkInterpreter.getSparkVersion().isSpark1()) {
      // spark 1.x will fail due to sparkR.version is not available for spark 1.x
      assertEquals(500, response.getStatus());
      assertTrue(response.getBody(),
              response.getBody().contains("could not find function \"sparkR.version\""));
    } else {
      assertEquals(200, response.getStatus());
      assertTrue(response.getBody(), response.getBody().contains("Spark Version"));
    }
  }
}
