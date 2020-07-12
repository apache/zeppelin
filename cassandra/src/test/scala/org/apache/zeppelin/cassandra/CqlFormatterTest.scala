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
import java.time.{Instant, LocalDate, LocalTime}
import java.util.Properties

import com.datastax.oss.driver.api.core.`type`.DataTypes
import com.datastax.oss.driver.api.core.`type`.codec.registry.CodecRegistry
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}

import scala.collection.JavaConverters._

class CqlFormatterTest extends FlatSpec
  with BeforeAndAfterEach
  with Matchers {

  val longVal: java.lang.Long = java.lang.Long.valueOf(12345678901L)
  val floatVal: java.lang.Float = java.lang.Float.valueOf(123.456789f)
  val intVal: java.lang.Integer = Integer.valueOf(123456)
  val doubleVal: java.lang.Double = java.lang.Double.valueOf(123.4567890123456789)
  val dateVal: LocalDate = LocalDate.of(2020, 6, 16)
  val timeVal: LocalTime = LocalTime.of(23, 59, 59, 123400000)
  val timestampVal:Instant = Instant.parse("2020-06-16T23:59:59.123456Z")
  val byteBufVal: Array[Byte] = Array[Byte](192.toByte, 168.toByte, 0, 10)

  "CqlFormatter" should "format objects with default settings" in {
    val defaultFormatter = new CqlFormatter()

    // please note that it doesn't format UdtValue & TupleType, as it's quite hard to emulate
    // they are checked in the integration tests
    defaultFormatter.formatHuman(intVal) should be("123456")
    defaultFormatter.formatHuman(longVal) should be("12345678901")
    defaultFormatter.formatHuman(floatVal) should be("123.45679")
    defaultFormatter.formatHuman(doubleVal) should be("123.456789012346")
    defaultFormatter.formatHuman("just text") should be("just text")
    defaultFormatter.formatHuman(java.lang.Boolean.TRUE) should be("true")
    defaultFormatter.formatHuman(List(1,2,3).asJava) should be("[1, 2, 3]")
    defaultFormatter.formatHuman(List("1","2","3").asJava) should be("[1, 2, 3]")
    defaultFormatter.formatHuman(Set(1, 2, 3).asJava) should be("{3, 1, 2}")
    defaultFormatter.formatHuman(Set("1", "2", "3").asJava) should be("{3, 1, 2}")
    defaultFormatter.formatHuman(Map(1 -> 1, 2 -> 2, 3 -> 3).asJava) should be("{1: 1, 2: 2, 3: 3}")
    defaultFormatter.formatHuman(Map(1 -> "1", 2 -> "2", 3 -> "3").asJava) should be("{1: 1, 2: 2, 3: 3}")
    defaultFormatter.formatHuman(dateVal) should be("2020-06-16")
    defaultFormatter.formatHuman(timeVal) should be("23:59:59.123")
    defaultFormatter.formatHuman(timestampVal) should be("2020-06-16T23:59:59.123Z")
    defaultFormatter.formatHuman(Map(1 -> timestampVal).asJava) should be("{1: 2020-06-16T23:59:59.123Z}")
    defaultFormatter.formatHuman(InetAddress.getLoopbackAddress) should be("127.0.0.1")
    defaultFormatter.formatHuman(InetAddress.getByAddress(byteBufVal)) should be("192.168.0.10")
    defaultFormatter.formatHuman(ByteBuffer.wrap(byteBufVal)) should be("0xc0a8000a")
  }

  "CqlFormatter" should "format objects with copied settings" in {
    val copiedFormatter = new CqlFormatter()
      .copy(floatPrecision = 2, doublePrecision = 4, timeZoneId = "Etc/GMT+2",
        timeFormat = "hh:mma", dateFormat = "E, d MMM yy", localeStr = "en_US")
    copiedFormatter.formatHuman(floatVal) should be("123.46")
    copiedFormatter.formatHuman(doubleVal) should be("123.4568")
    copiedFormatter.formatHuman(timestampVal) should be("2020-06-16T21:59:59.123-02:00")
    copiedFormatter.formatHuman(timeVal) should be("11:59PM")
    copiedFormatter.formatHuman(dateVal) should be("Tue, 16 Jun 20")
  }

  "CqlFormatter" should "format objects with settings from property object" in {
    val properties = new Properties()
    properties.setProperty(CassandraInterpreter.CASSANDRA_FORMAT_FLOAT_PRECISION, "2")
    properties.setProperty(CassandraInterpreter.CASSANDRA_FORMAT_DOUBLE_PRECISION, "4")
    properties.setProperty(CassandraInterpreter.CASSANDRA_FORMAT_TIME, "hh:mma")
    properties.setProperty(CassandraInterpreter.CASSANDRA_FORMAT_DATE, "E, d MMM yy")
    properties.setProperty(CassandraInterpreter.CASSANDRA_FORMAT_TIMEZONE, "Etc/GMT+2")

    val copiedFormatter = new CqlFormatter(properties)
    copiedFormatter.formatHuman(floatVal) should be("123.46")
    copiedFormatter.formatHuman(doubleVal) should be("123.4568")
    copiedFormatter.formatHuman(timestampVal) should be("2020-06-16T21:59:59.123-02:00")
    copiedFormatter.formatHuman(timeVal) should be("11:59PM")
    copiedFormatter.formatHuman(dateVal) should be("Tue, 16 Jun 20")
  }

  "CqlFormatter" should "format objects with locale" in {
    val copiedFormatter = new CqlFormatter()
      .copy(floatPrecision = 2, doublePrecision = 4, localeStr = "de_DE")
    copiedFormatter.formatHuman(floatVal) should be("123,46")
    copiedFormatter.formatHuman(doubleVal) should be("123,4568")
    copiedFormatter.formatHuman(timestampVal) should be("2020-06-16T23:59:59.123Z")
  }

  "CqlFormatter" should "format objects using CQL syntax" in {
    val cqlFormatter = new CqlFormatter().copy(outputFormat = "cql")
    val codecRegistry = CodecRegistry.DEFAULT

    // please note that it doesn't format UdtValue & TupleType, as it's quite hard to emulate
    // they are checked in the integration tests
    cqlFormatter.format(intVal, codecRegistry.codecFor(DataTypes.INT)) should be("123456")
    cqlFormatter.format(longVal, codecRegistry.codecFor(DataTypes.BIGINT)) should be("12345678901")
    cqlFormatter.format(floatVal, codecRegistry.codecFor(DataTypes.FLOAT)) should be("123.45679")
    cqlFormatter.format(doubleVal, codecRegistry.codecFor(DataTypes.DOUBLE)) should be("123.45678901234568")
    cqlFormatter.format("just text", codecRegistry.codecFor(DataTypes.TEXT)) should be("'just text'")
    cqlFormatter.format(java.lang.Boolean.TRUE, codecRegistry.codecFor(DataTypes.BOOLEAN)) should be("true")
    cqlFormatter.format(dateVal,
      codecRegistry.codecFor(DataTypes.DATE)) should be("'2020-06-16'")
    cqlFormatter.format(timeVal,
      codecRegistry.codecFor(DataTypes.TIME)) should be("'23:59:59.123400000'")
    cqlFormatter.format(InetAddress.getLoopbackAddress,
      codecRegistry.codecFor(DataTypes.INET)) should be("'127.0.0.1'")
    cqlFormatter.format(InetAddress.getByAddress(byteBufVal),
      codecRegistry.codecFor(DataTypes.INET)) should be("'192.168.0.10'")
    cqlFormatter.format(ByteBuffer.wrap(byteBufVal),
      codecRegistry.codecFor(DataTypes.BLOB)) should be("0xc0a8000a")
  }

}
