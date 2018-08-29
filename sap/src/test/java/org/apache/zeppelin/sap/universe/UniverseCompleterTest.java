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
package org.apache.zeppelin.sap.universe;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.completer.CachedCompleter;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Universe completer unit tests
 */
public class UniverseCompleterTest {

  private UniverseCompleter universeCompleter;
  private UniverseUtil universeUtil;
  private UniverseClient universeClient;

  @Before
  public void beforeTest() throws UniverseException {
    universeCompleter = new UniverseCompleter(0);
    universeUtil = new UniverseUtil();
    Map<String, UniverseInfo> universes = new HashMap<>();

    universes.put("testUniverse", new UniverseInfo("1", "testUniverse", "uvx"));
    universes.put("test with space", new UniverseInfo("2", "test with space", "uvx"));
    universes.put("(GLOBAL) universe", new UniverseInfo("3", "(GLOBAL) universe", "uvx"));
    UniverseInfo universeInfo = new UniverseInfo("1", "testUniverse", "uvx");
    Map<String, UniverseNodeInfo> testUniverseNodes = new HashMap<>();
    testUniverseNodes.put("[Dimension].[Test].[name1]",
        new UniverseNodeInfo("name1id", "name1", "dimension", "Dimension\\Test",
            "Dimension|folder\\Test|folder\\name1|dimension"));
    testUniverseNodes.put("[Dimension].[Test].[name2]",
        new UniverseNodeInfo("name2id", "name2", "dimension", "Dimension\\Test",
            "Dimension|folder\\Test|folder\\name2|dimension"));
    testUniverseNodes.put("[Filter].[name3]",
        new UniverseNodeInfo("name3id", "name3", "filter", "Filter",
            "Filter|folder\\name3|filter"));
    testUniverseNodes.put("[Filter].[name4]",
        new UniverseNodeInfo("name4id", "name4", "filter", "Filter",
            "Filter|folder\\name4|filter"));
    testUniverseNodes.put("[Measure].[name5]",
        new UniverseNodeInfo("name5id", "name5", "measure", "Measure",
            "Measure|folder\\name5|measure"));

    universeClient = mock(UniverseClient.class);
    when(universeClient.getUniverseInfo(anyString())).thenReturn(universeInfo);
    when(universeClient.getUniverseNodesInfo(anyString(), anyString()))
        .thenReturn(testUniverseNodes);
    when(universeClient.getUniversesMap()).thenReturn(universes);
  }

  @Test
  public void testCreateUniverseNameCompleter() {
    String buffer = "universe [";
    List<CharSequence> candidates = new ArrayList<>();
    universeCompleter.createOrUpdate(universeClient, null, buffer, 9);
    CachedCompleter completer = universeCompleter.getUniverseCompleter();
    assertNull(completer);
    universeCompleter.createOrUpdate(universeClient, null, buffer, 10);
    completer = universeCompleter.getUniverseCompleter();
    assertNotNull(completer);

    completer.getCompleter().complete(StringUtils.EMPTY, 0, candidates);
    assertEquals(3, candidates.size());
  }

  @Test
  public void testCreateUniverseNodesCompleter() {
    String buffer = "universe [testUniverse]; select [";
    List<CharSequence> candidates = new ArrayList<>();
    universeCompleter.createOrUpdate(universeClient, null, buffer, 32);
    Map<String, CachedCompleter> completerMap = universeCompleter.getUniverseInfoCompletersMap();
    assertFalse(completerMap.containsKey("testUniverse"));
    universeCompleter.createOrUpdate(universeClient, null, buffer, 33);
    completerMap = universeCompleter.getUniverseInfoCompletersMap();
    assertTrue(completerMap.containsKey("testUniverse"));
    CachedCompleter completer = completerMap.get("testUniverse");

    completer.getCompleter().complete(StringUtils.EMPTY, 0, candidates);
    assertEquals(3, candidates.size());
    List<String> candidatesStrings = new ArrayList<>();
    for (Object o : candidates) {
      UniverseNodeInfo info = (UniverseNodeInfo) o;
      candidatesStrings.add(info.getName());
    }
    List<String> expected = Arrays.asList("Filter", "Measure", "Dimension");
    Collections.sort(candidatesStrings);
    Collections.sort(expected);
    assertEquals(expected, candidatesStrings);
  }

  @Test
  public void testNestedUniverseNodes() {
    String buffer = "universe [testUniverse]; select [Dimension].[Test].[n";
    List<CharSequence> candidates = new ArrayList<>();

    universeCompleter.createOrUpdate(universeClient, null, buffer, 53);
    Map<String, CachedCompleter> completerMap = universeCompleter.getUniverseInfoCompletersMap();
    assertTrue(completerMap.containsKey("testUniverse"));
    CachedCompleter completer = completerMap.get("testUniverse");

    completer.getCompleter().complete("[Dimension].[Test].[n", 21, candidates);
    assertEquals(2, candidates.size());
    List<String> candidatesStrings = new ArrayList<>();
    for (Object o : candidates) {
      UniverseNodeInfo info = (UniverseNodeInfo) o;
      candidatesStrings.add(info.getName());
    }
    List<String> expected = Arrays.asList("name1", "name2");
    Collections.sort(candidatesStrings);
    Collections.sort(expected);
    assertEquals(expected, candidatesStrings);
  }
}
