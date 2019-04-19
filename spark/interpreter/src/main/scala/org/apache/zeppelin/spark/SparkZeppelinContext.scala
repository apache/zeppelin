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

package org.apache.zeppelin.spark

import java.util

import org.apache.spark.SparkContext
import org.apache.zeppelin.annotation.ZeppelinApi
import org.apache.zeppelin.display.AngularObjectWatcher
import org.apache.zeppelin.display.ui.OptionInput.ParamOption
import org.apache.zeppelin.interpreter.{BaseZeppelinContext, InterpreterContext, InterpreterHookRegistry}
import org.apache.zeppelin.serving.JsonApiHandler

import scala.collection.{JavaConversions, Seq}


/**
  * ZeppelinContext for Spark
  */
class SparkZeppelinContext(val sc: SparkContext,
                           val sparkShims: SparkShims,
                           val hooks2: InterpreterHookRegistry,
                           val maxResult2: Int) extends BaseZeppelinContext(hooks2, maxResult2) {

  private val interpreterClassMap = Map(
    "spark" -> "org.apache.zeppelin.spark.SparkInterpreter",
    "sql" -> "org.apache.zeppelin.spark.SparkSqlInterpreter",
    "dep" -> "org.apache.zeppelin.spark.DepInterpreter",
    "pyspark" -> "org.apache.zeppelin.spark.PySparkInterpreter",
    "ipyspark" -> "org.apache.zeppelin.spark.IPySparkInterpreter",
    "r" -> "org.apache.zeppelin.spark.SparkRInterpreter"
  )

  private val supportedClasses = scala.collection.mutable.ArrayBuffer[Class[_]]()

  try
    supportedClasses += Class.forName("org.apache.spark.sql.Dataset")
  catch {
    case e: ClassNotFoundException =>
  }

  try
    supportedClasses += Class.forName("org.apache.spark.sql.DataFrame")
  catch {
    case e: ClassNotFoundException =>

  }
  if (supportedClasses.isEmpty) throw new RuntimeException("Can not load Dataset/DataFrame class")

  override def getSupportedClasses: util.List[Class[_]] =
    JavaConversions.mutableSeqAsJavaList(supportedClasses)

  override def getInterpreterClassMap: util.Map[String, String] =
    JavaConversions.mapAsJavaMap(interpreterClassMap)

  override def showData(obj: Any, maxResult: Int): String = sparkShims.showDataFrame(obj, maxResult)

  @ZeppelinApi
  def select(name: String, options: Seq[(Any, String)]): Any = select(name, null, options)

  @ZeppelinApi
  def select(name: String, defaultValue: Any, options: Seq[(Any, String)]): Any =
    select(name, defaultValue, options.map(e => new ParamOption(e._1, e._2)).toArray)

  @ZeppelinApi
  def checkbox(name: String, options: Seq[(AnyRef, String)]): Seq[Any] = {
    val javaResult = checkbox(name, JavaConversions.seqAsJavaList(options.map(e => e._1)),
      options.map(e => new ParamOption(e._1, e._2)).toArray)
    JavaConversions.asScalaBuffer(javaResult)
  }

  @ZeppelinApi
  def checkbox(name: String, defaultChecked: Seq[AnyRef], options: Seq[(Any, String)]): Seq[Any] = {
    val defaultCheckedList = JavaConversions.seqAsJavaList(defaultChecked)
    val javaResult = checkbox(name, defaultCheckedList, options.map(e => new ParamOption(e._1, e._2)).toArray)
    JavaConversions.asScalaBuffer(javaResult)
  }

  @ZeppelinApi
  def noteSelect(name: String, options: Seq[(Any, String)]): Any = noteSelect(name, "", options)

  @ZeppelinApi
  def noteSelect(name: String, defaultValue: Any, options: Seq[(Any, String)]): AnyRef =
    noteSelect(name, defaultValue, options.map(e => new ParamOption(e._1, e._2)).toArray)

  @ZeppelinApi
  def noteCheckbox(name: String, options: Seq[(AnyRef, String)]): Seq[AnyRef] = {
    val javaResulst =noteCheckbox(name, JavaConversions.seqAsJavaList(options.map(e => e._1)),
      options.map(e => new ParamOption(e._1, e._2)).toArray)
    JavaConversions.asScalaBuffer(javaResulst)
  }

  @ZeppelinApi
  def noteCheckbox(name: String, defaultChecked: Seq[AnyRef], options: Seq[(AnyRef, String)]): Seq[AnyRef] = {
    val defaultCheckedList = JavaConversions.seqAsJavaList(defaultChecked)
    val javaResult = noteCheckbox(name, defaultCheckedList, options.map(e => new ParamOption(e._1, e._2)).toArray)
    JavaConversions.asScalaBuffer(javaResult)
  }

  @ZeppelinApi def angularWatch(name: String, func: (AnyRef, AnyRef) => Unit): Unit = {
    angularWatch(name, interpreterContext.getNoteId, func)
  }

  @deprecated def angularWatchGlobal(name: String, func: (AnyRef, AnyRef) => Unit): Unit = {
    angularWatch(name, null, func)
  }

  @ZeppelinApi def angularWatch(name: String,
                                func: (AnyRef, AnyRef, InterpreterContext) => Unit): Unit = {
    angularWatch(name, interpreterContext.getNoteId, func)
  }

  @deprecated def angularWatchGlobal(name: String,
                                     func: (AnyRef, AnyRef, InterpreterContext) => Unit): Unit = {
    angularWatch(name, null, func)
  }

  @ZeppelinApi def addRestApi[T, R](name: String,
                              func: (T) => R): Unit = {
    val handler = new JsonApiHandler[T]() {
      override def handle(t: T): AnyRef = func;
    };
    interpreterContext.addRestApi(name, handler)
  }

  private def angularWatch(name: String, noteId: String, func: (AnyRef, AnyRef) => Unit): Unit = {
    val w = new AngularObjectWatcher(getInterpreterContext) {
      override def watch(oldObject: Any, newObject: AnyRef, context: InterpreterContext): Unit = {
        func(newObject, newObject)
      }
    }
    angularWatch(name, noteId, w)
  }

  private def angularWatch(name: String, noteId: String,
                           func: (AnyRef, AnyRef, InterpreterContext) => Unit): Unit = {
    val w = new AngularObjectWatcher(getInterpreterContext) {
      override def watch(oldObject: AnyRef, newObject: AnyRef, context: InterpreterContext): Unit = {
        func(oldObject, newObject, context)
      }
    }
    angularWatch(name, noteId, w)
  }
}
