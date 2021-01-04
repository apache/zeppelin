
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

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.apache.zeppelin.r.IRInterpreterTest;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SparkIRInterpreterTest extends IRInterpreterTest {

  private RemoteInterpreterEventClient mockRemoteIntpEventClient = mock(RemoteInterpreterEventClient.class);

  @Override
  protected Interpreter createInterpreter(Properties properties) {
    return new SparkIRInterpreter(properties);
  }

  @Override
  @Before
  public void setUp() throws InterpreterException {
    Properties properties = new Properties();
    properties.setProperty(SparkStringConstants.MASTER_PROP_NAME, "local");
    properties.setProperty(SparkStringConstants.APP_NAME_PROP_NAME, "test");
    properties.setProperty("zeppelin.spark.maxResult", "100");
    properties.setProperty("spark.r.backendConnectionTimeout", "10");
    properties.setProperty("zeppelin.spark.deprecatedMsg.show", "false");

    InterpreterContext context = getInterpreterContext();
    InterpreterContext.set(context);
    interpreter = createInterpreter(properties);

    InterpreterGroup interpreterGroup = new InterpreterGroup();
    interpreterGroup.addInterpreterToSession(new LazyOpenInterpreter(interpreter), "session_1");
    interpreter.setInterpreterGroup(interpreterGroup);

    SparkInterpreter sparkInterpreter = new SparkInterpreter(properties);
    interpreterGroup.addInterpreterToSession(new LazyOpenInterpreter(sparkInterpreter), "session_1");
    sparkInterpreter.setInterpreterGroup(interpreterGroup);

    interpreter.open();
  }


  @Test
  public void testSparkRInterpreter() throws InterpreterException, InterruptedException, IOException {
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = interpreter.interpret("1+1", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> interpreterResultMessages = context.out.toInterpreterResultMessage();
    assertTrue(interpreterResultMessages.get(0).getData().contains("2"));

    context = getInterpreterContext();
    result = interpreter.interpret("sparkR.version()", context);
    if (result.code() == InterpreterResult.Code.ERROR) {
      // Spark 1.x has no api for Spark.version()
      // spark 1.x
      context = getInterpreterContext();
      result = interpreter.interpret("df <- createDataFrame(sqlContext, faithful)\nhead(df)", context);
      interpreterResultMessages = context.out.toInterpreterResultMessage();
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertTrue(interpreterResultMessages.get(0).getData().contains(">eruptions</th>"));
      // spark job url is sent
      verify(mockRemoteIntpEventClient, atLeastOnce()).onParaInfosReceived(any(Map.class));
    } else {
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      interpreterResultMessages = context.out.toInterpreterResultMessage();
      if (interpreterResultMessages.get(0).getData().contains("2.2")) {
        ENABLE_GOOGLEVIS_TEST = false;
      }
      context = getInterpreterContext();
      result = interpreter.interpret("df <- as.DataFrame(faithful)\nhead(df)", context);
      interpreterResultMessages = context.out.toInterpreterResultMessage();
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertTrue(interpreterResultMessages.get(0).getData().contains(">eruptions</th>"));
      // spark job url is sent
      verify(mockRemoteIntpEventClient, atLeastOnce()).onParaInfosReceived(any(Map.class));

      // cancel
      final InterpreterContext context2 = getInterpreterContext();
      Thread thread = new Thread() {
        @Override
        public void run() {
          try {
            InterpreterResult result = interpreter.interpret("ldf <- dapplyCollect(\n" +
                    "         df,\n" +
                    "         function(x) {\n" +
                    "           Sys.sleep(3)\n" +
                    "           x <- cbind(x, \"waiting_secs\" = x$waiting * 60)\n" +
                    "         })\n" +
                    "head(ldf, 3)", context2);
            assertTrue(result.message().get(0).getData().contains("cancelled"));
          } catch (InterpreterException e) {
            fail("Should not throw InterpreterException");
          }
        }
      };
      thread.setName("Cancel-Thread");
      thread.start();
      Thread.sleep(1000);
      interpreter.cancel(context2);
    }
  }

  @Override
  protected InterpreterContext getInterpreterContext() {
    InterpreterContext context = InterpreterContext.builder()
            .setNoteId("note_1")
            .setParagraphId("paragraph_1")
            .setInterpreterOut(new InterpreterOutput())
            .setLocalProperties(new HashMap<>())
            .setIntpEventClient(mockRemoteIntpEventClient)
            .build();
    return context;
  }
}
