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

import org.scalatest.{Matchers, BeforeAndAfterEach, FlatSpec}

class BoundValuesParserTest extends FlatSpec
with BeforeAndAfterEach
with Matchers {

  val parser = new BoundValuesParser

  "BoundValuesParser" should "parse quoted string" in {
    //Given
    val input = """'a'"""

    //When
    val parsed1 = parser.parse(parser.quotedString,input)
    val parsed2 = parser.parse(parser.value,input)

    //Then
    parsed1.get should be("'a'")
    parsed2.get should be("'a'")
  }

  "BoundValuesParser" should "parse integer" in {
    //Given
    val input = """00123"""

    //When
    val parsed = parser.parse(parser.value,input)

    //Then
    parsed.get should be("123")
  }

  "BoundValuesParser" should "parse decimal number" in {
    //Given
    val input1 = """00123.35000"""
    val input2 = """+123."""
    val input3 = """.35000"""
    val input4 = """-.35000"""

    //When
    val parsed1 = parser.parse(parser.value,input1)
    val parsed2 = parser.parse(parser.value,input2)
    val parsed3 = parser.parse(parser.value,input3)
    val parsed4 = parser.parse(parser.value,input4)

    //Then
    parsed1.get should be("123.35")
    parsed2.get should be("123.0")
    parsed3.get should be("0.35")
    parsed4.get should be("-0.35")
  }

  "BoundValuesParser" should "parse list" in {
    //Given
    val input = """['a','b','c']"""

    //When
    val parsed = parser.parse(parser.value,input)

    //Then
    parsed.get should be("['a','b','c']")
  }

  "BoundValuesParser" should "parse set" in {
    //Given
    val input = """{'a',2,3.4}"""

    //When
    val parsed = parser.parse(parser.value,input)

    //Then
    parsed.get should be("{'a',2,3.4}")
  }

  "BoundValuesParser" should "parse map" in {
    //Given
    val input = """{'key1': 'val', 'key2': 2, 'key3': 3.4}"""

    //When
    val parsed = parser.parse(parser.value,input)

    //Then
    parsed.get should be("{'key1': 'val', 'key2': 2, 'key3': 3.4}")
  }

  "BoundValuesParser" should "parse tuple" in {
    //Given
    val input = """('a',2,3.4)"""

    //When
    val parsed = parser.parse(parser.value,input)

    //Then
    parsed.get should be("('a',2,3.4)")
  }

  "BoundValuesParser" should "parse udt" in {
    //Given
    val input = """{col1: 'val1', col2: 2, col3: 3.4}"""

    //When
    val parsed = parser.parse(parser.value,input)

    //Then
    parsed.get should be("{col1: 'val1', col2: 2, col3: 3.4}")
  }

  "BoundValuesParser" should "parse date" in {
    //Given
    val input = """'2015-07-10 14:56:34'"""

    //When
    val parsed = parser.parse(parser.value,input)

    //Then
    parsed.get should be("2015-07-10 14:56:34")
  }

  "BoundValuesParser" should "parse nested types" in {

    //Given
    val input = "'jdoe','John','DOE'," +
      "{street_number: 3, street_name: 'Beverly Hills Bld', zip_code: 90209," +
      " country: 'USA', extra_info: ['Right on the hills','Next to the post box']," +
      " phone_numbers: {'home': 2016778524, 'office': 2015790847} }," +
      "('USA', 90209, 'Beverly Hills')"

    //When
    val parsed = parser.parse(parser.values,input)

    //Then
    parsed.get should be(List(
      "'jdoe'",
      "'John'",
      "'DOE'",
      "{street_number: 3, street_name: 'Beverly Hills Bld', zip_code: 90209, country: 'USA', extra_info: ['Right on the hills','Next to the post box'], phone_numbers: {'home': 2016778524, 'office': 2015790847}}",
      "('USA',90209,'Beverly Hills')"
    ))
  }

  "BoundValuesParser" should "not parse mustaches for zeppelin variables" in {
    //Given
    val input = "'jdoe',{{firstname='Jdoe'}}"

    //When
    val parsed = parser.parse(parser.values,input)

    //Then
    parsed.get should be(List("'jdoe'","{{firstname='Jdoe'}}"))

  }

  "BoundValuesParser" should "parse zeppelin variable" in {
    //Given
    val input = """'{{login=jdoe}}'"""

    //When
    val parsed = parser.parse(parser.values,input)

    //Then
    parsed.get should be(List("'{{login=jdoe}}'"))
  }

  "BoundValuesParser" should "parse null value" in {
    //Given
    val input = """'login',null,'LASTNAME'"""

    //When
    val parsed = parser.parse(parser.values,input)

    //Then
    parsed.get should be(List("'login'","null","'LASTNAME'"))
  }

}
