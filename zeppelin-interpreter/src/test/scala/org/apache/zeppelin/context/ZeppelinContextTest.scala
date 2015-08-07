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

package org.apache.zeppelin.context

import java.io.{PrintStream, ByteArrayOutputStream}
import java.util.concurrent.atomic.AtomicBoolean


import collection.JavaConversions._
import org.apache.zeppelin.display._
import org.apache.zeppelin.display.Input.ParamOption
import org.apache.zeppelin.interpreter.{InterpreterException, InterpreterContextRunner, InterpreterContext}
import org.mockito.Mockito.when
import org.scalatest._
import org.scalatest.mock.MockitoSugar

class ZeppelinContextTest extends FlatSpec
  with BeforeAndAfter
  with BeforeAndAfterEach
  with Matchers
  with MockitoSugar {
  
  var stream: ByteArrayOutputStream = null
  var registry: AngularObjectRegistry = null
  val interpreterContext: InterpreterContext = mock[InterpreterContext]
  val gui: GUI = mock[GUI]
  val out: PrintStream = mock[PrintStream]
  val noteId: String = "myNoteId"
  var zeppelinContext: ZeppelinContext = null

  override def beforeEach() {
    stream = new java.io.ByteArrayOutputStream()
    registry = new AngularObjectRegistry("int1", new AngularListener)
    when(interpreterContext.getAngularObjectRegistry).thenReturn(registry:AngularObjectRegistry)
    when(interpreterContext.getNoteId).thenReturn(noteId)
    zeppelinContext = new ZeppelinContext(1000)
    zeppelinContext.setInterpreterContext(interpreterContext)
    super.beforeEach() // To be stackable, must call super.beforeEach
  }

  "ZeppelinContext" should "display HTML" in {
    zeppelinContext.html() should be ("%html ")
    zeppelinContext.html("test") should be ("%html test")
  }

  "ZeppelinContext" should "display img" in {
    zeppelinContext.img("http://www.google.com") should be ("<img src='http://www.google.com' />")
    zeppelinContext.img64() should be ("%img ")
    zeppelinContext.img64("abcde") should be ("%img abcde")
  }

  "ZeppelinContext" should "add input to GUI with default value" in {
    //Given
    zeppelinContext.setGui(gui)
    when(gui.input("test input", "default")).thenReturn("defaultVal", Nil:_*)

    //When
    val actual: Any = zeppelinContext.input("test input", "default")

    //Then
    actual should be("defaultVal")

  }

  "ZeppelinContext" should "add select to GUI with default value" in {
    //Given
    zeppelinContext.setGui(gui)
    val paramOptions: Array[ParamOption] = Seq(new ParamOption(1,"1"),new ParamOption(2,"2")).toArray
    when(gui.select("test select", "1",paramOptions)).thenReturn("1", Nil:_*)

    //When
    val seq: Seq[(Any, String)] = Seq((1, "1"), (2, "2"))
    val actual: Any = zeppelinContext.select("test select", "1",seq)

    //Then
    actual should be("1")
  }

  "ZeppelinContext" should "run paragraph by id" in {
    //Given
    val hasRun1 = new AtomicBoolean(false)
    val hasRun2 = new AtomicBoolean(false)
    val runner1:InterpreterContextRunner = new InterpreterContextRunner("1", "par1") {
      override def run(): Unit = {hasRun1.getAndSet(true) }
    }
    val runner2:InterpreterContextRunner = new InterpreterContextRunner("2", "par2") {
      override def run(): Unit = {hasRun2.getAndSet(true)}
    }
    when(interpreterContext.getRunners()).thenReturn(Seq(runner1,runner2),Nil:_*)

    //When
    zeppelinContext.run("par1", interpreterContext)

    //Then
    hasRun1.get should be(true)
    hasRun2.get should be(false)
  }

  "ZeppelinContext" should "run paragraph by index" in {
    //Given
    val hasRun1 = new AtomicBoolean(false)
    val hasRun2 = new AtomicBoolean(false)
    val runner1:InterpreterContextRunner = new InterpreterContextRunner("1", "par1") {
      override def run(): Unit = {hasRun1.getAndSet(true) }
    }
    val runner2:InterpreterContextRunner = new InterpreterContextRunner("2", "par2") {
      override def run(): Unit = {hasRun2.getAndSet(true)}
    }
    when(interpreterContext.getParagraphId).thenReturn("whatever",Nil:_*)
    when(interpreterContext.getRunners()).thenReturn(Seq(runner1,runner2),null)

    //When
    zeppelinContext.run(0, interpreterContext)

    //Then
    hasRun1.get should be(true)
    hasRun2.get should be(false)
  }

  "ZeppelinContext" should "not run current paragraph" in {
    //Given
    val hasRun1 = new AtomicBoolean(false)
    val hasRun2 = new AtomicBoolean(false)
    val runner1:InterpreterContextRunner = new InterpreterContextRunner("1", "par1") {
      override def run(): Unit = {hasRun1.getAndSet(true) }
    }
    val runner2:InterpreterContextRunner = new InterpreterContextRunner("2", "par2") {
      override def run(): Unit = {hasRun2.getAndSet(true)}
    }
    when(interpreterContext.getParagraphId).thenReturn("par1",Nil:_*)
    when(interpreterContext.getRunners()).thenReturn(Seq(runner1,runner2),null)

    //When
    intercept[InterpreterException] {
      zeppelinContext.run(0, interpreterContext)
    }

    //Then
    hasRun1.get should be(false)
    hasRun2.get should be(false)
  }

  "ZeppelinContext" should "run paragraphs by index and id" in {
    //Given
    val hasRun1 = new AtomicBoolean(false)
    val hasRun2 = new AtomicBoolean(false)
    val hasRun3 = new AtomicBoolean(false)
    val runner1:InterpreterContextRunner = new InterpreterContextRunner("1", "par1") {
      override def run(): Unit = {hasRun1.getAndSet(true) }
    }
    val runner2:InterpreterContextRunner = new InterpreterContextRunner("2", "par2") {
      override def run(): Unit = {hasRun2.getAndSet(true)}
    }
    val runner3:InterpreterContextRunner = new InterpreterContextRunner("3", "par3") {
      override def run(): Unit = {hasRun3.getAndSet(true)}
    }
    when(interpreterContext.getParagraphId).thenReturn("par10",Nil:_*)
    when(interpreterContext.getRunners()).thenReturn(Seq(runner1,runner2,runner3): java.util.List[InterpreterContextRunner])

    //When
    zeppelinContext.run(List("par1",new Integer(1),"par3"):List[Any], interpreterContext)

    //Then
    hasRun1.get should be(true)
    hasRun2.get should be(true)
    hasRun3.get should be(true)
  }

  "ZeppelinContext" should "run all paragraphs except the current" in {
    //Given
    val hasRun1 = new AtomicBoolean(false)
    val hasRun2 = new AtomicBoolean(false)
    val hasRun3 = new AtomicBoolean(false)
    val runner1:InterpreterContextRunner = new InterpreterContextRunner("1", "par1") {
      override def run(): Unit = {hasRun1.getAndSet(true) }
    }
    val runner2:InterpreterContextRunner = new InterpreterContextRunner("2", "par2") {
      override def run(): Unit = {hasRun2.getAndSet(true)}
    }
    val runner3:InterpreterContextRunner = new InterpreterContextRunner("3", "par3") {
      override def run(): Unit = {hasRun3.getAndSet(true)}
    }
    when(interpreterContext.getParagraphId).thenReturn("par1",Nil:_*)
    when(interpreterContext.getRunners()).thenReturn(Seq(runner1,runner2,runner3): java.util.List[InterpreterContextRunner])

    //When
    zeppelinContext.runAll(interpreterContext)

    //Then
    hasRun1.get should be(false)
    hasRun2.get should be(true)
    hasRun3.get should be(true)
  }

  "ZeppelinContext" should "list paragraph ids" in {
    //Given
    val runner1:InterpreterContextRunner = new InterpreterContextRunner("1", "par1") {
      override def run(): Unit = {}
    }
    val runner2:InterpreterContextRunner = new InterpreterContextRunner("2", "par2") {
      override def run(): Unit = {}
    }
    val runner3:InterpreterContextRunner = new InterpreterContextRunner("3", "par3") {
      override def run(): Unit = {}
    }
    when(interpreterContext.getRunners()).thenReturn(Seq(runner1,runner2,runner3): java.util.List[InterpreterContextRunner])

    //When
    val actual: List[String] = zeppelinContext.listParagraphs

    //Then
    actual should be(List("par1", "par2", "par3"))
  }

  "ZeppelinContext" should "fetch Angular object by name" in {
    //Given
    registry.add("name1", "val", noteId)

    //When
    val actual = zeppelinContext.angular("name1")

    //Then
    actual should be("val")
  }

  "ZeppelinContext" should "fetch null if no Angular object found" in {
    //Given

    //When
    val actual = zeppelinContext.angular("name2")

    //Then
    assert(actual == null)
  }

  "ZeppelinContext" should "bind Angular object by name" in {
    //Given

    //When
    zeppelinContext.angularBind("name3", "value")

    //Then
    registry.get("name3", noteId).get() should be("value")
  }

  "ZeppelinContext" should "update bound Angular object by name with new value" in {
    //Given
    registry.add("name4", "val1", noteId)

    //When
    zeppelinContext.angularBind("name4", "val2")

    //Then
    registry.get("name4", noteId).get() should be("val2")
  }

  "ZeppelinContext" should "unbind Angular object by name" in {
    //Given
    registry.add("name5", "val1", noteId)

    //When
    zeppelinContext.angularUnbind("name5")

    //Then
    assert(registry.get("name5", noteId) == null )
  }

  "ZeppelinContext" should "add watch to Angular object by name" in {
    //Given
    registry.add("name6", "val1", noteId)

    val hasChanged = new AtomicBoolean(false)
    val watcher = new AngularObjectWatcher(interpreterContext) {
      override def watch(oldObject: scala.Any, newObject: scala.Any, context: InterpreterContext): Unit = {
        hasChanged.getAndSet(true)
      }
    }
    zeppelinContext.angularWatch("name6", watcher)

    //When
    zeppelinContext.angularBind("name6", "val2")

    //Then
    registry.get("name6", noteId).get() should be("val2")

    //Wait for the update to be effective
    java.lang.Thread.sleep(100)

    hasChanged.get() should be(true)
  }

  "ZeppelinContext" should "add watch to Angular object by name with anonymous function" in {
    //Given
    registry.add("name7", "val1", noteId)

    val hasChanged = new AtomicBoolean(false)
    zeppelinContext.angularWatch("name7", (oldObject: scala.Any, newObject: scala.Any) => {
      hasChanged.getAndSet(true)
    })

    //When
    zeppelinContext.angularBind("name7", "val2")

    //Then
    registry.get("name7", noteId).get() should be("val2")
    hasChanged.get() should be(true)
  }

  "ZeppelinContext" should "stop watching an Angular object using a given watcher" in {
    //Given
    registry.add("name8", "val1", noteId)
    val hasChanged = new AtomicBoolean(false)
    val watcher = new AngularObjectWatcher(interpreterContext) {
      override def watch(oldObject: scala.Any, newObject: scala.Any, context: InterpreterContext): Unit = {
        hasChanged.getAndSet(true)
      }
    }

    zeppelinContext.angularWatch("name8", watcher)

    //When
    zeppelinContext.angularUnwatch("name8", watcher)
    zeppelinContext.angularBind("name8", "val2")

    //Then
    registry.get("name8", noteId).get() should be("val2")
    hasChanged.get() should be(false)
  }

  "ZeppelinContext" should "stop watching an Angular object for all watchers" in {
    //Given
    registry.add("name9", "val1", noteId)
    val hasChanged1 = new AtomicBoolean(false)
    val hasChanged2 = new AtomicBoolean(false)
    val watcher1 = new AngularObjectWatcher(interpreterContext) {
      override def watch(oldObject: scala.Any, newObject: scala.Any, context: InterpreterContext): Unit = {
        hasChanged1.getAndSet(true)
      }
    }
    val watcher2 = new AngularObjectWatcher(interpreterContext) {
      override def watch(oldObject: scala.Any, newObject: scala.Any, context: InterpreterContext): Unit = {
        hasChanged1.getAndSet(true)
      }
    }

    zeppelinContext.angularWatch("name9", watcher1)
    zeppelinContext.angularWatch("name9", watcher2)

    //When
    zeppelinContext.angularUnwatch("name9")
    zeppelinContext.angularBind("name9", "val2")

    //Then
    registry.get("name9", noteId).get() should be("val2")
    hasChanged1.get() should be(false)
    hasChanged2.get() should be(false)
  }

  override def afterEach() {
    try super.afterEach() // To be stackable, must call super.afterEach
    stream = null
  }
}

class AngularListener extends AngularObjectRegistryListener {
  override def onAdd(interpreterGroupId: String, `object`: AngularObject[_]): Unit = {}

  override def onUpdate(interpreterGroupId: String, `object`: AngularObject[_]): Unit = {}

  override def onRemove(interpreterGroupId: String, name: String, noteId: String): Unit = {}
}
