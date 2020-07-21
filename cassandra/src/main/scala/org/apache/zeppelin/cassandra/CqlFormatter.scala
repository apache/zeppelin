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

import java.net.InetAddress
import java.nio.ByteBuffer
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.{Instant, LocalDate, LocalTime, ZoneId}
import java.time.format.DateTimeFormatter
import java.util.{Locale, Properties, TimeZone}

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.DataType
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.core.data.{TupleValue, UdtValue}
import io.netty.buffer.ByteBufUtil
import org.apache.commons.lang3.LocaleUtils
import org.apache.zeppelin.interpreter.InterpreterException

import scala.collection.JavaConverters._

object CqlFormatter {
  val DEFAULT_TIMEZONE = "UTC"
  val DEFAULT_FORMAT = "human"
  val DEFAULT_FLOAT_PRECISION = 5
  val DEFAULT_DOUBLE_PRECISION = 12
  val DEFAULT_TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
  val DEFAULT_TIME_FORMAT = "HH:mm:ss.SSS"
  val DEFAULT_DATE_FORMAT = "yyyy-MM-dd"
  val DEFAULT_LOCALE = "en_US"

  val allAvailableTimezones: Set[String] = TimeZone.getAvailableIDs.toSet

  def getNumberFormatter(locale: Locale, precision: Int): DecimalFormat = {
    val df = NumberFormat.getNumberInstance(locale).asInstanceOf[DecimalFormat]
    df.applyPattern("#." + "#" * precision)
    df
  }

  def getDateTimeFormatter(locale: Locale, timeZone: ZoneId, format: String): DateTimeFormatter = {
    try {
      DateTimeFormatter.ofPattern(format).withLocale(locale).withZone(timeZone)
    } catch {
      case ex: IllegalArgumentException =>
        throw new InterpreterException(
          s"Invalid time/date format: '$format'. error message: ${ex.getMessage}")
    }
  }

  def getLocale(localeStr: String): Locale = {
    try {
      LocaleUtils.toLocale(localeStr)
    } catch {
      case _: IllegalArgumentException =>
        throw new InterpreterException(s"Invalid locale: '$localeStr'")
    }
  }

  def getTimezone(tzStr: String): ZoneId = {
    if (!allAvailableTimezones.contains(tzStr)) {
      throw new InterpreterException(s"Invalid timezone: '$tzStr'")
    }
    TimeZone.getTimeZone(tzStr).toZoneId
  }

}

class CqlFormatter(val outputFormat: String = CqlFormatter.DEFAULT_FORMAT,
                   val floatPrecision: Int = CqlFormatter.DEFAULT_FLOAT_PRECISION,
                   val doublePrecision: Int = CqlFormatter.DEFAULT_DOUBLE_PRECISION,
                   val timestampFormat: String = CqlFormatter.DEFAULT_TIMESTAMP_FORMAT,
                   val timeFormat: String = CqlFormatter.DEFAULT_TIME_FORMAT,
                   val dateFormat: String = CqlFormatter.DEFAULT_DATE_FORMAT,
                   val timeZoneId: String = CqlFormatter.DEFAULT_TIMEZONE,
                   val localeStr: String = CqlFormatter.DEFAULT_LOCALE) {

  val isCqlFormat: Boolean = "cql".equalsIgnoreCase(outputFormat)
  val locale: Locale = CqlFormatter.getLocale(localeStr)
  val timeZone: ZoneId = CqlFormatter.getTimezone(timeZoneId)

  val floatFormatter: DecimalFormat = CqlFormatter.getNumberFormatter(locale, floatPrecision)
  val doubleFormatter: DecimalFormat = CqlFormatter.getNumberFormatter(locale, doublePrecision)

  val timestampFormatter: DateTimeFormatter = CqlFormatter.getDateTimeFormatter(
    locale, timeZone, timestampFormat)
  val timeFormatter: DateTimeFormatter = CqlFormatter.getDateTimeFormatter(
    locale, timeZone, timeFormat)
  val dateFormatter: DateTimeFormatter = CqlFormatter.getDateTimeFormatter(
    locale, timeZone, dateFormat)

  def this(properties: Properties) {
    this(
      properties.getProperty(CassandraInterpreter.CASSANDRA_FORMAT_TYPE,
        CqlFormatter.DEFAULT_FORMAT),
      properties.getProperty(CassandraInterpreter.CASSANDRA_FORMAT_FLOAT_PRECISION,
        CqlFormatter.DEFAULT_FLOAT_PRECISION.toString).toInt,
      properties.getProperty(
        CassandraInterpreter.CASSANDRA_FORMAT_DOUBLE_PRECISION,
        CqlFormatter.DEFAULT_DOUBLE_PRECISION.toString).toInt,
      properties.getProperty(CassandraInterpreter.CASSANDRA_FORMAT_TIMESTAMP,
        CqlFormatter.DEFAULT_TIMESTAMP_FORMAT),
      properties.getProperty(CassandraInterpreter.CASSANDRA_FORMAT_TIME,
        CqlFormatter.DEFAULT_TIME_FORMAT),
      properties.getProperty(CassandraInterpreter.CASSANDRA_FORMAT_DATE,
        CqlFormatter.DEFAULT_DATE_FORMAT),
      properties.getProperty(CassandraInterpreter.CASSANDRA_FORMAT_TIMEZONE,
        CqlFormatter.DEFAULT_TIMEZONE),
      properties.getProperty(CassandraInterpreter.CASSANDRA_FORMAT_LOCALE,
        CqlFormatter.DEFAULT_LOCALE)
    )
  }

  def copy(outputFormat: String = this.outputFormat,
           floatPrecision: Int = this.floatPrecision,
           doublePrecision: Int = this.doublePrecision,
           timestampFormat: String = this.timestampFormat,
           timeFormat: String = this.timeFormat,
           dateFormat: String = this.dateFormat,
           timeZoneId: String = this.timeZoneId,
           localeStr: String = this.localeStr) =
    new CqlFormatter(outputFormat, floatPrecision, doublePrecision, timestampFormat,
      timeFormat, dateFormat, timeZoneId, localeStr)

  def formatHuman(obj: Object): String = {
    if (obj == null) {
      "null"
    } else {
      obj match {
        case f: java.lang.Float =>
          floatFormatter.format(f)
        case d: java.lang.Double =>
          doubleFormatter.format(d)
        case m: java.util.Map[Object, Object] =>
          m.asScala.map{case(k,v) => formatHuman(k) + ": " + formatHuman(v)}.mkString("{", ", ", "}")
        case l: java.util.List[Object] =>
          l.asScala.map(x => formatHuman(x)).mkString("[", ", ", "]")
        case s: java.util.Set[Object] =>
          s.asScala.map(x => formatHuman(x)).mkString("{", ", ", "}")
        case t: Instant =>
            timestampFormatter.format(t.atZone(timeZone))
        case d: LocalDate =>
          dateFormatter.format(d)
        case t: LocalTime =>
          timeFormatter.format(t)
        case b: ByteBuffer =>
          "0x" + ByteBufUtil.hexDump(b.array())
        case i: InetAddress =>
          i.getHostAddress
        case t: TupleValue =>
          (0 until t.size()).map(i => formatHuman(t.getObject(i))).mkString("(", ", ", ")")
        case u: UdtValue =>
          val names = u.getType.getFieldNames
          (0 until u.size()).map(i => names.get(i).asInternal + ": " + formatHuman(u.getObject(i)))
            .mkString("{", ", ", "}")

        case _ => obj.toString
      }
    }
  }

  def format(obj: Object, codec: TypeCodec[AnyRef]): String = {
    if (isCqlFormat) {
      codec.format(obj)
    } else {
      formatHuman(obj)
    }
  }

  def getValueAsString(row: Row, name: String, dataType: DataType): String = {
    val value = row.getObject(name)
    if (isCqlFormat) {
      format(value, row.codecRegistry().codecFor(dataType, value))
    } else {
      formatHuman(value)
    }
  }

  override def toString: String = s"CqlFormatter(format=$outputFormat, fp=$floatPrecision, dp=$doublePrecision, " +
    s"tsFormat=$timestampFormat, tmFormat=$timeFormat, dtFormat=$dateFormat, " +
    s"timeozone=$timeZoneId, locale=$localeStr)"
}
