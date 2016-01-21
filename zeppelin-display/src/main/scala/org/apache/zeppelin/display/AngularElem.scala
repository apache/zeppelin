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

import java.io.PrintStream

import org.apache.zeppelin.interpreter.InterpreterContext

import scala.collection.JavaConversions
import scala.xml._

class AngularElem(val interpreterContext: InterpreterContext,
                  val modelName: String,
                  val angularObjects: Map[String, AngularObject[Any]],
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
    * @param callback
    * @return
    */
  def onClick(callback: () => Unit): AngularElem = {
    onEvent("ng-click", callback)
  }

  /**
    * On
    * @param callback
    * @return
    */
  def onChange(callback: () => Unit): AngularElem = {
    onEvent("ng-change", callback)
  }

  /**
    * Bind angularObject to ng-model directive
    * @param name name of angularObject
    * @param value initialValue
    * @return
    */
  def model(name: String, value: Any): AngularElem = {
    val registry = interpreterContext.getAngularObjectRegistry

    // create AngularFunction in current paragraph
    val elem = this % Attribute(None, "ng-model",
      Text(s"${name}"),
      Null)

    val angularObject = registry.add(name, value, interpreterContext.getNoteId)
      .asInstanceOf[AngularObject[Any]]

    new AngularElem(
      interpreterContext,
      name,
      angularObjects + (name -> angularObject),
      elem.prefix, elem.label, elem.attributes, elem.scope, elem.minimizeEmpty, elem.child:_*)
  }


  def model(name: String): AngularElem = {
    val registry = interpreterContext.getAngularObjectRegistry

    // create AngularFunction in current paragraph
    val elem = this % Attribute(None, "ng-model",
      Text(s"${name}"),
      Null)

    new AngularElem(
      interpreterContext,
      name,
      angularObjects,
      elem.prefix, elem.label, elem.attributes, elem.scope, elem.minimizeEmpty, elem.child:_*)
  }

  /**
    * Retrieve value of model
    * @return
    */
  def model(): Any = {
    if (angularObjects.contains(modelName)) {
      angularObjects(modelName).get()
    } else {
      None
    }
  }

  /**
    *
    * @param eventName angular directive like ng-click, ng-change, etc.
    * @return
    */
  def onEvent(eventName: String, callback: () => Unit): AngularElem = {
    val registry = interpreterContext.getAngularObjectRegistry

    // create AngularFunction in current paragraph
    val functionName = eventName.replaceAll("-", "_") + "_" + uniqueId
    val elem = this % Attribute(None, eventName,
      Text(s"${functionName}=$$event.timeStamp"),
      Null)

    val angularObject = registry.add(functionName, "", interpreterContext.getNoteId)
      .asInstanceOf[AngularObject[Any]]

    angularObject.addWatcher(new AngularObjectWatcher(interpreterContext) {
      override def watch(oldObject: scala.Any, newObject: scala.Any, context: InterpreterContext)
      :Unit = {
        InterpreterContext.set(interpreterContext)
        callback()
      }
    })

    new AngularElem(
      interpreterContext,
      modelName,
      angularObjects + (eventName -> angularObject),
      elem.prefix, elem.label, elem.attributes, elem.scope, elem.minimizeEmpty, elem.child:_*)
  }

  /**
    * Print with %angular prefix
    * @return
    */
  def display(out: java.io.PrintStream): Unit = {
    if (AngularElem.angularDirectivePrinted != interpreterContext.hashCode()) {
      AngularElem.angularDirectivePrinted = interpreterContext.hashCode()
      out.print("%angular ")
    }
    out.print(this.toString)
    out.flush()
  }

  /**
    * Print with %angular prefix
    */
  def display(): Unit = {
    val out = interpreterContext.out
    display(new PrintStream(out))
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
  private def remove(node: Node): Unit = {
    if (node.isInstanceOf[AngularElem]) {
      node.asInstanceOf[AngularElem].angularObjects.values.foreach{ ao =>
        interpreterContext.getAngularObjectRegistry.remove(ao.getName, ao.getNoteId)
      }
    }

    node.child.foreach(remove _)
  }
}

object AngularElem {
  implicit def Elem2AngularDisplayElem(elem: Elem) : AngularElem = {
    new AngularElem(InterpreterContext.get(), null,
      Map[String, AngularObject[Any]](),
      elem.prefix, elem.label, elem.attributes, elem.scope, elem.minimizeEmpty, elem.child:_*);
  }

  private var angularDirectivePrinted: Int = 0

  /**
    * Disassociate (remove) all angular object in this notebook
    */
  def disassociate() = {
    val ic = InterpreterContext.get
    val registry = ic.getAngularObjectRegistry

    JavaConversions.asScalaBuffer(registry.getAll(ic.getNoteId)).foreach(ao =>
      registry.remove(ao.getName, ao.getNoteId)
    )
  }
}