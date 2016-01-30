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

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.AngularObjectWatcher;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.junit.Test;

/**
 * Unit tests from {@link AngularBinder}.
 */
public class AngularBinderTest {

  @Test
  public void testBind() {
    String name = "name";
    Object value = "value";
    String noteId = "noteId";
    String paragraphId = "paragraphId";

    InterpreterContext mockContext = mock(InterpreterContext.class);
    AngularObjectWatcher mockWatcher = mock(AngularObjectWatcher.class);
    AngularObjectRegistry mockRegistry = mock(AngularObjectRegistry.class);
    @SuppressWarnings("rawtypes")
    AngularObject mockAngularObject = mock(AngularObject.class);

    when(mockContext.getAngularObjectRegistry()).thenReturn(mockRegistry);
    when(mockRegistry.get(eq(name), eq(noteId), eq(paragraphId))).thenReturn(mockAngularObject);


    AngularBinder.bind(mockContext, name, value, noteId, paragraphId, mockWatcher);

    verify(mockRegistry, times(4)).get(eq(name), eq(noteId), eq(paragraphId));
    verify(mockAngularObject).addWatcher(eq(mockWatcher));
  }
}
