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
package org.apache.zeppelin.display.angular.notebookscope

import org.apache.zeppelin.display.AngularObject
import org.apache.zeppelin.display.angular.AbstractAngularModel
import org.apache.zeppelin.interpreter.InterpreterContext

/**
  * Represents ng-model in notebook scope
  */
class AngularModel(name: String)
  extends org.apache.zeppelin.display.angular.AbstractAngularModel(name) {

  def this(name: String, newValue: Any) = {
    this(name)
    value(newValue)
  }

  override protected def getAngularObject(): AngularObject[Any] = {
    registry.get(name, context.getNoteId, null).asInstanceOf[AngularObject[Any]]
  }

  override protected def addAngularObject(value: Any): AngularObject[Any] = {
    registry.add(name, value, context.getNoteId, null).asInstanceOf[AngularObject[Any]]
  }
}


object AngularModel {
  def apply(name: String): AbstractAngularModel = {
    new AngularModel(name)
  }

  def apply(name: String, newValue: Any): AbstractAngularModel = {
    new AngularModel(name, newValue)
  }
}