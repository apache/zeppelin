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

import java.util
import java.util.Properties

import org.apache.zeppelin.interpreter.{Interpreter, InterpreterGroup, InterpreterResult}
import org.scalatest.FlatSpec
import java.util.List
import org.scalatest.Matchers._

class WrapperTest extends FlatSpec {
  RContext.resetRcon()

  val repl: RRepl = new RRepl(new Properties(), false)
  val group : InterpreterGroup = new InterpreterGroup()
  var lst = new util.LinkedList[Interpreter]()
  lst.add(repl)
  group.put(repl.getClassName(), lst)
  repl.setInterpreterGroup(group)

  "The R REPL" should "exist and be of the right class" in {

    repl shouldBe a[RRepl]
  }

  it should "Have a RRepl Interpreter inside" in {
    repl.getInnerInterpreter shouldBe a[RReplInterpreter]
  }
  val repi = repl.getInnerInterpreter.asInstanceOf[RReplInterpreter]

  it should "have a fresh rContext" in {
    assert(!repi.getrContext.isOpen)
  }

  val knitr: KnitR = new KnitR(new Properties(), false)
  lst = new util.LinkedList[Interpreter]()
  lst.add(knitr)
  group.put(knitr.getClassName(), lst)
  knitr.setInterpreterGroup(group)

  "The KnitR wrapper" should "exist and be of the right class" in {
    knitr shouldBe a[KnitR]
    }
    it should "have a KnitRInterpreter inside" in {
      knitr.getInnerInterpreter shouldBe a [KnitRInterpreter]
    }

  it should "share the RContext" in {
    knitr.getInnerInterpreter.asInstanceOf[KnitRInterpreter].getrContext should be theSameInstanceAs repi.getrContext
  }

  it should "open without error" in {
    knitr.open()
    assert(knitr.getInnerInterpreter.asInstanceOf[KnitRInterpreter].getrContext.isOpen)
  }

  it should "produce HTML in response to a simple query" in {
    val result = knitr.interpret(
      """
        |```{r}
        |2 + 2
        |```
      """.stripMargin, null)
    withClue(result.message().get(0).getData()) {
      result should have (
      'code (InterpreterResult.Code.SUCCESS)
      )
    }
  }

  it should "close properly" in {
    repi.getrContext.close()
    assertResult(false) {
      repi.getrContext.isOpen
    }
  }

  "Just in case there are two rContexts, the other one" should "close properly also" in {
    val rcon = knitr.getInnerInterpreter.asInstanceOf[KnitRInterpreter].getrContext
    rcon.close()
    assertResult(false) {
      rcon.isOpen
    }
  }

}
