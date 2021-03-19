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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static junit.framework.TestCase.assertEquals;

public class ParagraphTextParserTest {

  @Test
  public void testJupyter() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse("%jupyter(kernel=ir)");
    assertEquals("jupyter", parseResult.getIntpText());
    assertEquals(1, parseResult.getLocalProperties().size());
    assertEquals("ir", parseResult.getLocalProperties().get("kernel"));
    assertEquals("", parseResult.getScriptText());
  }


  @Test
  public void testCassandra() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse(
            "%cassandra(locale=ru_RU, timeFormat=\"E, d MMM yy\", floatPrecision = 5, output=cql)\n"
                    + "select * from system_auth.roles;");
    assertEquals("cassandra", parseResult.getIntpText());
    assertEquals(4, parseResult.getLocalProperties().size());
    assertEquals("E, d MMM yy", parseResult.getLocalProperties().get("timeFormat"));
    assertEquals("\nselect * from system_auth.roles;", parseResult.getScriptText());
  }

  @Test
  public void testSparkSubmit() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse(
            "%spark-submit --class A a.jar");
    assertEquals("spark-submit", parseResult.getIntpText());
    assertEquals("--class A a.jar", parseResult.getScriptText());
  }

  @Test
  public void testParagraphTextLocalPropertiesAndText() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse("%spark.pyspark(pool=pool_1) sc.version");
    assertEquals("spark.pyspark", parseResult.getIntpText());
    assertEquals(1, parseResult.getLocalProperties().size());
    assertEquals("pool_1", parseResult.getLocalProperties().get("pool"));
    assertEquals("sc.version", parseResult.getScriptText());
  }

  @Test
  public void testParagraphTextLocalPropertiesNoText() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse("%spark.pyspark(pool=pool_1)");
    assertEquals("spark.pyspark", parseResult.getIntpText());
    assertEquals(1, parseResult.getLocalProperties().size());
    assertEquals("pool_1", parseResult.getLocalProperties().get("pool"));
    assertEquals("", parseResult.getScriptText());
  }

  @Test
  public void testParagraphTextLocalPropertyNoValueNoText() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse("%spark.pyspark(pool)");
    assertEquals("spark.pyspark", parseResult.getIntpText());
    assertEquals(1, parseResult.getLocalProperties().size());
    assertEquals("pool", parseResult.getLocalProperties().get("pool"));
    assertEquals("", parseResult.getScriptText());
  }

  @Test
  public void testParagraphTextNoLocalProperties() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse("%spark.pyspark\nsc.version");
    assertEquals("spark.pyspark", parseResult.getIntpText());
    assertEquals(0, parseResult.getLocalProperties().size());
    assertEquals("\nsc.version", parseResult.getScriptText());
  }

  @Test
  public void testParagraphNoInterpreter() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse("sc.version");
    assertEquals("", parseResult.getIntpText());
    assertEquals(0, parseResult.getLocalProperties().size());
    assertEquals("sc.version", parseResult.getScriptText());
  }

  @Test
  public void testParagraphInterpreterWithoutProperties() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse("%spark() sc.version");
    assertEquals("spark", parseResult.getIntpText());
    assertEquals(0, parseResult.getLocalProperties().size());
    assertEquals("sc.version", parseResult.getScriptText());
  }

  @Test
  public void testParagraphTextQuotedPropertyValue1() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse(
            "%spark.pyspark(pool=\"value with = inside\")");
    assertEquals("spark.pyspark", parseResult.getIntpText());
    assertEquals(1, parseResult.getLocalProperties().size());
    assertEquals("value with = inside", parseResult.getLocalProperties().get("pool"));
    assertEquals("", parseResult.getScriptText());
  }

  @Test
  public void testParagraphTextQuotedPropertyValue2() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse(
            "%spark.pyspark(pool=\"value with \\\" inside\", p=\"eol\\ninside\" )");
    assertEquals("spark.pyspark", parseResult.getIntpText());
    assertEquals(2, parseResult.getLocalProperties().size());
    assertEquals("value with \" inside", parseResult.getLocalProperties().get("pool"));
    assertEquals("eol\ninside", parseResult.getLocalProperties().get("p"));
    assertEquals("", parseResult.getScriptText());
  }

  @Test
  public void testParagraphTextQuotedPropertyKeyAndValue() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse(
            "%spark.pyspark(\"po ol\"=\"value with \\\" inside\")");
    assertEquals("spark.pyspark", parseResult.getIntpText());
    assertEquals(1, parseResult.getLocalProperties().size());
    assertEquals("value with \" inside", parseResult.getLocalProperties().get("po ol"));
    assertEquals("", parseResult.getScriptText());
  }

  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();

  @Test
  public void testParagraphTextUnfinishedConfig() {
    exceptionRule.expect(RuntimeException.class);
    exceptionRule.expectMessage(
            "Problems by parsing paragraph. Not finished interpreter configuration");
    ParagraphTextParser.parse("%spark.pyspark(pool=");
  }

  @Test
  public void testParagraphTextUnfinishedQuote() {
    exceptionRule.expect(RuntimeException.class);
    exceptionRule.expectMessage(
            "Problems by parsing paragraph. Not finished interpreter configuration");
    ParagraphTextParser.parse("%spark.pyspark(pool=\"2314234) sc.version");
  }

  @Test
  public void testParagraphTextUnclosedBackslash() {
    exceptionRule.expect(RuntimeException.class);
    exceptionRule.expectMessage(
            "Problems by parsing paragraph. Unfinished escape sequence");
    ParagraphTextParser.parse("%spark.pyspark(pool=\\");
  }

  @Test
  public void testParagraphTextEmptyKey() {
    exceptionRule.expect(RuntimeException.class);
    exceptionRule.expectMessage(
            "Problems by parsing paragraph. Local property key is empty");
    ParagraphTextParser.parse("%spark.pyspark(pool=123, ,)");
  }
}
