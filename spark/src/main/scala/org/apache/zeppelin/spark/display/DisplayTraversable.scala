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

import org.apache.zeppelin.display.{DisplayParams, DisplayFunction}
import org.apache.zeppelin.interpreter.{InterpreterException}
import org.slf4j.{LoggerFactory, Logger}
import scala.collection.JavaConverters._

/**
 * Display function for Scala traversables
 */
class DisplayTraversable extends DisplayFunction with AbstractDisplay{

  val logger:Logger = LoggerFactory.getLogger(classOf[DisplayTraversable])

  override def canDisplay(anyObject: Any): Boolean =  {
    anyObject != null && classOf[Traversable[Product]].isAssignableFrom(anyObject.getClass)
  }

  override def display(anyObject: Any, displayParams: DisplayParams): Unit = {
    if(logger.isDebugEnabled()) logger.debug(s"Display $anyObject with params $displayParams")
    require(anyObject != null, "Cannot display null Scala collection")
    val collection: Traversable[_] = anyObject.asInstanceOf[Traversable[_]]
    val nullSafeList = Option(displayParams.columnsLabel).getOrElse(List[String]().asJava)
    val newParams = displayParams.copy(columnsLabel = nullSafeList)
    val output = printFormattedData(collection.take(newParams.maxResult), newParams.columnsLabel.asScala.toArray: _*)
    newParams.out.print(output)
  }
}