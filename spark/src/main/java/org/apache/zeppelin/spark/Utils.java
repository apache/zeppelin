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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Utility and helper functions for the Spark Interpreter
 */
class Utils {
  public static Logger logger = LoggerFactory.getLogger(Utils.class);

  static Object invokeMethod(Object o, String name) {
    return invokeMethod(o, name, new Class[]{}, new Object[]{});
  }

  static Object invokeMethod(Object o, String name, Class[] argTypes, Object[] params) {
    try {
      return o.getClass().getMethod(name, argTypes).invoke(o, params);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      logger.error(e.getMessage(), e);
    }
    return null;
  }

  static Object invokeStaticMethod(Class c, String name, Class[] argTypes, Object[] params) {
    try {
      return c.getMethod(name, argTypes).invoke(null, params);
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      logger.error(e.getMessage(), e);
    }
    return null;
  }

  static Object invokeStaticMethod(Class c, String name) {
    return invokeStaticMethod(c, name, new Class[]{}, new Object[]{});
  }

  static Class findClass(String name) {
    return findClass(name, false);
  }

  static Class findClass(String name, boolean silence) {
    try {
      return Utils.class.forName(name);
    } catch (ClassNotFoundException e) {
      if (!silence) {
        logger.error(e.getMessage(), e);
      }
      return null;
    }
  }

  static Object instantiateClass(String name, Class[] argTypes, Object[] params) {
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
      Utils.class.forName("org.apache.spark.repl.SparkIMain");
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

  static boolean isSpark2() {
    try {
      Utils.class.forName("org.apache.spark.sql.SparkSession");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
