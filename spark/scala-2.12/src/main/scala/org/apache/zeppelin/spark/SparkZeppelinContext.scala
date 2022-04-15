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
import org.apache.zeppelin.interpreter.{ZeppelinContext, InterpreterContext, InterpreterHookRegistry}

import scala.collection.Seq
import scala.collection.JavaConverters._


/**
  * ZeppelinContext for Spark
  */
class SparkZeppelinContext(val sc: SparkContext,
                           val sparkShims: SparkShims,
                           val hooks2: InterpreterHookRegistry,
                           val maxResult2: Int) extends ZeppelinContext(hooks2, maxResult2) {

  private val interpreterClassMap = Map(
    "spark" -> "org.apache.zeppelin.spark.SparkInterpreter",
    "sql" -> "org.apache.zeppelin.spark.SparkSqlInterpreter",
    "pyspark" -> "org.apache.zeppelin.spark.PySparkInterpreter",
    "ipyspark" -> "org.apache.zeppelin.spark.IPySparkInterpreter",
    "r" -> "org.apache.zeppelin.spark.SparkRInterpreter",
    "kotlin" -> "org.apache.zeppelin.spark.KotlinSparkInterpreter"
  )

  private val supportedClasses = scala.collection.mutable.ArrayBuffer[Class[_]]()

  try {
    supportedClasses += Class.forName("org.apache.spark.sql.Dataset")
  } catch {
    case e: ClassNotFoundException =>
  }

  try {
    supportedClasses += Class.forName("org.apache.spark.sql.DataFrame")
  } catch {
    case e: ClassNotFoundException =>

  }
  if (supportedClasses.isEmpty) throw new RuntimeException("Can not load Dataset/DataFrame class")

  override def getSupportedClasses: util.List[Class[_]] = supportedClasses.asJava

  override def getInterpreterClassMap: util.Map[String, String] = interpreterClassMap.asJava

  override def showData(obj: Any, maxResult: Int): String = sparkShims.showDataFrame(obj, maxResult, interpreterContext)

  /**
   * create paragraph level of dynamic form of Select with no item selected.
   *
   * @param name
   * @param options
   * @return text value of selected item
   */
  @ZeppelinApi
  def select(name: String, options: Seq[(Any, String)]): Any = select(name, options, null: Any)

  /**
   * create paragraph level of dynamic form of Select with default selected item.
   *
   * @param name
   * @param defaultValue
   * @param options
   * @return text value of selected item
   */
  @Deprecated
  @ZeppelinApi
  def select(name: String, defaultValue: Any, options: Seq[(Any, String)]): Any =
    select(name, options.map(e => new ParamOption(e._1, e._2)).toArray, defaultValue)

  /**
   * create paragraph level of dynamic form of Select with default selected item.
   *
   * @param name
   * @param options
   * @param defaultValue
   * @return text value of selected item
   */
  @ZeppelinApi
  def select(name: String, options: Seq[(Any, String)], defaultValue: Any): Any =
    select(name, options.map(e => new ParamOption(e._1, e._2)).toArray, defaultValue)


  /**
   * create note level of dynamic form of Select with no item selected.
   *
   * @param name
   * @param options
   * @return text value of selected item
   */
  @ZeppelinApi
  def noteSelect(name: String, options: Seq[(Any, String)]): Any =
    noteSelect(name, null, options.map(e => new ParamOption(e._1, e._2)).toArray)

  /**
   * create note level of dynamic form of Select with default selected item.
   *
   * @param name
   * @param options
   * @param defaultValue
   * @return text value of selected item
   */
  @ZeppelinApi
  def noteSelect(name: String, options: Seq[(Any, String)], defaultValue: Any): Any =
    noteSelect(name, options.map(e => new ParamOption(e._1, e._2)).toArray, defaultValue)

  /**
   * create note level of dynamic form of Select with default selected item.
   *
   * @param name
   * @param defaultValue
   * @param options
   * @return text value of selected item
   */
  @Deprecated
  @ZeppelinApi
  def noteSelect(name: String,  defaultValue: Any, options: Seq[(Any, String)]): Any =
    noteSelect(name, options.map(e => new ParamOption(e._1, e._2)).toArray, defaultValue)

  /**
   * create paragraph level of dynamic form of checkbox with no item checked.
   *
   * @param name
   * @param options
   * @return list of checked values of this checkbox
   */
  @ZeppelinApi
  def checkbox(name: String, options: Seq[(Any, String)]): Seq[Any] = {
    val javaResult = checkbox(name, options.map(e => new ParamOption(e._1, e._2)).toArray)
    javaResult.asScala
  }

  /**
   * create paragraph level of dynamic form of checkbox with default checked items.
   *
   * @param name
   * @param options
   * @param defaultChecked
   * @return list of checked values of this checkbox
   */
  @ZeppelinApi
  def checkbox(name: String, options: Seq[(Any, String)], defaultChecked: Seq[Any]): Seq[Any] = {
    val defaultCheckedList = defaultChecked.asJava
    val optionsArray = options.map(e => new ParamOption(e._1, e._2)).toArray
    val javaResult = checkbox(name, optionsArray, defaultCheckedList)
    javaResult.asScala
  }

  /**
   * create note level of dynamic form of checkbox with no item checked.
   *
   * @param name
   * @param options
   * @return list of checked values of this checkbox
   */
  @ZeppelinApi
  def noteCheckbox(name: String, options: Seq[(Any, String)]): Seq[Any] = {
    val javaResult = noteCheckbox(name, options.map(e => new ParamOption(e._1, e._2)).toArray)
    javaResult.asScala
  }

  /**
   * create note level of dynamic form of checkbox with default checked items.
   *
   * @param name
   * @param options
   * @param defaultChecked
   * @return list of checked values of this checkbox
   */
  @ZeppelinApi
  def noteCheckbox(name: String, options: Seq[(Any, String)], defaultChecked: Seq[Any]): Seq[Any] = {
    val javaResult = noteCheckbox(name,
      options.map(e => new ParamOption(e._1, e._2)).toArray, defaultChecked.asJava)
    javaResult.asScala
  }

  @ZeppelinApi
  def angularWatch(name: String, func: (AnyRef, AnyRef) => Unit): Unit = {
    angularWatch(name, interpreterContext.getNoteId, func)
  }

  @deprecated
  def angularWatchGlobal(name: String, func: (AnyRef, AnyRef) => Unit): Unit = {
    angularWatch(name, null, func)
  }

  @ZeppelinApi
  def angularWatch(name: String, func: (AnyRef, AnyRef, InterpreterContext) => Unit): Unit = {
    angularWatch(name, interpreterContext.getNoteId, func)
  }

  @deprecated
  def angularWatchGlobal(name: String,
                         func: (AnyRef, AnyRef, InterpreterContext) => Unit): Unit = {
    angularWatch(name, null, func)
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

  def getAsDataFrame(name: String): Object = {
    sparkShims.getAsDataFrame(get(name).toString)
  }
}
