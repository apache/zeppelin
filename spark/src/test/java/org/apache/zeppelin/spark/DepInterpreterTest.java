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

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DepInterpreterTest {
  private DepInterpreter dep;
  private InterpreterContext context;
  private File tmpDir;
  private SparkInterpreter repl;

  @Before
  public void setUp() throws Exception {
    tmpDir = new File(System.getProperty("java.io.tmpdir") + "/ZeppelinLTest_" + System.currentTimeMillis());
    System.setProperty("zeppelin.dep.localrepo", tmpDir.getAbsolutePath() + "/local-repo");

    tmpDir.mkdirs();

    Properties p = new Properties();

    dep = new DepInterpreter(p);
    dep.open();

    InterpreterGroup intpGroup = new InterpreterGroup();
    intpGroup.add(new SparkInterpreter(p));
    intpGroup.add(dep);
    dep.setInterpreterGroup(intpGroup);

    context = new InterpreterContext("note", "id", "title", "text", new HashMap<String, Object>(), new GUI(),
        new AngularObjectRegistry(intpGroup.getId(), null),
        new LinkedList<InterpreterContextRunner>());
  }

  @After
  public void tearDown() throws Exception {
    dep.close();
    delete(tmpDir);
  }

  private void delete(File file) {
    if (file.isFile()) file.delete();
    else if (file.isDirectory()) {
      File[] files = file.listFiles();
      if (files != null && files.length > 0) {
        for (File f : files) {
          delete(f);
        }
      }
      file.delete();
    }
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
