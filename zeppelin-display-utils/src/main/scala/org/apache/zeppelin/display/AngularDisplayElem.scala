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
    val ic = InterpreterContext.get
    val registry = ic.getAngularObjectRegistry


    // create AngularFunction in current paragraph
    val functionName = uniqueId + "_" + eventName.replaceAll("-", "_")
    val elem = this % Attribute(None, eventName,
      Text(s"""function(){${functionName}($$event)}"""),
      Null)

    val angularObject = registry.add(functionName, "", ic.getNoteId)
      .asInstanceOf[AngularObject[Any]]

    angularObject.addWatcher(new AngularObjectWatcher(ic) {
      override def watch(oldObject: scala.Any, newObject: scala.Any, context: InterpreterContext)
      : Unit = {
        callback()
      }
    })

    new AngularDisplayElem(
      angularObjects + (eventName -> angularObject),
      elem.prefix, elem.label, elem.attributes, elem.scope, elem.minimizeEmpty, elem.child:_*)
  }

  /**
    * disassociate this element and it's child from front-end
    * by removing angularobject
    */
  def disassociate() = {
    remove(this)
  }

  /**
    * Remove all angularObject recursively
    * @param node
    */
  private def remove(node: Node) : Unit = {
    if (node.isInstanceOf[AngularDisplayElem]) {
      node.asInstanceOf[AngularDisplayElem].angularObjects.values.foreach{ ao =>
        val ic = InterpreterContext.get()
        ic.getAngularObjectRegistry.remove(ao.getName, ao.getNoteId)
      }
    }

    node.child.foreach(remove _)
  }
}

object AngularDisplayElem {
  implicit def Elem2AngularDisplayElem(elem: Elem) : AngularDisplayElem = {
    new AngularDisplayElem(
      Map[String, AngularObject[Any]](),
      elem.prefix, elem.label, elem.attributes, elem.scope, elem.minimizeEmpty, elem.child:_*);
  }
}