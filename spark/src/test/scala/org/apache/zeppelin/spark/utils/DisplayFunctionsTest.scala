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

package org.apache.zeppelin.spark.utils

import java.io.ByteArrayOutputStream

import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}
import org.scalatest.{BeforeAndAfter, _}

case class Person(login : String, name: String, age: Int)

class DisplayFunctionsTest extends FlatSpec with BeforeAndAfter with BeforeAndAfterEach with Matchers {
  var sc: SparkContext = null
  var testRDDTuples: RDD[(String,String,Int)]  = null
  var testRDDPersons: RDD[Person]  = null
  var stream: ByteArrayOutputStream = null
  
  before {
    val sparkConf: SparkConf = new SparkConf(true)
      .setAppName("test-DisplayFunctions")
      .setMaster("local")
    sc = new SparkContext(sparkConf)
    testRDDTuples = sc.parallelize(List(("jdoe","John DOE",32),("hsue","Helen SUE",27),("rsmith","Richard SMITH",45)))
    testRDDPersons = sc.parallelize(List(Person("jdoe","John DOE",32),Person("hsue","Helen SUE",27),Person("rsmith","Richard SMITH",45)))
  }

  override def beforeEach() {
    stream = new java.io.ByteArrayOutputStream()
    super.beforeEach() // To be stackable, must call super.beforeEach
  }


  "DisplayFunctions" should "generate correct column headers for tuples" in {

    Console.withOut(stream) {
      new DisplayFunctions[(String,String,Int)](testRDDTuples).displayAsTable("Login","Name","Age")
    }

    stream.toString("UTF-8") should be("%table Login\tName\tAge\n" +
      "jdoe\tJohn DOE\t32\n" +
      "hsue\tHelen SUE\t27\n" +
      "rsmith\tRichard SMITH\t45\n")
  }

  "DisplayFunctions" should "generate correct column headers for case class" in {

    Console.withOut(stream) {
      new DisplayFunctions[Person](testRDDPersons).displayAsTable("Login","Name","Age")
    }

    stream.toString("UTF-8") should be("%table Login\tName\tAge\n" +
      "jdoe\tJohn DOE\t32\n" +
      "hsue\tHelen SUE\t27\n" +
      "rsmith\tRichard SMITH\t45\n")
  }

  "DisplayFunctions" should "truncate exceeding column headers for tuples" in {

    Console.withOut(stream) {
      new DisplayFunctions[(String,String,Int)](testRDDTuples).displayAsTable("Login","Name","Age","xxx","yyy")
    }

    stream.toString("UTF-8") should be("%table Login\tName\tAge\n" +
      "jdoe\tJohn DOE\t32\n" +
      "hsue\tHelen SUE\t27\n" +
      "rsmith\tRichard SMITH\t45\n")
  }

  "DisplayFunctions" should "pad missing column headers with ColumnXXX for tuples" in {

    Console.withOut(stream) {
      new DisplayFunctions[(String,String,Int)](testRDDTuples).displayAsTable("Login")
    }

    stream.toString("UTF-8") should be("%table Login\tColumn2\tColumn3\n" +
      "jdoe\tJohn DOE\t32\n" +
      "hsue\tHelen SUE\t27\n" +
      "rsmith\tRichard SMITH\t45\n")
  }

  "DisplayFunctions" should "pad all missing column headers with ColumnXXX for tuples" in {

    Console.withOut(stream) {
      new DisplayFunctions[(String,String,Int)](testRDDTuples).displayAsTable()
    }

    stream.toString("UTF-8") should be("%table Column1\tColumn2\tColumn3\n" +
      "jdoe\tJohn DOE\t32\n" +
      "hsue\tHelen SUE\t27\n" +
      "rsmith\tRichard SMITH\t45\n")
  }

  "DisplayUtils" should "display HTML" in {
    DisplayUtils.html() should be ("%html ")
    DisplayUtils.html("test") should be ("%html test")
  }

  "DisplayUtils" should "display img" in {
    DisplayUtils.img("http://www.google.com") should be ("<img src='http://www.google.com' />")
    DisplayUtils.img64() should be ("%img ")
    DisplayUtils.img64("abcde") should be ("%img abcde")
  }

  override def afterEach() {
    try super.afterEach() // To be stackable, must call super.afterEach
    stream = null
  }

  after {
    sc.stop()
  }


}
