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
package org.apache.zeppelin.util;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;


/**
 * Utility class for creating instances via java reflection.
 *
 */
public class ReflectionUtils {

  public static Class<?> getClazz(String className) throws IOException {
    Class clazz = null;
    try {
      clazz = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
    } catch (ClassNotFoundException e) {
      throw new IOException("Unable to load class: " + className, e);
    }

    return clazz;
  }

  private static <T> T getNewInstance(Class<T> clazz) throws IOException {
    T instance;
    try {
      instance = clazz.newInstance();
    } catch (InstantiationException e) {
      throw new IOException(
          "Unable to instantiate class with 0 arguments: " + clazz.getName(), e);
    } catch (IllegalAccessException e) {
      throw new IOException(
          "Unable to instantiate class with 0 arguments: " + clazz.getName(), e);
    }
    return instance;
  }

  private static <T> T getNewInstance(Class<T> clazz,
                                      Class<?>[] parameterTypes,
                                      Object[] parameters)
      throws IOException {
    T instance;
    try {
      Constructor<T> constructor = clazz.getConstructor(parameterTypes);
      instance = constructor.newInstance(parameters);
    } catch (InstantiationException e) {
      throw new IOException(
          "Unable to instantiate class with " + parameters.length + " arguments: " +
              clazz.getName(), e);
    } catch (IllegalAccessException e) {
      throw new IOException(
          "Unable to instantiate class with " + parameters.length + " arguments: " +
              clazz.getName(), e);
    } catch (NoSuchMethodException e) {
      throw new IOException(
          "Unable to instantiate class with " + parameters.length + " arguments: " +
              clazz.getName(), e);
    } catch (InvocationTargetException e) {
      throw new IOException(
          "Unable to instantiate class with " + parameters.length + " arguments: " +
              clazz.getName(), e);
    }
    return instance;
  }

  public static <T> T createClazzInstance(String className) throws IOException {
    Class<?> clazz = getClazz(className);
    @SuppressWarnings("unchecked")
    T instance = (T) getNewInstance(clazz);
    return instance;
  }

  public static <T> T createClazzInstance(String className,
                                          Class<?>[] parameterTypes,
                                          Object[] parameters) throws IOException {
    Class<?> clazz = getClazz(className);
    T instance = (T) getNewInstance(clazz, parameterTypes, parameters);
    return instance;
  }


}
