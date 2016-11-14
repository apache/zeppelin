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
package org.apache.zeppelin.display.angular

import org.apache.zeppelin.annotation.ZeppelinApi
import org.apache.zeppelin.display.AngularObject
import org.apache.zeppelin.interpreter.InterpreterContext

/**
  * Represents ng-model with angular object
  */
abstract class AbstractAngularModel(name: String) {
  val context = InterpreterContext.get
  val registry = context.getAngularObjectRegistry


  /**
    * Create AngularModel with initial Value
    *
    * @param name name of model
    * @param newValue value
    */
  @ZeppelinApi
  def this(name: String, newValue: Any) = {
    this(name)
    value(newValue)
  }

  protected def getAngularObject(): AngularObject[Any]
  protected def addAngularObject(value: Any): AngularObject[Any]

  /**
    * Get value of the model
    *
    * @return
    */
  @ZeppelinApi
  def apply(): Any = {
    value()
  }

  /**
    * Get value of the model
    *
    * @return
    */
  @ZeppelinApi
  def value(): Any = {
    val angularObject = getAngularObject()
    if (angularObject == null) {
      None
    } else {
      angularObject.get
    }
  }

  @ZeppelinApi
  def apply(newValue: Any): Unit = {
    value(newValue)
  }


  /**
    * Set value of the model
    *
    * @param newValue
    */
  @ZeppelinApi
  def value(newValue: Any): Unit = {
    var angularObject = getAngularObject()
    if (angularObject == null) {
      // create new object
      angularObject = addAngularObject(newValue)
    } else {
      angularObject.set(newValue)
    }
    angularObject.get()
  }

  @ZeppelinApi
  def remove(): Any = {
    val angularObject = getAngularObject()

    if (angularObject == null) {
      None
    } else {
      registry.remove(name, angularObject.getNoteId(), angularObject.getParagraphId())
      angularObject.get
    }
  }
}
