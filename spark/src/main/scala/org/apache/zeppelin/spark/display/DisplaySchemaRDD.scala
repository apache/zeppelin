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

import org.apache.spark.SparkContext
import org.apache.zeppelin.display.{DisplayParams, DisplayFunction}
import org.apache.zeppelin.interpreter.{InterpreterContext, InterpreterException}
import org.slf4j.{LoggerFactory, Logger}

/**
 * Display function for SchemaRDD
 * @param sc the current SparkContext
 */
class DisplaySchemaRDD(val sc: SparkContext) extends DisplayFunction with AbstractDisplay {

  val logger:Logger = LoggerFactory.getLogger(classOf[DisplaySchemaRDD])

  val cls = try {
    Class.forName("org.apache.spark.sql.SchemaRDD")
  } catch {
    case cnfe: ClassNotFoundException =>
      throw new InterpreterException("Cannot instantiate 'org.apache.spark.sql.SchemaRDD'. " +
        "Are you sure you have Spark 1.2 or above ?")
    case e:Throwable => throw new InterpreterException(e)
  }

  override def canDisplay(anyObject: Any): Boolean = {
    anyObject != null && cls.isInstance(anyObject)
  }

  override def display(anyObject: Any, displayParams: DisplayParams): Unit = {
    if(logger.isDebugEnabled()) logger.debug(s"Display $anyObject with params $displayParams")

    require(anyObject != null, "Cannot display null SchemaRDD")
    val output = printDFOrSchemaRDD(sc, displayParams.context, anyObject, displayParams.maxResult)
    displayParams.out.print(output)
  }
}
