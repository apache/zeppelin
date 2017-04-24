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

package org.apache.zeppelin.scio

import com.google.api.services.bigquery.model.{TableFieldSchema, TableSchema}
import com.spotify.scio.bigquery._
import org.apache.avro.Schema
import org.apache.avro.Schema.Parser
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.zeppelin.scio.avro.Account
import org.apache.zeppelin.scio.util.TestUtils
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{FlatSpec, Matchers}

/**
 * DisplayHelpersTest tests.
 *
 * Most tests have test scope implicit imports due to scala 2.10 bug
 * https://issues.scala-lang.org/browse/SI-3346
 *
 * Note: we can't depend on the order of data coming from SCollection.
 */
@RunWith(classOf[JUnitRunner])
class DisplayHelpersTest extends FlatSpec with Matchers {
  private val testRowLimit = 20
  sys.props("zeppelin.scio.maxResult") = 20.toString

  import TestUtils._

  // -----------------------------------------------------------------------------------------------
  // AnyVal SCollection Tests
  // -----------------------------------------------------------------------------------------------

  private val anyValHeader = s"$table value"
  private val endTable = DisplayHelpers.endTable

  "DisplayHelpers" should "support Integer SCollection via AnyVal" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinSCollection
    val o = captureOut {
      sideEffectWithData(Seq(1, 2, 3)) { in =>
        in.closeAndDisplay()
      }
    }
    o should contain theSameElementsAs Seq(anyValHeader,
                                           "1",
                                           "2",
                                           "3",
                                           endTable)
    o.head should be(anyValHeader)
    o.last should be(endTable)
  }

  it should "support Long SCollection via AnyVal" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinSCollection
    val o = captureOut {
      sideEffectWithData(Seq(1L, 2L, 3L)) { in =>
        in.closeAndDisplay()
      }
    }
    o should contain theSameElementsAs Seq(anyValHeader,
                                           "1",
                                           "2",
                                           "3",
                                           endTable)
    o.head should be(anyValHeader)
    o.last should be(endTable)
  }

  it should "support Double SCollection via AnyVal" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinSCollection
    val o = captureOut {
      sideEffectWithData(Seq(1.0D, 2.0D, 3.0D)) { in =>
        in.closeAndDisplay()
      }
    }
    o should contain theSameElementsAs Seq(anyValHeader,
                                           "1.0",
                                           "2.0",
                                           "3.0",
                                           endTable)
    o.head should be(anyValHeader)
    o.last should be(endTable)
  }

  it should "support Float SCollection via AnyVal" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinSCollection
    val o = captureOut {
      sideEffectWithData(Seq(1.0F, 2.0F, 3.0F)) { in =>
        in.closeAndDisplay()
      }
    }
    o should contain theSameElementsAs Seq(anyValHeader,
                                           "1.0",
                                           "2.0",
                                           "3.0",
                                           endTable)
    o.head should be(anyValHeader)
    o.last should be(endTable)
  }

  it should "support Short SCollection via AnyVal" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinSCollection
    val o = captureOut {
      sideEffectWithData(Seq(1.toShort, 2.toShort, 3.toShort)) { in =>
        in.closeAndDisplay()
      }
    }
    o should contain theSameElementsAs Seq(anyValHeader,
                                           "1",
                                           "2",
                                           "3",
                                           endTable)
    o.head should be(anyValHeader)
    o.last should be(endTable)
  }

  it should "support Byte SCollection via AnyVal" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinSCollection
    val o = captureOut {
      sideEffectWithData(Seq(1.toByte, 2.toByte, 3.toByte)) { in =>
        in.closeAndDisplay()
      }
    }
    o should contain theSameElementsAs Seq(anyValHeader,
                                           "1",
                                           "2",
                                           "3",
                                           endTable)
    o.head should be(anyValHeader)
    o.last should be(endTable)
  }

  it should "support Boolean SCollection via AnyVal" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinSCollection
    val o = captureOut {
      sideEffectWithData(Seq(true, false, true)) { in =>
        in.closeAndDisplay()
      }
    }
    o should contain theSameElementsAs Seq(anyValHeader,
                                           "true",
                                           "false",
                                           "true",
                                           endTable)
    o.head should be(anyValHeader)
    o.last should be(endTable)
  }

  it should "support Char SCollection via AnyVal" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinSCollection
    val o = captureOut {
      sideEffectWithData(Seq('a', 'b', 'c')) { in =>
        in.closeAndDisplay()
      }
    }
    o should contain theSameElementsAs Seq(anyValHeader,
                                           "a",
                                           "b",
                                           "c",
                                           endTable)
    o.head should be(anyValHeader)
    o.last should be(endTable)
  }

  it should "support SCollection of AnyVal over row limit" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinSCollection
    val o = captureOut {
      sideEffectWithData(1 to 21) { in =>
        in.closeAndDisplay()
      }
    }
    o.size should be > testRowLimit
    o.head should be(anyValHeader)
    o.last should be(rowLimitReached)
  }

  it should "support empty SCollection of AnyVal" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinSCollection
    val o = captureOut {
      sideEffectWithData(Seq.empty[AnyVal]) { in =>
        in.closeAndDisplay()
      }
    }
    o should contain theSameElementsAs DisplayHelpers.sCollectionEmptyMsg.split(newline)
  }

  // -----------------------------------------------------------------------------------------------
  // String SCollection Tests
  // -----------------------------------------------------------------------------------------------

  private val stringHeader = s"$table value"

  it should "support String SCollection" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinStringSCollection
    val o = captureOut {
      sideEffectWithData(Seq("a","b","c")) { in =>
        in.closeAndDisplay()
      }
    }
    o should contain theSameElementsAs Seq(stringHeader,
                                           "a",
                                           "b",
                                           "c",
                                           endTable)
    o.head should be (stringHeader)
    o.last should be (endTable)
  }

  it should "support empty SCollection of String" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinStringSCollection
    val o = captureOut {
      sideEffectWithData(Seq.empty[String]) { in =>
        in.closeAndDisplay()
      }
    }
    o should contain theSameElementsAs DisplayHelpers.sCollectionEmptyMsg.split(newline)
  }

  it should "support SCollection of String over row limit" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinStringSCollection
    val o = captureOut {
      sideEffectWithData(Seq.fill(21)("a")) { in =>
        in.closeAndDisplay()
      }
    }
    o.size should be > testRowLimit
    o.head should be(stringHeader)
    o.last should be(rowLimitReached)
  }

  // -----------------------------------------------------------------------------------------------
  // KV SCollection Tests
  // -----------------------------------------------------------------------------------------------

  private val kvHeader = s"$table key${tab}value"

  it should "support KV (ints) SCollection" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinKVSCollection
    val o = captureOut {
      sideEffectWithData(Seq((1,2), (3,4))) { in =>
        in.closeAndDisplay()
      }
    }
    o should contain theSameElementsAs Seq(kvHeader,
                                           s"3${tab}4",
                                           s"1${tab}2",
                                           endTable)
    o.head should be (kvHeader)
    o.last should be (endTable)
  }

  it should "support KV (str keys) SCollection" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinKVSCollection
    val o = captureOut {
      sideEffectWithData(Seq(("foo",2), ("bar",4))) { in =>
        in.closeAndDisplay()
      }
    }
    o should contain theSameElementsAs Seq(kvHeader,
                                           s"foo${tab}2",
                                           s"bar${tab}4",
                                           endTable)
    o.head should be (kvHeader)
    o.last should be (endTable)
  }

  it should "support KV (str values) SCollection" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinKVSCollection
    val o = captureOut {
      sideEffectWithData(Seq((2,"foo"), (4,"bar"))) { in =>
        in.closeAndDisplay()
      }
    }
    o should contain theSameElementsAs Seq(kvHeader,
                                           s"2${tab}foo",
                                           s"4${tab}bar",
                                           endTable)
    o.head should be (kvHeader)
    o.last should be (endTable)
  }

  it should "support empty KV SCollection" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinKVSCollection
    captureOut {
      sideEffectWithData(Seq.empty[(Int, Int)]) { in =>
        in.closeAndDisplay()
      }
    } should contain theSameElementsAs DisplayHelpers.sCollectionEmptyMsg.split(newline)
  }

  it should "support SCollection of KV over row limit" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinKVSCollection
    val o = captureOut {
      sideEffectWithData(Seq.fill(21)(("foo", 1))) { in =>
        in.closeAndDisplay()
      }
    }
    o.size should be > testRowLimit
    o.head should be(kvHeader)
    o.last should be(rowLimitReached)
  }

  // -----------------------------------------------------------------------------------------------
  // Product SCollection Tests
  // -----------------------------------------------------------------------------------------------

  private val testCaseClassHeader = s"$table foo${tab}bar${tab}a"

  it should "support SCollection of Tuple of 3" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinProductSCollection
    val tupleHeader = s"$table _1${tab}_2${tab}_3"
    val o = captureOut {
      sideEffectWithData(Seq.fill(3)((1,2,3))) { in =>
        in.closeAndDisplay()
      }
    }
    o should contain theSameElementsAs
      (Seq(tupleHeader, endTable) ++ Seq.fill(3)(s"1${tab}2${tab}3"))
    o.head should be(tupleHeader)
    o.last should be (endTable)
  }

  it should "support SCollection of Tuple of 22" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinProductSCollection
    val tupleHeader = s"$table " + (1 to 21).map(i => s"_$i$tab").mkString + "_22"
    val o = captureOut {
      sideEffectWithData(
        Seq.fill(3)((1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22))) { in =>
          in.closeAndDisplay()
      }
    }
    o should contain theSameElementsAs (Seq(tupleHeader, endTable) ++
      Seq.fill(3)((1 to 21).map(i => s"$i$tab").mkString + "22"))
    o.head should be(tupleHeader)
    o.last should be (endTable)
  }

  it should "support SCollection of Case Class of 22" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinProductSCollection
    val tupleHeader = s"$table " + (1 to 21).map(i => s"a$i$tab").mkString + "a22"
    val o = captureOut {
      sideEffectWithData(
        Seq.fill(3)(CC22(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22))) { in =>
        in.closeAndDisplay()
      }
    }
    o should contain theSameElementsAs (Seq(tupleHeader, endTable) ++
      Seq.fill(3)((1 to 21).map(i => s"$i$tab").mkString + "22"))
    o.head should be(tupleHeader)
    o.last should be (endTable)
  }

  it should "support SCollection of Case Class" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinProductSCollection
    val o = captureOut {
      sideEffectWithData(Seq.fill(3)(TestCaseClass(1, "foo", 2.0D))) { in =>
        in.closeAndDisplay()
      }
    }
    o should contain theSameElementsAs (Seq(testCaseClassHeader, endTable) ++
      Seq.fill(3)(s"1${tab}foo${tab}2.0"))
    o.head should be(testCaseClassHeader)
    o.last should be (endTable)
  }

  it should "support empty SCollection of Product" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinProductSCollection
    captureOut {
      sideEffectWithData(Seq.empty[Product]) { in =>
        in.closeAndDisplay()
      }
    } should contain theSameElementsAs DisplayHelpers.sCollectionEmptyMsg.split(newline)
  }

  it should "support SCollection of Product over row limit" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinProductSCollection
    val o = captureOut {
      sideEffectWithData(Seq.fill(21)(TestCaseClass(1, "foo", 2.0D))) { in =>
        in.closeAndDisplay()
      }
    }

    o.size should be > testRowLimit
    o.head should be(testCaseClassHeader)
    o.last should be(rowLimitReached)
  }

  // -----------------------------------------------------------------------------------------------
  // Avro SCollection Tests
  // -----------------------------------------------------------------------------------------------

  import scala.collection.JavaConverters._

  private val schema = {
    def f(name: String, tpe: Schema.Type) =
      new Schema.Field(
        name,
        Schema.createUnion(List(Schema.create(Schema.Type.NULL), Schema.create(tpe)).asJava),
        null, null)

    val s = Schema.createRecord("GenericAccountRecord", null, null, false)
    s.setFields(List(
      f("id", Schema.Type.INT),
      f("amount", Schema.Type.DOUBLE),
      f("name", Schema.Type.STRING),
      f("type", Schema.Type.STRING)
    ).asJava)
    s
  }

  private def getTestGenericAvro(i: Int): GenericRecord = {
    val s: Schema = new Parser().parse(schema.toString)
    val r = new GenericData.Record(s)
    r.put("id", i)
    r.put("amount", i.toDouble)
    r.put("name", "user" + i)
    r.put("type", "checking")
    r
  }

  private def getTestAccountAvro(): Account = {
    Account.newBuilder()
      .setId(2)
      .setAmount(2.0D)
      .setName("user2")
      .setType("checking")
      .build()
  }

  private val avroGenericRecordHeader = s"$table id${tab}amount${tab}name${tab}type"
  private val avroAccountHeader = s"$table id${tab}type${tab}name${tab}amount"

  it should "support SCollection of GenericRecord" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinAvroSCollection
    val o = captureOut {
      sideEffectWithData(Seq.fill(3)(getTestGenericAvro(1))) { in =>
        in.closeAndDisplay()
      }
    }
    o should contain theSameElementsAs (Seq(avroGenericRecordHeader, endTable) ++
      Seq.fill(3)(s"1${tab}1.0${tab}user1${tab}checking"))
    o.head should be(avroGenericRecordHeader)
    o.last should be (endTable)
  }

  it should "support SCollection of SpecificRecord Avro" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinAvroSCollection

    val o = captureOut {
      sideEffectWithData(Seq.fill(3)(getTestAccountAvro())) { in =>
        in.closeAndDisplay()
      }
    }
    o should contain theSameElementsAs (Seq(avroAccountHeader, endTable) ++
      Seq.fill(3)(s"2${tab}checking${tab}user2${tab}2.0"))
    o.head should be(avroAccountHeader)
    o.last should be (endTable)
  }

  it should "support empty SCollection of SpecificRecord Avro" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinAvroSCollection
    captureOut {
      sideEffectWithData(Seq.empty[Account]) { in =>
        in.closeAndDisplay()
      }
    } should contain theSameElementsAs DisplayHelpers.sCollectionEmptyMsg.split(newline)
  }

  it should "support empty SCollection of GenericRecord Avro" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinAvroSCollection
    captureOut {
      sideEffectWithData(Seq.empty[GenericRecord]) { in =>
        in.closeAndDisplay()
      }
    } should contain theSameElementsAs DisplayHelpers.sCollectionEmptyMsg.split(newline)
  }

  it should "support SCollection of GenericRecord Avro over row limit" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinAvroSCollection
    val o = captureOut {
      sideEffectWithData(Seq.fill(21)(getTestGenericAvro(1))) { in =>
        in.closeAndDisplay()
      }
    }

    o.size should be > testRowLimit
    o.head should be(avroGenericRecordHeader)
    o.last should be(rowLimitReached)
  }

  it should "support SCollection of SpecificRecord Avro over row limit" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinAvroSCollection
    val o = captureOut {
      sideEffectWithData(Seq.fill(21)(getTestAccountAvro())) { in =>
        in.closeAndDisplay()
      }
    }

    o.size should be > testRowLimit
    o.head should be(avroAccountHeader)
    o.last should be(rowLimitReached)
  }

  // -----------------------------------------------------------------------------------------------
  // TableRow SCollection Tests
  // -----------------------------------------------------------------------------------------------

  private val bQSchema = new TableSchema().setFields(List(
    new TableFieldSchema().setName("id").setType("INTEGER"),
    new TableFieldSchema().setName("amount").setType("FLOAT"),
    new TableFieldSchema().setName("type").setType("STRING"),
    new TableFieldSchema().setName("name").setType("STRING")
  ).asJava)

  private val bQHeader = s"$table id${tab}amount${tab}type${tab}name"

  private def getBQTableRow(): TableRow = {
    TableRow("id" -> 3, "amount" -> 3.0D, "type" -> "checking", "name" -> "user3")
  }

  it should "support SCollection of TableRow" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinBQTableSCollection
    val o = captureOut {
      sideEffectWithData(Seq.fill(3)(getBQTableRow())) { in =>
        in.closeAndDisplay(bQSchema)
      }
    }
    o should contain theSameElementsAs (Seq(bQHeader, endTable) ++
      Seq.fill(3)(s"3${tab}3.0${tab}checking${tab}user3"))
    o.head should be(bQHeader)
    o.last should be (endTable)
  }

  it should "print error on empty BQ schema" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinBQTableSCollection
    captureOut {
      sideEffectWithData(Seq.fill(3)(getBQTableRow())) { in =>
        in.closeAndDisplay(new TableSchema())
      }
    } should contain theSameElementsAs DisplayHelpers.bQSchemaIncomplete.split(newline)
  }

  it should "support SCollection of TableRow over row limit" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinBQTableSCollection
    val o = captureOut {
      sideEffectWithData(Seq.fill(21)(getBQTableRow())) { in =>
        in.closeAndDisplay(bQSchema)
      }
    }

    o.size should be > testRowLimit
    o.head should be(bQHeader)
    o.last should be(rowLimitReached)
  }

  it should "support empty SCollection of TableRow" in {
    import org.apache.zeppelin.scio.DisplaySCollectionImplicits.ZeppelinBQTableSCollection
    captureOut {
      sideEffectWithData(Seq.empty[TableRow]) { in =>
        in.closeAndDisplay(new TableSchema())
      }
    } should contain theSameElementsAs DisplayHelpers.sCollectionEmptyMsg.split(newline)
  }

}
