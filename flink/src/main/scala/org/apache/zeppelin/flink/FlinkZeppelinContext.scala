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

import org.apache.flink.api.scala.DataSet
import org.apache.flink.streaming.api.scala._
import org.apache.flink.table.api.internal.TableImpl
import org.apache.flink.table.api.{Table, TableEnvironment}
import org.apache.flink.table.api.scala.BatchTableEnvironment
import org.apache.flink.types.Row
import org.apache.zeppelin.annotation.ZeppelinApi
import org.apache.zeppelin.display.AngularObjectWatcher
import org.apache.zeppelin.display.ui.OptionInput.ParamOption
import org.apache.zeppelin.flink.util.TableUtil
import org.apache.zeppelin.interpreter.{BaseZeppelinContext, InterpreterContext, InterpreterHookRegistry, ResultMessages}

import scala.collection.{JavaConversions, Seq}


/**
  * ZeppelinContext for Flink
  */
class FlinkZeppelinContext(val btenv: TableEnvironment,
                           val hooks2: InterpreterHookRegistry,
                           val maxResult2: Int) extends BaseZeppelinContext(hooks2, maxResult2) {

  private var currentSql: String = _

  private val interpreterClassMap = Map(
    "flink" -> "org.apache.zeppelin.flink.FlinkInterpreter",
    "bsql" -> "org.apache.zeppelin.flink.FlinkBatchSqlInterpreter",
    "ssql" -> "org.apache.zeppelin.flink.FlinkStreamSqlInterpreter",
    "pyflink" -> "org.apache.zeppelin.flink.PyFlinkInterpreter",
    "ipyflink" -> "org.apache.zeppelin.flink.IPyFlinkInterpreter"
  )

  private val supportedClasses = Seq(classOf[DataSet[_]])

  def setCurrentSql(sql: String): Unit = {
    this.currentSql = sql
  }

  override def getSupportedClasses: _root_.java.util.List[Class[_]] =
    JavaConversions.seqAsJavaList(supportedClasses)

  override def getInterpreterClassMap: _root_.java.util.Map[String, String] =
    JavaConversions.mapAsJavaMap(interpreterClassMap)

  override def showData(obj: Any, maxResult: Int): String = {
    def showTable(columnsNames: Array[String], rows: Seq[Row]): String = {
      val columnNames = obj.asInstanceOf[Table].getSchema.getFieldNames
      val builder: StringBuilder = new StringBuilder("%table ")
      builder.append(columnNames.mkString("\t"))
      builder.append("\n")
      val isLargerThanMaxResult = rows.size > maxResult
      var displayRows = rows
      if (isLargerThanMaxResult) {
        displayRows = rows.take(maxResult)
      }
      for (row <- displayRows) {
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

      if (isLargerThanMaxResult) {
        builder.append("\n")
        builder.append(ResultMessages.getExceedsLimitRowsMessage(maxResult, "zeppelin.spark.maxResult"))
      }
      // append %text at the end, otherwise the following output will be put in table as well.
      builder.append("\n%text ")
      builder.toString()
    }

    if (obj.isInstanceOf[DataSet[_]]) {
      val ds = obj.asInstanceOf[DataSet[_]]
      val env = btenv.asInstanceOf[BatchTableEnvironment]
      val table = env.fromDataSet(ds)
      val columnNames: Array[String] = table.getSchema.getFieldNames
      val dsRows: DataSet[Row] = env.toDataSet[Row](table)
      showTable(columnNames, dsRows.first(maxResult + 1).collect())
    } else if (obj.isInstanceOf[Table]) {
      if (btenv.isInstanceOf[BatchTableEnvironment]) {
        val table = obj.asInstanceOf[Table]
        val columnNames: Array[String] = table.getSchema.getFieldNames
        val dsRows: DataSet[Row] = btenv.asInstanceOf[BatchTableEnvironment].toDataSet[Row](table)
        showTable(columnNames, dsRows.first(maxResult + 1).collect())
      } else {
        var rows = TableUtil.collect(obj.asInstanceOf[TableImpl], currentSql)
        val columnNames = obj.asInstanceOf[Table].getSchema.getFieldNames
        showTable(columnNames, rows)
      }
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