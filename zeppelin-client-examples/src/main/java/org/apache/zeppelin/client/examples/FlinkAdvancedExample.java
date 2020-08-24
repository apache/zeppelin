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

import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.client.ClientConfig;
import org.apache.zeppelin.client.ExecuteResult;
import org.apache.zeppelin.client.websocket.SimpleMessageHandler;
import org.apache.zeppelin.client.ZSession;

import java.util.HashMap;
import java.util.Map;

public class FlinkAdvancedExample {
  public static void main(String[] args) {

    ZSession session = null;
    try {
      ClientConfig clientConfig = new ClientConfig("http://localhost:8080");
      Map<String, String> intpProperties = new HashMap<>();

      session = ZSession.builder()
              .setClientConfig(clientConfig)
              .setInterpreter("flink")
              .setIntpProperties(intpProperties)
              .build();

      // if MessageHandler is specified, then websocket is enabled.
      // you can get continuous output from Zeppelin via websocket.
      session.start(new SimpleMessageHandler());
      System.out.println("Flink Web UI: " + session.getWeburl());

      String code = "benv.fromElements(1,2,3,4,5,6,7,8,9,10).map(e=> {Thread.sleep(1000); e}).print()";
      System.out.println("Submit code: " + code);
      // use submit to run flink code in non-blocking way.
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
      result = session.submit("benv.fromElements(1,2,3,4,5,6,7,8,9,10).map(e=> {Thread.sleep(1000); e}).print()");
      System.out.println("Job status: " + result.getStatus());
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

      System.out.println("-----------------------------------------------------------------------------");
      String initCode = IOUtils.toString(FlinkAdvancedExample.class.getResource("/init_stream.scala"));
      result = session.execute(initCode);
      System.out.println("Job status: " + result.getStatus() + ", data: " + result.getResults().get(0).getData());

      // run flink ssql
      Map<String, String> localProperties = new HashMap<>();
      localProperties.put("type", "update");
      result = session.submit("ssql", localProperties, "select url, count(1) as pv from log group by url");
      session.waitUntilFinished(result.getStatementId());

      result = session.submit("ssql", localProperties, "select url, count(1) as pv from log group by url");
      session.waitUntilRunning(result.getStatementId());
      Thread.sleep(10 * 1000);
      System.out.println("Try to cancel statement: " + result.getStatementId());
      session.cancel(result.getStatementId());
      session.waitUntilFinished(result.getStatementId());
      System.out.println("Job status: " + result.getStatus());

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
