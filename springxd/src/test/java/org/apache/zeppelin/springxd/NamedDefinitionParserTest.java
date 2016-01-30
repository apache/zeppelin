/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.zeppelin.springxd;

import static org.apache.zeppelin.springxd.NamedDefinitionParser.getNamedDefinition;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Unit tests for {@link NamedDefinitionParser}
 */
public class NamedDefinitionParserTest {

  @Test
  public void testBlankInputLine() {
    assertEquals("", getNamedDefinition(null).getLeft());
    assertEquals("", getNamedDefinition(null).getRight());

    assertEquals("", getNamedDefinition(" ").getLeft());
    assertEquals("", getNamedDefinition(" ").getRight());

    assertEquals("boza", getNamedDefinition("boza=").getLeft());
    assertEquals("", getNamedDefinition("boza=").getRight());
  }

  @Test
  public void testNonComplientFormat() {
    assertEquals("", getNamedDefinition("boza").getLeft());
    assertEquals("", getNamedDefinition("boza").getRight());

    // Empty name is not accepted
    assertEquals("", getNamedDefinition("=boza").getLeft());
    assertEquals("", getNamedDefinition("=boza").getRight());

    assertEquals("", getNamedDefinition("name-1=definition").getLeft());
    assertEquals("", getNamedDefinition("name-1=definition").getRight());
  }

  @Test
  public void testEmptyDefinition() {
    assertEquals("boza", getNamedDefinition("boza=").getLeft());
    assertEquals("", getNamedDefinition("boza=").getRight());
  }

  @Test
  public void testComplientDefinitions() {
    assertEquals("name", getNamedDefinition("name=").getLeft());
    assertEquals("", getNamedDefinition("name=").getRight());

    assertEquals("name", getNamedDefinition("name=definition").getLeft());
    assertEquals("definition", getNamedDefinition("name=definition").getRight());

    assertEquals("name_1", getNamedDefinition("name_1=definition").getLeft());
    assertEquals("definition", getNamedDefinition("name_1=definition").getRight());

    // Check space truncation
    assertEquals("name", getNamedDefinition("  name   = definition  ").getLeft());
    assertEquals("definition  ", getNamedDefinition("  name   = definition  ").getRight());
  }
}
