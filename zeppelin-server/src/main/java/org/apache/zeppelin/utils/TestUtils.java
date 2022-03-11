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
package org.apache.zeppelin.utils;

import java.util.Arrays;
import org.apache.zeppelin.server.ZeppelinServer;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.ServiceLocatorFactory;

//TODO(zjffdu) refactor this class, it should not be called by non-test code. Or rename it.
public class TestUtils {
  public static <T> T getInstance(Class<T> clazz) {
    checkCalledByTestMethod();
    return getInstance(ServiceLocatorFactory.getInstance().find(ZeppelinServer.SERVICE_LOCATOR_NAME), clazz);
  }

  public static void clearInstances() {
    checkCalledByTestMethod();
    ServiceLocatorFactory.getInstance().destroy(ZeppelinServer.SERVICE_LOCATOR_NAME);
  }

  static <T> T getInstance(ServiceLocator serviceLocator, Class<T> clazz) {
    return serviceLocator.getService(clazz);
  }

  static void checkCalledByTestMethod() {
    StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
    // The first element of [0] indicates 'java.lang.Thread.getStackTrace'.
    // The second element of [1] indicates this method.
    // The third element of [2] indicates a caller of this method.
    if (Arrays.stream(stackTraceElements)
        .noneMatch(stackTraceElement -> stackTraceElement.getClassName().contains("Test"))) {
      throw new RuntimeException("This method shouldn't be used in production");
    }
  }
}
