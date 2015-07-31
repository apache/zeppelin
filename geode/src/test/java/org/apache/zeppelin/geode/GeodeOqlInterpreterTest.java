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

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

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

  @Test
  public void oqlNumberResponse() throws Exception {
    oqlTest(new ArrayList<Object>(Arrays.asList(66, 67)).iterator(), "Result\n66\n67\n");
  }

  @Test
  public void oqlStructResponse() throws Exception {
    String[] fields = new String[] {"field1", "field2"};
    Struct struct = new StructImpl(new StructTypeImpl(fields), new String[] {"val1", "val2"});

    oqlTest(new ArrayList<Object>(Arrays.asList(struct)).iterator(),
        "field1\tfield2\t\nval1\tval2\t\n");
  }

  @Test
  public void oqlPdxInstanceResponse() throws Exception {
    ByteArrayInputStream bais = new ByteArrayInputStream("koza\tboza\n".getBytes());
    PdxInstance pdxInstance = new PdxInstanceImpl(new PdxType(), new DataInputStream(bais), 4);
    oqlTest(new ArrayList<Object>(Arrays.asList(pdxInstance)).iterator(), "\n\n");
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
    oqlTest(new ArrayList<Object>(Arrays.asList(unspported1, unspported2)).iterator(),
        "Unsuppoted Type\n" + unspported1.toString() + "\n" + unspported1.toString() + "\n");
  }

  private void oqlTest(Iterator<Object> queryResponseIterator, String expectedOutput)
      throws Exception {

    GeodeOqlInterpreter spyGeodeOqlInterpreter = spy(new GeodeOqlInterpreter(new Properties()));

    QueryService mockQueryService = mock(QueryService.class, RETURNS_DEEP_STUBS);

    when(spyGeodeOqlInterpreter.getQueryService()).thenReturn(mockQueryService);

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

    when(spyGeodeOqlInterpreter.getQueryService())
        .thenThrow(new RuntimeException("Test exception"));

    InterpreterResult interpreterResult = spyGeodeOqlInterpreter.interpret(OQL_QUERY, null);

    assertEquals(Code.ERROR, interpreterResult.code());
    assertEquals("Test exception", interpreterResult.message());
  }

  @Test
  public void testFormType() {
    assertEquals(FormType.SIMPLE, new GeodeOqlInterpreter(new Properties()).getFormType());
  }
}
