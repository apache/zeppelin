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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import java.util.List;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectBuilder;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.Interpreter.FormType;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterResult.Type;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSetting.Status;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.resource.ResourcePool;
import org.apache.zeppelin.scheduler.JobListener;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

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

    final Paragraph paragraph = new Paragraph(note, null, null, null);
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

  @Test
  public void returnDefaultParagraphWithNewUser() {
    Paragraph p = new Paragraph("para_1", null, null, null, null);
    Object defaultValue = "Default Value";
    p.setResult(defaultValue);
    Paragraph newUserParagraph = p.getUserParagraph("new_user");
    assertNotNull(newUserParagraph);
    assertEquals(defaultValue, newUserParagraph.getReturn());
  }

  @Test
  public void returnUnchangedResultsWithDifferentUser() throws Throwable {
    InterpreterSettingManager mockInterpreterSettingManager = mock(InterpreterSettingManager.class);
    Note mockNote = mock(Note.class);
    when(mockNote.getCredentials()).thenReturn(mock(Credentials.class));
    Paragraph spyParagraph = spy(new Paragraph("para_1", mockNote,  null, null, mockInterpreterSettingManager));

    doReturn("spy").when(spyParagraph).getRequiredReplName();


    Interpreter mockInterpreter = mock(Interpreter.class);
    doReturn(mockInterpreter).when(spyParagraph).getRepl(anyString());

    InterpreterGroup mockInterpreterGroup = mock(InterpreterGroup.class);
    when(mockInterpreter.getInterpreterGroup()).thenReturn(mockInterpreterGroup);
    when(mockInterpreterGroup.getId()).thenReturn("mock_id_1");
    when(mockInterpreterGroup.getAngularObjectRegistry()).thenReturn(mock(AngularObjectRegistry.class));
    when(mockInterpreterGroup.getResourcePool()).thenReturn(mock(ResourcePool.class));

    List<InterpreterSetting> spyInterpreterSettingList = spy(Lists.<InterpreterSetting>newArrayList());
    InterpreterSetting mockInterpreterSetting = mock(InterpreterSetting.class);
    InterpreterOption mockInterpreterOption = mock(InterpreterOption.class);
    when(mockInterpreterSetting.getOption()).thenReturn(mockInterpreterOption);
    when(mockInterpreterOption.permissionIsSet()).thenReturn(false);
    when(mockInterpreterSetting.getStatus()).thenReturn(Status.READY);
    when(mockInterpreterSetting.getId()).thenReturn("mock_id_1");
    when(mockInterpreterSetting.getInterpreterGroup(anyString(), anyString())).thenReturn(mockInterpreterGroup);
    spyInterpreterSettingList.add(mockInterpreterSetting);
    when(mockNote.getId()).thenReturn("any_id");
    when(mockInterpreterSettingManager.getInterpreterSettings(anyString())).thenReturn(spyInterpreterSettingList);

    doReturn("spy script body").when(spyParagraph).getScriptBody();

    when(mockInterpreter.getFormType()).thenReturn(FormType.NONE);

    ParagraphJobListener mockJobListener = mock(ParagraphJobListener.class);
    doReturn(mockJobListener).when(spyParagraph).getListener();
    doNothing().when(mockJobListener).onOutputUpdateAll(Mockito.<Paragraph>any(), Mockito.anyList());

    InterpreterResult mockInterpreterResult = mock(InterpreterResult.class);
    when(mockInterpreter.interpret(anyString(), Mockito.<InterpreterContext>any())).thenReturn(mockInterpreterResult);
    when(mockInterpreterResult.code()).thenReturn(Code.SUCCESS);


    // Actual test
    List<InterpreterResultMessage> result1 = Lists.newArrayList();
    result1.add(new InterpreterResultMessage(Type.TEXT, "result1"));
    when(mockInterpreterResult.message()).thenReturn(result1);

    AuthenticationInfo user1 = new AuthenticationInfo("user1");
    spyParagraph.setAuthenticationInfo(user1);
    spyParagraph.jobRun();
    Paragraph p1 = spyParagraph.getUserParagraph(user1.getUser());

    List<InterpreterResultMessage> result2 = Lists.newArrayList();
    result2.add(new InterpreterResultMessage(Type.TEXT, "result2"));
    when(mockInterpreterResult.message()).thenReturn(result2);

    AuthenticationInfo user2 = new AuthenticationInfo("user2");
    spyParagraph.setAuthenticationInfo(user2);
    spyParagraph.jobRun();
    Paragraph p2 = spyParagraph.getUserParagraph(user2.getUser());

    assertNotEquals(p1.getReturn().toString(), p2.getReturn().toString());

    assertEquals(p1, spyParagraph.getUserParagraph(user1.getUser()));



  }
}
