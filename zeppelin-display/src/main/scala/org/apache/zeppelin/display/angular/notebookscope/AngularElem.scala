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

import org.apache.zeppelin.display.angular.AbstractAngularElem
import org.apache.zeppelin.display.{angular, AngularObject}
import org.apache.zeppelin.interpreter.InterpreterContext

import scala.collection.JavaConversions
import scala.xml._

/**
  * AngularElement in notebook scope
  */
class AngularElem(override val interpreterContext: InterpreterContext,
                  override val modelName: String,
                  override val angularObjects: Map[String, AngularObject[Any]],
                  prefix: String,
                  label: String,
                  attributes1: MetaData,
                  scope: NamespaceBinding,
                  minimizeEmpty: Boolean,
                  child: Node*)
  extends AbstractAngularElem(
    interpreterContext, modelName, angularObjects, prefix, label, attributes1, scope,
    minimizeEmpty, child: _*) {

  override protected def addAngularObject(name: String, value: Any): AngularObject[Any] = {
    val registry = interpreterContext.getAngularObjectRegistry
    registry.add(name, value, interpreterContext.getNoteId, null).asInstanceOf[AngularObject[Any]]

  }

  override protected def newElem(interpreterContext: InterpreterContext,
                                 name: String,
                                 angularObjects: Map[String, AngularObject[Any]],
                                 elem: scala.xml.Elem): angular.AbstractAngularElem = {
    new AngularElem(
      interpreterContext,
      name,
      angularObjects,
      elem.prefix,
      elem.label,
      elem.attributes,
      elem.scope,
      elem.minimizeEmpty,
      elem.child:_*)
  }
}

object AngularElem {
  implicit def Elem2AngularDisplayElem(elem: Elem): AbstractAngularElem = {
    new AngularElem(InterpreterContext.get(), null,
      Map[String, AngularObject[Any]](),
      elem.prefix, elem.label, elem.attributes, elem.scope, elem.minimizeEmpty, elem.child:_*);
  }

  /**
    * Disassociate (remove) all angular object in this notebook
    */
  def disassociate() = {
    val ic = InterpreterContext.get
    val registry = ic.getAngularObjectRegistry

    JavaConversions.asScalaBuffer(registry.getAll(ic.getNoteId, null)).foreach(ao =>
      registry.remove(ao.getName, ao.getNoteId, null)
    )
  }
}