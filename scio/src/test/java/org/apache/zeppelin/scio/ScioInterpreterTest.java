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

package org.apache.zeppelin.scio;

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ScioInterpreterTest {
  private static ScioInterpreter repl;
  private static InterpreterGroup intpGroup;
  private InterpreterContext context;

  private static Properties getScioTestProperties() {
    Properties p = new Properties();
    //TODO: do we need some properties here?
    return p;
  }

  @Before
  public void setUp() throws Exception {
    if (repl == null) {
      intpGroup = new InterpreterGroup();
      intpGroup.put("note", new LinkedList<Interpreter>());
      repl = new ScioInterpreter(getScioTestProperties());
      repl.setInterpreterGroup(intpGroup);
      intpGroup.get("note").add(repl);
      repl.open();
    }

    context = new InterpreterContext("note", "id", "title", "text",
        new AuthenticationInfo(),
        new HashMap<String, Object>(),
        new GUI(),
        new AngularObjectRegistry(intpGroup.getId(), null),
        new LocalResourcePool("id"),
        new LinkedList<InterpreterContextRunner>(),
        new InterpreterOutput(new InterpreterOutputListener() {
          @Override
          public void onAppend(InterpreterOutput out, byte[] line) {
          }

          @Override
          public void onUpdate(InterpreterOutput out, byte[] output) {
          }
        }));
  }

  @Test
  public void testBasicIntp() {
    assertEquals(InterpreterResult.Code.SUCCESS,
        repl.interpret("val a = 1\nval b = 2", context).code());

    assertEquals(InterpreterResult.Code.ERROR,
        repl.interpret("val a:Int = 'ds'", context).code());

    InterpreterResult incomplete = repl.interpret("val a = \"\"\"", context);
    assertEquals(InterpreterResult.Code.INCOMPLETE, incomplete.code());
    assertTrue(incomplete.message().length() > 0);
  }

  @Test
  public void testBasicPipeline() {
    assertEquals(InterpreterResult.Code.SUCCESS,
        repl.interpret("sc.parallelize(1 to 10).closeAndCollect().toList", context).code());
  }
}
