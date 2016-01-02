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
    var a = 0
    val elem = <div></div>.onClick(() => {
      a = a + 1
    })

    click(elem)

    // click create thread for callback function to run. So it'll may not immediately invoked
    // after click. therefore eventually should be
    eventually { a should be(1) }
  }

  def click(elem: AngularDisplayElem) = {
    fireEvent("ng-click", elem)
  }

  // simulate click
  def fireEvent(eventName: String, elem: AngularDisplayElem) = {
    val angularFunction = elem.angularFunctions(eventName);
    val angularObject = angularFunction.angularObject.asInstanceOf[AngularObject[Object]]
    angularObject.set("invoke")
  }
}
