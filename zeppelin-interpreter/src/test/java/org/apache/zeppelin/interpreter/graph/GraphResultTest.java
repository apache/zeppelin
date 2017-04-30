package org.apache.zeppelin.interpreter.graph;

import static org.junit.Assert.assertEquals;

import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.graph.GraphResult.Graph;
import org.junit.Test;

import com.google.gson.Gson;

public class GraphResultTest {

  @Test
  public void testGraphResult() {
    final String expected = "{\"nodes\":[{\"id\":1,\"label\":\"User\",\"data\":{\"fullname\":\"Andrea Santurbano\"}},"
    		+ "{\"id\":2,\"label\":\"User\",\"data\":{\"fullname\":\"Moon soo Lee\"}}],"
    		+ "\"edges\":[{\"source\":2,\"target\":1,\"id\":1,\"label\":\"HELPS\",\"data\":{\"project\":\"Zeppelin\",\"githubUrl\":\"https://github.com/apache/zeppelin/pull/1582\"}}]}";
    Graph graphExpected = new Gson().fromJson(expected, Graph.class);
    GraphResult intepreterResult = new GraphResult(InterpreterResult.Code.SUCCESS, graphExpected);
    assertEquals("The type is NETWORK", InterpreterResult.Type.NETWORK, intepreterResult.message().get(0).getType());
    Graph resultGraph = new Gson().fromJson(intepreterResult.toString().replace("%network ", ""), Graph.class);
    assertEquals("Nodes must have the same size", graphExpected.getNodes().size(), resultGraph.getNodes().size());
    assertEquals("Edges must have the same size", graphExpected.getEdges().size(), resultGraph.getEdges().size());

    Node nodeSourceExpected = graphExpected.getNodes().iterator().next();
    Node nodeTargetExpected = graphExpected.getNodes().iterator().next();
    Relationship relExpected = graphExpected.getEdges().iterator().next();

    Node nodeSourceResult = resultGraph.getNodes().iterator().next();
    Node nodeTargetResult = resultGraph.getNodes().iterator().next();
    Relationship relResult = resultGraph.getEdges().iterator().next();

    assertEquals("Nodes source must have the same id", nodeSourceExpected.getId(), nodeSourceResult.getId());
    assertEquals("Nodes target must have the same id", nodeTargetExpected.getId(), nodeTargetResult.getId());
    assertEquals("Edges must have the same id", relExpected.getId(), relResult.getId());
    assertEquals("Edges must have the same id", relExpected.getId(), relResult.getId());
  }
}
