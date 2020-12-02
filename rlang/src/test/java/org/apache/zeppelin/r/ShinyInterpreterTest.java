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

package org.apache.zeppelin.r;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class ShinyInterpreterTest {

  protected ShinyInterpreter interpreter;

  @Before
  public void setUp() throws InterpreterException {
    Properties properties = new Properties();

    InterpreterContext context = getInterpreterContext();
    InterpreterContext.set(context);
    interpreter = new ShinyInterpreter(properties);

    InterpreterGroup interpreterGroup = new InterpreterGroup();
    interpreterGroup.addInterpreterToSession(new LazyOpenInterpreter(interpreter), "session_1");
    interpreter.setInterpreterGroup(interpreterGroup);

    interpreter.open();
  }

  @After
  public void tearDown() throws InterpreterException {
    if (interpreter != null) {
      interpreter.close();
    }
  }

  @Test
  public void testShinyApp() throws
          IOException, InterpreterException, InterruptedException, UnirestException {
    /****************** Launch Shiny app with default app name *****************************/
    InterpreterContext context = getInterpreterContext();
    context.getLocalProperties().put("type", "ui");
    InterpreterResult result =
            interpreter.interpret(IOUtils.toString(getClass().getResource("/ui.R"), StandardCharsets.UTF_8), context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    context = getInterpreterContext();
    context.getLocalProperties().put("type", "server");
    result = interpreter.interpret(IOUtils.toString(getClass().getResource("/server.R"), StandardCharsets.UTF_8), context);
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
    assertEquals(200, response.getStatus());
    assertTrue(response.getBody(), response.getBody().contains("Shiny Text"));

    /************************ Launch another shiny app (app2) *****************************/
    context = getInterpreterContext();
    context.getLocalProperties().put("type", "ui");
    context.getLocalProperties().put("app", "app2");
    result =
            interpreter.interpret(IOUtils.toString(getClass().getResource("/ui.R"), StandardCharsets.UTF_8), context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    context = getInterpreterContext();
    context.getLocalProperties().put("type", "server");
    context.getLocalProperties().put("app", "app2");
    result = interpreter.interpret(IOUtils.toString(getClass().getResource("/server.R"), StandardCharsets.UTF_8), context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    final InterpreterContext context3 = getInterpreterContext();
    context3.getLocalProperties().put("type", "run");
    context3.getLocalProperties().put("app", "app2");
    thread = new Thread(() -> {
      try {
        interpreter.interpret("", context3);
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
    thread.start();
    // wait for the shiny app start
    Thread.sleep(5 * 1000);
    // extract shiny url
    resultMessages = context3.out.toInterpreterResultMessage();
    assertEquals(1, resultMessages.size());
    assertEquals(InterpreterResult.Type.HTML, resultMessages.get(0).getType());
    resultMessageData = resultMessages.get(0).getData();
    assertTrue(resultMessageData, resultMessageData.contains("<iframe"));
    matcher = urlPattern.matcher(resultMessageData);
    if (!matcher.matches()) {
      fail("Unable to extract url: " + resultMessageData);
    }
    String shinyURL2 = matcher.group(1);

    // verify shiny app via calling its rest api
    response = Unirest.get(shinyURL2).asString();
    assertEquals(200, response.getStatus());
    assertTrue(response.getBody(), response.getBody().contains("Shiny Text"));

    // cancel paragraph to stop the first shiny app
    interpreter.cancel(getInterpreterContext());
    // wait for shiny app to be stopped
    Thread.sleep(1000);
    try {
      Unirest.get(shinyURL).asString();
      fail("Should fail to connect to shiny app");
    } catch (Exception e) {
      assertTrue(e.getMessage(), e.getMessage().contains("Connection refused"));
    }

    // the second shiny app still works
    response = Unirest.get(shinyURL2).asString();
    assertEquals(200, response.getStatus());
    assertTrue(response.getBody(), response.getBody().contains("Shiny Text"));
  }

  @Test
  public void testInvalidShinyApp()
          throws IOException, InterpreterException, InterruptedException, UnirestException {
    InterpreterContext context = getInterpreterContext();
    context.getLocalProperties().put("type", "ui");
    InterpreterResult result =
            interpreter.interpret(IOUtils.toString(getClass().getResource("/invalid_ui.R"), StandardCharsets.UTF_8), context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    context = getInterpreterContext();
    context.getLocalProperties().put("type", "server");
    result = interpreter.interpret(IOUtils.toString(getClass().getResource("/server.R"), StandardCharsets.UTF_8), context);
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

    // call shiny app via rest api
    HttpResponse<String> response = Unirest.get(shinyURL).asString();
    assertEquals(500, response.getStatus());

    resultMessages = context2.out.toInterpreterResultMessage();
    assertTrue(resultMessages.get(1).getData(),
            resultMessages.get(1).getData().contains("object 'Invalid_code' not found"));

    // cancel paragraph to stop shiny app
    interpreter.cancel(getInterpreterContext());
    // wait for shiny app to be stopped
    Thread.sleep(1000);
    try {
      Unirest.get(shinyURL).asString();
      fail("Should fail to connect to shiny app");
    } catch (Exception e) {
      assertTrue(e.getMessage(), e.getMessage().contains("Connection refused"));
    }
  }

  protected InterpreterContext getInterpreterContext() {
    InterpreterContext context = InterpreterContext.builder()
            .setNoteId("note_1")
            .setParagraphId("paragraph_1")
            .setInterpreterOut(new InterpreterOutput(null))
            .setLocalProperties(new HashMap<>())
            .setInterpreterClassName(ShinyInterpreter.class.getName())
            .setIntpEventClient(mock(RemoteInterpreterEventClient.class))
            .build();
    return context;
  }
}
