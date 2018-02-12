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

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DepInterpreterTest {

  @Rule
  public TemporaryFolder tmpDir = new TemporaryFolder();

  private DepInterpreter dep;
  private InterpreterContext context;

  private Properties getTestProperties() throws IOException {
    Properties p = new Properties();
    p.setProperty("zeppelin.dep.localrepo", tmpDir.newFolder().getAbsolutePath());
    p.setProperty("zeppelin.dep.additionalRemoteRepository", "spark-packages,http://dl.bintray.com/spark-packages/maven,false;");
    return p;
  }

  @Before
  public void setUp() throws Exception {
    Properties p = getTestProperties();

    dep = new DepInterpreter(p);
    dep.open();

    InterpreterGroup intpGroup = new InterpreterGroup();
    intpGroup.put("note", new LinkedList<Interpreter>());
    intpGroup.get("note").add(new SparkInterpreter(p));
    intpGroup.get("note").add(dep);
    dep.setInterpreterGroup(intpGroup);

    context = new InterpreterContext("note", "id", null, "title", "text", new AuthenticationInfo(),
        new HashMap<String, Object>(), new GUI(), new GUI(),
        new AngularObjectRegistry(intpGroup.getId(), null),
        null,
        new LinkedList<InterpreterContextRunner>(), null);
  }

  @After
  public void tearDown() throws Exception {
    dep.close();
  }

  @Test
  public void testDefault() {
    dep.getDependencyContext().reset();
    InterpreterResult ret = dep.interpret("z.load(\"org.apache.commons:commons-csv:1.1\")", context);
    assertEquals(Code.SUCCESS, ret.code());

    assertEquals(1, dep.getDependencyContext().getFiles().size());
    assertEquals(1, dep.getDependencyContext().getFilesDist().size());

    // Add a test for the spark-packages repo - default in additionalRemoteRepository
    ret = dep.interpret("z.load(\"amplab:spark-indexedrdd:0.3\")", context);
    assertEquals(Code.SUCCESS, ret.code());

    // Reset at the end of the test
    dep.getDependencyContext().reset();
  }
}
