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

import org.apache.zeppelin.interpreter.InterpreterResult.Code
import org.apache.zeppelin.interpreter.InterpreterResult.Code.{SUCCESS, ERROR}
import org.apache.zeppelin.interpreter.InterpreterResult.Type
import org.apache.zeppelin.interpreter.InterpreterResult.Type.{TEXT, HTML, TABLE, IMG}
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document

import scala.collection.JavaConversions._

import scala.util.matching.Regex

case class RDisplay(content: String, `type`: Type, code: Code)

object ZeppelinRDisplay {

  val pattern = new Regex("""^ *\[\d*\] """)

  def render(html: String, imageWidth: String): RDisplay = {

    val document = Jsoup.parse(html)
    document.outputSettings().prettyPrint(false)

    val body = document.body()

    if (body.getElementsByTag("p").isEmpty) return RDisplay(body.html(), HTML, SUCCESS)

    val bodyHtml = body.html()

    if (! bodyHtml.contains("<img")
      &&  ! bodyHtml.contains("<script")
      && ! bodyHtml.contains("%html ")
      && ! bodyHtml.contains("%table ")
      && ! bodyHtml.contains("%img ")
    ) {
      return textDisplay(body)
    }

    if (bodyHtml.contains("%table")) {
      return tableDisplay(body)
    }

    if (bodyHtml.contains("%img")) {
      return imgDisplay(body)
    }

    return htmlDisplay(body, imageWidth)

  }

  private def textDisplay(body: Element): RDisplay = {
    RDisplay(body.getElementsByTag("p").first().html(), TEXT, SUCCESS)
  }

  private def tableDisplay(body: Element): RDisplay = {
    val p = body.getElementsByTag("p").first().html.replace("“%table " , "").replace("”", "")
    val r = (pattern findFirstIn p).getOrElse("")
    val table = p.replace(r, "").replace("\\t", "\t").replace("\\n", "\n")
    RDisplay(table, TABLE, SUCCESS)
  }

  private def imgDisplay(body: Element): RDisplay = {
    val p = body.getElementsByTag("p").first().html.replace("“%img " , "").replace("”", "")
    val r = (pattern findFirstIn p).getOrElse("")
    val img = p.replace(r, "")
    RDisplay(img, IMG, SUCCESS)
  }

  private def htmlDisplay(body: Element, imageWidth: String): RDisplay = {

    var div = new String()

    for (element <- body.children) {

      val eHtml = element.html()
      var eOuterHtml = element.outerHtml()

      eOuterHtml = eOuterHtml.replace("“%html " , "").replace("”", "")

      val r = (pattern findFirstIn eHtml).getOrElse("")

      div = div + eOuterHtml.replace(r, "")

    }

    val content =  div
      .replaceAll("src=\"//", "src=\"http://")
      .replaceAll("href=\"//", "href=\"http://")

    body.html(content)

    for (image <- body.getElementsByTag("img")) {
      image.attr("width", imageWidth)
    }

    RDisplay(body.html, HTML, SUCCESS)

  }

}
