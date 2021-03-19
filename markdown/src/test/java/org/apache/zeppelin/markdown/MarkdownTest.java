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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MarkdownTest {

  Markdown md;

  @Before
  public void setUp() {
    Properties props = new Properties();
    props.put(Markdown.MARKDOWN_PARSER_TYPE, Markdown.PARSER_TYPE_MARKDOWN4J);
    md = new Markdown(props);
    md.open();
  }

  @After
  public void tearDown() {
    md.close();
  }

  @Test
  public void sanitizeInput() {
    String input = "This is "
        + "<script>alert(1);</script> "
        + "<div onclick='alert(2)'>this is div</div> "
        + "text";
    String output = md.sanitizeInput(input);
    assertFalse(output.contains("<script>"));
    assertFalse(output.contains("onclick"));
    assertTrue(output.contains("this is div"));
  }
}
