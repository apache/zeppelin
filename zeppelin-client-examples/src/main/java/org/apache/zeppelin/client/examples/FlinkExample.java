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

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.client.ClientConfig;
import org.apache.zeppelin.client.ExecuteResult;
import org.apache.zeppelin.client.ZSession;

import java.util.HashMap;
import java.util.Map;


/**
 * Basic example of run flink code (scala, sql, python) via session api.
 */
public class FlinkExample {
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

      session.start();
      System.out.println("Flink Web UI: " + session.getWeburl());

      // scala (single result)
      ExecuteResult result = session.execute("benv.fromElements(1,2,3).print()");
      System.out.println("Result: " + result.getResults().get(0).getData());

      // scala (multiple result)
      result = session.execute("val data = benv.fromElements(1,2,3).map(e=>(e, e * 2))\n" +
              "data.print()\n" +
              "z.show(data)");

      // The first result is text output
      System.out.println("Result 1: type: " + result.getResults().get(0).getType() +
              ", data: " + result.getResults().get(0).getData() );
      // The second result is table output
      System.out.println("Result 2: type: " + result.getResults().get(1).getType() +
              ", data: " + result.getResults().get(1).getData() );
      System.out.println("Flink Job Urls:\n" + StringUtils.join(result.getJobUrls(), "\n"));

      // error output
      result = session.execute("1/0");
      System.out.println("Result status: " + result.getStatus() +
              ", data: " + result.getResults().get(0).getData());

      // pyflink
      result = session.execute("pyflink", "type(b_env)");
      System.out.println("benv: " + result.getResults().get(0).getData());
      // matplotlib
      result = session.execute("ipyflink", "%matplotlib inline\n" +
              "import matplotlib.pyplot as plt\n" +
              "plt.plot([1,2,3,4])\n" +
              "plt.ylabel('some numbers')\n" +
              "plt.show()");
      System.out.println("Matplotlib result, type: " + result.getResults().get(0).getType() +
              ", data: " + result.getResults().get(0).getData());

      // flink sql
      result = session.execute("ssql", "show tables");
      System.out.println("Flink tables: " + result.getResults().get(0).getData());

      // flink invalid sql
      result = session.execute("bsql", "select * from unknown_table");
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
