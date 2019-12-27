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

import org.junit.Test;

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
  public void testParagraphText() {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse("%spark.pyspark(pool=pool_1) sc.version");
    assertEquals("spark.pyspark", parseResult.getIntpText());
    assertEquals(1, parseResult.getLocalProperties().size());
    assertEquals("pool_1", parseResult.getLocalProperties().get("pool"));
    assertEquals("sc.version", parseResult.getScriptText());

    // no script text
    parseResult = ParagraphTextParser.parse("%spark.pyspark(pool=pool_1)");
    assertEquals("spark.pyspark", parseResult.getIntpText());
    assertEquals(1, parseResult.getLocalProperties().size());
    assertEquals("pool_1", parseResult.getLocalProperties().get("pool"));
    assertEquals("", parseResult.getScriptText());

    // no paragraph local properties
    parseResult = ParagraphTextParser.parse("%spark.pyspark sc.version");
    assertEquals("spark.pyspark", parseResult.getIntpText());
    assertEquals(0, parseResult.getLocalProperties().size());
    assertEquals("sc.version", parseResult.getScriptText());

    // no intp text and paragraph local properties
    parseResult = ParagraphTextParser.parse("sc.version");
    assertEquals("", parseResult.getIntpText());
    assertEquals(0, parseResult.getLocalProperties().size());
    assertEquals("sc.version", parseResult.getScriptText());
  }
}
