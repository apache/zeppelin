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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.*;

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;

import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(SparkRInterpreter.ZeppelinRFactory.class)
public class SparkRInterpreterTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(SparkRInterpreterTest.class);

  private static final String MOCK_RSCALA_RESULT = "<body><p>         Mock R Result   </p></body>";
  private static final String MOCK_R_INTERPRETER_RESULT = "<pre style='background-color: white; border: 0px;'>         Mock R Result   </pre>";

  private static InterpreterContext context;
  private static InterpreterGroup intpGroup;
  private static SparkInterpreter sparkInterpreter;
  private static SparkRInterpreter.ZeppelinRFactory zeppelinRFactory;
  private static SparkRInterpreter sparkRInterpreter;

  @BeforeClass
  public static void beforeClass() {
    initInterpreters();
  }

  @Test
  public void testSuccess() throws Exception {
    InterpreterResult ret = sparkRInterpreter.interpret(MOCK_RSCALA_RESULT, context);
    assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals(MOCK_R_INTERPRETER_RESULT, ret.message());
    assertEquals(InterpreterResult.Type.HTML, ret.type());
  }

  private static void initInterpreters() {

    Properties p = new Properties();

    sparkInterpreter = new SparkInterpreter(p);
    intpGroup = new InterpreterGroup();
    intpGroup.add(sparkInterpreter);

    zeppelinRFactory = mock(SparkRInterpreter.ZeppelinRFactory.class);
    doNothing().when(zeppelinRFactory).open(Mockito.anyString(), Mockito.anyString(), any(SparkInterpreter.class));
    when(zeppelinRFactory.getS0(anyString())).thenReturn(MOCK_RSCALA_RESULT);

    mockStatic(SparkRInterpreter.ZeppelinRFactory.class);
    when(SparkRInterpreter.ZeppelinRFactory.instance()).thenReturn(zeppelinRFactory);

    intpGroup.add(sparkRInterpreter);
    sparkRInterpreter = new SparkRInterpreter(p);
    sparkRInterpreter.setInterpreterGroup(intpGroup);
    sparkRInterpreter.open();

    context = new InterpreterContext("note", "id", "title", "text", new HashMap<String, Object>(), new GUI(),
            new AngularObjectRegistry(intpGroup.getId(), null),
            null,
            new LinkedList<InterpreterContextRunner>(), new InterpreterOutput(new InterpreterOutputListener() {
      @Override
      public void onAppend(InterpreterOutput out, byte[] line) {
      }
      @Override
      public void onUpdate(InterpreterOutput out, byte[] output) {
      }
    }));

  }

}
