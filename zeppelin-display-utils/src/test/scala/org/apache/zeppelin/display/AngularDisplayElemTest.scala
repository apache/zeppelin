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
package org.apache.zeppelin.display

import java.io.{PrintStream, ByteArrayOutputStream}
import java.util

import org.apache.zeppelin.interpreter.{InterpreterContextRunner, InterpreterContext, InterpreterGroup}
import org.scalatest.concurrent.Eventually
import org.scalatest.{Matchers, BeforeAndAfterEach, BeforeAndAfter, FlatSpec}

/**
  * Test
  */
class AngularDisplayElemTest
  extends FlatSpec with BeforeAndAfter with BeforeAndAfterEach with Eventually with Matchers {
  import AngularDisplayElem._

  override def beforeEach() {
    val intpGroup = new InterpreterGroup()
    val context = new InterpreterContext("note", "id", "title", "text",
      new util.HashMap[String, Object](), new GUI(), new AngularObjectRegistry(
        intpGroup.getId(), null),
      new util.LinkedList[InterpreterContextRunner]())

    InterpreterContext.set(context)
    super.beforeEach() // To be stackable, must call super.beforeEach
  }

  "DisplayUtilsElement" should "provide onclick method" in {
    registry.getAll("note").size() should be(0)

    var a = 0
    val elem = <div></div>.onClick(() => {
      a = a + 1
    })

    registry.getAll("note").size() should be(1)

    // click create thread for callback function to run. So it'll may not immediately invoked
    // after click. therefore eventually should be
    click(elem)
    eventually {
      a should be(1)
    }

    click(elem)
    eventually {
      a should be(2)
    }

    // disassociate
    elem.disassociate()
    registry.getAll("note").size() should be(0)
  }

  "display()" should "print angular display directive only once in a paragraph" in {
    val out = new ByteArrayOutputStream()
    val printOut = new PrintStream(out)

    <div></div>.display(printOut)
    out.toString should be("%angular <div></div>")

    out.reset
    <div></div>.display(printOut)
    out.toString should be("<div></div>")
  }

  def registry = {
    InterpreterContext.get().getAngularObjectRegistry
  }

  def click(elem: AngularDisplayElem) = {
    fireEvent("ng-click", elem)
  }

  // simulate click
  def fireEvent(eventName: String, elem: AngularDisplayElem) = {
    val angularObject:AngularObject[Any] = elem.angularObjects(eventName);
    angularObject.set("event");
  }
}
