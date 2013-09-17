/*
 * Copyright (C) 2012 The Regents of The University California.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nflabs.zeppelin.shark

import java.util.{ArrayList => JavaArrayList, HashSet => JavaHashSet}
import scala.collection.JavaConversions._

import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory
import org.apache.hadoop.hive.ql.hooks.{ReadEntity, WriteEntity}
import org.apache.hadoop.hive.ql.plan.{DDLWork, CreateTableDesc}
import org.apache.hadoop.hive.metastore.api.FieldSchema

import shark.api.DataTypes
import org.apache.hadoop.hive.ql.exec.DDLTask
import org.apache.hadoop.hive.conf.HiveConf


private[shark] object HiveUtils {

  private val timestampManfiest = classManifest[java.sql.Timestamp]
  private val stringManifest = classManifest[String]

 
  def getJavaPrimitiveObjectInspector(m: ClassManifest[_]): PrimitiveObjectInspector = m match {
    case Manifest.Boolean => PrimitiveObjectInspectorFactory.javaBooleanObjectInspector
    case Manifest.Byte => PrimitiveObjectInspectorFactory.javaByteObjectInspector
    case Manifest.Short => PrimitiveObjectInspectorFactory.javaShortObjectInspector
    case Manifest.Int => PrimitiveObjectInspectorFactory.javaIntObjectInspector
    case Manifest.Long => PrimitiveObjectInspectorFactory.javaLongObjectInspector
    case Manifest.Float => PrimitiveObjectInspectorFactory.javaFloatObjectInspector
    case Manifest.Double => PrimitiveObjectInspectorFactory.javaDoubleObjectInspector
    case Manifest.Unit => PrimitiveObjectInspectorFactory.javaVoidObjectInspector
    case `timestampManfiest` => PrimitiveObjectInspectorFactory.javaTimestampObjectInspector
    case `stringManifest` => PrimitiveObjectInspectorFactory.javaStringObjectInspector
  }
  
    /**
   * Execute the create table DDL operation against Hive's metastore.
   */
  def createTable(
      tableName: String,
      columnNames: Seq[String],
      columnTypes: Seq[ClassManifest[_]]): Boolean =
  {
    val schema = columnNames.zip(columnTypes).map { case (colName, manifest) =>
      new FieldSchema(colName, DataTypes.fromManifest(manifest).hiveName, "")
    }

    // Setup the create table descriptor with necessary information.
    val desc = new CreateTableDesc()
    desc.setTableName(tableName)
    desc.setCols(new JavaArrayList[FieldSchema](schema))
    desc.setTblProps(Map("shark.cache" -> "heap"))
    desc.setInputFormat("org.apache.hadoop.mapred.TextInputFormat")
    desc.setOutputFormat("org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat")
    desc.setSerName(classOf[shark.memstore2.ColumnarSerDe].getName)
    desc.setNumBuckets(-1)
    // Execute the create table against the metastore.
    val work = new DDLWork(new JavaHashSet[ReadEntity], new JavaHashSet[WriteEntity], desc)
    val task = new DDLTask
    task.initialize(new HiveConf, null, null)
    task.setWork(work)

    // Hive returns 0 if the create table command is executed successfully.
    task.execute(null) == 0
  }
}
