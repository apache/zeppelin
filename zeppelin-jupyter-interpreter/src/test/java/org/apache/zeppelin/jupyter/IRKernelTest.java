/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.zeppelin.jupyter;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IRKernelTest {

  protected Interpreter interpreter;
  protected static boolean ENABLE_GOOGLEVIS_TEST = true;

  protected Interpreter createInterpreter(Properties properties) {
    return new JupyterInterpreter(properties);
  }

  @Before
  public void setUp() throws InterpreterException {
    Properties properties = new Properties();

    InterpreterContext context = getInterpreterContext();
    InterpreterContext.set(context);
    interpreter = createInterpreter(properties);

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
  public void testIRInterpreter() throws InterpreterException, IOException {
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = interpreter.interpret("1+1", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, resultMessages.size());
    assertEquals(resultMessages.toString(),
            InterpreterResult.Type.HTML, resultMessages.get(0).getType());
    assertEquals(resultMessages.toString(), "2", resultMessages.get(0).getData());

    // error
    context = getInterpreterContext();
    result = interpreter.interpret("unknown_var", context);
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, resultMessages.size());
    assertEquals(result.toString(), InterpreterResult.Type.TEXT, resultMessages.get(0).getType());
    assertTrue(resultMessages.toString(),
            resultMessages.get(0).getData().contains("object 'unknown_var' not found"));

    context = getInterpreterContext();
    result = interpreter.interpret("foo <- TRUE\n" +
            "print(foo)\n" +
            "bare <- c(1, 2.5, 4)\n" +
            "print(bare)\n" +
            "double <- 15.0\n" +
            "print(double)", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, resultMessages.size());
    assertEquals(result.toString(), InterpreterResult.Type.TEXT, resultMessages.get(0).getType());
    assertTrue(resultMessages.toString(),
            resultMessages.get(0).getData().contains("[1] TRUE\n" +
                    "[1] 1.0 2.5 4.0\n" +
                    "[1] 15\n"));

    // plotting
    context = getInterpreterContext();
    result = interpreter.interpret("hist(mtcars$mpg)", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, resultMessages.size());
    assertEquals(resultMessages.toString(),
            InterpreterResult.Type.IMG, resultMessages.get(0).getType());

    // ggplot2
    result = interpreter.interpret("library(ggplot2)\n" +
            "ggplot(diamonds, aes(x=carat, y=price, color=cut)) + geom_point()",
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, resultMessages.size());
    assertEquals(resultMessages.toString(),
            InterpreterResult.Type.IMG, resultMessages.get(0).getType());

    // googlevis
    // TODO(zjffdu) It is weird that googlevis doesn't work with spark 2.2
    if (ENABLE_GOOGLEVIS_TEST) {
      context = getInterpreterContext();
      result = interpreter.interpret("library(googleVis)\n" +
              "df=data.frame(country=c(\"US\", \"GB\", \"BR\"), \n" +
              "              val1=c(10,13,14), \n" +
              "              val2=c(23,12,32))\n" +
              "Bar <- gvisBarChart(df)\n" +
              "print(Bar, tag = 'chart')", context);
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      resultMessages = context.out.toInterpreterResultMessage();
      assertEquals(2, resultMessages.size());
      assertEquals(resultMessages.toString(),
              InterpreterResult.Type.HTML, resultMessages.get(1).getType());
      assertTrue(resultMessages.get(1).getData(),
              resultMessages.get(1).getData().contains("javascript"));
    }
  }

  protected InterpreterContext getInterpreterContext() {
    Map<String, String> localProperties = new HashMap<>();
    localProperties.put("kernel", "ir");
    InterpreterContext context = InterpreterContext.builder()
            .setNoteId("note_1")
            .setParagraphId("paragraph_1")
            .setInterpreterOut(new InterpreterOutput())
            .setLocalProperties(localProperties)
            .build();
    return context;
  }
}
