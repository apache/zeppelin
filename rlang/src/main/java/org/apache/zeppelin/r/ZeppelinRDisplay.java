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

package org.apache.zeppelin.r;

import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterResult.Type;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Document.OutputSettings;
import org.jsoup.safety.Whitelist;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


class RDisplay {
  private String content;
  private Type typ;
  private Code code;

  public RDisplay(String content, Type typ, Code code) {
    this.content = content;
    this.typ = typ;
    this.code = code;
  }

  public String getContent() {
    return content;
  }

  public Type getTyp() {
    return typ;
  }

  public Code getCode() {
    return code;
  }
}

public class ZeppelinRDisplay {

  private static Pattern pattern = Pattern.compile("^ *\\[\\d*\\]");

  public static RDisplay render( String html, String imageWidth) {

    Document document = Jsoup.parse(html);
    document.outputSettings().prettyPrint(false);

    Element body = document.body();

    if (body.getElementsByTag("p").isEmpty()) {
      return new RDisplay(body.html(), Type.HTML, Code.SUCCESS);
    }

    String bodyHtml = body.html();

    if (! bodyHtml.contains("<img")
      &&  ! bodyHtml.contains("<script")
      && ! bodyHtml.contains("%html ")
      && ! bodyHtml.contains("%table ")
      && ! bodyHtml.contains("%img ")
    ) {
      return textDisplay(body);
    }

    if (bodyHtml.contains("%table")) {
      return tableDisplay(body);
    }

    if (bodyHtml.contains("%img")) {
      return imgDisplay(body);
    }

    return htmlDisplay(body, imageWidth);
  }

  private static RDisplay textDisplay(Element body) {
    // remove HTML tag while preserving whitespaces and newlines
    String text = Jsoup.clean(body.html(), "",
      Whitelist.none(), new OutputSettings().prettyPrint(false));
    return new RDisplay(text, Type.TEXT, Code.SUCCESS);
  }

  private static RDisplay tableDisplay(Element body) {
    String p = body.getElementsByTag("p").first().html().replace("“%table " , "").replace("”", "");
    Matcher matcher = pattern.matcher(p);
    if (matcher.matches()) {
      p = p.replace(matcher.group(), "");
    }
    String table = p.replace("\\t", "\t").replace("\\n", "\n");
    return new RDisplay(table, Type.TABLE, Code.SUCCESS);
  }

  private static RDisplay imgDisplay(Element body) {
    String p = body.getElementsByTag("p").first().html().replace("“%img " , "").replace("”", "");
    Matcher matcher = pattern.matcher(p);
    if (matcher.matches()) {
      p = p.replace(matcher.group(), "");
    }
    return new RDisplay(p, Type.IMG, Code.SUCCESS);
  }

  private static RDisplay htmlDisplay(Element body, String imageWidth) {
    String div = "";
    for (Element element : body.children()) {
      String eHtml = element.html();
      String eOuterHtml = element.outerHtml();

      eOuterHtml = eOuterHtml.replace("“%html " , "").replace("”", "");

      Matcher matcher = pattern.matcher(eHtml);
      if (matcher.matches()) {
        eOuterHtml = eOuterHtml.replace(matcher.group(), "");
      }

      div = div + eOuterHtml;
    }

    String content =  div
      .replaceAll("src=\"//", "src=\"http://")
      .replaceAll("href=\"//", "href=\"http://");

    body.html(content);

    for (Element image : body.getElementsByTag("img")) {
      image.attr("width", imageWidth);
    }

    return new RDisplay(body.html(), Type.HTML, Code.SUCCESS);
  }
}
