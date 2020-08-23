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

public class PythonExample {

  public static void main(String[] args) {

    ZSession session = null;
    try {
      ClientConfig clientConfig = new ClientConfig("http://localhost:8080");
      Map<String, String> intpProperties = new HashMap<>();

      session = ZSession.builder()
              .setClientConfig(clientConfig)
              .setInterpreter("python")
              .setIntpProperties(intpProperties)
              .build();

      session.start(new SimpleMessageHandler());

      // single statement
      ExecuteResult result = session.execute("print('hello world')");
      System.out.println(result.getResults().get(0).getData());

      // multiple statement
      result = session.execute("print('hello world')\nprint('hello world2')");
      System.out.println(result.getResults().get(0).getData());

      // error output
      result = session.execute("1/0");
      System.out.println("Result status: " + result.getStatus() +
              ", data: " + result.getResults().get(0).getData());

      // matplotlib
      result = session.execute("ipython", "%matplotlib inline\n" +
              "import matplotlib.pyplot as plt\n" +
              "plt.plot([1,2,3,4])\n" +
              "plt.ylabel('some numbers')\n" +
              "plt.show()");
      System.out.println("Matplotlib result, type: " + result.getResults().get(0).getType() +
              ", data: " + result.getResults().get(0).getData());

      // show pandas dataframe
      result = session.execute("ipython", "import pandas as pd\n" +
              "df = pd.DataFrame({'name':['a','b','c'], 'count':[12,24,18]})\n" +
              "z.show(df)");
      System.out.println("Pandas dataframe result, type: " + result.getResults().get(0).getType() +
              ", data: " + result.getResults().get(0).getData());

      // streaming output
      result = session.execute("import time\n" +
              "for i in range(1,10):\n" +
              "    print(i)\n" +
              "    time.sleep(1)");
      System.out.println("Python streaming result, type: " + result.getResults().get(0).getType() +
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
