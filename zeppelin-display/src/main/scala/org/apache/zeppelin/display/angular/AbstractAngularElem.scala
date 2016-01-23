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

import java.io.PrintStream

import org.apache.zeppelin.display.{AngularObjectWatcher, AngularObject}
import org.apache.zeppelin.interpreter.{InterpreterResult, InterpreterContext}

import scala.xml._

/**
  * Element that binded to Angular object
  */
abstract class AbstractAngularElem(val interpreterContext: InterpreterContext,
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
  def onClick(callback: () => Unit): AbstractAngularElem = {
    onEvent("ng-click", callback)
  }

  /**
    * On
    * @param callback
    * @return
    */
  def onChange(callback: () => Unit): AbstractAngularElem = {
    onEvent("ng-change", callback)
  }

  /**
    * Bind angularObject to ng-model directive
    * @param name name of angularObject
    * @param value initialValue
    * @return
    */
  def model(name: String, value: Any): AbstractAngularElem = {
    val registry = interpreterContext.getAngularObjectRegistry

    // create AngularFunction in current paragraph
    val elem = this % Attribute(None, "ng-model",
      Text(s"${name}"),
      Null)

    val angularObject = addAngularObject(name, value)
      .asInstanceOf[AngularObject[Any]]

    newElem(
      interpreterContext,
      name,
      angularObjects + (name -> angularObject),
      elem)
  }


  def model(name: String): AbstractAngularElem = {
    val registry = interpreterContext.getAngularObjectRegistry

    // create AngularFunction in current paragraph
    val elem = this % Attribute(None, "ng-model",
      Text(s"${name}"),
      Null)

    newElem(
      interpreterContext,
      name,
      angularObjects,
      elem)
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
  def onEvent(eventName: String, callback: () => Unit): AbstractAngularElem = {
    val registry = interpreterContext.getAngularObjectRegistry

    // create AngularFunction in current paragraph
    val functionName = eventName.replaceAll("-", "_") + "_" + uniqueId
    val elem = this % Attribute(None, eventName,
      Text(s"${functionName}=$$event.timeStamp"),
      Null)

    val angularObject = addAngularObject(functionName, "")

    angularObject.addWatcher(new AngularObjectWatcher(interpreterContext) {
      override def watch(oldObject: scala.Any, newObject: scala.Any, context: InterpreterContext)
      :Unit = {
        InterpreterContext.set(interpreterContext)
        callback()
      }
    })

    newElem(
      interpreterContext,
      modelName,
      angularObjects + (eventName -> angularObject),
      elem)
  }

  protected def addAngularObject(name: String, value: Any): AngularObject[Any]

  protected def newElem(interpreterContext: InterpreterContext,
                        name: String,
                        angularObjects: Map[String, AngularObject[Any]],
                        elem: scala.xml.Elem): AbstractAngularElem

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
    if (node.isInstanceOf[AbstractAngularElem]) {
      node.asInstanceOf[AbstractAngularElem].angularObjects.values.foreach{ ao =>
        interpreterContext.getAngularObjectRegistry.remove(ao.getName, ao.getNoteId, ao
          .getParagraphId)
      }
    }

    node.child.foreach(remove _)
  }

  /**
    * Print into provided print stream
    * @return
    */
  def display(out: java.io.PrintStream): Unit = {
    out.print(this.toString)
    out.flush()
  }

  /**
    * Print into InterpreterOutput
    */
  def display(): Unit = {
    val out = interpreterContext.out
    out.setType(InterpreterResult.Type.ANGULAR)
    out.write(this.toString())
    out.flush()
  }
}

