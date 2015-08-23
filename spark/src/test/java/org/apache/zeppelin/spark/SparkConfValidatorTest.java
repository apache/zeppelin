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

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SparkConfValidatorTest {

  private File tmpDir;

  @Before
  public void setUp() {
    tmpDir = new File(System.getProperty("java.io.tmpdir") + "/ZeppelinLTest_" + System.currentTimeMillis());
    tmpDir.mkdirs();
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
  public void testValidateSpark() {
    String sparkHome = null;
    String hadoopHome = null;
    String hadoopConfDir = null;
    String pysparkPath = null;

    SparkConfValidator scv = new SparkConfValidator(
        sparkHome, hadoopHome, hadoopConfDir, pysparkPath);

    assertTrue(scv.validateSpark());
  }

  @Test
  public void testValidateYarn() {
    String sparkHome = null;
    String hadoopHome = null;
    String hadoopConfDir = "/tmp";
    String pysparkPath = null;

    SparkConfValidator scv = new SparkConfValidator(
        sparkHome, hadoopHome, hadoopConfDir, pysparkPath);

    Properties p = new Properties();
    p.setProperty("spark.yarn.jar", "../README.md");

    boolean testBuiltWithYarnEnabled = scv.validateYarn(p);

    if (testBuiltWithYarnEnabled) {
      // check HADOOP_CONF_DIR is missing
      scv = new SparkConfValidator(
          sparkHome, hadoopHome, null, pysparkPath);
      assertFalse(scv.validateYarn(p));

      // check HADOOP_CONF_DIR not exists or a file
      scv = new SparkConfValidator(
          sparkHome, hadoopHome, "/notexists", pysparkPath);
      assertFalse(scv.validateYarn(p));

      scv = new SparkConfValidator(
          sparkHome, hadoopHome, "../README.md", pysparkPath);
      assertFalse(scv.validateYarn(p));


      // check spark.yarn.jar is missing
      scv = new SparkConfValidator(
          sparkHome, hadoopHome, hadoopConfDir, pysparkPath);
      p.setProperty("spark.yarn.jar", "/notexists");
      assertFalse(scv.validateYarn(p));

      // check spark.yarn.jar is not a file
      scv = new SparkConfValidator(
          sparkHome, hadoopHome, hadoopConfDir, pysparkPath);
      p.setProperty("spark.yarn.jar", "/tmp");
      assertFalse(scv.validateYarn(p));
    }
  }

  @Test
  public void testValidatePyspark() throws IOException {
    String sparkHome = null;
    String hadoopHome = null;
    String hadoopConfDir = null;
    String pythonPath = null;

    SparkConfValidator scv = new SparkConfValidator(
        sparkHome, hadoopHome, hadoopConfDir, pythonPath);
    assertFalse(scv.validatePyspark(false));

    // create mock file and directories
    new File(tmpDir, "py4j-0.0.0.0-src.zip").createNewFile();
    new File(tmpDir, "pyspark.zip").createNewFile();
    new File(tmpDir, "python").mkdir();
    new File(tmpDir, "invalidpath").mkdir();

    // when py4j and python dir is found
    pythonPath = tmpDir.getAbsolutePath() + "/py4j-0.0.0.0-src.zip:" +
        tmpDir.getAbsolutePath() + "/python";
    scv = new SparkConfValidator(tmpDir.getAbsolutePath(), hadoopHome, hadoopConfDir, pythonPath);
    assertTrue(scv.validatePyspark(false));

    scv = new SparkConfValidator(null, hadoopHome, hadoopConfDir, pythonPath);
    assertFalse(scv.validatePyspark(false));

    scv = new SparkConfValidator(tmpDir.getAbsolutePath() + "/invalidpath", hadoopHome, hadoopConfDir, pythonPath);
    assertFalse(scv.validatePyspark(false));

    // when py4j and python.zip is found
    pythonPath = tmpDir.getAbsolutePath() + "/py4j-0.0.0.0-src.zip:" +
        tmpDir.getAbsolutePath() + "/python";

    scv = new SparkConfValidator(tmpDir.getAbsolutePath(), hadoopHome, hadoopConfDir, pythonPath);
    assertTrue(scv.validatePyspark(false));

    scv = new SparkConfValidator(null, hadoopHome, hadoopConfDir, pythonPath);
    assertFalse(scv.validatePyspark(false));

    scv = new SparkConfValidator(tmpDir.getAbsolutePath() + "/invalidpath", hadoopHome, hadoopConfDir, pythonPath);
    assertFalse(scv.validatePyspark(false));

    // when python dir or python.zip is not found
    pythonPath = tmpDir.getAbsolutePath() + "/py4j-0.0.0.0-src.zip:" +
        tmpDir.getAbsolutePath() + "/invalidpath";

    scv = new SparkConfValidator(tmpDir.getAbsolutePath(), hadoopHome, hadoopConfDir, pythonPath);
    assertFalse(scv.validatePyspark(false));

    // when py4j can not be found
    pythonPath = tmpDir.getAbsolutePath() + "/invalidpath:" +
        tmpDir.getAbsolutePath() + "/python";
    scv = new SparkConfValidator(tmpDir.getAbsolutePath(), hadoopHome, hadoopConfDir, pythonPath);
    assertFalse(scv.validatePyspark(false));
  }
}
