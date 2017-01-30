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

package org.apache.zeppelin.helium;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.*;

public class HeliumPackageTest {

  private Gson gson = new Gson();

  @Test
  public void parseSpellPackageInfo() {
    String exampleSpell = "{\n" +
        "  \"type\" : \"SPELL\",\n" +
        "  \"name\" : \"echo-spell\",\n" +
        "  \"description\" : \"'%echo' - return just what receive (example)\",\n" +
        "  \"artifact\" : \"./zeppelin-examples/zeppelin-example-spell-echo\",\n" +
        "  \"license\" : \"Apache-2.0\",\n" +
        "  \"icon\" : \"<i class='fa fa-repeat'></i>\",\n" +
        "  \"spell\": {\n" +
        "    \"magic\": \"%echo\",\n" +
        "    \"usage\": \"%echo <TEXT>\"\n" +
        "  }\n" +
        "}";

    HeliumPackage p = gson.fromJson(exampleSpell, HeliumPackage.class);
    assertEquals(p.getSpellInfo().getMagic(), "%echo");
    assertEquals(p.getSpellInfo().getUsage(), "%echo <TEXT>");
  }
}