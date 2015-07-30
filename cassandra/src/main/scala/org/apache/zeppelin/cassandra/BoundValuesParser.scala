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
package org.apache.zeppelin.cassandra

import java.text.SimpleDateFormat
import java.util.{Date}

import scala.util.parsing.combinator._

/**
 * Parser of bound values passed into @bind parameters
 */
class BoundValuesParser extends RegexParsers with JavaTokenParsers {

  val STANDARD_DATE_PATTERN = """(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})""".r
  val ACCURATE_DATE_PATTERN = """(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})""".r

  def value : Parser[String] = "null" | "true" | "false" | zeppelinVariable |
    map | list | set | tuple| udt |
    decimal | integer | standardDate | quotedString


  def integer: Parser[String] = """\d+""".r ^^{_.toLong.toString}

  def decimal: Parser[String] = """[+-]?(?:(?:\d+\.(?:\d*)?)|(?:\.\d+))""".r ^^{_.toDouble.toString}

  def standardDate: Parser[String] = s"""'${STANDARD_DATE_PATTERN.toString}(?:\\.\\d{3})?'""".r ^^{_.replaceAll("'","")}

  def quotedString: Parser[String] = """'[^']+'""".r //^^ {_.replaceAll("(?<!')'","")}

  def list: Parser[String] = "["~>repsep(value, ",")<~"]" ^^ {_.mkString("[",",","]")}

  def set: Parser[String] = "{"~>repsep(value, ",")<~"}" ^^ {_.mkString("{",",","}")}

  def map: Parser[String] = "{"~>repsep(member, ",")<~"}" ^^{_.mkString("{",", ","}")}

  def tuple: Parser[String] = "(" ~> repsep(value, ",") <~ ")" ^^{_.mkString("(",",",")")}

  def udt: Parser[String] = "{"~>repsep(udtMember, ",")<~"}" ^^{_.mkString("{",", ","}")}

  def member: Parser[String] = quotedString ~ ":" ~ value ^^{ case name~sep~mapVal => name+": "+mapVal}

  def udtColumnName: Parser[String] = """(?:(?:[a-zA-Z][a-zA-Z0-9_]*)|(?:"[^"]+"))""".r

  def udtMember: Parser[String] = udtColumnName ~ ":" ~ value ^^{ case name~sep~mapVal => name+": "+mapVal}

  def zeppelinVariable: Parser[String] = "{{"~"""\w+=[^}]+""".r~"}}" ^^{case prefix~variable~suffix => prefix+variable+suffix}

  def values: Parser[List[String]] = repsep(value, ",")

}
