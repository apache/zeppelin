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
import org.apache.zeppelin.client.NoteResult;
import org.apache.zeppelin.client.ParagraphResult;
import org.apache.zeppelin.client.ZeppelinClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Basic example of running existing note via ZeppelinClient (low level api)
 *
 */
public class ZeppelinClientExample2 {

  public static void main(String[] args) throws Exception {
    ClientConfig clientConfig = new ClientConfig("http://localhost:8080");
    ZeppelinClient zClient = new ZeppelinClient(clientConfig);

    String zeppelinVersion = zClient.getVersion();
    System.out.println("Zeppelin version: " + zeppelinVersion);

    ParagraphResult paragraphResult = zClient.executeParagraph("2A94M5J1Z", "20150210-015259_1403135953");
    System.out.println("Execute the 1st spark tutorial paragraph, paragraph result: " + paragraphResult);

    paragraphResult = zClient.executeParagraph("2A94M5J1Z", "20150210-015302_1492795503");
    System.out.println("Execute the 2nd spark tutorial paragraph, paragraph result: " + paragraphResult);

    Map<String, String> parameters = new HashMap<>();
    parameters.put("maxAge", "40");
    paragraphResult = zClient.executeParagraph("2A94M5J1Z", "20150212-145404_867439529", parameters);
    System.out.println("Execute the 3rd spark tutorial paragraph, paragraph result: " + paragraphResult);

    parameters = new HashMap<>();
    parameters.put("marital", "married");
    paragraphResult = zClient.executeParagraph("2A94M5J1Z", "20150213-230422_1600658137", parameters);
    System.out.println("Execute the 4th spark tutorial paragraph, paragraph result: " + paragraphResult);
  }
}
