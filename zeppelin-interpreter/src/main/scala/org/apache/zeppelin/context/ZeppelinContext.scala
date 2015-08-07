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

package org.apache.zeppelin.context

import java.util

import org.apache.zeppelin.display.Input.ParamOption
import org.apache.zeppelin.display._
import org.apache.zeppelin.interpreter.{InterpreterContext, InterpreterContextRunner, InterpreterException}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConversions.{collectionAsScalaIterable}
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer}
import scala.collection.JavaConverters._


/**
 * ZeppelinContext
 * @param defaultMaxResult the default max result
 *
 */
class ZeppelinContext(private var defaultMaxResult: Int)  extends mutable.HashMap[String,Any] {

  val logger:Logger = LoggerFactory.getLogger(classOf[ZeppelinContext])

  private var gui: GUI = null
  private var interpreterContext: InterpreterContext = null
  val displayFunctionRegistry = mutable.ArrayBuffer.empty[DisplayFunction]

  /**
   * Register a new display function
   * @param displayFunction
   */
  def registerDisplayFunction(displayFunction: DisplayFunction): Unit = {
    if (logger.isDebugEnabled()) logger.debug(s"Registering display function $displayFunction")

    if (!displayFunctionRegistry.contains(displayFunction)) {
      displayFunctionRegistry.append(displayFunction)
    } else {
      logger.warn(s"Display function $displayFunction is already registered !")
    }
  }

  /**
   * Display the current object to the standard console output
   * @param obj current object to display
   * @return formatted output
   */
  def display(obj: AnyRef): Unit = {
    display(obj, DisplayParams(defaultMaxResult, Console.out, interpreterContext, List[String]().asJava))
  }

  /**
   * Display the current object to the standard console output,
   * restricted to the first 'maxResult' items
   * @param obj current object to display
   * @param maxResult max results
   * @return formatted output
   */
  def display(obj: AnyRef, maxResult: Int): Unit = {
    display(obj, DisplayParams(maxResult, Console.out, interpreterContext, List[String]().asJava))
  }

  /**
   * Display the current object to the standard console output,
   * with the provided columns label optionally
   * @param obj current object to display
   * @param firstColumnLabel first columns label
   * @param remainingColumnsLabel remaining columns label
   * @return formatted output
   */
  def display(obj: AnyRef, firstColumnLabel: String, remainingColumnsLabel: String*): Unit = {
    val orElse = Option(remainingColumnsLabel).getOrElse(Seq[String]())
    val columnsLabel: List[String] = List(firstColumnLabel) ::: orElse.toList
    display(obj, DisplayParams(defaultMaxResult, Console.out, interpreterContext,columnsLabel.asJava))
  }

  /**
   * Display the current object to the standard console output,
   * with the provided columns label optionally
   * @param obj current object to display
   * @param maxResult max results
   * @param columnsLabel columns label
   * @return formatted output
   */
  def display(obj: AnyRef, maxResult: Int, columnsLabel: String*): Unit = {
    val safeList = Option(columnsLabel).getOrElse(Seq[String]())
    display(obj, DisplayParams(maxResult, Console.out, interpreterContext, safeList.asJava))
  }

  /**
   * Display the current object with display parameters
   * @param displayParams display parameters
   * @param obj current object to display
   * @return formatted output
   */
  def display(obj: AnyRef, displayParams: DisplayParams): Unit = {

    if (logger.isDebugEnabled()) logger.debug(s"Attempting to display $obj with params $displayParams")

    require(obj != null, "Cannot display null object")

    val max = Option(displayParams.maxResult).getOrElse(defaultMaxResult)
    val columnsLabel = Option(displayParams.columnsLabel).getOrElse(List[String]().asJava)
    val context = Option(displayParams.context).getOrElse(interpreterContext)
    val newParams = displayParams.copy(maxResult = max, columnsLabel = columnsLabel, context = context)

    val matchedDisplayedFunctions: ArrayBuffer[DisplayFunction] = displayFunctionRegistry
      .filter(_.canDisplay(obj))

    if (logger.isDebugEnabled())
      logger.debug(s"""Matched display function(s) found for $obj: ${matchedDisplayedFunctions.mkString(",")}""")

    val displayFunction: Option[DisplayFunction] = matchedDisplayedFunctions.headOption

    matchedDisplayedFunctions.size match {
      case 0 => throw new InterpreterException(s"Cannot find any suitable display function for object ${obj.toString}")
      case 1 => displayFunction.get.display(obj, newParams)
      case _ => {
        logger.warn(s"More than one display function found for type ${obj.getClass}. Will use the first one : $displayFunction")
        displayFunction.get.display(obj, newParams)
      }
    }
  }

  /**
   * Define and retrieve the current value of an input from the GUI
   * @param name name of the GUI input
   * @return current input value
   */
  def input (name: String): Any = {
    input(name, "")
  }

  /**
   * Define a default value and retrieve the current value of an input from the GUI
   * @param name name of the GUI input
   * @param defaultValue default value for the input
   * @return current input value
   */
  def input(name: String, defaultValue: Any): Any = {
    gui.input(name, defaultValue)
  }

  /**
   * Define an HMTL &lt;select&gt; and retrieve the current value from the GUI
   * @param name name of the HMTL &lt;select&gt;
   * @param options list of (value,displayedValue)
   * @return current selected element
   */
  def select(name:String, options: Iterable[(Any, String)]):Any = {
    select(name, "", options)
  }

  /**
   * Define an HMTL &lt;select&gt; with a default value and retrieve the current value from the GUI
   * @param name name of the HMTL &lt;select&gt;
   * @param defaultValue default selected value
   * @param options list of (value,displayedValue)
   * @return current selected element
   */
  def select(name: String, defaultValue: Any, options: Iterable[(Any, String)]): Any = {
    val paramOptions = options.map{case(value,label) => new ParamOption(value,label)}.toArray
    gui.select(name, defaultValue, paramOptions)
  }

  def setGui(o: GUI) {
    this.gui = o
  }

  /**
   * Set max result for display
   * @param maxResult max result for display
   */
  def setMaxResult(maxResult: Int):Unit = {
    this.defaultMaxResult = maxResult
  }

  def getMaxResult(): Int = {
    this.defaultMaxResult
  }

  def getInterpreterContext: InterpreterContext = interpreterContext

  def setInterpreterContext(interpreterContext: InterpreterContext):Unit = {
    this.interpreterContext = interpreterContext
  }

  /**
   * Run paragraph by id
   * @param id paragraph id
   */
  def run(id: String):Unit = {
    run(id, this.interpreterContext)
  }

  /**
   * Run paragraph by id
   * @param id paragraph id
   * @param context interpreter context
   */
  def run(id: String, context: InterpreterContext):Unit = {
    if (id == context.getParagraphId) {
      throw new InterpreterException("Can not run current Paragraph")
    }

    context.getRunners.filter(r => r.getParagraphId == id).foreach(r => {
      r.run
      return
    })
    throw new InterpreterException("Paragraph " + id + " not found")
  }

  /**
   * Run paragraph at idx
   * @param idx paragraph index to run
   */
  def run(idx: Int):Unit = {
    run(idx, this.interpreterContext)
  }

  /**
   * Run paragraph at index
   * @param idx index starting from 0
   * @param context interpreter context
   */
  def run(idx: Int, context: InterpreterContext):Unit = {
    val runners: util.List[InterpreterContextRunner] = context.getRunners
    if (idx >= runners.size) {
      throw new InterpreterException("Index out of bound")
    }
    val runner: InterpreterContextRunner = runners.get(idx)
    if (runner.getParagraphId == context.getParagraphId) {
      throw new InterpreterException("Can not run current Paragraph")
    }
    runner.run
  }

  /**
   * Run paragraphs using their index or id
   * @param paragraphIdOrIdx list of paragraph id or idx
   */
  def run(paragraphIdOrIdx: List[Any]):Unit = {
    run(paragraphIdOrIdx, this.interpreterContext)
  }

  /**
   * Run paragraphs using their index or id
   * @param paragraphIdOrIdx list of paragraph id or idx
   */
  def run(paragraphIdOrIdx: List[Any], context: InterpreterContext):Unit = {

    val unknownIds: List[Any] = paragraphIdOrIdx.filter(x => (!x.isInstanceOf[String] && !x.isInstanceOf[Int]))
    if (unknownIds.size > 0) {
      throw new InterpreterException(s"""Paragraphs ${unknownIds.mkString(",")} not found""")
    }
    paragraphIdOrIdx
      .filter(_.isInstanceOf[String])
      .foreach(id => run(id.asInstanceOf[String], context))

    paragraphIdOrIdx
      .filter(_.isInstanceOf[Int])
      .foreach(index => run(index.asInstanceOf[Int], context))

  }

  /**
   * Run all paragraphs, except the current
   */
  def runAll:Unit = {
    runAll(interpreterContext)
  }

  /**
   * Run all paragraphs. except the current
   */
  def runAll(context: InterpreterContext):Unit = {
    context.getRunners.foreach(r => {
      if (r.getParagraphId != context.getParagraphId) r.run
    })
  }

  /**
   * List all paragraps
   * @return paragraph ids as list
   */
  def listParagraphs: List[String] = {
    interpreterContext.getRunners.map(_.getParagraphId).toList
  }

  /**
   * Retrieve an Angular variable by name
   * @param name variable name
   * @return variable value
   */
  def angular(name: String): Any = {
    Option(getAngularObject(name, interpreterContext))
      .map(_.get)
      .getOrElse(null)
  }

  /**
   * Get angular object. Look up global registry
   * @param name variable name
   * @return value
   */
  def angularGlobal(name: String): AnyRef = {
    val registry: AngularObjectRegistry = interpreterContext.getAngularObjectRegistry
    Option(registry.get(name, null))
      .map(_.get)
      .getOrElse(null)
  }

  /**
   * Create angular variable in local registry and bind with front end Angular display system.
   * If variable exists, it'll be overwritten.
   * @param name name of the variable
   * @param o value
   */
  def angularBind(name: String, o: AnyRef) {
    angularBindWithNodeId(name, o, interpreterContext.getNoteId)
  }

  /**
   * Create angular variable in global registry and bind with front end Angular display system.
   * If variable exists, it'll be overwritten.
   * @param name name of the variable
   * @param o value
   */
  def angularBindGlobal(name: String, o: AnyRef) {
    angularBindWithNodeId(name, o, null)
  }

  /**
   * Create angular variable in local registry and bind with front end Angular display system.
   * If variable exists, value will be overwritten and watcher will be added.
   * @param name name of variable
   * @param o value
   * @param watcher watcher of the variable
   */
  def angularBind(name: String, o: AnyRef, watcher: AngularObjectWatcher) {
    angularBindWithWatcher(name, o, interpreterContext.getNoteId, watcher)
  }

  /**
   * Create angular variable in global registry and bind with front end Angular display system.
   * If variable exists, value will be overwritten and watcher will be added.
   * @param name name of variable
   * @param o value
   * @param watcher watcher of the variable
   */
  def angularBindGlobal(name: String, o: AnyRef, watcher: AngularObjectWatcher) {
    angularBindWithWatcher(name, o, null, watcher)
  }

  /**
   * Add watcher into angular variable (local registry)
   * @param name name of the variable
   * @param watcher watcher
   */
  def angularWatch(name: String, watcher: AngularObjectWatcher) {
    angularWatch(name, interpreterContext.getNoteId, watcher)
  }

  /**
   * Add watcher into angular variable (global registry)
   * @param name name of the variable
   * @param watcher watcher
   */
  def angularWatchGlobal(name: String, watcher: AngularObjectWatcher) {
    angularWatch(name, null, watcher)
  }

  def angularWatch(name: String, func: (AnyRef, AnyRef) => Unit) {
    angularWatch(name, interpreterContext.getNoteId, func)
  }

  def angularWatchGlobal(name: String, func: (AnyRef, AnyRef) => Unit) {
    angularWatch(name, null, func)
  }

  def angularWatch(name: String, func: (AnyRef, AnyRef, InterpreterContext) => Unit) {
    angularWatch(name, interpreterContext.getNoteId, func)
  }

  def angularWatchGlobal(name: String, func: (AnyRef, AnyRef, InterpreterContext) => Unit) {
    angularWatch(name, null, func)
  }

  /**
   * Remove watcher from angular variable (local)
   * @param name
   * @param watcher
   */
  def angularUnwatch(name: String, watcher: AngularObjectWatcher) {
    angularUnwatchFromWatcher(name, interpreterContext.getNoteId, watcher)
  }

  /**
   * Remove watcher from angular variable (global)
   * @param name
   * @param watcher
   */
  def angularUnwatchGlobal(name: String, watcher: AngularObjectWatcher) {
    angularUnwatchFromWatcher(name, null, watcher)
  }

  /**
   * Remove all watchers for the angular variable (local)
   * @param name
   */
  def angularUnwatch(name: String) {
    angularUnwatchFromNoteId(name, interpreterContext.getNoteId)
  }

  /**
   * Remove all watchers for the angular variable (global)
   * @param name
   */
  def angularUnwatchGlobal(name: String) {
    angularUnwatchFromNoteId(name, null)
  }

  /**
   * Remove angular variable and all the watchers.
   * @param name
   */
  def angularUnbind(name: String) {
    val noteId: String = interpreterContext.getNoteId
    angularUnbind(name, noteId)
  }

  /**
   * Remove angular variable and all the watchers.
   * @param name
   */
  def angularUnbindGlobal(name: String) {
    angularUnbind(name, null)
  }

  private def getAngularObject(name: String, interpreterContext: InterpreterContext): AngularObject[_] = {
    val registry: AngularObjectRegistry = interpreterContext.getAngularObjectRegistry
    Option(registry.get(name, interpreterContext.getNoteId))
      .getOrElse(registry.get(name, null))
  }

  /**
   * Create angular variable in local registry and bind with front end Angular display system.
   * If variable exists, it'll be overwritten.
   * @param name name of the variable
   * @param o value
   */
  private def angularBindWithNodeId(name: String, o: AnyRef, noteId: String) {
    val registry: AngularObjectRegistry = interpreterContext.getAngularObjectRegistry
    if (registry.get(name, noteId) == null) {
      registry.add(name, o, noteId)
    }
    else {
      registry.get(name, noteId)
        .asInstanceOf[AngularObject[AnyRef]]
        .set(o)
    }
  }

  /**
   * Create angular variable in local registry and bind with front end Angular display system.
   * If variable exists, value will be overwritten and watcher will be added.
   * @param name name of variable
   * @param o value
   * @param watcher watcher of the variable
   */
  private def angularBindWithWatcher(name: String, o: AnyRef, noteId: String, watcher: AngularObjectWatcher) {
    val registry: AngularObjectRegistry = interpreterContext.getAngularObjectRegistry
    if (registry.get(name, noteId) == null) {
      registry.add(name, o, noteId)
    }
    else {
      registry.get(name, noteId)
        .asInstanceOf[AngularObject[AnyRef]]
        .set(o)
    }
    angularWatch(name, watcher)
  }

  /**
   * Add watcher into angular binding variable
   * @param name name of the variable
   * @param watcher watcher
   */
  private def angularWatch(name: String, noteId: String, watcher: AngularObjectWatcher) {
    Option(interpreterContext.getAngularObjectRegistry)
      .foreach(_.get(name, noteId).addWatcher(watcher))
  }

  private def angularWatch(name: String, noteId: String, func: (AnyRef, AnyRef) => Unit) {
    val w: AngularObjectWatcher = new AngularObjectWatcher((getInterpreterContext)) {
      def watch(oldObject: AnyRef, newObject: AnyRef, context: InterpreterContext) {
        func.apply(newObject, newObject)
      }
    }
    angularWatch(name, noteId, w)
  }

  private def angularWatch(name: String, noteId: String, func: (AnyRef, AnyRef, InterpreterContext) => Unit) {
    val w: AngularObjectWatcher = new AngularObjectWatcher((getInterpreterContext)) {
      def watch(oldObject: AnyRef, newObject: AnyRef, context: InterpreterContext) {
        func.apply(oldObject, newObject, context)
      }
    }
    angularWatch(name, noteId, w)
  }

  /**
   * Remove watcher
   * @param name
   * @param watcher
   */
  private def angularUnwatchFromWatcher(name: String, noteId: String, watcher: AngularObjectWatcher) {
    Option(interpreterContext.getAngularObjectRegistry)
      .foreach(_.get(name, noteId).removeWatcher(watcher))
  }

  /**
   * Remove all watchers for the angular variable
   * @param name
   */
  private def angularUnwatchFromNoteId(name: String, noteId: String) {
    Option(interpreterContext.getAngularObjectRegistry)
      .foreach(_.get(name, noteId).clearAllWatchers)
  }

  /**
   * Remove angular variable and all the watchers.
   * @param name
   */
  private def angularUnbind(name: String, noteId: String) {
    interpreterContext.getAngularObjectRegistry.remove(name, noteId)
  }

  /**
   * Display HTML code
   * @param htmlContent unescaped HTML content
   * @return HTML content prefixed by the magic %html
   */
  def html(htmlContent: String = ""):String = s"%html $htmlContent"

  /**
   * Display image using base 64 content
   * @param base64Content base64 content
   * @return base64Content prefixed by the magic %img
   */
  def img64(base64Content: String = "") = s"%img $base64Content"

  /**
   * Display image using URL
   * @param url image URL
   * @return a HTML &lt;img&gt; tag with src = base64 content
   */
  def img(url: String) = s"<img src='$url' />"
}
