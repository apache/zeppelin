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

import static org.apache.zeppelin.springxd.AbstractSpringXdResourceCompletion.EMPTY_ZEPPELIN_COMPLETION;
import static org.apache.zeppelin.springxd.AbstractSpringXdResourceCompletion.LINE_SEPARATOR;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link AbstractSpringXdResourceCompletion}
 */
public class AbstractSpringXdResourceCompletionTest {

  private AbstractSpringXdResourceCompletion resourceCompletion;

  @Before
  public void before() {
    resourceCompletion = new AbstractSpringXdResourceCompletion() {
      @Override
      public List<String> doSpringXdCompletion(String completionPreffix) {
        return Arrays.asList(completionPreffix + "one", completionPreffix + "two",
            completionPreffix + "three");
      }
    };
  }

  @Test
  public void testCompletion() {
    assertEquals(Arrays.asList("one", "two", "three"), resourceCompletion.completion("boza", 2));
  }

  @Test
  public void testGetCompletionPreffixSingleLine() {
    String multilineBufffer = "012 456";

    assertEquals("", resourceCompletion.getCompletionPreffix(multilineBufffer, 0));
    assertEquals("01", resourceCompletion.getCompletionPreffix(multilineBufffer, 3));
    assertEquals("012 456", resourceCompletion.getCompletionPreffix(multilineBufffer, 8));
    assertEquals("012 456", resourceCompletion.getCompletionPreffix(multilineBufffer, 9));
    assertEquals("012 456", resourceCompletion.getCompletionPreffix(multilineBufffer, 100));
  }

  @Test
  public void testGetCompletionPreffixMultiLine() {
    String line1 = "012 456";
    String line2 = "654 210";
    String line3 = "987 564";

    String multilineBufffer = line1 + LINE_SEPARATOR + line2 + LINE_SEPARATOR + line3;

    // line 1
    assertEquals("", resourceCompletion.getCompletionPreffix(multilineBufffer, 0));
    assertEquals("0", resourceCompletion.getCompletionPreffix(multilineBufffer, 2));
    assertEquals("012 456",
        resourceCompletion.getCompletionPreffix(multilineBufffer, line1.length() + 1));

    // line 2
    assertEquals("", resourceCompletion.getCompletionPreffix(multilineBufffer, 9));
    assertEquals("65", resourceCompletion.getCompletionPreffix(multilineBufffer, 11));
    assertEquals(
        "654 210",
        resourceCompletion.getCompletionPreffix(multilineBufffer,
            (line1 + LINE_SEPARATOR + line2).length() + 1));

    // line 3
    assertEquals("", resourceCompletion.getCompletionPreffix(multilineBufffer, 17));
    assertEquals("98", resourceCompletion.getCompletionPreffix(multilineBufffer, 19));
    assertEquals("987 564",
        resourceCompletion.getCompletionPreffix(multilineBufffer, multilineBufffer.length() + 1));
  }

  @Test
  public void testConvertXdToZeppelinCompletions() {

    List<String> xdCompletions = Arrays.asList("boza one", "boza two", "boza three");

    assertEquals(Arrays.asList("boza one", "boza two", "boza three"),
        resourceCompletion.convertXdToZeppelinCompletions(xdCompletions, ""));
    assertEquals(Arrays.asList("boza one", "boza two", "boza three"),
        resourceCompletion.convertXdToZeppelinCompletions(xdCompletions, null));

    assertEquals(Arrays.asList("one", "two", "three"),
        resourceCompletion.convertXdToZeppelinCompletions(xdCompletions, "boza "));

    assertEquals(EMPTY_ZEPPELIN_COMPLETION,
        resourceCompletion.convertXdToZeppelinCompletions(null, "fsadfasdf"));
    assertEquals(EMPTY_ZEPPELIN_COMPLETION,
        resourceCompletion.convertXdToZeppelinCompletions(new ArrayList<String>(), "fsadfasdf"));
  }
}
