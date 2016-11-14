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

package org.apache.zeppelin.rinterpreter


// TODO:  Option for setting size of output images

import java.util._

import org.apache.zeppelin.interpreter.InterpreterContext
import org.apache.zeppelin.interpreter.InterpreterResult
import org.apache.zeppelin.rinterpreter.rscala.RException

class RReplInterpreter(property: Properties, startSpark : Boolean = true) extends RInterpreter(property, startSpark) {

 // protected val rContext : RContext = RContext(property)

  def this(property : Properties) = {
    this(property, true)
  }
  private var firstCell : Boolean = true
  def interpret(st: String, context: InterpreterContext): InterpreterResult = {
    rContext.synchronized {
      try {
        import scala.collection.immutable._
        logger.info("intrpreting " + st)
        rContext.set(".zreplin", st.split("\n"))
        rContext.eval(".zreplout <- rzeppelin:::.z.valuate(.zreplin)")

        val reslength: Int = rContext.evalI0("length(.zreplout)")
        logger.debug("Length of evaluate result is " + reslength)
        var gotError: Boolean = false
        val result: String = List.range(1, reslength + 1).map((i: Int) => {
          rContext.evalS1(s"class(.zreplout[[${i}]])") match {
            case x: Array[String] if x contains ("recordedplot") => {
              if (!rContext.testRPackage("repr", fail = false)) return new InterpreterResult(InterpreterResult.Code.ERROR,
                InterpreterResult.Type.TEXT,
                "Displaying images through the R REPL requires the repr package, which is not installed.")
              val image: String = rContext.evalS0(s"base64enc:::base64encode(repr:::repr_jpg(.zreplout[[${i}]]))")
              return new InterpreterResult(InterpreterResult.Code.SUCCESS,
                InterpreterResult.Type.IMG, image)
            }
            //TODO: If the html contains a link to a file, transform it to a DataURI.  This is necessary for htmlwidgets
            case x: Array[String] if x contains ("html") => {
              val html: String = RInterpreter.processHTML(rContext.evalS0(s"rzeppelin:::.z.repr(.zreplout[[${i}]])"))
              return new InterpreterResult(InterpreterResult.Code.SUCCESS,
                InterpreterResult.Type.HTML, html)
            }
            case x: Array[String] if x contains "data.frame" => {
              val table: Array[String] = rContext.evalS1( s"""rzeppelin:::.z.table(.zreplout[[${i}]])""")
              return new InterpreterResult(InterpreterResult.Code.SUCCESS,
                InterpreterResult.Type.TABLE,
                table.mkString(sep = "\n"))
            }
            case x: Array[String] if x contains "source" => rContext.evalS0(s".zreplout[[${i}]]" + "$src")
            case x: Array[String] if x contains "character" => rContext.evalS0(s".zreplout[[${i}]]")
            case x: Array[String] if x contains "packageStartupMessage" => if (firstCell) {""} else {
              firstCell = true
              "Package Startup Message: " + rContext.evalS1(s"rzeppelin:::.z.repr(.zreplout[[${i}]])").mkString("\n")
            }
            case x: Array[String] if x contains "simpleError" => {
              gotError = true
              val error = rContext.evalS1(s"rzeppelin:::.z.repr(.zreplout[[${i}]])").mkString("\n")
              logger.error(error)
              error
            }
            case _ => rContext.evalS1(s"rzeppelin:::.z.repr(.zreplout[[${i}]])").mkString("\n")
          }
        }).mkString("\n\n")
        return new InterpreterResult({
          if (!gotError) InterpreterResult.Code.SUCCESS
          else InterpreterResult.Code.ERROR
        }, result)
      } catch {
        case re: RException => return re.getInterpreterResult(st)
        case e: Exception => {
          logger.error("Error interpreting " + st, e)
          return new InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage() + e.getStackTrace)
        }
      }
    }
  }
}
