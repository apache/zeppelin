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

package org.apache.zeppelin.notebook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParagraphTextParserTest {

  @Test
  void testJupyter() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse("%jupyter(kernel=ir)");
    assertEquals("jupyter", parseResult.getIntpText());
    assertEquals(1, parseResult.getLocalProperties().size());
    assertEquals("ir", parseResult.getLocalProperties().get("kernel"));
    assertEquals("", parseResult.getScriptText());
  }


  @Test
  void testCassandra() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse(
            "%cassandra(locale=ru_RU, timeFormat=\"E, d MMM yy\", floatPrecision = 5, output=cql)\n"
                    + "select * from system_auth.roles;");
    assertEquals("cassandra", parseResult.getIntpText());
    assertEquals(4, parseResult.getLocalProperties().size());
    assertEquals("E, d MMM yy", parseResult.getLocalProperties().get("timeFormat"));
    assertEquals("\nselect * from system_auth.roles;", parseResult.getScriptText());
  }

  @Test
  void testSparkSubmit() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse(
            "%spark-submit --class A a.jar");
    assertEquals("spark-submit", parseResult.getIntpText());
    assertEquals("--class A a.jar", parseResult.getScriptText());
  }

  @Test
  void testParagraphTextLocalPropertiesAndText() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse("%spark.pyspark(pool=pool_1) sc.version");
    assertEquals("spark.pyspark", parseResult.getIntpText());
    assertEquals(1, parseResult.getLocalProperties().size());
    assertEquals("pool_1", parseResult.getLocalProperties().get("pool"));
    assertEquals("sc.version", parseResult.getScriptText());
  }

  @Test
  void testParagraphTextLocalPropertiesNoText() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse("%spark.pyspark(pool=pool_1)");
    assertEquals("spark.pyspark", parseResult.getIntpText());
    assertEquals(1, parseResult.getLocalProperties().size());
    assertEquals("pool_1", parseResult.getLocalProperties().get("pool"));
    assertEquals("", parseResult.getScriptText());
  }

  @Test
  void testParagraphTextLocalPropertyNoValueNoText() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse("%spark.pyspark(pool)");
    assertEquals("spark.pyspark", parseResult.getIntpText());
    assertEquals(1, parseResult.getLocalProperties().size());
    assertEquals("pool", parseResult.getLocalProperties().get("pool"));
    assertEquals("", parseResult.getScriptText());
  }

  @Test
  void testParagraphTextNoLocalProperties() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse("%spark.pyspark\nsc.version");
    assertEquals("spark.pyspark", parseResult.getIntpText());
    assertEquals(0, parseResult.getLocalProperties().size());
    assertEquals("\nsc.version", parseResult.getScriptText());
  }

  @Test
  void testParagraphNoInterpreter() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse("sc.version");
    assertEquals("", parseResult.getIntpText());
    assertEquals(0, parseResult.getLocalProperties().size());
    assertEquals("sc.version", parseResult.getScriptText());
  }

  @Test
  void testParagraphInterpreterWithoutProperties() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse("%spark() sc.version");
    assertEquals("spark", parseResult.getIntpText());
    assertEquals(0, parseResult.getLocalProperties().size());
    assertEquals("sc.version", parseResult.getScriptText());
  }

  @Test
  void testParagraphTextQuotedPropertyValue1() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse(
            "%spark.pyspark(pool=\"value with = inside\")");
    assertEquals("spark.pyspark", parseResult.getIntpText());
    assertEquals(1, parseResult.getLocalProperties().size());
    assertEquals("value with = inside", parseResult.getLocalProperties().get("pool"));
    assertEquals("", parseResult.getScriptText());
  }

  @Test
  void testParagraphTextQuotedPropertyValue2() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse(
            "%spark.pyspark(pool=\"value with \\\" inside\", p=\"eol\\ninside\" )");
    assertEquals("spark.pyspark", parseResult.getIntpText());
    assertEquals(2, parseResult.getLocalProperties().size());
    assertEquals("value with \" inside", parseResult.getLocalProperties().get("pool"));
    assertEquals("eol\ninside", parseResult.getLocalProperties().get("p"));
    assertEquals("", parseResult.getScriptText());
  }

  @Test
  void testParagraphTextQuotedPropertyKeyAndValue() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse(
            "%spark.pyspark(\"po ol\"=\"value with \\\" inside\")");
    assertEquals("spark.pyspark", parseResult.getIntpText());
    assertEquals(1, parseResult.getLocalProperties().size());
    assertEquals("value with \" inside", parseResult.getLocalProperties().get("po ol"));
    assertEquals("", parseResult.getScriptText());
  }


  @Test
  void testParagraphTextUnfinishedConfig() {
    assertThrows(RuntimeException.class,
            () -> ParagraphTextParser.parse("%spark.pyspark(pool="),
            "Problems by parsing paragraph. Not finished interpreter configuration");
  }

  @Test
  void testParagraphTextUnfinishedQuote() {
    assertThrows(RuntimeException.class,
            () -> ParagraphTextParser.parse("%spark.pyspark(pool=\"2314234) sc.version"),
            "Problems by parsing paragraph. Not finished interpreter configuration");
  }

  @Test
  void testParagraphTextUnclosedBackslash() {
    assertThrows(RuntimeException.class,
            () -> ParagraphTextParser.parse("%spark.pyspark(pool=\\"),
            "Problems by parsing paragraph. Unfinished escape sequence");
  }

  @Test
  void testParagraphTextEmptyKey() {
    assertThrows((RuntimeException.class),
            () -> ParagraphTextParser.parse("%spark.pyspark(pool=123, ,)"),
            "Problems by parsing paragraph. Local property key is empty");
  }
}
