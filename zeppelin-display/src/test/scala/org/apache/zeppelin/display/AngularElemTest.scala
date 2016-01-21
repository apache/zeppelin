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

import org.apache.zeppelin.interpreter._
import org.scalatest.concurrent.Eventually
import org.scalatest.{Matchers, BeforeAndAfterEach, BeforeAndAfter, FlatSpec}

/**
  * Test
  */
class AngularElemTest
  extends FlatSpec with BeforeAndAfter with BeforeAndAfterEach with Eventually with Matchers {
  import AngularElem._

  override def beforeEach() {
    val intpGroup = new InterpreterGroup()
    val context = new InterpreterContext("note", "id", "title", "text",
      new util.HashMap[String, Object](), new GUI(), new AngularObjectRegistry(
        intpGroup.getId(), null),
      new util.LinkedList[InterpreterContextRunner](),
      new InterpreterOutput(new InterpreterOutputListener() {
        override def onAppend(out: InterpreterOutput, line: Array[Byte]): Unit = {
          // nothing to do
        }

        override def onUpdate(out: InterpreterOutput, output: Array[Byte]): Unit = {
          // nothing to do
        }
      }))

    InterpreterContext.set(context)
    super.beforeEach() // To be stackable, must call super.beforeEach
  }

  "AngularElem" should "provide onclick method" in {
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

  "AngularElem" should "print angular display directive only once in a paragraph" in {
    val out = new ByteArrayOutputStream()
    val printOut = new PrintStream(out)

    <div></div>.display(printOut)
    out.toString should be("%angular <div></div>")

    out.reset
    <div></div>.display(printOut)
    out.toString should be("<div></div>")
  }

  "AngularElem" should "bind angularObject to ng-model directive " in {
    <div></div>.model("name", "value").toString should be("<div ng-model=\"name\"></div>")
    <div></div>.model("name", "value").model() should be("value")
    <div></div>.model() should be(None)
  }

  "AngularElem" should "able to disassociate AngularObjects" in {
    val elem1 = <div></div>.model("name1", "value1")
    val elem2 = <div></div>.model("name2", "value2")
    val elem3 = <div></div>.model("name3", "value3")

    registrySize should be(3)

    elem1.disassociate()
    registrySize should be(2)

    AngularElem.disassociate()
    registrySize should be(0)
  }

  "AngularElem" should "allow access to InterpreterContext inside of callback function" in {
    AngularModel("name", "value")
    var modelValue = ""

    val elem = <div></div>.onClick(() =>
      modelValue = AngularModel("name")().toString
    )

    click(elem)

    eventually { modelValue should be("value")}
  }


  def registry = {
    InterpreterContext.get().getAngularObjectRegistry
  }

  def registrySize = {
    registry.getAll(noteId).size
  }

  def noteId = {
    InterpreterContext.get().getNoteId
  }

  def click(elem: AngularElem) = {
    fireEvent("ng-click", elem)
  }

  // simulate click
  def fireEvent(eventName: String, elem: AngularElem) = {
    val angularObject:AngularObject[Any] = elem.angularObjects(eventName);
    angularObject.set("event");
  }
}
