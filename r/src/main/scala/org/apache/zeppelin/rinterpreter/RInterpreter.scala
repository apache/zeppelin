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

import java.io.{BufferedInputStream, File, FileInputStream}
import java.nio.file.{Files, Paths}
import java.util._

import org.apache.commons.codec.binary.{Base64, StringUtils}
import org.apache.zeppelin.interpreter.Interpreter.FormType
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter
import org.apache.zeppelin.interpreter.{InterpreterContext, _}
import org.apache.zeppelin.scheduler.Scheduler
import org.apache.zeppelin.spark.SparkInterpreter
import org.jsoup.Jsoup
import org.jsoup.nodes._
import org.jsoup.select.Elements
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConversions._
import scala.io.Source

abstract class RInterpreter(properties : Properties, startSpark : Boolean = true) extends Interpreter (properties) {

  protected val logger: Logger = RInterpreter.logger
  logger.trace("Initialising an RInterpreter of class " + this.getClass.getName)

  def getrContext: RContext = rContext

  protected lazy val rContext : RContext = synchronized{ RContext(property, this.getInterpreterGroup().getId()) }

  def open: Unit = rContext.synchronized {
    logger.trace("RInterpreter opening")
    // We leave this as an Option[] because the pattern of nesting SparkInterpreter inside of wrapper interpreters
    // has changed several times, and this allows us to fail more gracefully and handle those changes in one place.
    val intp : Option[SparkInterpreter] =  getSparkInterpreter()
    rContext.open(intp)
    rContext.testRPackage("htmltools", message =
      """You can continue
        | without it, but some interactive visualizations will fail.
        | You can install it from cran."""")
    rContext.testRPackage("repr", license = true, message =
      """You can continue
        | without it, but some forms of output from the REPL may not appear properly."""")
    rContext.testRPackage("base64enc", license = true, message =
      """You can continue
        | without it, but the REPL may not show images properly.""")
    rContext.testRPackage("evaluate", license = false, message =
      """
        |The REPL needs this to run.  It can be installed from CRAN
        | Thanks to Hadley Wickham and Yihui Xie for graciously making evaluate available under an Apache-compatible
        | license so it can be used with this project.""".stripMargin)
  }

  def getSparkInterpreter() : Option[SparkInterpreter] =
    getSparkInterpreter(getInterpreterInTheSameSessionByClassName(classOf[SparkInterpreter].getName))

  def getSparkInterpreter(p1 : Interpreter) : Option[SparkInterpreter] = p1 match {
    case s : SparkInterpreter => Some[SparkInterpreter](s)
    case lzy : LazyOpenInterpreter => {
      val p = lzy.getInnerInterpreter
      lzy.open()
      return getSparkInterpreter(p)
    }
    case w : WrappedInterpreter => return getSparkInterpreter(w.getInnerInterpreter)
    case _ => None
  }

  def close: Unit = {
    rContext.close
  }

  def getProgress(context :InterpreterContext): Int  = rContext.getProgress

  def cancel(context:InterpreterContext) : Unit = {}

  def getFormType: FormType = {
    return FormType.NONE
  }

  override def getScheduler : Scheduler = rContext.getScheduler

  // TODO:  completion is disabled because it could not be tested with current Zeppelin code
  /*def completion(buf :String,cursor : Int) : List[String] = Array[String]("").toList

  private[rinterpreter] def hiddenCompletion(buf :String,cursor : Int) : List[String] =
    rContext.evalS1(s"""
       |rzeppelin:::.z.completion("$buf", $cursor)
     """.stripMargin).toList*/
}

object RInterpreter {

  private val logger: Logger = LoggerFactory.getLogger(getClass)
  logger.trace("logging inside the RInterpreter singleton")

  // These are the additional properties we need on top of the ones provided by the spark interpreters
  lazy val props: Map[String, InterpreterProperty] = new InterpreterPropertyBuilder()
    .add("rhadoop.cmd",          "HADOOP_CMD", "rhadoop.cmd", "", "Usually /usr/bin/hadoop")
    .add("rhadooop.streamingjar", "HADOOP_STREAMING", "rhadooop.streamingjar", "", "Usually /usr/lib/hadoop/contrib/streaming/hadoop-streaming-<version>.jar")
    .add("rscala.debug",          "RSCALA_DEBUG", "rscala.debug","false", "Whether to turn on rScala debugging") // TEST:  Implemented but not tested
    .add("rscala.timeout",        "RSCALA_TIMEOUT", "rscala.timeout","60", "Timeout for rScala") // TEST:  Implemented but not tested
    .build

  def getProps() = {
    props
  }

  // Some R interactive visualization packages insist on producing HTML that refers to javascript
  // or css by file path.  These functions are intended to load those files and embed them into the
  // HTML as Base64 encoded DataURIs.
  //FIXME These don't error but may not yet properly be converting script links
  def scriptToBase(doc : Element, testAttr : String, tag : String, mime : String): Unit = {
    val elems : Elements = doc.getElementsByTag(tag)
    elems.filter( (e : Element) => {
      e.attributes().hasKey(testAttr) && e.attr(testAttr) != "" && e.attr(testAttr).slice(0,1) == "/"
    }).foreach(scriptToBase(_, testAttr, mime))
  }

  def scriptToBase(node : Element, field : String, mime : String) : Unit = node.attr(field) match {
    case x if Files.exists(Paths.get(x)) => node.attr(field, dataURI(x, mime))
    case x if x.slice(0,4) == "http" => {}
    case x if x.contains("ajax") => {}
    case x if x.contains("googleapis") => {}
    case x if x.slice(0,2) == "//" => node.attr(field, "http:" + x)
    case _ => {}
  }

  def dataURI(file : String, mime : String) : String = {
    val fp = new File(file)
    val fdata = new Array[Byte](fp.length().toInt)
    val fin = new BufferedInputStream(new FileInputStream(fp))
    try {
      fin.read(fdata)
    } finally {
      fin.close()
    }
    s"""data:${mime};base64,""" + StringUtils.newStringUtf8(Base64.encodeBase64(fdata, false))
  }

  // The purpose here is to deal with knitr producing HTML with script and css tags outside the <body>
  def processHTML(input: Array[String]): String = processHTML(input.mkString("\n"))

  def processHTML(input: String) : String = {
		val doc : Document = Jsoup.parse(input)
    processHTML(doc)
	}

	private def processHTML(doc : Document) : String = {
    val bod : Element = doc.body()
    val head : Element = doc.head()
    // Try to ignore the knitr script that breaks zeppelin display
		head.getElementsByTag("script").reverseIterator.foreach(bod.prependChild(_))
    // Only get css from head if it links to a file
    head.getElementsByTag("link").foreach(bod.prependChild(_))
    scriptToBase(bod, "href", "link", "text/css")
    scriptToBase(bod, "src", "script", "text/javascript")
    bod.html()
	}
}
