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
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.*;

import static org.junit.Assert.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PySparkInterpreterTest {
  public static LazyOpenInterpreter sparkInterpreter;
  public static PySparkInterpreter pySparkInterpreter;
  public static InterpreterGroup intpGroup;
  private File tmpDir;
  public static Logger LOGGER = LoggerFactory.getLogger(PySparkInterpreterTest.class);
  private InterpreterContext context;

  public static Properties getPySparkTestProperties() {
    Properties p = new Properties();
    p.setProperty("master", "local[*]");
    p.setProperty("spark.app.name", "Zeppelin Test");
    p.setProperty("zeppelin.spark.useHiveContext", "true");
    p.setProperty("zeppelin.spark.maxResult", "1000");
    p.setProperty("zeppelin.spark.importImplicit", "true");
    p.setProperty("zeppelin.pyspark.python", "python");
    return p;
  }

  @Before
  public void setUp() throws Exception {
    tmpDir = new File(System.getProperty("java.io.tmpdir") + "/ZeppelinLTest_" + System.currentTimeMillis());
    System.setProperty("zeppelin.dep.localrepo", tmpDir.getAbsolutePath() + "/local-repo");
    tmpDir.mkdirs();

    intpGroup = new InterpreterGroup();
    intpGroup.put("note", new LinkedList<Interpreter>());

    if (sparkInterpreter == null) {
      sparkInterpreter = new LazyOpenInterpreter(new SparkInterpreter(getPySparkTestProperties()));
      intpGroup.get("note").add(sparkInterpreter);
      sparkInterpreter.setInterpreterGroup(intpGroup);
    }

    if (pySparkInterpreter == null) {
      pySparkInterpreter = new PySparkInterpreter(getPySparkTestProperties());
      intpGroup.get("note").add(pySparkInterpreter);
      pySparkInterpreter.setInterpreterGroup(intpGroup);
      pySparkInterpreter.open();
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
      pySparkInterpreter.interpret("a = 1\n", context).code());
  }

}
