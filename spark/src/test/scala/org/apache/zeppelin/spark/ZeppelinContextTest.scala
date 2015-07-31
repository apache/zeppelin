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

package org.apache.zeppelin.spark

import java.io.{PrintStream, ByteArrayOutputStream}
import java.util.concurrent.atomic.AtomicBoolean


import collection.JavaConversions._
import org.apache.spark.sql.SQLContext
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD
import org.apache.zeppelin.display._
import org.apache.zeppelin.display.Input.ParamOption
import org.apache.zeppelin.interpreter.{InterpreterException, InterpreterContextRunner, InterpreterContext}
import org.apache.zeppelin.spark.dep.DependencyResolver
import org.mockito.Mockito.when
import org.scalatest._
import org.scalatest.mock.MockitoSugar

case class Person(login : String, name: String, age: Int)

class ZeppelinContextTest extends FlatSpec
  with BeforeAndAfter
  with BeforeAndAfterEach
  with Matchers
  with MockitoSugar {

  var sc: SparkContext = null
  var testTuples:List[(String, String, Int)] = null
  var testPersons:List[Person] = null
  var testRDDTuples: RDD[(String,String,Int)]  = null
  var testRDDPersons: RDD[Person]  = null
  var stream: ByteArrayOutputStream = null
  var registry: AngularObjectRegistry = null
  val sqlContext: SQLContext = mock[SQLContext]
  val interpreterContext: InterpreterContext = mock[InterpreterContext]
  val dep: DependencyResolver = mock[DependencyResolver]
  val gui: GUI = mock[GUI]
  val out: PrintStream = mock[PrintStream]
  val noteId: String = "myNoteId"


  before {
    val sparkConf: SparkConf = new SparkConf(true)
      .setAppName("test-DisplayFunctions")
      .setMaster("local")
    sc = new SparkContext(sparkConf)
    testTuples = List(("jdoe", "John DOE", 32), ("hsue", "Helen SUE", 27), ("rsmith", "Richard SMITH", 45))
    testRDDTuples = sc.parallelize(testTuples)
    testPersons = List(Person("jdoe", "John DOE", 32), Person("hsue", "Helen SUE", 27), Person("rsmith", "Richard SMITH", 45))
    testRDDPersons = sc.parallelize(testPersons)
    registry = new AngularObjectRegistry("int1", new AngularListener)
    when(interpreterContext.getAngularObjectRegistry).thenReturn(registry:AngularObjectRegistry)
  }

  override def beforeEach() {
    stream = new java.io.ByteArrayOutputStream()
    when(interpreterContext.getNoteId).thenReturn(noteId)
    super.beforeEach() // To be stackable, must call super.beforeEach
  }


  "DisplayFunctions" should "generate correct column headers for tuples" in {
    implicit val sparkMaxResult = new SparkMaxResult(100)
    Console.withOut(stream) {
      new DisplayRDDFunctions[(String,String,Int)](testRDDTuples).display("Login","Name","Age")
    }

    stream.toString("UTF-8") should be("%table Login\tName\tAge\n" +
      "jdoe\tJohn DOE\t32\n" +
      "hsue\tHelen SUE\t27\n" +
      "rsmith\tRichard SMITH\t45\n")
  }

  "DisplayFunctions" should "generate correct column headers for case class" in {
    implicit val sparkMaxResult = new SparkMaxResult(100)
    Console.withOut(stream) {
      new DisplayRDDFunctions[Person](testRDDPersons).display("Login","Name","Age")
    }

    stream.toString("UTF-8") should be("%table Login\tName\tAge\n" +
      "jdoe\tJohn DOE\t32\n" +
      "hsue\tHelen SUE\t27\n" +
      "rsmith\tRichard SMITH\t45\n")
  }

  "DisplayFunctions" should "truncate exceeding column headers for tuples" in {
    implicit val sparkMaxResult = new SparkMaxResult(100)
    Console.withOut(stream) {
      new DisplayRDDFunctions[(String,String,Int)](testRDDTuples).display("Login","Name","Age","xxx","yyy")
    }

    stream.toString("UTF-8") should be("%table Login\tName\tAge\n" +
      "jdoe\tJohn DOE\t32\n" +
      "hsue\tHelen SUE\t27\n" +
      "rsmith\tRichard SMITH\t45\n")
  }

  "DisplayFunctions" should "pad missing column headers with ColumnXXX for tuples" in {
    implicit val sparkMaxResult = new SparkMaxResult(100)
    Console.withOut(stream) {
      new DisplayRDDFunctions[(String,String,Int)](testRDDTuples).display("Login")
    }

    stream.toString("UTF-8") should be("%table Login\tColumn2\tColumn3\n" +
      "jdoe\tJohn DOE\t32\n" +
      "hsue\tHelen SUE\t27\n" +
      "rsmith\tRichard SMITH\t45\n")
  }

  "DisplayUtils" should "restricts RDD to sparkMaxresult with implicit limit" in {

    implicit val sparkMaxResult = new SparkMaxResult(2)

    Console.withOut(stream) {
      new DisplayRDDFunctions[(String,String,Int)](testRDDTuples).display("Login")
    }

    stream.toString("UTF-8") should be("%table Login\tColumn2\tColumn3\n" +
      "jdoe\tJohn DOE\t32\n" +
      "hsue\tHelen SUE\t27\n")
  }

  "DisplayUtils" should "restricts RDD to sparkMaxresult with explicit limit" in {

    implicit val sparkMaxResult = new SparkMaxResult(2)

    Console.withOut(stream) {
      new DisplayRDDFunctions[(String,String,Int)](testRDDTuples).display(1,"Login")
    }

    stream.toString("UTF-8") should be("%table Login\tColumn2\tColumn3\n" +
      "jdoe\tJohn DOE\t32\n")
  }

  "DisplayFunctions" should "display traversable of tuples" in {

    Console.withOut(stream) {
      new DisplayTraversableFunctions[(String,String,Int)](testTuples).display("Login","Name","Age")
    }

    stream.toString("UTF-8") should be("%table Login\tName\tAge\n" +
      "jdoe\tJohn DOE\t32\n" +
      "hsue\tHelen SUE\t27\n" +
      "rsmith\tRichard SMITH\t45\n")
  }

  "DisplayFunctions" should "display traversable of case class" in {

    Console.withOut(stream) {
      new DisplayTraversableFunctions[Person](testPersons).display("Login","Name","Age")
    }

    stream.toString("UTF-8") should be("%table Login\tName\tAge\n" +
      "jdoe\tJohn DOE\t32\n" +
      "hsue\tHelen SUE\t27\n" +
      "rsmith\tRichard SMITH\t45\n")
  }

  "DisplayUtils" should "display HTML" in {
    ZeppelinContext.html() should be ("%html ")
    ZeppelinContext.html("test") should be ("%html test")
  }

  "DisplayUtils" should "display img" in {
    ZeppelinContext.img("http://www.google.com") should be ("<img src='http://www.google.com' />")
    ZeppelinContext.img64() should be ("%img ")
    ZeppelinContext.img64("abcde") should be ("%img abcde")
  }

  "ZeppelinContext" should "add input to GUI with default value" in {
    //Given
    val z = new ZeppelinContext(sc, sqlContext, interpreterContext, dep, out, 100)
    z.setGui(gui)
    when(gui.input("test input", "default")).thenReturn("defaultVal", Nil:_*)

    //When
    val actual: Any = z.input("test input", "default")

    //Then
    actual should be("defaultVal")

  }

  "ZeppelinContext" should "add select to GUI with default value" in {
    //Given
    val z = new ZeppelinContext(sc, sqlContext, interpreterContext, dep, out, 100)
    z.setGui(gui)
    val paramOptions: Array[ParamOption] = Seq(new ParamOption(1,"1"),new ParamOption(2,"2")).toArray
    when(gui.select("test select", "1",paramOptions)).thenReturn("1", Nil:_*)

    //When
    val seq: Seq[(Any, String)] = Seq((1, "1"), (2, "2"))
    val actual: Any = z.select("test select", "1",seq)

    //Then
    actual should be("1")
  }

  "ZeppelinContext" should "run paragraph by id" in {
    //Given
    val z = new ZeppelinContext(sc, sqlContext, interpreterContext, dep, out, 100)
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
    z.run("par1", interpreterContext)

    //Then
    hasRun1.get should be(true)
    hasRun2.get should be(false)
  }

  "ZeppelinContext" should "run paragraph by index" in {
    //Given
    val z = new ZeppelinContext(sc, sqlContext, interpreterContext, dep, out, 100)
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
    z.run(0, interpreterContext)

    //Then
    hasRun1.get should be(true)
    hasRun2.get should be(false)
  }

  "ZeppelinContext" should "not run current paragraph" in {
    //Given
    val z = new ZeppelinContext(sc, sqlContext, interpreterContext, dep, out, 100)
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
      z.run(0, interpreterContext)
    }

    //Then
    hasRun1.get should be(false)
    hasRun2.get should be(false)
  }

  "ZeppelinContext" should "run paragraphs by index and id" in {
    //Given
    val z = new ZeppelinContext(sc, sqlContext, interpreterContext, dep, out, 100)
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
    z.run(List("par1",new Integer(1),"par3"):List[Any], interpreterContext)

    //Then
    hasRun1.get should be(true)
    hasRun2.get should be(true)
    hasRun3.get should be(true)
  }

  "ZeppelinContext" should "run all paragraphs except the current" in {
    //Given
    val z = new ZeppelinContext(sc, sqlContext, interpreterContext, dep, out, 100)
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
    z.runAll(interpreterContext)

    //Then
    hasRun1.get should be(false)
    hasRun2.get should be(true)
    hasRun3.get should be(true)
  }

  "ZeppelinContext" should "list paragraph ids" in {
    //Given
    val z = new ZeppelinContext(sc, sqlContext, interpreterContext, dep, out, 100)
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
    val actual: List[String] = z.listParagraphs

    //Then
    actual should be(List("par1", "par2", "par3"))
  }

  "ZeppelinContext" should "fetch Angular object by name" in {
    //Given
    val z = new ZeppelinContext(sc, sqlContext, interpreterContext, dep, out, 100)

    registry.add("name1", "val", noteId)

    //When
    val actual = z.angular("name1")

    //Then
    actual should be("val")
  }

  "ZeppelinContext" should "fetch null if no Angular object found" in {
    //Given
    val z = new ZeppelinContext(sc, sqlContext, interpreterContext, dep, out, 100)

    //When
    val actual = z.angular("name2")

    //Then
    assert(actual == null)
  }

  "ZeppelinContext" should "bind Angular object by name" in {
    //Given
    val z = new ZeppelinContext(sc, sqlContext, interpreterContext, dep, out, 100)

    //When
    z.angularBind("name3", "value")

    //Then
    registry.get("name3", noteId).get() should be("value")
  }

  "ZeppelinContext" should "update bound Angular object by name with new value" in {
    //Given
    val z = new ZeppelinContext(sc, sqlContext, interpreterContext, dep, out, 100)
    registry.add("name4", "val1", noteId)

    //When
    z.angularBind("name4", "val2")

    //Then
    registry.get("name4", noteId).get() should be("val2")
  }

  "ZeppelinContext" should "unbind Angular object by name" in {
    //Given
    val z = new ZeppelinContext(sc, sqlContext, interpreterContext, dep, out, 100)
    registry.add("name5", "val1", noteId)

    //When
    z.angularUnbind("name5")

    //Then
    assert(registry.get("name5", noteId) == null )
  }

  "ZeppelinContext" should "add watch to Angular object by name" in {
    //Given
    val z = new ZeppelinContext(sc, sqlContext, interpreterContext, dep, out, 100)
    registry.add("name6", "val1", noteId)

    val hasChanged = new AtomicBoolean(false)
    val watcher = new AngularObjectWatcher(interpreterContext) {
      override def watch(oldObject: scala.Any, newObject: scala.Any, context: InterpreterContext): Unit = {
        hasChanged.getAndSet(true)
      }
    }
    z.angularWatch("name6", watcher)

    //When
    z.angularBind("name6", "val2")

    //Then
    registry.get("name6", noteId).get() should be("val2")

    //Wait for the update to be effective
    java.lang.Thread.sleep(100)

    hasChanged.get() should be(true)
  }

  "ZeppelinContext" should "add watch to Angular object by name with anonymous function" in {
    //Given
    val z = new ZeppelinContext(sc, sqlContext, interpreterContext, dep, out, 100)
    registry.add("name7", "val1", noteId)

    val hasChanged = new AtomicBoolean(false)
    z.angularWatch("name7", (oldObject: scala.Any, newObject: scala.Any) => {
      hasChanged.getAndSet(true)
    })

    //When
    z.angularBind("name7", "val2")

    //Then
    registry.get("name7", noteId).get() should be("val2")
    hasChanged.get() should be(true)
  }

  "ZeppelinContext" should "stop watching an Angular object using a given watcher" in {
    //Given
    val z = new ZeppelinContext(sc, sqlContext, interpreterContext, dep, out, 100)
    registry.add("name8", "val1", noteId)
    val hasChanged = new AtomicBoolean(false)
    val watcher = new AngularObjectWatcher(interpreterContext) {
      override def watch(oldObject: scala.Any, newObject: scala.Any, context: InterpreterContext): Unit = {
        hasChanged.getAndSet(true)
      }
    }

    z.angularWatch("name8", watcher)

    //When
    z.angularUnwatch("name8", watcher)
    z.angularBind("name8", "val2")

    //Then
    registry.get("name8", noteId).get() should be("val2")
    hasChanged.get() should be(false)
  }

  "ZeppelinContext" should "stop watching an Angular object for all watchers" in {
    //Given
    val z = new ZeppelinContext(sc, sqlContext, interpreterContext, dep, out, 100)
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

    z.angularWatch("name9", watcher1)
    z.angularWatch("name9", watcher2)

    //When
    z.angularUnwatch("name9")
    z.angularBind("name9", "val2")

    //Then
    registry.get("name9", noteId).get() should be("val2")
    hasChanged1.get() should be(false)
    hasChanged2.get() should be(false)
  }

  override def afterEach() {
    try super.afterEach() // To be stackable, must call super.afterEach
    stream = null
  }

  after {
    sc.stop()
  }
}

class AngularListener extends AngularObjectRegistryListener {
  override def onAdd(interpreterGroupId: String, `object`: AngularObject[_]): Unit = {}

  override def onUpdate(interpreterGroupId: String, `object`: AngularObject[_]): Unit = {}

  override def onRemove(interpreterGroupId: String, name: String, noteId: String): Unit = {}
}
