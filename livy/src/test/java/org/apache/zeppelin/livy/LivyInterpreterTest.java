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


import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

@RunWith(MockitoJUnitRunner.class)
public class LivyInterpreterTest {

  @Rule
  public ErrorCollector collector = new ErrorCollector();

  private static LivyPySparkInterpreter interpreter;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private InterpreterContext interpreterContext;

  @AfterClass
  public static void tearDown() {
    interpreter.close();
  }

  @Before
  public void prepareContext() throws Exception {
    interpreter = new LivyPySparkInterpreter(new Properties());
    interpreter.userSessionMap = new HashMap<>();
    interpreter.userSessionMap.put(null, 0);
    interpreter.livyHelper = Mockito.mock(LivyHelper.class);
    interpreter.open();

    doReturn(new InterpreterResult(InterpreterResult.Code.SUCCESS)).when(interpreter.livyHelper)
        .interpret("print \"x is 1.\"", interpreterContext, interpreter.userSessionMap);
  }

  @Test
  public void checkInitVariables() throws Exception {
    collector.checkThat("Check that, if userSessionMap is made: ",
        interpreter.userSessionMap, CoreMatchers.notNullValue());
  }

  @Test
  public void checkBasicInterpreter() throws Exception {

    String paragraphString = "print \"x is 1.\"";

    final InterpreterResult actual = interpreter.interpret(paragraphString, interpreterContext);

    collector.checkThat("Check that, result is computed: ",
        actual.code(), CoreMatchers.equalTo(InterpreterResult.Code.SUCCESS));
    assertThat(actual).isNotNull();
  }

}