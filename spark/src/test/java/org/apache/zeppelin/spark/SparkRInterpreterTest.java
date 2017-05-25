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

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.MethodSorters;

import static org.junit.Assert.assertEquals;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SparkRInterpreterTest {

  @ClassRule
  public static TemporaryFolder tmpDir = new TemporaryFolder();

  static SparkInterpreter sparkInterpreter;
  static SparkRInterpreter sparkRInterpreter;
  static InterpreterGroup intpGroup;
  static InterpreterContext context;

  private static Properties getPySparkTestProperties() throws IOException {
    Properties p = new Properties();
    p.setProperty("master", "local[*]");
    p.setProperty("spark.app.name", "Zeppelin Test");
    p.setProperty("zeppelin.spark.maxResult", "1000");
    p.setProperty("zeppelin.spark.importImplicit", "true");
    p.setProperty("zeppelin.dep.localrepo", tmpDir.newFolder().getAbsolutePath());
    p.setProperty("zeppelin.R.cmd", "R");
    p.setProperty("zeppelin.R.precode", "precodeVar=2+1");
    return p;
  }

  @BeforeClass
  public static void setUp() throws Exception {
    intpGroup = new InterpreterGroup();
    intpGroup.put("note", new LinkedList<Interpreter>());

    sparkInterpreter = new SparkInterpreter(getPySparkTestProperties());
    intpGroup.get("note").add(sparkInterpreter);
    sparkInterpreter.setInterpreterGroup(intpGroup);
    sparkInterpreter.open();

    sparkRInterpreter = new SparkRInterpreter(getPySparkTestProperties());
    intpGroup.get("note").add(sparkRInterpreter);
    sparkRInterpreter.setInterpreterGroup(intpGroup);
    sparkRInterpreter.open();

    context = new InterpreterContext("note", "id", null, "title", "text",
      new AuthenticationInfo(),
      new HashMap<String, Object>(),
      new GUI(),
      new AngularObjectRegistry(intpGroup.getId(), null),
      new LocalResourcePool("id"),
      new LinkedList<InterpreterContextRunner>(),
      new InterpreterOutput(null));
  }

  @AfterClass
  public static void tearDown() {
    sparkRInterpreter.close();
    sparkInterpreter.close();
  }

  @Test
  public void testPrecode() {
    InterpreterResult interpreterResult = sparkRInterpreter.interpret("print(precodeVar)", context);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
  }
}
