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

import java.util.Properties;

import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

//TODO(zjffdu) add more test for Interpreter which is a very important class
public class InterpreterTest {

  @Test
  public void testDefaultProperty() {
    Properties p = new Properties();
    p.put("p1", "v1");
    Interpreter intp = new DummyInterpreter(p);

    assertEquals(1, intp.getProperties().size());
    assertEquals("v1", intp.getProperties().get("p1"));
    assertEquals("v1", intp.getProperty("p1"));
  }

  @Test
  public void testOverriddenProperty() {
    Properties p = new Properties();
    p.put("p1", "v1");
    Interpreter intp = new DummyInterpreter(p);
    Properties overriddenProperty = new Properties();
    overriddenProperty.put("p1", "v2");
    intp.setProperties(overriddenProperty);

    assertEquals(1, intp.getProperties().size());
    assertEquals("v2", intp.getProperties().get("p1"));
    assertEquals("v2", intp.getProperty("p1"));
  }

  @Test
  public void testPropertyWithReplacedContextFields() {
    String noteId = "testNoteId";
    String paragraphTitle = "testParagraphTitle";
    String paragraphText = "testParagraphText";
    String paragraphId = "testParagraphId";
    String user = "username";
    InterpreterContext.set(new InterpreterContext(noteId,
        paragraphId,
        null,
        paragraphTitle,
        paragraphText,
        new AuthenticationInfo("testUser", null, "testTicket"),
        null,
        null,
        null,
        null,
        null,
        null,
        null));
    Properties p = new Properties();
    p.put("p1", "replName #{noteId}, #{paragraphTitle}, #{paragraphId}, #{paragraphText}, #{replName}, #{noteId}, #{user}," +
        " #{authenticationInfo}");
    Interpreter intp = new DummyInterpreter(p);
    intp.setUserName(user);
    String actual = intp.getProperty("p1");
    InterpreterContext.remove();

    assertEquals(
        String.format("replName %s, #{paragraphTitle}, #{paragraphId}, #{paragraphText}, , %s, %s, #{authenticationInfo}", noteId,
            noteId, user),
        actual
    );
  }

  public static class DummyInterpreter extends Interpreter {

    public DummyInterpreter(Properties property) {
      super(property);
    }

    @Override
    public void open() {

    }

    @Override
    public void close() {

    }

    @Override
    public InterpreterResult interpret(String st, InterpreterContext context) {
      return null;
    }

    @Override
    public void cancel(InterpreterContext context) {

    }

    @Override
    public FormType getFormType() {
      return null;
    }

    @Override
    public int getProgress(InterpreterContext context) {
      return 0;
    }
  }

}
