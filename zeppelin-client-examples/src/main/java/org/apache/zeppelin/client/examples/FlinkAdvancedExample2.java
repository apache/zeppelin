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
import org.apache.zeppelin.client.websocket.CompositeMessageHandler;
import org.apache.zeppelin.client.ExecuteResult;
import org.apache.zeppelin.client.websocket.StatementMessageHandler;
import org.apache.zeppelin.client.ZSession;

import java.util.HashMap;
import java.util.Map;

public class FlinkAdvancedExample2 {
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

      // CompositeMessageHandler allow you to add StatementMessageHandler for each statement.
      // otherwise you have to use a global MessageHandler.
      session.start(new CompositeMessageHandler());
      System.out.println("Flink Web UI: " + session.getWeburl());

      System.out.println("-----------------------------------------------------------------------------");
      String initCode = IOUtils.toString(FlinkAdvancedExample.class.getResource("/init_stream.scala"));
      ExecuteResult result = session.execute(initCode);
      System.out.println("Job status: " + result.getStatus() + ", data: " + result.getResults().get(0).getData());

      // run flink ssql
      Map<String, String> localProperties = new HashMap<>();
      localProperties.put("type", "update");
      result = session.submit("ssql", localProperties, "select url, count(1) as pv from log group by url",
              new MyStatementMessageHandler1());
      session.waitUntilFinished(result.getStatementId());

      result = session.submit("ssql", localProperties, "select upper(url), count(1) as pv from log group by url",
              new MyStatementMessageHandler2());
      session.waitUntilFinished(result.getStatementId());

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

  public static class MyStatementMessageHandler1 implements StatementMessageHandler {

    @Override
    public void onStatementAppendOutput(String statementId, int index, String output) {
      System.out.println("MyStatementMessageHandler1, append output: " + output);
    }

    @Override
    public void onStatementUpdateOutput(String statementId, int index, String type, String output) {
      System.out.println("MyStatementMessageHandler1, update output: " + output);
    }
  }

  public static class MyStatementMessageHandler2 implements StatementMessageHandler {

    @Override
    public void onStatementAppendOutput(String statementId, int index, String output) {
      System.out.println("MyStatementMessageHandler2, append output: " + output);
    }

    @Override
    public void onStatementUpdateOutput(String statementId, int index, String type, String output) {
      System.out.println("MyStatementMessageHandler2, update output: " + output);
    }
  }
}
