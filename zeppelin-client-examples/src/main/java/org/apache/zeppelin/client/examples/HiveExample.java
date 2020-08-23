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

package org.apache.zeppelin.client.examples;

import org.apache.zeppelin.client.ClientConfig;
import org.apache.zeppelin.client.ExecuteResult;
import org.apache.zeppelin.client.SimpleMessageHandler;
import org.apache.zeppelin.client.ZSession;

import java.util.HashMap;
import java.util.Map;

public class HiveExample {

  public static void main(String[] args) {

    ZSession session = null;
    try {
      ClientConfig clientConfig = new ClientConfig("http://localhost:8080");
      Map<String, String> intpProperties = new HashMap<>();

      session = ZSession.builder()
              .setClientConfig(clientConfig)
              .setInterpreter("hive")
              .setIntpProperties(intpProperties)
              .build();

      session.start(new SimpleMessageHandler());

      // single sql
      ExecuteResult result = session.execute("show databases");
      System.out.println("show database result : " + result.getResults().get(0).getData());

      // multiple sql
      result = session.execute("use tpch_text_5;\nshow tables");
      System.out.println("show tables result: " + result.getResults().get(0).getData());

      // select, you can see the hive sql job progress via SimpleMessageHandler
      result = session.execute("select count(1) from lineitem");
      System.out.println("Result status: " + result.getStatus() +
              ", data: " + result.getResults().get(0).getData());

      // invalid sql
      result = session.execute("select * from unknown_table");
      System.out.println("Result status: " + result.getStatus() +
              ", data: " + result.getResults().get(0).getData());
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if (session != null) {
        try {
          session.stop();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }
}
