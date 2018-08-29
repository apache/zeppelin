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

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * Util class for PySpark
 */
public class PythonUtils {

  /**
   * Get the PYTHONPATH for PySpark, either from SPARK_HOME, if it is set, or from ZEPPELIN_HOME
   * when it is embedded mode.
   *
   * This method will called in zeppelin server process and spark driver process when it is
   * local or yarn-client mode.
   */
  public static String sparkPythonPath() {
    List<String> pythonPath = new ArrayList<String>();
    String sparkHome = System.getenv("SPARK_HOME");
    String zeppelinHome = System.getenv("ZEPPELIN_HOME");
    if (zeppelinHome == null) {
      zeppelinHome = new File("..").getAbsolutePath();
    }
    if (sparkHome != null) {
      // non-embedded mode when SPARK_HOME is specified.
      File pyspark = new File(sparkHome, "python/lib/pyspark.zip");
      if (!pyspark.exists()) {
        throw new RuntimeException("No pyspark.zip found under " + sparkHome + "/python/lib");
      }
      pythonPath.add(pyspark.getAbsolutePath());
      File[] py4j = new File(sparkHome + "/python/lib").listFiles(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return name.startsWith("py4j");
        }
      });
      if (py4j.length == 0) {
        throw new RuntimeException("No py4j files found under " + sparkHome + "/python/lib");
      } else if (py4j.length > 1) {
        throw new RuntimeException("Multiple py4j files found under " + sparkHome + "/python/lib");
      } else {
        pythonPath.add(py4j[0].getAbsolutePath());
      }
    } else {
      // embedded mode
      File pyspark = new File(zeppelinHome, "interpreter/spark/pyspark/pyspark.zip");
      if (!pyspark.exists()) {
        throw new RuntimeException("No pyspark.zip found: " + pyspark.getAbsolutePath());
      }
      pythonPath.add(pyspark.getAbsolutePath());
      File[] py4j = new File(zeppelinHome, "interpreter/spark/pyspark").listFiles(
          new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
              return name.startsWith("py4j");
            }
          });
      if (py4j.length == 0) {
        throw new RuntimeException("No py4j files found under " + zeppelinHome +
            "/interpreter/spark/pyspark");
      } else if (py4j.length > 1) {
        throw new RuntimeException("Multiple py4j files found under " + sparkHome +
            "/interpreter/spark/pyspark");
      } else {
        pythonPath.add(py4j[0].getAbsolutePath());
      }
    }

    // add ${ZEPPELIN_HOME}/interpreter/lib/python for all the cases
    pythonPath.add(zeppelinHome + "/interpreter/lib/python");
    return StringUtils.join(pythonPath, ":");
  }
}
