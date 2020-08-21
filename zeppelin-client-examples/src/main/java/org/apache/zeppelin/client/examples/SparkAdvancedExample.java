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

public class SparkAdvancedExample {

  public static void main(String[] args) {

    ZSession session = null;
    try {
      ClientConfig clientConfig = new ClientConfig("http://localhost:8080");
      Map<String, String> intpProperties = new HashMap<>();
      intpProperties.put("spark.master", "local[*]");

      session = ZSession.builder()
              .setClientConfig(clientConfig)
              .setInterpreter("spark")
              .setIntpProperties(intpProperties)
              .build();

      // if MessageHandler is specified, then websocket is enabled.
      // you can get continuous output from Zeppelin via websocket.
      session.start(new SimpleMessageHandler());
      System.out.println("Spark Web UI: " + session.getWeburl());

      String code = "sc.range(1,10).map(e=> {Thread.sleep(2000); e}).sum()";
      System.out.println("Submit code: " + code);
      // use submit to run spark code in non-blocking way.
      ExecuteResult result = session.submit(code);
      System.out.println("Job status: " + result.getStatus());
      while(!result.getStatus().isCompleted()) {
        result = session.queryStatement(result.getStatementId());
        System.out.println("Job status: " + result.getStatus() + ", progress: " + result.getProgress());
        Thread.sleep(1000);
      }
      System.out.println("Job status: " + result.getStatus() + ", data: " + result.getResults().get(0).getData());

      System.out.println("-----------------------------------------------------------------------------");
      System.out.println("Submit code: " + code);
      result = session.submit("sc.range(1,10).map(e=> {Thread.sleep(2000); e}).sum()");
      System.out.println("Job status: " + result.getStatus());
      result = session.waitUntilFinished(result.getStatementId());
      System.out.println("Job status: " + result.getStatus() + ", data: " + result.getResults().get(0).getData());

      System.out.println("-----------------------------------------------------------------------------");
      System.out.println("Submit code: " + code);
      result = session.submit("sc.range(1,10).map(e=> {Thread.sleep(2000); e}).sum()");
      System.out.println("Job status: " + result.getStatus());
      session.waitUntilRunning(result.getStatementId());
      System.out.println("Try to cancel statement: " + result.getStatementId());
      session.cancel(result.getStatementId());
      result = session.waitUntilFinished(result.getStatementId());
      System.out.println("Job status: " + result.getStatus() + ", data: " + result.getResults().get(0).getData());

      System.out.println("-----------------------------------------------------------------------------");
      code = "for(i <- 1 to 10) {\n" +
              "   Thread.sleep(1000)\n" +
              "   println(i)\n" +
              "}";
      System.out.println("Submit code: " + code);
      result = session.execute(code);
      System.out.println("Job status: " + result.getStatus() + ", data: " + result.getResults().get(0).getData());

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
