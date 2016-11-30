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

package org.apache.zeppelin.markdown;

import org.apache.zeppelin.interpreter.InterpreterResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class Markdown4jParserTest {

  Markdown md;

  @Before
  public void setUp() throws Exception {
    Properties props = new Properties();
    props.put(Markdown.MARKDOWN_PARSER_TYPE, Markdown.PARSER_TYPE_MARKDOWN4J);
    md = new Markdown(props);
    md.open();
  }

  @After
  public void tearDown() throws Exception {
    md.close();
  }

  @Test
  public void testStrikethrough() {
    InterpreterResult result = md.interpret("This is ~~deleted~~ text", null);
    assertEquals("<p>This is <s>deleted</s> text</p>\n", result.message().get(0).getData());
  }
}
