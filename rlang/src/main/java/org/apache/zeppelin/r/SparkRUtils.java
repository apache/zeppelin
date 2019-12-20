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

package org.apache.zeppelin.r;

import org.apache.zeppelin.interpreter.InterpreterException;

import java.io.File;

public class SparkRUtils {

  public static String getSparkRLib(boolean isSparkSupported) throws InterpreterException {
    String sparkRLibPath;

    if (System.getenv("SPARK_HOME") != null) {
      // local or yarn-client mode when SPARK_HOME is specified
      sparkRLibPath = System.getenv("SPARK_HOME") + "/R/lib";
    } else if (System.getenv("ZEPPELIN_HOME") != null){
      // embedded mode when SPARK_HOME is not specified or for native R support
      String interpreter = "r";
      if (isSparkSupported) {
        interpreter = "spark";
      }
      sparkRLibPath = System.getenv("ZEPPELIN_HOME") + "/interpreter/" + interpreter + "/R/lib";
      // workaround to make sparkr work without SPARK_HOME
      System.setProperty("spark.test.home", System.getenv("ZEPPELIN_HOME") + "/interpreter/" + interpreter);
    } else {
      // yarn-cluster mode
      sparkRLibPath = "sparkr";
    }
    if (!new File(sparkRLibPath).exists()) {
      throw new InterpreterException(String.format("sparkRLib '%s' doesn't exist", sparkRLibPath));
    }

    return sparkRLibPath;
  }
}
