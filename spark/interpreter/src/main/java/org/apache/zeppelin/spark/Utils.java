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
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility and helper functions for the Spark Interpreter
 */
class Utils {
  public static Logger logger = LoggerFactory.getLogger(Utils.class);
  private static final String SCALA_COMPILER_VERSION = evaluateScalaCompilerVersion();

  static Object invokeMethod(Object o, String name) {
    return invokeMethod(o, name, new Class[]{}, new Object[]{});
  }

  static Object invokeMethod(Object o, String name, Class<?>[] argTypes, Object[] params) {
    try {
      return o.getClass().getMethod(name, argTypes).invoke(o, params);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      logger.error(e.getMessage(), e);
    }
    return null;
  }

  static Object invokeStaticMethod(Class<?> c, String name, Class<?>[] argTypes, Object[] params) {
    try {
      return c.getMethod(name, argTypes).invoke(null, params);
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      logger.error(e.getMessage(), e);
    }
    return null;
  }

  static Object invokeStaticMethod(Class<?> c, String name) {
    return invokeStaticMethod(c, name, new Class[]{}, new Object[]{});
  }

  static Class<?> findClass(String name) {
    return findClass(name, false);
  }

  static Class<?> findClass(String name, boolean silence) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException e) {
      if (!silence) {
        logger.error(e.getMessage(), e);
      }
      return null;
    }
  }

  static Object instantiateClass(String name, Class<?>[] argTypes, Object[] params) {
    try {
      Constructor<?> constructor = Utils.class.getClassLoader()
              .loadClass(name).getConstructor(argTypes);
      return constructor.newInstance(params);
    } catch (NoSuchMethodException | ClassNotFoundException | IllegalAccessException |
      InstantiationException | InvocationTargetException e) {
      logger.error(e.getMessage(), e);
    }
    return null;
  }

  // function works after intp is initialized
  static boolean isScala2_10() {
    try {
      Class.forName("org.apache.spark.repl.SparkIMain");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    } catch (IncompatibleClassChangeError e) {
      return false;
    }
  }

  static boolean isScala2_11() {
    return !isScala2_10();
  }

  static boolean isCompilerAboveScala2_11_7() {
    if (isScala2_10() || SCALA_COMPILER_VERSION == null) {
      return false;
    }
    Pattern p = Pattern.compile("([0-9]+)[.]([0-9]+)[.]([0-9]+)");
    Matcher m = p.matcher(SCALA_COMPILER_VERSION);
    if (m.matches()) {
      int major = Integer.parseInt(m.group(1));
      int minor = Integer.parseInt(m.group(2));
      int bugfix = Integer.parseInt(m.group(3));
      return (major > 2 || (major == 2 && minor > 11) || (major == 2 && minor == 11 && bugfix > 7));
    }
    return false;
  }

  private static String evaluateScalaCompilerVersion() {
    String version = null;
    try {
      Properties p = new Properties();
      Class<?> completionClass = findClass("scala.tools.nsc.interpreter.JLineCompletion");
      if (completionClass != null) {
        try (java.io.InputStream in = completionClass.getClass()
          .getResourceAsStream("/compiler.properties")) {
          p.load(in);
          version = p.getProperty("version.number");
        } catch (java.io.IOException e) {
          logger.error("Failed to evaluate Scala compiler version", e);
        }
      }
    } catch (RuntimeException e) {
      logger.error("Failed to evaluate Scala compiler version", e);
    }
    return version;
  }

  static boolean isSpark2() {
    try {
      Class.forName("org.apache.spark.sql.SparkSession");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  public static String buildJobGroupId(InterpreterContext context) {
    return "zeppelin-" + context.getNoteId() + "-" + context.getParagraphId() + "\n" +
            context.getParagraphText().substring(0,
                    context.getParagraphText().length() > 200 ? 200 : context.getParagraphText().length());
  }

  public static String buildJobDesc(InterpreterContext context) {
    return "Started by: " + getUserName(context.getAuthenticationInfo());
  }

  public static String getNoteId(String jobgroupId) {
    int indexOf = jobgroupId.indexOf("-");
    int secondIndex = jobgroupId.indexOf("-", indexOf + 1);
    return jobgroupId.substring(indexOf + 1, secondIndex);
  }

  public static String getParagraphId(String jobgroupId) {
    int indexOf = jobgroupId.indexOf("-");
    int secondIndex = jobgroupId.indexOf("-", indexOf + 1);
    return jobgroupId.substring(secondIndex + 1, jobgroupId.length());
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
}
