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
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
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
  //public static SparkInterpreter sparkInterpreter;
  public static PySparkInterpreter pySparkInterpreter;
  public static InterpreterGroup intpGroup;
  private File tmpDir;
  public static Logger LOGGER = LoggerFactory.getLogger(PySparkInterpreterTest.class);

  private static final String INTERPRETER_SCRIPT =
    System.getProperty("os.name").startsWith("Windows") ?
      "../bin/interpreter.cmd" :
      "../bin/interpreter.sh";

  public static Properties getPySparkTestProperties() {
    Properties p = new Properties();
    p.setProperty("master", "local[*]");
    p.setProperty("spark.app.name", "Zeppelin Test");
    p.setProperty("zeppelin.spark.useHiveContext", "true");
    p.setProperty("zeppelin.spark.maxResult", "1000");
    p.setProperty("zeppelin.spark.importImplicit", "true");
    p.setProperty("zeppelin.pyspark.python", "python");
    //p.setProperty("zeppelin.interpreter.localRepo", "/home/nflabs/zeppelin/local-repo");

    return p;
  }

  @Before
  public void setUp() throws Exception {
    tmpDir = new File(System.getProperty("java.io.tmpdir") + "/ZeppelinLTest_" + System.currentTimeMillis());
    System.setProperty("zeppelin.dep.localrepo", tmpDir.getAbsolutePath() + "/local-repo");
    //System.setProperty("zeppelin.interpreter.localrepo", tmpDir.getAbsolutePath() + "/local-repo");
    tmpDir.mkdirs();


//    String s = System.getProperty("PATH");
    //System.setProperty("PYTHONPATH", "/home/nflabs/zeppelin/spark-dependencies/target/spark-dist/spark-2.0.0/python:/home/nflabs/zeppelin/spark-dependencies/target/spark-dist/spark-2.0.0/python/lib/py4j-0.10.1-src.zip");
/*
    //System.setProperty("PATH", "/home/nflabs/zeppelin/spark-dependencies/target/spark-dist/spark-2.0.0/python/lib/py4j-0.10.1-src.zip");
    s = System.getProperty("PATH");
*/

    intpGroup = new InterpreterGroup();
    intpGroup.put("note", new LinkedList<Interpreter>());

//    SparkConf conf = sparkInterpreter.getSparkContext().getConf();
//    String zip = conf.get("spark.files");
/*

    pysparkBasePath =
      new InterpreterProperty("ZEPPELIN_HOME", "zeppelin.home", "../", null).getValue();
    pysparkPath = new File(pysparkBasePath,
      "interpreter" + File.separator + "spark" + File.separator + "pyspark");
*/


    /*
    RemoteInterpreter remoteInterpreter = createPysparkInterpreter(getPySparkTestProperties(), "note");
    intpGroup.get("note").add(remoteInterpreter);
    remoteInterpreter.setInterpreterGroup(intpGroup);
    remoteInterpreter.open();
*/

    if (sparkInterpreter == null) {
      sparkInterpreter = new LazyOpenInterpreter(new SparkInterpreter(getPySparkTestProperties()));
      //sparkInterpreter = new SparkInterpreter(getPySparkTestProperties());
      intpGroup.get("note").add(sparkInterpreter);
      sparkInterpreter.setInterpreterGroup(intpGroup);
      //sparkInterpreter.open();
    }

    if (pySparkInterpreter == null) {
      //pySparkInterpreter = new LazyOpenInterpreter(new PySparkInterpreter(getPySparkTestProperties()));
      pySparkInterpreter = new PySparkInterpreter(getPySparkTestProperties());
      intpGroup.get("note").add(pySparkInterpreter);
      pySparkInterpreter.setInterpreterGroup(intpGroup);
      pySparkInterpreter.open();
    }
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
  public void testPySparkCompletion() {
    //pySparkInterpreter.interpret("int(\"123\")", context).code();
    //List<InterpreterCompletion> completions = pySparkInterpreter.completion("sc.", "sc.".length());
    List<InterpreterCompletion> completions = pySparkInterpreter.completion("sc.", "sc.".length());
    assertTrue(completions.size() > 0);
  }
}
