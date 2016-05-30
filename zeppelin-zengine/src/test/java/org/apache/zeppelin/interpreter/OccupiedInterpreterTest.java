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

package org.apache.zeppelin.interpreter;

import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class OccupiedInterpreterTest {
  @Test
  public void getOccupiedInterpreter() throws Exception {
    String noteId = "test1";
    assertTrue(OccupiedInterpreter.getDefaultInterpreterName()
            .equals(OccupiedInterpreter.getOccupiedInterpreter(noteId)));
  }

  @Test
  public void setOccupiedInterpreter() throws Exception {
    String noteId = "test2";
    assertTrue(OccupiedInterpreter.getDefaultInterpreterName()
            .equals(OccupiedInterpreter.getOccupiedInterpreter(noteId)));

    String interpreterName = "interpreterName";
    OccupiedInterpreter.setOccupiedInterpreter(noteId, interpreterName);

    assertTrue(interpreterName.equals(OccupiedInterpreter.getOccupiedInterpreter(noteId)));
    assertFalse(OccupiedInterpreter.getDefaultInterpreterName()
            .equals(OccupiedInterpreter.getOccupiedInterpreter(noteId)));
  }

  @Test
  public void setOccupiedInterpreterFromParagraph() throws Exception {
    Note note = Mockito.mock(Note.class);

    String noteId = "test3";
    String interpreter = "%sh";
    Paragraph p = new Paragraph(null, null, null);
    p.setText(interpreter + System.lineSeparator() + "echo hello");

    p = Mockito.spy(p);
    Mockito.when(p.getNote()).thenReturn(note);
    Mockito.when(note.getId()).thenReturn(noteId);
    OccupiedInterpreter.setOccupiedInterpreter(p);

    assertTrue(interpreter.equals(OccupiedInterpreter.getOccupiedInterpreter(noteId)));

    noteId = "test4";
    Mockito.when(note.getId()).thenReturn(noteId);
    p.setText("This is test." + System.lineSeparator() + interpreter);
    OccupiedInterpreter.setOccupiedInterpreter(p);

    System.out.println(OccupiedInterpreter.getOccupiedInterpreter(noteId));
    assertTrue(OccupiedInterpreter.getDefaultInterpreterName()
            .equals(OccupiedInterpreter.getOccupiedInterpreter(noteId)));
  }

  @Test
  public void parseInterpreterName() throws Exception {
    String interpreter = "%sh";

    String text = interpreter + System.lineSeparator() + "echo hello" + interpreter + "hello";
    System.out.println(OccupiedInterpreter.parseInterpreterName(text));
    assertTrue(interpreter.equals(OccupiedInterpreter.parseInterpreterName(text)));

    text = "This is test." + System.lineSeparator() + interpreter;
    System.out.println(OccupiedInterpreter.parseInterpreterName(text));
    assertNull(OccupiedInterpreter.parseInterpreterName(text));
  }

  @Test
  public void parseInterpreterName2() throws Exception {
    String interpreter = "%md";

    String text = "%md hello";
    System.out.println(OccupiedInterpreter.parseInterpreterName(text));

    assertTrue(interpreter.equals(OccupiedInterpreter.parseInterpreterName(text)));
  }

  @Test
  public void parseInterpreterName3() throws Exception {
    Note note = Mockito.mock(Note.class);
    Mockito.when(note.getId()).thenReturn("test6");

    Paragraph p1 = new Paragraph(null, null, null);
    p1 = Mockito.spy(p1);
    Mockito.when(p1.getNote()).thenReturn(note);
    Mockito.when(p1.getText()).thenReturn("%md" + System.lineSeparator() + "hello");
    OccupiedInterpreter.setOccupiedInterpreter(p1);

    Mockito.when(p1.getText()).thenReturn("println(\"Hello\")");
    OccupiedInterpreter.setOccupiedInterpreter(p1);

    String expectedInterpreter = OccupiedInterpreter.getDefaultInterpreterName();
    System.out.println(OccupiedInterpreter.getOccupiedInterpreter("test6"));
    assertTrue(expectedInterpreter.equals(OccupiedInterpreter.getOccupiedInterpreter("test6")));
  }

  @Test
  public void setInterpreterNameIfEmptyText() throws Exception {
    String noteId = "test5";

    Paragraph p = new Paragraph(null, null, null);
    p = Mockito.spy(p);
    Note note = Mockito.mock(Note.class);

    Mockito.when(p.getNote()).thenReturn(note);
    Mockito.when(note.getId()).thenReturn(noteId);

    OccupiedInterpreter.setInterpreterNameIfEmptyText(p);

    System.out.println(OccupiedInterpreter.getDefaultInterpreterName());
    System.out.println(p.getText());
    assertTrue(OccupiedInterpreter.getDefaultInterpreterName().equals(p.getText()));
  }
}