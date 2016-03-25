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

package org.apache.zeppelin.scalding;

import static org.junit.Assert.*;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.user.AuthenticationInfo;
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

/**
 * Tests for the Scalding interpreter for Zeppelin.
 *
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ScaldingInterpreterTest {
  public static ScaldingInterpreter repl;
  private InterpreterContext context;
  private File tmpDir;

  @Before
  public void setUp() throws Exception {
    tmpDir = new File(System.getProperty("java.io.tmpdir") + "/ZeppelinLTest_" + System.currentTimeMillis());
    System.setProperty("zeppelin.dep.localrepo", tmpDir.getAbsolutePath() + "/local-repo");

    tmpDir.mkdirs();

    if (repl == null) {
      Properties p = new Properties();

      repl = new ScaldingInterpreter(p);
      repl.open();
    }

    InterpreterGroup intpGroup = new InterpreterGroup();
    context = new InterpreterContext("note", "id", "title", "text", new AuthenticationInfo(),
        new HashMap<String, Object>(), new GUI(), new AngularObjectRegistry(
            intpGroup.getId(), null), null,
        new LinkedList<InterpreterContextRunner>(), null);
  }

  @After
  public void tearDown() throws Exception {
    delete(tmpDir);
    repl.close();
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
  public void testNextLineComments() {
    assertEquals(InterpreterResult.Code.SUCCESS, repl.interpret("\"123\"\n/*comment here\n*/.toInt", context).code());
  }

  @Test
  public void testNextLineCompanionObject() {
    String code = "class Counter {\nvar value: Long = 0\n}\n // comment\n\n object Counter {\n def apply(x: Long) = new Counter()\n}";
    assertEquals(InterpreterResult.Code.SUCCESS, repl.interpret(code, context).code());
  }

  @Test
  public void testBasicIntp() {
    assertEquals(InterpreterResult.Code.SUCCESS,
        repl.interpret("val a = 1\nval b = 2", context).code());

    // when interpret incomplete expression
    InterpreterResult incomplete = repl.interpret("val a = \"\"\"", context);
    assertEquals(InterpreterResult.Code.INCOMPLETE, incomplete.code());
    assertTrue(incomplete.message().length() > 0); // expecting some error
                                                   // message
  }

  @Test
  public void testBasicScalding() {
    assertEquals(InterpreterResult.Code.SUCCESS,
        repl.interpret("case class Sale(state: String, name: String, sale: Int)\n" +
          "val salesList = List(Sale(\"CA\", \"A\", 60), Sale(\"CA\", \"A\", 20), Sale(\"VA\", \"B\", 15))\n" +
          "val salesPipe = TypedPipe.from(salesList)\n" +
          "val results = salesPipe.map{x => (1, Set(x.state), x.sale)}.\n" +
          "    groupAll.sum.values.map{ case(count, set, sum) => (count, set.size, sum) }\n" +
          "results.dump", 
          context).code());
  }

  @Test
  public void testNextLineInvocation() {
    assertEquals(InterpreterResult.Code.SUCCESS, repl.interpret("\"123\"\n.toInt", context).code());
  }

  @Test
  public void testEndWithComment() {
    assertEquals(InterpreterResult.Code.SUCCESS, repl.interpret("val c=1\n//comment", context).code());
  }

  @Test
  public void testReferencingUndefinedVal() {
    InterpreterResult result = repl.interpret("def category(min: Int) = {"
        + "    if (0 <= value) \"error\"" + "}", context);
    assertEquals(Code.ERROR, result.code());
  }
}
