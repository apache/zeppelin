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

package org.apache.spark.api.r

import org.scalatest.FlatSpec
import org.scalatest.Matchers._

class RBackendHelperTest extends FlatSpec {

  val backend : RBackendHelper = RBackendHelper()
  val backend2 : RBackendHelper = RBackendHelper()

  "RBackendHelper" should "create a SparkR backend" in {
    val rbackend = backend
    assert(true) // only looking for exceptions here
  }

  it should "initialize properly, returning a port > 0" in {
    val port = backend.init()
    assert(port > 0)
  }

  it should "start a thread" in {
    val backend = backend2
    backend.init()
    val thread = backend.start()
    thread shouldBe a [Thread]
  }
  
  it should "close without error" in {
    backend2.close
    assert(true) // only looking for exceptions
  }
}
