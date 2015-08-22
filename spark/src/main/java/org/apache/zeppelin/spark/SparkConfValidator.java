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

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.spark.SparkContext;
/**
 * Validate configurations.
 */
public class SparkConfValidator {
  private String error;
  private String sparkHome;
  private String hadoopHome;
  private String hadoopConfDir;
  private String pysparkPath;

  /**
   * 
   * @param sparkHome SPARK_HOME env variable
   * @param hadoopHome HADOOP_HOME env variable
   * @param hadoopConfDir HADOOP_CONF_DIR env variable
   * @param pysparkPath PYSPARKPATH env variable
   */
  public SparkConfValidator(String sparkHome, String hadoopHome,
      String hadoopConfDir, String pysparkPath) {
    clear();
    this.sparkHome = sparkHome;
    this.hadoopHome = hadoopHome;
    this.hadoopConfDir = hadoopConfDir;
    this.pysparkPath = pysparkPath;
  }
  private void clear() {
    error = "";
  }

  public boolean validateSpark() {
    clear();

    // Check classes are loaded
    if (!checkSparkClassAvailability()) {
      return false;
    }

    return true;
  }
  
  private boolean checkSparkClassAvailability() {
    if (checkClassAvailability("org.apache.spark.SparkContext")) {
      return true;
    } else {
      // classes are not available.
      if (sparkHome == null) {
        error = "SPARK_HOME is not defined";
        return false;
      } else {
        if (!new File(sparkHome).isDirectory()) {
          error = "SPARK_HOME " + sparkHome + " is not a valid directory";
          return false;
        }
      }

      // unknown reason
      error = "Spark artifacts are not available in current classpaths\n";
      printClasspath();
      return false;
    }
  }
  
  private void printClasspath() {
    ClassLoader cl = getClass().getClassLoader();
    URL[] urls = ((URLClassLoader)cl).getURLs();
    for (URL url : urls) {
      error += url.getFile() + "\n";
    }
  }
  
  private boolean checkClassAvailability(String className) {
    try {
      getClass().getClassLoader().loadClass(className);
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
  
  public boolean validateYarn(Properties property) {
    clear();
    if (!checkClassAvailability("org.apache.spark.deploy.yarn.YarnSparkHadoopUtil")) {
      if (sparkHome == null) {
        error += "Build Zeppelin with -Pyarn flag or set SPARK_HOME";
        return false;
      } else if (!new File(sparkHome).isDirectory()) {
        error += "SPARK_HOME " + sparkHome + " is not a valid directory";
        return false;
      } else {
        // unknown reason
        error += "spark-yarn artifact is not available in current classpaths\n";
        printClasspath();
        return false;
      }
    }
    
    if (!checkClassAvailability("org.apache.hadoop.yarn.conf.YarnConfiguration")) {
      if (hadoopHome == null) {
        // unknown reason
        error += "hadoop-yarn-api artifact is not available in current classpaths.\n";
        error += "Please rebuild Zeppelin or try to set HADOOP_HOME"; 
        printClasspath();
        return false;
      } else if (!new File(hadoopHome).isDirectory()) {
        error += "HADOOP_HOME " + hadoopHome + " is not a valid directory";
        return false;
      }
    }

    // check hadoop conf dir
    if (hadoopConfDir == null) {
      error += "HADOOP_CONF_DIR is not defined";
      return false;
    } else if (!new File(hadoopConfDir).isDirectory()) {
      error += "HADOOP_CONF_DIR " + hadoopConfDir + " is not a valid directory";
      return false;
    }

    // check spark.yarn.jar
    String sparkYarnJar = property.getProperty("spark.yarn.jar");
    if (sparkYarnJar == null || sparkYarnJar.trim().length() == 0) {
      error += "spark.yarn.jar is not defined. Please set this property in Interpreter menu";
      return false;
    } else if (!new File(sparkYarnJar).isFile()) {
      error += "spark.yarn.jar " + sparkYarnJar + " is not a valid file";
      return false;
    }
    return true;
  }

  public boolean validatePyspark(boolean yarnMode) {
    clear();

    if (pysparkPath == null) {
      error += "PYSPARKPATH is not defined. It is usually configured automatically. Please report this problem";
      return false;
    } else {
      boolean pysparkFound = false;
      boolean py4jFound = false;

      for (String p : pysparkPath.split(":")) {
        File path = new File(p);
        String name = path.getName();
        
        if (Pattern.matches("py4j.*src.zip", name) && path.isFile()) {
          py4jFound = true;
        } else if (name.equals("pyspark.zip") && path.isFile()) {
          pysparkFound = true;
        } else if (name.equals("python") && sparkHome != null && 
            path.getAbsolutePath().equals(sparkHome + "/python")) {
          pysparkFound = true;
        }
      }

      if (!pysparkFound) {
        error += "pyspark.zip or SPARK_HOME/python directory is not found";
        return false;
      }

      if (!py4jFound) {
        error += "py4j-x.x.x.x-src.zip is not found. Please check your SPARK_HOME";
        return false;
      }

      if (yarnMode) {
        // more test on yarn-client mode
      }
      return true;
    }
  }

  
  public String getError() {
    return error;
  }
}
