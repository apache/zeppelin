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

import java.io.File
import java.net.URLClassLoader
import java.util.Properties

import org.apache.zeppelin.interpreter.InterpreterContext
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion

import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.{IMain, JPrintWriter}

class FlinkScala211Interpreter(override val properties: Properties,
                               override val flinkScalaClassLoader: URLClassLoader)
  extends FlinkScalaInterpreter(properties, flinkScalaClassLoader) {

  override def completion(buf: String,
                          cursor: Int,
                          context: InterpreterContext): java.util.List[InterpreterCompletion] = {
    val completions = scalaCompletion.completer().complete(buf.substring(0, cursor), cursor).candidates
      .map(e => new InterpreterCompletion(e, e, null))
    scala.collection.JavaConversions.seqAsJavaList(completions)
  }

  override def createIMain(settings: Settings, out: JPrintWriter): IMain = new FlinkILoopInterpreter(settings, out)

  override def createSettings(): Settings = {
    val settings = new Settings()
    // Don't call settings#embeddedDefaults for scala-2.11, otherwise it could cause weird error
    settings.usejavacp.value = true
    settings.Yreplsync.value = true
    settings.classpath.value = userJars.mkString(File.pathSeparator)
    settings
  }
}
