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


package org.apache.zeppelin.rinterpreter

import java.util.Properties

import org.apache.zeppelin.RTest
import org.apache.zeppelin.rinterpreter.rscala.RException
import org.apache.zeppelin.spark.SparkInterpreter
import org.scalatest.Matchers._
import org.scalatest._

class RContextTest extends FlatSpec {
  RContext.resetRcon()
  
  val rcon = RContext(new Properties(), "test")
  
  "The RContext Singleton" should "create an RContext without Spark" in { () =>
    rcon shouldBe a[RContext]
  }
  
  "The RContext" should "be openable without spark" in { () =>
    rcon.open(None)
    assert(rcon.isOpen)
  }

  it should "be able to confirm that stats is available" taggedAs(RTest) in { () =>
    assertResult(true) {
      rcon.testRPackage("stats")
    }
  }

  it should "be able to confirm that a bogus package is not available"  taggedAs(RTest) in { () =>
    assertResult(false) {
      rcon.testRPackage("thisisagarbagepackagename")
    }
  }

  it should "be able to add 2 + 2"  taggedAs(RTest) in { () =>
    assertResult(4) {
      rcon.evalI0("2 + 2")
    }
  }
  it should "be able to return a vector"  taggedAs(RTest) in { () =>
    assertResult(10) {
      rcon.evalI1("1:10").length
    }
  }
  it should "be able to return a string"  taggedAs(RTest) in { () =>
    
    assertResult("hello world") {
      rcon.evalS0("'hello world'")
    }
  }
  it should "be able to return a vector of strings"  taggedAs(RTest)  in { () =>
    
    assertResult(26) {
      rcon.evalS1("LETTERS").length
    }
  }

  it should "throw an RException if told to evaluate garbage code"  taggedAs(RTest)  in { () =>
    
    intercept[RException] {
      rcon.eval("funkyfunction()")
    }
  }

//  it should "Throw an exception if we try to initialize SparkR without a SQLContext" in {() =>
//
//    intercept[RuntimeException] {
//      rcon.initializeSparkRTest()
//    }
//  }

  it should "have rzeppelin available"  taggedAs(RTest) in { () =>
    
    assertResult(true) {
      rcon.testRPackage("rzeppelin")
    }
  }
  it should "have evaluate available"  taggedAs(RTest) in { () =>
    
    assertResult(true) {
      rcon.testRPackage("evaluate")
    }
  }
  it should "have repr available"  taggedAs(RTest) in { () =>
    
    assertResult(true) {
      rcon.testRPackage("repr")
    }
  }
  it should "also close politely"  taggedAs(RTest) in { () =>
    
    rcon.close()
    assertResult(2) {rcon.isOpen}
  }
}
