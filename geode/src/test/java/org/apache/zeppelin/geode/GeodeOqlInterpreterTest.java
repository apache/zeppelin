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
package org.apache.zeppelin.geode;

import static org.apache.zeppelin.geode.GeodeOqlInterpreter.*;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Properties;

import org.apache.zeppelin.interpreter.Interpreter.FormType;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.junit.Test;

import com.gemstone.gemfire.cache.query.QueryService;
import com.gemstone.gemfire.cache.query.SelectResults;
import com.gemstone.gemfire.cache.query.Struct;
import com.gemstone.gemfire.cache.query.internal.StructImpl;
import com.gemstone.gemfire.cache.query.internal.types.StructTypeImpl;
import com.gemstone.gemfire.pdx.PdxInstance;
import com.gemstone.gemfire.pdx.internal.PdxInstanceImpl;
import com.gemstone.gemfire.pdx.internal.PdxType;

public class GeodeOqlInterpreterTest {

  private static final String OQL_QUERY = "select * from /region";

  private static Iterator<Object> asIterator(Object... items) {
    return new ArrayList<Object>(Arrays.asList(items)).iterator();
  }

  @Test
  public void testOpenCommandIndempotency() {

    Properties properties = new Properties();
    properties.put(LOCATOR_HOST, DEFAULT_HOST);
    properties.put(LOCATOR_PORT, DEFAULT_PORT);
    properties.put(MAX_RESULT, DEFAULT_MAX_RESULT);

    GeodeOqlInterpreter spyGeodeOqlInterpreter = spy(new GeodeOqlInterpreter(properties));

    // Ensure that an attempt to open new connection will clean any remaining connections
    spyGeodeOqlInterpreter.open();
    spyGeodeOqlInterpreter.open();
    spyGeodeOqlInterpreter.open();

    verify(spyGeodeOqlInterpreter, times(3)).open();
    verify(spyGeodeOqlInterpreter, times(3)).close();
  }

  @Test
  public void oqlNumberResponse() throws Exception {
    testOql(asIterator(66, 67), "Result\n66\n67\n", 10);
    testOql(asIterator(66, 67), "Result\n66\n", 1);
  }

  @Test
  public void oqlStructResponse() throws Exception {
    String[] fields = new String[] {"field1", "field2"};
    Struct s1 = new StructImpl(new StructTypeImpl(fields), new String[] {"val11", "val12"});
    Struct s2 = new StructImpl(new StructTypeImpl(fields), new String[] {"val21", "val22"});

    testOql(asIterator(s1, s2), "field1\tfield2\t\nval11\tval12\t\nval21\tval22\t\n", 10);
    testOql(asIterator(s1, s2), "field1\tfield2\t\nval11\tval12\t\n", 1);
  }

  @Test
  public void oqlStructResponseWithReservedCharacters() throws Exception {
    String[] fields = new String[] {"fi\teld1", "f\nield2"};
    Struct s1 = new StructImpl(new StructTypeImpl(fields), new String[] {"v\nal\t1", "val2"});

    testOql(asIterator(s1), "fi eld1\tf ield2\t\nv al 1\tval2\t\n", 10);
  }

  @Test
  public void oqlPdxInstanceResponse() throws Exception {
    ByteArrayInputStream bais = new ByteArrayInputStream("koza\tboza\n".getBytes());
    PdxInstance pdx1 = new PdxInstanceImpl(new PdxType(), new DataInputStream(bais), 4);
    PdxInstance pdx2 = new PdxInstanceImpl(new PdxType(), new DataInputStream(bais), 4);

    testOql(asIterator(pdx1, pdx2), "\n\n\n", 10);
    testOql(asIterator(pdx1, pdx2), "\n\n", 1);
  }

  private static class DummyUnspportedType {
    @Override
    public String toString() {
      return "Unsupported Indeed";
    }
  }

  @Test
  public void oqlUnsupportedTypeResponse() throws Exception {
    DummyUnspportedType unspported1 = new DummyUnspportedType();
    DummyUnspportedType unspported2 = new DummyUnspportedType();

    testOql(asIterator(unspported1, unspported2), "Unsuppoted Type\n" + unspported1.toString()
        + "\n" + unspported1.toString() + "\n", 10);
  }

  private void testOql(Iterator<Object> queryResponseIterator, String expectedOutput, int maxResult)
      throws Exception {

    GeodeOqlInterpreter spyGeodeOqlInterpreter = spy(new GeodeOqlInterpreter(new Properties()));

    QueryService mockQueryService = mock(QueryService.class, RETURNS_DEEP_STUBS);

    when(spyGeodeOqlInterpreter.getQueryService()).thenReturn(mockQueryService);
    when(spyGeodeOqlInterpreter.getMaxResult()).thenReturn(maxResult);

    @SuppressWarnings("unchecked")
    SelectResults<Object> mockResults = mock(SelectResults.class);

    when(mockQueryService.newQuery(eq(OQL_QUERY)).execute()).thenReturn(mockResults);

    when(mockResults.iterator()).thenReturn(queryResponseIterator);

    InterpreterResult interpreterResult = spyGeodeOqlInterpreter.interpret(OQL_QUERY, null);

    assertEquals(Code.SUCCESS, interpreterResult.code());
    assertEquals(expectedOutput, interpreterResult.message());
  }

  @Test
  public void oqlWithQueryException() throws Exception {

    GeodeOqlInterpreter spyGeodeOqlInterpreter = spy(new GeodeOqlInterpreter(new Properties()));

    when(spyGeodeOqlInterpreter.getExceptionOnConnect()).thenReturn(
        new RuntimeException("Test Exception On Connect"));

    InterpreterResult interpreterResult = spyGeodeOqlInterpreter.interpret(OQL_QUERY, null);

    assertEquals(Code.ERROR, interpreterResult.code());
    assertEquals("Test Exception On Connect", interpreterResult.message());
  }

  @Test
  public void oqlWithExceptionOnConnect() throws Exception {

    GeodeOqlInterpreter spyGeodeOqlInterpreter = spy(new GeodeOqlInterpreter(new Properties()));

    when(spyGeodeOqlInterpreter.getQueryService()).thenThrow(
        new RuntimeException("Expected Test Exception!"));

    InterpreterResult interpreterResult = spyGeodeOqlInterpreter.interpret(OQL_QUERY, null);

    assertEquals(Code.ERROR, interpreterResult.code());
    assertEquals("Expected Test Exception!", interpreterResult.message());
  }

  @Test
  public void testFormType() {
    assertEquals(FormType.SIMPLE, new GeodeOqlInterpreter(new Properties()).getFormType());
  }
}
