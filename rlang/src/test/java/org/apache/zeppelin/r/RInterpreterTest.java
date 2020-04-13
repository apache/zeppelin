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

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RInterpreterTest {

  private RInterpreter rInterpreter;

  @Before
  public void setUp() throws InterpreterException {
    Properties properties = new Properties();
    properties.setProperty("zeppelin.R.knitr", "true");
    properties.setProperty("spark.r.backendConnectionTimeout", "10");

    InterpreterContext context = getInterpreterContext();
    InterpreterContext.set(context);
    rInterpreter = new RInterpreter(properties);

    InterpreterGroup interpreterGroup = new InterpreterGroup();
    interpreterGroup.addInterpreterToSession(new LazyOpenInterpreter(rInterpreter), "session_1");
    rInterpreter.setInterpreterGroup(interpreterGroup);

    rInterpreter.open();
  }

  @After
  public void tearDown() throws InterpreterException {
    rInterpreter.close();
  }

  @Test
  public void testSparkRInterpreter() throws InterpreterException, InterruptedException, IOException {
    InterpreterResult result = rInterpreter.interpret("1+1", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertTrue(result.message().get(0).getData().contains("2"));

    InterpreterContext context = getInterpreterContext();
    result = rInterpreter.interpret("foo <- TRUE\n" +
            "print(foo)\n" +
            "bare <- c(1, 2.5, 4)\n" +
            "print(bare)\n" +
            "double <- 15.0\n" +
            "print(double)", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertTrue(result.toString(),
                       result.message().get(0).getData().contains("[1] TRUE\n" +
                               "[1] 1.0 2.5 4.0\n" +
                               "[1] 15\n"));

    // plotting
    context = getInterpreterContext();
    context.getLocalProperties().put("imageWidth", "100");
    result = rInterpreter.interpret("hist(mtcars$mpg)", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(1, result.message().size());
    assertEquals(InterpreterResult.Type.HTML, result.message().get(0).getType());
    assertTrue(result.message().get(0).getData().contains("<img src="));
    assertTrue(result.message().get(0).getData().contains("width=\"100\""));

    result = rInterpreter.interpret("library(ggplot2)\n" +
            "ggplot(diamonds, aes(x=carat, y=price, color=cut)) + geom_point()", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(1, result.message().size());
    assertEquals(InterpreterResult.Type.HTML, result.message().get(0).getType());
    assertTrue(result.message().get(0).getData().contains("<img src="));

    // sparkr backend would be timeout after 10 seconds
    Thread.sleep(15 * 1000);
    result = rInterpreter.interpret("1+1", getInterpreterContext());
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    assertTrue(result.message().get(0).getData().contains("sparkR backend is dead"));
  }

  @Test
  public void testInvalidR() throws InterpreterException {
    tearDown();

    Properties properties = new Properties();
    properties.setProperty("zeppelin.R.cmd", "invalid_r");
    properties.setProperty("spark.master", "local");
    properties.setProperty("spark.app.name", "test");

    InterpreterGroup interpreterGroup = new InterpreterGroup();
    Interpreter rInterpreter = new LazyOpenInterpreter(new RInterpreter(properties));
    interpreterGroup.addInterpreterToSession(rInterpreter, "session_1");
    rInterpreter.setInterpreterGroup(interpreterGroup);

    InterpreterContext context = getInterpreterContext();
    InterpreterContext.set(context);

    try {
      rInterpreter.interpret("1+1", getInterpreterContext());
      fail("Should fail to open SparkRInterpreter");
    } catch (InterpreterException e) {
      String stacktrace = ExceptionUtils.getStackTrace(e);
      assertTrue(stacktrace, stacktrace.contains("No such file or directory"));
    }
  }

  private InterpreterContext getInterpreterContext() {
    InterpreterContext context = InterpreterContext.builder()
            .setNoteId("note_1")
            .setParagraphId("paragraph_1")
            .setInterpreterOut(new InterpreterOutput(null))
            .setLocalProperties(new HashMap<>())
            .build();
    return context;
  }
}
