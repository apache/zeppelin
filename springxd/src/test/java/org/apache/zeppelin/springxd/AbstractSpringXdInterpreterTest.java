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

import static org.apache.zeppelin.springxd.AbstractSpringXdInterpreter.DEFAULT_SPRINGXD_URL;
import static org.apache.zeppelin.springxd.AbstractSpringXdInterpreter.SPRINGXD_URL;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.zeppelin.interpreter.Interpreter.FormType;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.junit.Before;
import org.junit.Test;
import org.springframework.xd.rest.client.impl.SpringXDTemplate;

/**
 * Unit tests for {@link AbstractSpringXdInterpreter}
 */
public class AbstractSpringXdInterpreterTest {

  InterpreterContext mockContext;

  AbstractSpringXdResourceCompletion mockXdResourceCompletion;

  AbstractSpringXdResourceManager mockXdResourceManager;

  Properties props;

  AbstractSpringXdInterpreter xdInterpreter;

  private SpringXDTemplate mockSpringXDTemplate;

  @Before
  public void before() {
    mockContext = mock(InterpreterContext.class);
    mockSpringXDTemplate = mock(SpringXDTemplate.class);
    mockXdResourceCompletion = mock(AbstractSpringXdResourceCompletion.class);
    mockXdResourceManager = mock(AbstractSpringXdResourceManager.class);

    props = new Properties();
    props.put(SPRINGXD_URL, DEFAULT_SPRINGXD_URL);
  }

  @Test(expected = NullPointerException.class)
  public void testInterpretMissingOpen() {
    xdInterpreter = getXdInterpreter(props);
    xdInterpreter.interpret("multiLineResourceDefinitions", mockContext);
  }

  @Test
  public void testOpen() throws URISyntaxException {

    xdInterpreter = spy(getXdInterpreter(props));

    xdInterpreter.open();

    verify(xdInterpreter).close();
    verify(xdInterpreter).doCreateResourceCompletion();
    verify(xdInterpreter).doCreateResourceManager();
    verify(xdInterpreter).doCreateSpringXDTemplate(eq(new URI(DEFAULT_SPRINGXD_URL)));
  }

  @Test
  public void testClose() {
    xdInterpreter = spy(getXdInterpreter(props));

    xdInterpreter.open();

    xdInterpreter.close();

    verify(xdInterpreter, times(2)).close(); // fist close happens during the open()
    verify(mockXdResourceManager).destroyAllNotebookDeployedResources();
  }

  @Test
  public void testCancel() {
    xdInterpreter = spy(getXdInterpreter(props));
    xdInterpreter.open();

    String noteId = "noteId";
    String paragraphId = "paragraphId";

    when(mockContext.getNoteId()).thenReturn(noteId);
    when(mockContext.getParagraphId()).thenReturn(paragraphId);

    xdInterpreter.cancel(mockContext);

    verify(mockXdResourceManager).destroyDeployedResourceBy(eq(noteId), eq(paragraphId));
  }

  @Test
  public void testProgress() {
    xdInterpreter = spy(getXdInterpreter(props));
    assertEquals(0, xdInterpreter.getProgress(mockContext));
  }

  @Test
  public void testGetFormType() {
    xdInterpreter = spy(getXdInterpreter(props));
    assertEquals(FormType.SIMPLE, xdInterpreter.getFormType());
  }

  @Test
  public void testCompetion() {
    xdInterpreter = spy(getXdInterpreter(props));
    xdInterpreter.open();

    String buf = "buf";
    int cursor = 666;
    List<String> expectedCompletion = Arrays.asList("1", "2", "3");

    when(mockXdResourceCompletion.completion(eq(buf), eq(cursor))).thenReturn(expectedCompletion);

    List<String> completion = xdInterpreter.completion(buf, cursor);

    assertEquals(expectedCompletion, completion);
  }

  @Test
  public void testInterpret() throws URISyntaxException {

    xdInterpreter = spy(getXdInterpreter(props));
    xdInterpreter.open();

    String noteId = "noteId";
    String paragraphId = "paragraphId";

    when(mockContext.getNoteId()).thenReturn(noteId);
    when(mockContext.getParagraphId()).thenReturn(paragraphId);

    doNothing().when(mockXdResourceManager).destroyDeployedResourceBy(eq(noteId), eq(paragraphId));

    InterpreterResult result = xdInterpreter.interpret("multiLineResourceDefinitions", mockContext);

    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    verify(xdInterpreter).close();

    verify(xdInterpreter).doCreateResourceCompletion();
    verify(xdInterpreter).doCreateResourceManager();
    verify(xdInterpreter).doCreateSpringXDTemplate(eq(new URI(DEFAULT_SPRINGXD_URL)));
    verify(xdInterpreter).doCreateAngularResponse(eq(mockContext));
  }

  private AbstractSpringXdInterpreter getXdInterpreter(Properties props) {

    return new AbstractSpringXdInterpreter(props) {

      @Override
      public AbstractSpringXdResourceCompletion doCreateResourceCompletion() {
        return mockXdResourceCompletion;
      }

      @Override
      public AbstractSpringXdResourceManager doCreateResourceManager() {
        return mockXdResourceManager;
      }

      @Override
      protected SpringXDTemplate doCreateSpringXDTemplate(URI uri) {
        return mockSpringXDTemplate;
      }

      @Override
      protected String doCreateAngularResponse(InterpreterContext ctx) {
        return "ANGULAR RESPONSE";
      }
    };
  }
}
