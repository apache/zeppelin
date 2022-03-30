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

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;

/**
 * Utility and helper functions for the Spark Interpreter
 */
class Utils {
  private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);
  private static String DEPRECATED_MESSAGE =
          "%html <font color=\"red\">Spark lower than 2.2 is deprecated, " +
          "if you don't want to see this message, please set " +
          "zeppelin.spark.deprecateMsg.show to false.</font>";


  public static String buildJobGroupId(InterpreterContext context) {
    String uName = "anonymous";
    if (context.getAuthenticationInfo() != null) {
      uName = getUserName(context.getAuthenticationInfo());
    }
    return "zeppelin|" + uName + "|" + context.getNoteId() + "|" + context.getParagraphId();
  }

  public static String buildJobDesc(InterpreterContext context) {
    return "Started by: " + getUserName(context.getAuthenticationInfo());
  }

  public static String getUserName(AuthenticationInfo info) {
    String uName = "";
    if (info != null) {
      uName = info.getUser();
    }
    if (uName == null || uName.isEmpty()) {
      uName = "anonymous";
    }
    return uName;
  }

  public static void printDeprecateMessage(SparkVersion sparkVersion,
                                            InterpreterContext context,
                                            Properties properties) throws InterpreterException {
    context.out.clear();
    if (sparkVersion.olderThan(SparkVersion.SPARK_2_2_0)
            && Boolean.parseBoolean(
                    properties.getProperty("zeppelin.spark.deprecatedMsg.show", "true"))) {
      try {
        context.out.write(DEPRECATED_MESSAGE);
        context.out.write("%text ");
      } catch (IOException e) {
        throw new InterpreterException(e);
      }
    }
  }
}
