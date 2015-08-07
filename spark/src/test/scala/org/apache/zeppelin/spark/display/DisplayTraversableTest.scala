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
package org.apache.zeppelin.spark.display

import java.io.{PrintStream, ByteArrayOutputStream}
import java.util.Arrays.asList

import org.apache.zeppelin.context.ZeppelinContext
import org.apache.zeppelin.display.DisplayParams
import org.apache.zeppelin.interpreter.InterpreterException
import org.scalatest._
import org.scalatest.mock.MockitoSugar


class DisplayTraversableTest extends FlatSpec
  with BeforeAndAfter
  with BeforeAndAfterEach
  with Matchers
  with MockitoSugar {

  var testTuples: List[(String, String, Int)] = null
  var testPersons: List[Person] = null
  var z: ZeppelinContext = new ZeppelinContext(2)
  var stream: ByteArrayOutputStream = null
  var printStream: PrintStream = null

  before {
    testTuples = List(("jdoe", "John DOE", 32), ("hsue", "Helen SUE", 27), ("rsmith", "Richard SMITH", 45))
    testPersons = List(Person("jdoe", "John DOE", 32), Person("hsue", "Helen SUE", 27), Person("rsmith", "Richard SMITH", 45))
    z.registerDisplayFunction(new DisplayTraversable)
  }

  override def beforeEach(): Unit = {
    stream = new java.io.ByteArrayOutputStream()
    printStream = new PrintStream(stream)
  }

  "DisplayTraversable" should "generate correct column headers for tuples" in {
    z.display(testTuples, DisplayParams(100, printStream, null, asList("Login","Name","Age")))

    stream.toString("UTF-8") should be("%table Login\tName\tAge\n" +
      "jdoe\tJohn DOE\t32\n" +
      "hsue\tHelen SUE\t27\n" +
      "rsmith\tRichard SMITH\t45\n")
  }

  "DisplayTraversable" should "generate correct column headers for case class" in {
    z.display(testPersons, DisplayParams(100, printStream, null, asList("Login","Name","Age")))

    stream.toString("UTF-8") should be("%table Login\tName\tAge\n" +
      "jdoe\tJohn DOE\t32\n" +
      "hsue\tHelen SUE\t27\n" +
      "rsmith\tRichard SMITH\t45\n")
  }

  "DisplayTraversable" should "truncate exceeding column headers for tuples" in {
    z.display(testTuples, DisplayParams(100, printStream, null, asList("Login","Name","Age","xxx","yyy")))

    stream.toString("UTF-8") should be("%table Login\tName\tAge\n" +
      "jdoe\tJohn DOE\t32\n" +
      "hsue\tHelen SUE\t27\n" +
      "rsmith\tRichard SMITH\t45\n")
  }

  "DisplayTraversable" should "pad missing column headers with ColumnXXX for tuples" in {
    z.display(testTuples, DisplayParams(100, printStream, null, asList("Login")))

    stream.toString("UTF-8") should be("%table Login\tColumn2\tColumn3\n" +
      "jdoe\tJohn DOE\t32\n" +
      "hsue\tHelen SUE\t27\n" +
      "rsmith\tRichard SMITH\t45\n")
  }

  "DisplayTraversable" should "display tuples with limit" in {
    z.display(testTuples, DisplayParams(2, printStream, null, asList("Login","Name","Age")))

    stream.toString("UTF-8") should be("%table Login\tName\tAge\n" +
      "jdoe\tJohn DOE\t32\n" +
      "hsue\tHelen SUE\t27\n")
  }


  "DisplayTraversable" should "refuse to display non Product collection" in {
    val exception = intercept[InterpreterException] {
      z.display(List[String]("a","b", "c"), DisplayParams(100, printStream, null, asList("Value")))
    }
    exception.getMessage should be ("a should be an instance of scala.Product (case class or tuple)")
  }

  "DisplayTraversable" should "exception when displaying mixed collection with Product and non Product" in {
    val exception = intercept[InterpreterException] {
      z.display(List(("a","b"), 1, "c"), DisplayParams(100, printStream, null, asList("Value")))
    }
    exception.getMessage should be ("1 should be an instance of scala.Product (case class or tuple)")
  }
}