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

import org.apache.zeppelin.interpreter.InterpreterContext

/**
  * Represents ng-model
  */
class AngularModel(name: String) {
  val registry = InterpreterContext.get.getAngularObjectRegistry
  val noteId = InterpreterContext.get.getNoteId

  def this(name: String, newValue: Any) = {
    this(name)

    value(newValue)
  }


  /**
    * Get value of the model
    * @return
    */
  def apply(): Any = {
    value()
  }

  /**
    * Get value of the model
    * @return
    */
  def value(): Any = {
    val angularObject = registry.get(name, noteId)
    if (angularObject == null) {
      None
    } else {
      angularObject.get
    }
  }


  def apply(newValue: Any): Unit = {
    value(newValue)
  }

  def :=(newValue: Any) = {
    new AngularModel(name, newValue)
  }

  /**
    * Set value of the model
    * @param newValue
    */
  def value(newValue: Any): Unit = {
    var angularObject = registry.get(name, noteId)
    if (angularObject == null) {
      // create new object
      angularObject = registry.add(name, newValue, noteId)
    } else {
      angularObject.asInstanceOf[AngularObject[Any]].set(newValue)
    }
    angularObject.get()
  }

  def remove(): Any = {
    val angularObject = registry.get(name, noteId)
    registry.remove(name, noteId)

    if (angularObject == null) {
      None
    } else {
      angularObject.get
    }
  }
}


object AngularModel {
  def apply(name: String): AngularModel = {
    new AngularModel(name)
  }

  def apply(name: String, newValue: Any): AngularModel = {
    new AngularModel(name, newValue)
  }

}