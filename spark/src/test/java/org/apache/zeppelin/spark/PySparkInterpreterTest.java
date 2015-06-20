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
import static org.junit.Assert.assertTrue;

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
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PySparkInterpreterTest {
  private SparkInterpreter repl;
  private PySparkInterpreter py;
  private SparkSqlInterpreter sql;
  private InterpreterContext context;
  private InterpreterGroup intpGroup;
  private File tmpDir;

  @Before
  public void setUp() throws Exception {
    tmpDir = new File(System.getProperty("java.io.tmpdir") + "/ZeppelinLTest_" + System.currentTimeMillis());
    tmpDir.mkdirs();
    System.setProperty("zeppelin.dep.localrepo", tmpDir.getAbsolutePath() + "/local-repo");
    
    Properties p = new Properties();
    if (repl == null) {

        if (SparkInterpreterTest.repl == null) {
          repl = new SparkInterpreter(p);
          SparkInterpreterTest.repl = repl;
        } else {
          repl = SparkInterpreterTest.repl;
        }
      sql = new SparkSqlInterpreter(p);
      py = new PySparkInterpreter(p);

      intpGroup = new InterpreterGroup();
        intpGroup.add(repl);
        intpGroup.add(py);
        intpGroup.add(sql);
        repl.setInterpreterGroup(intpGroup);
        py.setInterpreterGroup(intpGroup);
        sql.setInterpreterGroup(intpGroup);
        repl.open();
        sql.open();
        py.open();
      }

    context = new InterpreterContext("id", "title", "text",
        new HashMap<String, Object>(), new GUI(), new AngularObjectRegistry(
            intpGroup.getId(), null),
        new LinkedList<InterpreterContextRunner>());
  }

  @After
  public void tearDown() throws Exception {
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
  public void testBasicIntp() {
    assertEquals(InterpreterResult.Code.SUCCESS,
        py.interpret("a = 1\nb = 2", context).code());

    // when interpret incomplete expression
    InterpreterResult incomplete = py.interpret("a = '''", context);
    assertEquals(incomplete.message(), InterpreterResult.Code.ERROR, incomplete.code());
    assertTrue(incomplete.message().length() > 0); // expecting some error
                                                   // message
  }

  @Test
  public void testEndWithComment() {
	  InterpreterResult res = py.interpret("c=1\n#comment", context);
    assertEquals(res.message(), InterpreterResult.Code.SUCCESS, res.code());
  }

  @Test
  public void testPyspark(){
    InterpreterResult result = py.interpret("foo = sc.parallelize(range(10))", context);
    assertEquals(result.message(), Code.SUCCESS, result.code());

    result = py.interpret("print foo.reduce(lambda x, y: x + y)", context);
    assertEquals(result.message(), Code.SUCCESS, result.code());
    assertEquals("45\n", result.message());
  }

  @Test
  public void testReferencingUndefinedVariable() {
    InterpreterResult result = py.interpret("def category(min):\n  if 0 <= value:\n    \"error\"\n\ncategory(5)\n", context);
    assertEquals(result.message(), Code.ERROR, result.code());
  }
}
