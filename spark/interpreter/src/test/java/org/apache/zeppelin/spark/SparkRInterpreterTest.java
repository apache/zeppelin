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

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteEventClient;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SparkRInterpreterTest {

  private SparkRInterpreter sparkRInterpreter;
  private SparkInterpreter sparkInterpreter;
  private RemoteEventClient mockRemoteEventClient = mock(RemoteEventClient.class);

  @Test
  public void testSparkRInterpreter() throws InterpreterException, InterruptedException {
    Properties properties = new Properties();
    properties.setProperty("spark.master", "local");
    properties.setProperty("spark.app.name", "test");
    properties.setProperty("zeppelin.spark.maxResult", "100");
    properties.setProperty("zeppelin.spark.test", "true");
    properties.setProperty("zeppelin.spark.useNew", "true");
    properties.setProperty("zeppelin.R.knitr", "true");
    properties.setProperty("spark.r.backendConnectionTimeout", "10");

    sparkRInterpreter = new SparkRInterpreter(properties);
    sparkInterpreter = new SparkInterpreter(properties);

    InterpreterGroup interpreterGroup = new InterpreterGroup();
    interpreterGroup.addInterpreterToSession(new LazyOpenInterpreter(sparkRInterpreter), "session_1");
    interpreterGroup.addInterpreterToSession(new LazyOpenInterpreter(sparkInterpreter), "session_1");
    sparkRInterpreter.setInterpreterGroup(interpreterGroup);
    sparkInterpreter.setInterpreterGroup(interpreterGroup);

    InterpreterContext.set(getInterpreterContext());
    sparkRInterpreter.open();
    sparkInterpreter.getZeppelinContext().setEventClient(mockRemoteEventClient);

    InterpreterResult result = sparkRInterpreter.interpret("1+1", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertTrue(result.message().get(0).getData().contains("2"));
    // spark web url is sent
    verify(mockRemoteEventClient).onMetaInfosReceived(any(Map.class));

    result = sparkRInterpreter.interpret("sparkR.version()", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    if (result.message().get(0).getData().contains("2.")) {
      // spark 2.x
      result = sparkRInterpreter.interpret("df <- as.DataFrame(faithful)\nhead(df)", getInterpreterContext());
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertTrue(result.message().get(0).getData().contains("eruptions waiting"));
      // spark job url is sent
      verify(mockRemoteEventClient, atLeastOnce()).onParaInfosReceived(any(String.class), any(String.class), any(Map.class));

      // cancel
      final InterpreterContext context = getInterpreterContext();
      Thread thread = new Thread() {
        @Override
        public void run() {
          try {
            InterpreterResult result = sparkRInterpreter.interpret("ldf <- dapplyCollect(\n" +
                "         df,\n" +
                "         function(x) {\n" +
                "           Sys.sleep(3)\n" +
                "           x <- cbind(x, \"waiting_secs\" = x$waiting * 60)\n" +
                "         })\n" +
                "head(ldf, 3)", context);
            assertTrue(result.message().get(0).getData().contains("cancelled"));
          } catch (InterpreterException e) {
            fail("Should not throw InterpreterException");
          }
        }
      };
      thread.setName("Cancel-Thread");
      thread.start();
      Thread.sleep(1000);
      sparkRInterpreter.cancel(context);
    } else {
      // spark 1.x
      result = sparkRInterpreter.interpret("df <- createDataFrame(sqlContext, faithful)\nhead(df)", getInterpreterContext());
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertTrue(result.message().get(0).getData().contains("eruptions waiting"));
      // spark job url is sent
      verify(mockRemoteEventClient, atLeastOnce()).onParaInfosReceived(any(String.class), any(String.class), any(Map.class));
    }

    // plotting
    result = sparkRInterpreter.interpret("hist(mtcars$mpg)", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(1, result.message().size());
    assertEquals(InterpreterResult.Type.HTML, result.message().get(0).getType());
    assertTrue(result.message().get(0).getData().contains("<img src="));

    result = sparkRInterpreter.interpret("library(ggplot2)\n" +
        "ggplot(diamonds, aes(x=carat, y=price, color=cut)) + geom_point()", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(1, result.message().size());
    assertEquals(InterpreterResult.Type.HTML, result.message().get(0).getType());
    assertTrue(result.message().get(0).getData().contains("<img src="));

    // sparkr backend would be timeout after 10 seconds
    Thread.sleep(15 * 1000);
    result = sparkRInterpreter.interpret("1+1", getInterpreterContext());
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    assertTrue(result.message().get(0).getData().contains("sparkR backend is dead"));
  }

  private InterpreterContext getInterpreterContext() {
    InterpreterContext context = InterpreterContext.builder()
        .setNoteId("note_1")
        .setParagraphId("paragraph_1")
        .setEventClient(mockRemoteEventClient)
        .setInterpreterOut(new InterpreterOutput(null))
        .build();
    return context;
  }
}
