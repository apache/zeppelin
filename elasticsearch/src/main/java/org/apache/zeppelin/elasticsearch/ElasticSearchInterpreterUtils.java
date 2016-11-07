package org.apache.zeppelin.elasticsearch;

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

import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.transport.Transport;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Utils for ElasticSearchInterpreter.
 */
public class ElasticSearchInterpreterUtils {

  public static boolean isElasticSearchVersion5() {
    try {
      Class.forName("org.elasticsearch.node.NodeBuilder");
      return false;
    } catch (ClassNotFoundException e) {
      return true;
    }
  }

  public static TransportClient createTransportClient(Settings settings) {
    try {
      if (isElasticSearchVersion5()) {
        return createTransportClientForVersion5(settings);
      } else {
        return createTransportClientForVersion2(settings);
      }
    } catch (ReflectiveOperationException e) {
      // Wrap checked exception.
      throw new RuntimeException("Failed to initialize ElasticSearchClient", e);
    }
  }

  public static TransportClient createTransportClientForVersion2(Settings settings) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    // TransportClient in 5.x client doesn't have the `builder` method and `Builder` class

    // `TransportClient.builder().settings(settings).build()`
    Object builder = Class.forName("org.elasticsearch.client.transport.TransportClient")
        .getMethod("builder", new Class[]{})
        .invoke(null, new Object[]{});

    Object builder2 = builder.getClass()
        .getMethod("settings", Settings.class)
        .invoke(builder, settings);

    TransportClient client = (TransportClient) builder2.getClass()
        .getMethod("build")
        .invoke(builder2);

    return client;
  }

  public static TransportClient createTransportClientForVersion5(Settings settings) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
    // PreBuiltTransportClient was introduced since 5.x client.

    // `new PreBuiltTransportClient(settings)`
    Constructor c = Class
        .forName("org.elasticsearch.transport.client.PreBuiltTransportClient")
        .getConstructor(Settings.class);

    return (TransportClient) c.newInstance(settings);
  }
}
