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
import org.apache.zeppelin.client.websocket.SimpleMessageHandler;
import org.apache.zeppelin.client.ZSession;

import java.util.HashMap;
import java.util.Map;


/**
 * Basic example of run r code via session api.
 */
public class RExample {

  public static void main(String[] args) {

    ZSession session = null;
    try {
      ClientConfig clientConfig = new ClientConfig("http://localhost:8080");
      Map<String, String> intpProperties = new HashMap<>();

      session = ZSession.builder()
              .setClientConfig(clientConfig)
              .setInterpreter("r")
              .setIntpProperties(intpProperties)
              .build();

      session.start(new SimpleMessageHandler());

      // single statement
      ExecuteResult result = session.execute("bare <- c(1, 2.5, 4)\n" +
              "print(bare)");
      System.out.println(result.getResults().get(0).getData());

      // error output
      result = session.execute("1/0");
      System.out.println("Result status: " + result.getStatus() +
              ", data: " + result.getResults().get(0).getData());

      // R plotting
      result = session.execute("ir", "pairs(iris)");
      System.out.println("R plotting result, type: " + result.getResults().get(0).getType() +
              ", data: " + result.getResults().get(0).getData());

      // ggplot2
      result = session.execute("ir", "library(ggplot2)\n" +
              "ggplot(mpg, aes(displ, hwy, colour = class)) + \n" +
              "  geom_point()");
      System.out.println("ggplot2 plotting result, type: " + result.getResults().get(0).getType() +
              ", data: " + result.getResults().get(0).getData());

      // googlevis
      result = session.execute("ir", "library(googleVis)\n" +
              "df=data.frame(country=c(\"US\", \"GB\", \"BR\"), \n" +
              "              val1=c(10,13,14), \n" +
              "              val2=c(23,12,32))\n" +
              "Bar <- gvisBarChart(df)\n" +
              "print(Bar, tag = 'chart')");
      System.out.println("googlevis plotting result, type: " + result.getResults().get(0).getType() +
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
