/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.flink

import java.util

import org.apache.flink.api.scala.DataSet
import org.apache.flink.streaming.api.scala._
import org.apache.flink.table.api.Table
import org.apache.flink.table.api.scala.BatchTableEnvironment
import org.apache.flink.types.Row
import org.apache.zeppelin.annotation.ZeppelinApi
import org.apache.zeppelin.display.AngularObjectWatcher
import org.apache.zeppelin.display.ui.OptionInput.ParamOption
import org.apache.zeppelin.interpreter.{BaseZeppelinContext, InterpreterContext,
  InterpreterHookRegistry}

import scala.collection.{JavaConversions, Seq}


/**
  * ZeppelinContext for Flink
  */
class FlinkZeppelinContext(val btenv: BatchTableEnvironment,
                           val hooks2: InterpreterHookRegistry,
                           val maxResult2: Int) extends BaseZeppelinContext(hooks2, maxResult2) {

  private val interpreterClassMap = Map(
    "flink" -> "org.apache.zeppelin.flink.FlinkInterpreter",
    "sql" -> "org.apache.zeppelin.flink.FlinkSqlInterpreter"
  )

  private val supportedClasses = Seq(classOf[DataSet[_]])

  override def getSupportedClasses: util.List[Class[_]] =
    JavaConversions.seqAsJavaList(supportedClasses)

  override def getInterpreterClassMap: util.Map[String, String] =
    JavaConversions.mapAsJavaMap(interpreterClassMap)

  override def showData(obj: Any, maxResult: Int): String = {
    def showTable(table: Table): String = {
      val columnNames: Array[String] = table.getSchema.getColumnNames
      val dsRow: DataSet[Row] = btenv.toDataSet[Row](table)
      val builder: StringBuilder = new StringBuilder("%table ")
      builder.append(columnNames.mkString("\t"))
      builder.append("\n")
      val rows = dsRow.first(maxResult).collect()
      for (row <- rows) {
        var i = 0;
        while (i < row.getArity) {
          builder.append(row.getField(i))
          i += 1
          if (i != row.getArity) {
            builder.append("\t");
          }
        }
        builder.append("\n")
      }
      // append %text at the end, otherwise the following output will be put in table as well.
      builder.append("\n%text ")
      builder.toString()
    }

    if (obj.isInstanceOf[DataSet[_]]) {
      val ds = obj.asInstanceOf[DataSet[_]]
      val table = btenv.fromDataSet(ds)
      showTable(table)
    } else if (obj.isInstanceOf[Table]) {
      showTable(obj.asInstanceOf[Table])
    } else {
      obj.toString
    }
  }


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
    val javaResult = checkbox(name, defaultCheckedList,
      options.map(e => new ParamOption(e._1, e._2)).toArray)
    JavaConversions.asScalaBuffer(javaResult)
  }

  @ZeppelinApi
  def noteSelect(name: String, options: Seq[(Any, String)]): Any = noteSelect(name, "", options)

  @ZeppelinApi
  def noteSelect(name: String, defaultValue: Any, options: Seq[(Any, String)]): AnyRef =
    noteSelect(name, defaultValue, options.map(e => new ParamOption(e._1, e._2)).toArray)

  @ZeppelinApi
  def noteCheckbox(name: String, options: Seq[(AnyRef, String)]): Seq[AnyRef] = {
    val javaResulst = noteCheckbox(name, JavaConversions.seqAsJavaList(options.map(e => e._1)),
      options.map(e => new ParamOption(e._1, e._2)).toArray)
    JavaConversions.asScalaBuffer(javaResulst)
  }

  @ZeppelinApi
  def noteCheckbox(name: String,
                   defaultChecked: Seq[AnyRef],
                   options: Seq[(AnyRef, String)]): Seq[AnyRef] = {
    val defaultCheckedList = JavaConversions.seqAsJavaList(defaultChecked)
    val javaResult = noteCheckbox(name, defaultCheckedList,
      options.map(e => new ParamOption(e._1, e._2)).toArray)
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
      override def watch(oldObject: AnyRef,
                         newObject: AnyRef,
                         context: InterpreterContext): Unit = {
        func(oldObject, newObject, context)
      }
    }
    angularWatch(name, noteId, w)
  }
}
