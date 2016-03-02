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

package org.apache.zeppelin.spark.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

object CsqlParserUtils {
  val DateIntervalPattern = "(?s)(.*)(interval)[(']+([0-9\\-]+)[', ]+([0-9\\-]+)[)](.*)".r
  val FancyIntervalPattern = "(?s)(.*)(interval)[(']+([0-9a-z\\-+ ]+)[',]+([0-9a-z\\-+ ]+)[)](.*)".r
  val TodayPattern = "[ ]*(today)".r
  val TodayPlusDaysPattern  = ".*(today )[ \\+]+([0-9]+) day.*".r
  val TodayMinusDaysPattern  = ".*(today )[ \\-]+([0-9]+) day.*".r
  val TodayPlusHoursPattern = ".*(today )[ \\+]+([0-9]+) hour.*".r
  val TodayMinusHoursPattern = ".*(today )[ \\-]+([0-9]+) hour.*".r
  var dateParser: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")

  /**
   * Give a query like:
   * select foo from bar where day in interval('2016-01-01', '2016-01-03')
   * Expand the result to:
   * select foo from bar where day in ('123', '124', '124')
   *
   * @return
   */
  def parseAndExpandInterval(query: String): String = {
    if (!query.contains("interval(")) return query

    var (prefix, start, end, suffix) =
      query match {
        case DateIntervalPattern(queryPrefix, label, start, end, querySuffix) =>
          println("Suffix: " + querySuffix)
          (queryPrefix, dateParser.parse(start), dateParser.parse(end), querySuffix)
        case FancyIntervalPattern(queryPrefix, label, start, end, querySuffix) =>
          (queryPrefix, toDate(start), toDate(end), querySuffix)
        case _ => throw new RuntimeException("Can't parse interval(): " + query)
      }

    if (end.before(start)) throw new RuntimeException("Start can't be after end!")
    if (end == start) throw new RuntimeException("End is exclusive and should not equal start!")

    val dates = new scala.collection.mutable.ArrayBuffer[Long]

    val intervalStart: Int = query.indexOf("interval")
    val intervalEnd: Int = query.indexOf(")", intervalStart)

    while (start.before(end)) {
      dates += TimeUnit.MILLISECONDS.toDays(start.getTime)
      start = new Date(start.getTime + TimeUnit.DAYS.toMillis(1))
    }
    val newInClause: String = "(" + dates.mkString(", ") + ")"

    prefix + newInClause + suffix
  }

  private def toDate(field: String): Date = {
    field match {
      case TodayPattern(_) => new Date()
      case TodayPlusDaysPattern(startSpecial, startIncrement) =>
        new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(startIncrement.toLong) )
      case TodayMinusDaysPattern(startSpecial, startIncrement) =>
        new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(startIncrement.toLong) )
      case TodayPlusHoursPattern(startSpecial, startIncrement) =>
        new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(startIncrement.toLong) )
      case TodayMinusHoursPattern(startSpecial, startIncrement) =>
        new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(startIncrement.toLong) )
      case _ => throw new RuntimeException(s"Invalid date: '$field'")
    }
  }
}
