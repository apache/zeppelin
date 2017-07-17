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
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SparkRInterpreterTest {

  private SparkRInterpreter sparkRInterpreter;
  private SparkInterpreter sparkInterpreter;


  @Test
  public void testSparkRInterpreter() throws IOException, InterruptedException, InterpreterException {
    Properties properties = new Properties();
    properties.setProperty("spark.master", "local");
    properties.setProperty("spark.app.name", "test");
    properties.setProperty("zeppelin.spark.maxResult", "100");
    properties.setProperty("zeppelin.spark.test", "true");
    properties.setProperty("zeppelin.spark.useNew", "true");
    properties.setProperty("zeppelin.R.knitr", "true");

    sparkRInterpreter = new SparkRInterpreter(properties);
    sparkInterpreter = new SparkInterpreter(properties);

    InterpreterGroup interpreterGroup = new InterpreterGroup();
    interpreterGroup.addInterpreterToSession(new LazyOpenInterpreter(sparkRInterpreter), "session_1");
    interpreterGroup.addInterpreterToSession(new LazyOpenInterpreter(sparkInterpreter), "session_1");
    sparkRInterpreter.setInterpreterGroup(interpreterGroup);
    sparkInterpreter.setInterpreterGroup(interpreterGroup);

    sparkRInterpreter.open();

    InterpreterResult result = sparkRInterpreter.interpret("1+1", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertTrue(result.message().get(0).getData().contains("2"));

    result = sparkRInterpreter.interpret("sparkR.version()", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    if (result.message().get(0).getData().contains("2.")) {
      // spark 2.x
      result = sparkRInterpreter.interpret("df <- as.DataFrame(faithful)\nhead(df)", getInterpreterContext());
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertTrue(result.message().get(0).getData().contains("eruptions waiting"));
    } else {
      // spark 1.x
      result = sparkRInterpreter.interpret("df <- createDataFrame(sqlContext, faithful)\nhead(df)", getInterpreterContext());
      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertTrue(result.message().get(0).getData().contains("eruptions waiting"));
    }
  }

  private InterpreterContext getInterpreterContext() {
    return new InterpreterContext(
        "noteId",
        "paragraphId",
        "replName",
        "paragraphTitle",
        "paragraphText",
        new AuthenticationInfo(),
        new HashMap<String, Object>(),
        new GUI(),
        new GUI(),
        new AngularObjectRegistry("spark", null),
        null,
        null,
        null);
  }
}
