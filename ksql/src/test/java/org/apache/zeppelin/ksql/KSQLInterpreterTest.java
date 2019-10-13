/*
* Copyright 2016 Google Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0

* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.apache.zeppelin.ksql;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Stubber;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class KSQLInterpreterTest {

  private InterpreterContext context;

  private static final Map<String, String> PROPS = new HashMap<String, String>() {{
      put("ksql.url", "http://localhost:8088");
      put("ksql.streams.auto.offset.reset", "earliest");
    }};


  @Before
  public void setUpZeppelin() throws IOException {
    context = InterpreterContext.builder()
        .setInterpreterOut(new InterpreterOutput(null))
        .setParagraphId("ksql-test")
        .build();
  }

  @Test
  public void shouldRenderKSQLSelectAsTable() throws InterpreterException,
      IOException, InterruptedException {
    // given
    Properties p = new Properties();
    p.putAll(PROPS);
    KSQLRestService service = Mockito.mock(KSQLRestService.class);
    Stubber stubber = Mockito.doAnswer((invocation) -> {
      Consumer<  KSQLResponse> callback = (Consumer<  KSQLResponse>)
            invocation.getArguments()[2];
      IntStream.range(1, 5)
          .forEach(i -> {
            Map<String, Object> map = new HashMap<>();
            if (i == 4) {
              map.put("row", null);
              map.put("terminal", true);
            } else {
              map.put("row", Collections.singletonMap("columns", Arrays.asList("value " + i)));
              map.put("terminal", false);
            }
            callback.accept(new KSQLResponse(Arrays.asList("fieldName"), map));
            try {
              Thread.sleep(3000);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
          });
      return null;
    });
    stubber.when(service).executeQuery(Mockito.any(String.class),
          Mockito.anyString(),
          Mockito.any(Consumer.class));
    Interpreter interpreter = new KSQLInterpreter(p, service);

    // when
    String query = "select * from orders";
    interpreter.interpret(query, context);

    // then
    String expected = "%table fieldName\n" +
        "value 1\n" +
        "value 2\n" +
        "value 3\n";
    assertEquals(1, context.out.toInterpreterResultMessage().size());
    assertEquals(expected, context.out.toInterpreterResultMessage().get(0).toString());
    assertEquals(InterpreterResult.Type.TABLE, context.out
        .toInterpreterResultMessage().get(0).getType());
    interpreter.close();
  }

  @Test
  public void shouldRenderKSQLNonSelectAsTable() throws InterpreterException,
      IOException, InterruptedException {
    // given
    Properties p = new Properties();
    p.putAll(PROPS);
    KSQLRestService service = Mockito.mock(KSQLRestService.class);
    Map<String, Object> row1 = new HashMap<>();
    row1.put("name", "orders");
    row1.put("registered", "false");
    row1.put("replicaInfo", "[1]");
    row1.put("consumerCount", "0");
    row1.put("consumerGroupCount", "0");
    Map<String, Object> row2 = new HashMap<>();
    row2.put("name", "orders");
    row2.put("registered", "false");
    row2.put("replicaInfo", "[1]");
    row2.put("consumerCount", "0");
    row2.put("consumerGroupCount", "0");
    Stubber stubber = Mockito.doAnswer((invocation) -> {
      Consumer<  KSQLResponse> callback = (Consumer<  KSQLResponse>)
            invocation.getArguments()[2];
      callback.accept(new KSQLResponse(row1));
      callback.accept(new KSQLResponse(row2));
      return null;
    });
    stubber.when(service).executeQuery(
        Mockito.any(String.class),
        Mockito.anyString(),
        Mockito.any(Consumer.class));
    Interpreter interpreter = new KSQLInterpreter(p, service);

    // when
    String query = "show topics";
    interpreter.interpret(query, context);

    // then
    List<Map<String, Object>> expected = Arrays.asList(row1, row2);

    String[] lines = context.out.toInterpreterResultMessage()
        .get(0).toString()
        .replace("%table ", "")
        .trim()
        .split("\n");
    List<String[]> rows = Stream.of(lines)
        .map(line -> line.split("\t"))
        .collect(Collectors.toList());
    List<Map<String, String>> actual = rows.stream()
        .skip(1)
        .map(row -> IntStream.range(0, row.length)
            .mapToObj(index -> new AbstractMap.SimpleEntry<>(rows.get(0)[index], row[index]))
            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue())))
        .collect(Collectors.toList());
    assertEquals(1, context.out.toInterpreterResultMessage().size());
    assertEquals(expected, actual);
    assertEquals(InterpreterResult.Type.TABLE, context.out
        .toInterpreterResultMessage().get(0).getType());
  }
}
