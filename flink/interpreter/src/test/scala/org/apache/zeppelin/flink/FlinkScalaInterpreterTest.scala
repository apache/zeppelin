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

package org.apache.zeppelin.flink


import java.util.Properties

import org.junit.Assert.assertEquals
import org.scalatest.FunSuite

class FlinkScalaInterpreterTest extends FunSuite {

  test("testReplaceYarnAddress") {
    val flinkScalaInterpreter = new FlinkScalaInterpreter(new Properties())
    var targetURL = flinkScalaInterpreter.replaceYarnAddress("http://localhost:8081",
      "http://my-server:9090/gateway")
    assertEquals("http://my-server:9090/gateway", targetURL)

    targetURL = flinkScalaInterpreter.replaceYarnAddress("https://localhost:8081/",
      "https://my-server:9090/gateway")
    assertEquals("https://my-server:9090/gateway/", targetURL)

    targetURL = flinkScalaInterpreter.replaceYarnAddress("https://localhost:8081/proxy/app_1",
      "https://my-server:9090/gateway")
    assertEquals("https://my-server:9090/gateway/proxy/app_1", targetURL)
  }
}
