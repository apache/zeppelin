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

package org.apache.zeppelin.spark2

import java.io.IOException
import java.lang.reflect.Method
import java.util
import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.SparkContext
import org.apache.spark.sql.{SQLContext, DataFrame, Row}
import org.apache.spark.sql.SparkSession
import org.apache.zeppelin.display.{AngularObjectWatcher, AngularObjectRegistry, AngularObject, GUI}
import org.apache.zeppelin.interpreter.{InterpreterContextRunner, InterpreterContext, InterpreterException}
import org.apache.zeppelin.resource.{ResourceSet, Resource, ResourcePool}
import org.apache.zeppelin.spark2.dep.SparkDependencyResolver
import org.apache.zeppelin.display.Input

import scala.collection.JavaConversions._

/**
 * Spark context for zeppelin.
 */
object ZeppelinContext {

  def showDF(z: ZeppelinContext, df: DataFrame): String = {
    return showDF(z.sc, z.interpreterContext, df, z.maxResult)
  }

  def showDF(sc: SparkContext, interpreterContext: InterpreterContext, df: DataFrame, maxResult: Int): String = {
    var rows: Array[Row] = null
    var take: Method = null
    val jobGroup: String = "zeppelin-" + interpreterContext.getParagraphId
    sc.setJobGroup(jobGroup, "Zeppelin", false)
    try {
      take = df.getClass.getMethod("take", classOf[Int])
      rows = df.take(maxResult + 1)
    }
    catch {
      case e: Any => {
        sc.clearJobGroup
        throw new InterpreterException(e)
      }
    }
    var columns: List[Attribute] = null
    try {
      val qe: AnyRef = df.getClass.getMethod("queryExecution").invoke(df)
      val a: AnyRef = qe.getClass.getMethod("analyzed").invoke(qe)
      val seq: Seq[Any] = a.getClass.getMethod("output").invoke(a).asInstanceOf[Seq[Any]]
      columns = scala.collection.JavaConverters.seqAsJavaListConverter(seq).asJava.asInstanceOf[List[Attribute]]
    }
    catch {
      case e: Any => {
        throw new InterpreterException(e)
      }
    }
    var msg: StringBuilder = new StringBuilder
    msg.append("%table ")
    import scala.collection.JavaConversions._
    for (col <- columns) {
      msg.append(col.name + "\t")
    }
    val trim: String = msg.toString.trim
    msg = new StringBuilder(trim)
    msg.append("\n")
    try { {
      var r: Int = 0
      while (r < maxResult && r < rows.length) {
        {
          val row: AnyRef = rows(r)
          val isNullAt: Method = row.getClass.getMethod("isNullAt", classOf[Int])
          val apply: Method = row.getClass.getMethod("apply", classOf[Int])
          var i: Int = 0
          /*
            TODO(ECH)
                    while (i < columns.size) {
                      {
                        if (!isNullAt.invoke(row, i).asInstanceOf[Boolean]) {
                          msg.append(apply.invoke(row, i).toString)
                        }
                        else {
                          msg.append("null")
                        }
                        if (i != columns.size - 1) {
                          msg.append("\t")
                        }
                      }
                      ({
                        i += 1; i - 1
                      })
                    }
          */
          msg.append("\n")
        }
        ({
          r += 1; r - 1
        })
      }
    }
    }
    catch {
      case e: Any => {
        throw new InterpreterException(e)
      }
    }
    if (rows.length > maxResult) {
      msg.append("\n<font color=red>Results are limited by " + maxResult + ".</font>")
    }
    sc.clearJobGroup
    return msg.toString
  }

}

class ZeppelinContext {
  private var dep: SparkDependencyResolver = null
  private var interpreterContext: InterpreterContext = null
  private var maxResult: Int = 0

  var sc: SparkContext = null
  var spark: SparkSession = null
  private var gui: GUI = null

  def this(sc: SparkContext, spark: SparkSession, interpreterContext: InterpreterContext, dep: SparkDependencyResolver, maxResult: Int) {
    this()
    this.sc = sc
    this.spark = spark
    this.interpreterContext = interpreterContext
    this.dep = dep
    this.maxResult = maxResult
  }

  def input(name: String): AnyRef = {
    return input(name, "")
  }

  def input(name: String, defaultValue: AnyRef): AnyRef = {
    return gui.input(name, defaultValue)
  }

  def select(name: String, options: Iterable[(AnyRef, String)]): AnyRef = {
    return select(name, "", options)
  }

  def select(name: String, defaultValue: AnyRef, options: Iterable[(AnyRef, String)]): AnyRef = {
    return gui.select(name, defaultValue, tuplesToParamOptions(options))
  }

  def checkbox(name: String, options: Iterable[(AnyRef, String)]): Iterable[AnyRef] = {
    val allChecked: util.List[AnyRef] = new util.LinkedList[AnyRef]
    import scala.collection.JavaConversions._
    for (option <- asJavaIterable(options)) {
      allChecked.add(option._1)
    }
    return checkbox(name, collectionAsScalaIterable(allChecked), options)
  }

  def checkbox(name: String, defaultChecked: Iterable[AnyRef], options: Iterable[(AnyRef, String)]): Iterable[AnyRef] = {
    return collectionAsScalaIterable(gui.checkbox(name, asJavaCollection(defaultChecked), tuplesToParamOptions(options)))
  }

  private def tuplesToParamOptions(options: Iterable[(AnyRef, String)]): Array[Input.ParamOption] = {
    val n: Int = options.size
    val paramOptions: Array[Input.ParamOption] = new Array[Input.ParamOption](n)
    val it: Iterator[(AnyRef, String)] = asJavaIterable(options).iterator
    val i: Int = 0
/*
TODO(ECH)
    while (it.hasNext) {
      val valueAndDisplayValue: (AnyRef, String) = it.next
      paramOptions(({
        i += 1; i - 1
      })) = new Input.ParamOption(valueAndDisplayValue._1, valueAndDisplayValue._2)
    }
*/
    return paramOptions
  }

  def setGui(o: GUI) {
    this.gui = o
  }

  private def restartInterpreter {
  }

  def getInterpreterContext: InterpreterContext = {
    return interpreterContext
  }

  def setInterpreterContext(interpreterContext: InterpreterContext) {
    this.interpreterContext = interpreterContext
  }

  def setMaxResult(maxResult: Int) {
    this.maxResult = maxResult
  }

  /**
   * show DataFrame
   * @param o DataFrame object
   */
  def show(o: DataFrame) {
    show(o, maxResult)
  }

  /**
   * show DataFrame or SchemaRDD
   * @param o DataFrame or SchemaRDD object
   * @param maxResult maximum number of rows to display
   */
  def show(o: DataFrame, maxResult: Int) {
    var cls: Class[_] = null
    try {
      cls = Class.forName("org.apache.spark.sql.DataFrame")
    }
    catch {
      case e: ClassNotFoundException => {
      }
    }
    if (cls == null) {
      throw new InterpreterException("Can not road DataFrame/SchemaRDD class")
    }
    try {
      if (cls.isInstance(o)) {
        interpreterContext.out.write(ZeppelinContext.showDF(sc, interpreterContext, o, maxResult))
      }
      else {
        interpreterContext.out.write(o.toString)
      }
    }
    catch {
      case e: IOException => {
        throw new InterpreterException(e)
      }
    }
  }

  /**
   * Run paragraph by id
   * @param id
   */
  def run(id: String) {
    run(id, interpreterContext)
  }

  /**
   * Run paragraph by id
   * @param id
   * @param context
   */
  def run(id: String, context: InterpreterContext) {
    if (id == context.getParagraphId) {
      throw new InterpreterException("Can not run current Paragraph")
    }
    import scala.collection.JavaConversions._
    for (r <- context.getRunners) {
      if (id == r.getParagraphId) {
        r.run
        return
      }
    }
    throw new InterpreterException("Paragraph " + id + " not found")
  }

  /**
   * Run paragraph at idx
   * @param idx
   */
  def run(idx: Int) {
    run(idx, interpreterContext)
  }

  /**
   * Run paragraph at index
   * @param idx index starting from 0
   * @param context interpreter context
   */
  def run(idx: Int, context: InterpreterContext) {
    if (idx >= context.getRunners.size) {
      throw new InterpreterException("Index out of bound")
    }
    val runner: InterpreterContextRunner = context.getRunners.get(idx)
    if (runner.getParagraphId == context.getParagraphId) {
      throw new InterpreterException("Can not run current Paragraph")
    }
    runner.run
  }

  def run(paragraphIdOrIdx: List[AnyRef]) {
    run(paragraphIdOrIdx, interpreterContext)
  }

  /**
   * Run paragraphs
   * @param paragraphIdOrIdx list of paragraph id or idx
   */
  def run(paragraphIdOrIdx: List[AnyRef], context: InterpreterContext) {
    import scala.collection.JavaConversions._
    for (idOrIdx <- paragraphIdOrIdx) {
      if (idOrIdx.isInstanceOf[String]) {
        val id: String = idOrIdx.asInstanceOf[String]
        run(id, context)
      }
      else if (idOrIdx.isInstanceOf[Integer]) {
        val idx: Integer = idOrIdx.asInstanceOf[Integer]
        run(idx, context)
      }
      else {
        throw new InterpreterException("Paragraph " + idOrIdx + " not found")
      }
    }
  }

  def runAll {
    runAll(interpreterContext)
  }

  /**
   * Run all paragraphs. except this.
   */
  def runAll(context: InterpreterContext) {
    for (r <- context.getRunners) {
      if (r.getParagraphId == context.getParagraphId) {
        // TODO(ECH)
        // continue //todo: continue is not supported
      }
      r.run
    }
  }

  def listParagraphs: java.util.List[String] = {
    val paragraphs: java.util.List[String] = new util.LinkedList[String]
    import scala.collection.JavaConversions._
    for (r <- interpreterContext.getRunners) {
      paragraphs.add(r.getParagraphId)
    }
    return paragraphs
  }

  private def getAngularObject(name: String, interpreterContext: InterpreterContext): AngularObject[_] = {
    val registry: AngularObjectRegistry = interpreterContext.getAngularObjectRegistry
    val noteId: String = interpreterContext.getNoteId
    val paragraphAo: AngularObject[_] = registry.get(name, noteId, interpreterContext.getParagraphId)
    val noteAo: AngularObject[_] = registry.get(name, noteId, null)
    var ao: AngularObject[_] = if (paragraphAo != null) paragraphAo else noteAo
    if (ao == null) {
      ao = registry.get(name, null, null)
    }
    return ao
  }

  /**
   * Get angular object. Look up notebook scope first and then global scope
   * @param name variable name
   * @return value
   */
  def angular(name: String): AnyRef = {
    val ao: AngularObject[_] = getAngularObject(name, interpreterContext)
    if (ao == null) {
      return null
    }
    else {
      return ao.get
    }
  }

  /**
   * Get angular object. Look up global scope
   * @param name variable name
   * @return value
   */
  def angularGlobal(name: String): AnyRef = {
    val registry: AngularObjectRegistry = interpreterContext.getAngularObjectRegistry
    val ao: AngularObject[_] = registry.get(name, null, null)
    if (ao == null) {
      return null
    }
    else {
      return ao.get
    }
  }

  /**
   * Create angular variable in notebook scope and bind with front end Angular display system.
   * If variable exists, it'll be overwritten.
   * @param name name of the variable
   * @param o value
   */
  def angularBind(name: String, o: AnyRef) {
    angularBind(name, o, interpreterContext.getNoteId)
  }

  /**
   * Create angular variable in global scope and bind with front end Angular display system.
   * If variable exists, it'll be overwritten.
   * @param name name of the variable
   * @param o value
   */
  def angularBindGlobal(name: String, o: AnyRef) {
    angularBind(name, o, null.asInstanceOf[String])
  }

  /**
   * Create angular variable in local scope and bind with front end Angular display system.
   * If variable exists, value will be overwritten and watcher will be added.
   * @param name name of variable
   * @param o value
   * @param watcher watcher of the variable
   */
  def angularBind(name: String, o: AnyRef, watcher: AngularObjectWatcher) {
    angularBind(name, o, interpreterContext.getNoteId, watcher)
  }

  /**
   * Create angular variable in global scope and bind with front end Angular display system.
   * If variable exists, value will be overwritten and watcher will be added.
   * @param name name of variable
   * @param o value
   * @param watcher watcher of the variable
   */
  def angularBindGlobal(name: String, o: AnyRef, watcher: AngularObjectWatcher) {
    angularBind(name, o, null, watcher)
  }

  /**
   * Add watcher into angular variable (local scope)
   * @param name name of the variable
   * @param watcher watcher
   */
  def angularWatch(name: String, watcher: AngularObjectWatcher) {
    angularWatch(name, interpreterContext.getNoteId, watcher)
  }

  /**
   * Add watcher into angular variable (global scope)
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
    angularUnwatch(name, interpreterContext.getNoteId, watcher)
  }

  /**
   * Remove watcher from angular variable (global)
   * @param name
   * @param watcher
   */
  def angularUnwatchGlobal(name: String, watcher: AngularObjectWatcher) {
    angularUnwatch(name, null, watcher)
  }

  /**
   * Remove all watchers for the angular variable (local)
   * @param name
   */
  def angularUnwatch(name: String) {
    angularUnwatch(name, interpreterContext.getNoteId)
  }

  /**
   * Remove all watchers for the angular variable (global)
   * @param name
   */
  def angularUnwatchGlobal(name: String) {
    angularUnwatch(name, null.asInstanceOf[String])
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

  /**
   * Create angular variable in notebook scope and bind with front end Angular display system.
   * If variable exists, it'll be overwritten.
   * @param name name of the variable
   * @param o value
   */
  private def angularBind(name: String, o: AnyRef, noteId: String) {
    val registry: AngularObjectRegistry = interpreterContext.getAngularObjectRegistry
    if (registry.get(name, noteId, null) == null) {
      registry.add(name, o, noteId, null)
    }
    else {
      val a = registry.get(name, noteId, null).asInstanceOf[AngularObject[AnyRef]].set(o)
    }
  }

  /**
   * Create angular variable in notebook scope and bind with front end Angular display
   * system.
   * If variable exists, value will be overwritten and watcher will be added.
   * @param name name of variable
   * @param o value
   * @param watcher watcher of the variable
   */
  private def angularBind(name: String, o: AnyRef, noteId: String, watcher: AngularObjectWatcher) {
    val registry: AngularObjectRegistry = interpreterContext.getAngularObjectRegistry
    if (registry.get(name, noteId, null) == null) {
      registry.add(name, o, noteId, null)
    }
    else {
      registry.get(name, noteId, null).asInstanceOf[AngularObject[AnyRef]].set(o)
    }
    angularWatch(name, watcher)
  }

  /**
   * Add watcher into angular binding variable
   * @param name name of the variable
   * @param watcher watcher
   */
  private def angularWatch(name: String, noteId: String, watcher: AngularObjectWatcher) {
    val registry: AngularObjectRegistry = interpreterContext.getAngularObjectRegistry
    if (registry.get(name, noteId, null) != null) {
      registry.get(name, noteId, null).addWatcher(watcher)
    }
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
  private def angularUnwatch(name: String, noteId: String, watcher: AngularObjectWatcher) {
    val registry: AngularObjectRegistry = interpreterContext.getAngularObjectRegistry
    if (registry.get(name, noteId, null) != null) {
      registry.get(name, noteId, null).removeWatcher(watcher)
    }
  }

  /**
   * Remove all watchers for the angular variable
   * @param name
   */
  private def angularUnwatch(name: String, noteId: String) {
    val registry: AngularObjectRegistry = interpreterContext.getAngularObjectRegistry
    if (registry.get(name, noteId, null) != null) {
      registry.get(name, noteId, null).clearAllWatchers
    }
  }

  /**
   * Remove angular variable and all the watchers.
   * @param name
   */
  private def angularUnbind(name: String, noteId: String) {
    val registry: AngularObjectRegistry = interpreterContext.getAngularObjectRegistry
    registry.remove(name, noteId, null)
  }

  /**
   * Add object into resource pool
   * @param name
   * @param value
   */
  def put(name: String, value: AnyRef) {
    val resourcePool: ResourcePool = interpreterContext.getResourcePool
    resourcePool.put(name, value)
  }

  /**
   * Get object from resource pool
   * Search local process first and then the other processes
   * @param name
   * @return null if resource not found
   */
  def get(name: String): AnyRef = {
    val resourcePool: ResourcePool = interpreterContext.getResourcePool
    val resource: Resource = resourcePool.get(name)
    if (resource != null) {
      return resource.get
    }
    else {
      return null
    }
  }

  /**
   * Remove object from resourcePool
   * @param name
   */
  def remove(name: String) {
    val resourcePool: ResourcePool = interpreterContext.getResourcePool
    resourcePool.remove(name)
  }

  /**
   * Check if resource pool has the object
   * @param name
   * @return
   */
  def containsKey(name: String): Boolean = {
    val resourcePool: ResourcePool = interpreterContext.getResourcePool
    val resource: Resource = resourcePool.get(name)
    return resource != null
  }

  /**
   * Get all resources
   */
  def getAll: ResourceSet = {
    val resourcePool: ResourcePool = interpreterContext.getResourcePool
    return resourcePool.getAll
  }

}
