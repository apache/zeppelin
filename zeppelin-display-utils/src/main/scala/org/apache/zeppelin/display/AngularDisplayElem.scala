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

import scala.xml._

class AngularDisplayElem(val angularObjects: Map[String, AngularObject[Any]],
                         val angularFunctions: Map[String, AngularFunction],
                         prefix: String,
                         label: String,
                         attributes1: MetaData,
                         scope: NamespaceBinding,
                         minimizeEmpty: Boolean,
                         child: Node*)
  extends Elem(prefix, label, attributes1, scope, minimizeEmpty, child:_*) {

  val uniqueId = java.util.UUID.randomUUID.toString.replaceAll("-", "_")

  /**
    * On click element
    * @return
    */
  def onClick(callback: () => Unit) : AngularDisplayElem = {
    onEvent("ng-click", callback)
  }

  /**
    *
    * @param eventName angular directive like ng-click, ng-change, etc.
    * @return
    */
  def onEvent(eventName: String, callback: () => Unit) : AngularDisplayElem = {
    val interpreterContext = InterpreterContext.get
    val registry = interpreterContext.getAngularObjectRegistry


    // create AngularFunction in current paragraph
    val functionName = uniqueId + "_" + eventName
    val elem = this % Attribute(None, eventName, Text("functionName()"), Null)

    val fn = new AngularFunction(
      registry,
      functionName,
      interpreterContext.getNoteId,
      interpreterContext.getParagraphId,
      new AngularFunctionRunnable {
        override def run(args: AnyRef*): Unit = {
          callback()
        }
      })

    new AngularDisplayElem(
      angularObjects,
      angularFunctions + (eventName -> fn),
      elem.prefix, elem.label, elem.attributes, elem.scope, elem.minimizeEmpty, elem.child:_*)
  }
}

object AngularDisplayElem {
  implicit def Elem2AngularDisplayElem(elem: Elem) : AngularDisplayElem = {
    new AngularDisplayElem(
      Map[String, AngularObject[Any]](),
      Map[String, AngularFunction](),
      elem.prefix, elem.label, elem.attributes, elem.scope, elem.minimizeEmpty, elem.child:_*);
  }
}