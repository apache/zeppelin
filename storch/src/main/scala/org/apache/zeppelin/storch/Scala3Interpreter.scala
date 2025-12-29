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
package org.apache.zeppelin.storch

import org.apache.zeppelin.interpreter.Interpreter.FormType
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion
import org.apache.zeppelin.interpreter.{Interpreter, InterpreterContext, InterpreterResult}
import org.slf4j.{Logger, LoggerFactory}

import java.io.File
import java.util
import java.util.stream.{Collectors, Stream}
import java.util.{Collections, Properties, UUID}

/**
 * scala3 interpreter
 */
object Scala3Interpreter {
  private val LOGGER = LoggerFactory.getLogger(classOf[Scala3Interpreter])
}

class Scala3Interpreter(property: Properties) extends Interpreter(property) {
  override def open(): Unit = {
  }

  override def close(): Unit = {
    /* Clean up .class files created during the compilation process. */
    Stream.of(new File(".").listFiles((f: File) => f.getAbsolutePath.endsWith(".class")))
//      .
//      forEach((f: File) => f.delete)
  }

  override def interpret(code: String, context: InterpreterContext): InterpreterResult = {
    // choosing new name to class containing Main method
    val generatedClassName = "C" + UUID.randomUUID.toString.replace("-", "")
    context.out.clear
    
    try {
      val res = "meet interpret try to success" //StaticRepl.execute(generatedClassName, code)
      new InterpreterResult(InterpreterResult.Code.SUCCESS, res)
    } catch {
      case e: Exception =>
//        LOGGER.error("Exception in Interpreter while interpret", e)
        new InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage)
    }
  }

  override def cancel(context: InterpreterContext): Unit = {
  }

  override def getFormType: Interpreter.FormType = FormType.SIMPLE

  override def getProgress(context: InterpreterContext) = 0

  override def completion(buf: String, cursor: Int, interpreterContext: InterpreterContext): util.List[InterpreterCompletion] = Collections.emptyList

//  def displayTableFromSimpleMap(keyName: String, valueName: String, rows: util.Map[?, ?]): String = {
//    var table = new StringBuilder()
//    table ++=  s"%table\n ${keyName} \t ${valueName} \n"
////    table ++= keyName ++ "\t" ++ valueName ++ "\n"
//    table ++= rows.entrySet.stream.map((e: util.Map.Entry[?, ?]) => e.getKey + "\t" + e.getValue).collect(Collectors.joining("\n"))
//    table.toString()
//  }
}