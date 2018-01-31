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

package org.apache.zeppelin.sap.universe;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UniverseUtilTest {

  private UniverseClient universeClient;
  private UniverseUtil universeUtil;

  @Before
  public void beforeTest() throws UniverseException {
      universeUtil = new UniverseUtil();
      UniverseInfo universeInfo = new UniverseInfo("1", "testUniverse", "uvx");
      Map<String, UniverseNodeInfo> testUniverseNodes = new HashMap<>();
      testUniverseNodes.put("[Dimension].[Test].[name1]",
          new UniverseNodeInfo("name1id", "name1", "dimension", "Dimension\\Test",
              "Dimension|folder\\Test|folder\\name1|dimension"));
      testUniverseNodes.put("[Dimension].[Test].[name2]",
          new UniverseNodeInfo("name2id", "name2", "dimension", "Filter\\Test",
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
  }

  @Test
  public void testForConvert() throws UniverseException {
      String request = "universe [testUniverse];\n" +
          "select [Measure].[name5]\n" +
          "where [Filter].[name3] and [Dimension].[Test].[name2] > 1";
      UniverseQuery universeQuery = universeUtil.convertQuery(request, universeClient, null);
      assertNotNull(universeQuery);
      assertNotNull(universeQuery.getUniverseInfo());
      assertEquals("<resultObjects>\n  <resultObject path=\"Measure|folder\\name5|measure\"" +
          " id=\"name5id\"/>\n</resultObjects>", universeQuery.getSelect());
      assertEquals("<and>\n<predefinedFilter path=\"Filter|folder\\name3|filter\"" +
          " id=\"name3id\"/>" +
          "\n<comparisonFilter path=\"Dimension|folder\\Test|folder\\name2|dimension\"" +
          " operator=\"GreaterThan\" id=\"name2id\">\n<constantOperand>\n<value>\n" +
          "<caption type=\"Numeric\">1</caption>\n </value>\n</constantOperand>\n" +
          "</comparisonFilter>\n</and>", universeQuery.getWhere());
      assertEquals("testUniverse", universeQuery.getUniverseInfo().getName());
  }

  @Test
  public void testConvertConditions() throws UniverseException {
    String request = "universe [testUniverse];\n" +
        "select [Measure].[name5]\n" +
        "where [Filter].[name3] " +
        "and [Dimension].[Test].[name2] >= 1 " +
        "and [Dimension].[Test].[name2] < 20 " +
        "and [Dimension].[Test].[name1] <> 'test' " +
        "and [Dimension].[Test].[name1] is not null " +
        "and [Measure].[name5] is null" +
        "and [Dimension].[Test].[name1] in ('var1', 'v a r 2') " +
        "and [Dimension].[Test].[name1] in ('var1','withoutspaces')" +
        "and [Dimension].[Test].[name1] in ('one value')" +
        "and [Dimension].[Test].[name2] in (1,3,4)";
    UniverseQuery universeQuery = universeUtil.convertQuery(request, universeClient, null);
    assertNotNull(universeQuery);
    assertEquals("<and>\n<predefinedFilter path=\"Filter|folder\\name3|filter\" id=\"name3id\"/>" +
        "\n<comparisonFilter path=\"Dimension|folder\\Test|folder\\name2|dimension\" " +
        "operator=\"GreaterThanOrEqualTo\" id=\"name2id\">\n<constantOperand>\n<value>\n" +
        "<caption type=\"Numeric\">1</caption>\n </value>\n</constantOperand>\n" +
        "</comparisonFilter>\n<comparisonFilter" +
        " path=\"Dimension|folder\\Test|folder\\name2|dimension\" operator=\"LessThan\"" +
        " id=\"name2id\">\n<constantOperand>\n<value>\n<caption type=\"Numeric\">20</caption>\n" +
        " </value>\n</constantOperand>\n</comparisonFilter>\n<comparisonFilter " +
        "path=\"Dimension|folder\\Test|folder\\name1|dimension\" operator=\"NotEqualTo\"" +
        " id=\"name1id\">\n<constantOperand>\n<value>\n<caption type=\"String\">test</caption>\n" +
        " </value>\n</constantOperand>\n</comparisonFilter>\n<comparisonFilter id=\"name1id\"" +
        " path=\"Dimension|folder\\Test|folder\\name1|dimension\" operator=\"IsNotNull\"/>\n" +
        "<comparisonFilter id=\"name5id\" path=\"Measure|folder\\name5|measure\"" +
        " operator=\"IsNull\"/>\n<comparisonFilter " +
        "path=\"Dimension|folder\\Test|folder\\name1|dimension\" operator=\"InList\" " +
        "id=\"name1id\">\n<constantOperand>\n<value>\n<caption type=\"String\">var1</caption>\n" +
        " </value>\n<value>\n<caption type=\"String\">v a r 2</caption>\n </value>\n" +
        "</constantOperand>\n</comparisonFilter>\n<comparisonFilter " +
        "path=\"Dimension|folder\\Test|folder\\name1|dimension\" operator=\"InList\" " +
        "id=\"name1id\">\n<constantOperand>\n<value>\n<caption type=\"String\">var1</caption>\n" +
        " </value>\n<value>\n<caption type=\"String\">withoutspaces</caption>\n </value>\n" +
        "</constantOperand>\n</comparisonFilter>\n<comparisonFilter " +
        "path=\"Dimension|folder\\Test|folder\\name1|dimension\" operator=\"InList\" " +
        "id=\"name1id\">\n<constantOperand>\n<value>\n<caption type=\"String\">one " +
        "value</caption>\n </value>\n</constantOperand>\n</comparisonFilter>\n" +
        "<comparisonFilter path=\"Dimension|folder\\Test|folder\\name2|dimension\"" +
        " operator=\"InList\" id=\"name2id\">\n<constantOperand>\n<value>\n" +
        "<caption type=\"Numeric\">1</caption>\n </value>\n<value>\n<caption" +
        " type=\"Numeric\">3</caption>\n </value>\n<value>\n<caption type=\"Numeric\">4" +
        "</caption>\n </value>\n</constantOperand>\n</comparisonFilter>\n</and>",
        universeQuery.getWhere());
  }

  @Test(expected = UniverseException.class)
  public void testFailConvertWithoutUniverse() throws UniverseException {
    String request = "universe ;\n" +
        "select [Measure].[name5]\n" +
        "where [Filter].[name3] and [Dimension].[Test].[name2] > 1";
    universeUtil.convertQuery(request, universeClient, null);
  }

  @Test(expected = UniverseException.class)
  public void testFailConvertWithIncorrectCondition() throws UniverseException {
    String request = "universe [testUniverse];\n" +
        "select [Measure].[name5]\n" +
        "where [Filter].[name";
    universeUtil.convertQuery(request, universeClient, null);
  }

  @Test(expected = UniverseException.class)
  public void testFailConvertWithIncorrectSelect() throws UniverseException {
    String request = "universe [testUniverse];\n" +
        "select [not].[exist]";
    universeUtil.convertQuery(request, universeClient, null);
  }

}
