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

package org.apache.zeppelin.notebook;


import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectBuilder;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class ParagraphTest {
  @Test
  public void scriptBodyWithReplName() {
    String text = "%spark(1234567";
    assertEquals("(1234567", Paragraph.getScriptBody(text));

    text = "%table 1234567";
    assertEquals("1234567", Paragraph.getScriptBody(text));
  }

  @Test
  public void scriptBodyWithoutReplName() {
    String text = "12345678";
    assertEquals(text, Paragraph.getScriptBody(text));
  }

  @Test
  public void replNameAndNoBody() {
    String text = "%md";
    assertEquals("md", Paragraph.getRequiredReplName(text));
    assertEquals("", Paragraph.getScriptBody(text));
  }
  
  @Test
  public void replSingleCharName() {
    String text = "%r a";
    assertEquals("r", Paragraph.getRequiredReplName(text));
    assertEquals("a", Paragraph.getScriptBody(text));
  }

  @Test
  public void replNameEndsWithWhitespace() {
    String text = "%md\r\n###Hello";
    assertEquals("md", Paragraph.getRequiredReplName(text));

    text = "%md\t###Hello";
    assertEquals("md", Paragraph.getRequiredReplName(text));

    text = "%md\u000b###Hello";
    assertEquals("md", Paragraph.getRequiredReplName(text));

    text = "%md\f###Hello";
    assertEquals("md", Paragraph.getRequiredReplName(text));

    text = "%md\n###Hello";
    assertEquals("md", Paragraph.getRequiredReplName(text));

    text = "%md ###Hello";
    assertEquals("md", Paragraph.getRequiredReplName(text));
  }

  @Test
  public void should_extract_variable_from_angular_object_registry() throws Exception {
    //Given
    final String noteId = "noteId";

    final AngularObjectRegistry registry = mock(AngularObjectRegistry.class);
    final Note note = mock(Note.class);
    final Map<String, Input> inputs = new HashMap<>();
    inputs.put("name", null);
    inputs.put("age", null);
    inputs.put("job", null);

    final String scriptBody = "My name is ${name} and I am ${age=20} years old. " +
            "My occupation is ${ job = engineer | developer | artists}";

    final Paragraph paragraph = new Paragraph(note, null, null);
    final String paragraphId = paragraph.getId();

    final AngularObject nameAO = AngularObjectBuilder.build("name", "DuyHai DOAN", noteId,
            paragraphId);

    final AngularObject ageAO = AngularObjectBuilder.build("age", 34, noteId, null);

    when(note.getId()).thenReturn(noteId);
    when(registry.get("name", noteId, paragraphId)).thenReturn(nameAO);
    when(registry.get("age", noteId, null)).thenReturn(ageAO);

    final String expected = "My name is DuyHai DOAN and I am 34 years old. " +
            "My occupation is ${ job = engineer | developer | artists}";
    //When
    final String actual = paragraph.extractVariablesFromAngularRegistry(scriptBody, inputs,
            registry);

    //Then
    verify(registry).get("name", noteId, paragraphId);
    verify(registry).get("age", noteId, null);
    assertEquals(actual, expected);
  }
}
