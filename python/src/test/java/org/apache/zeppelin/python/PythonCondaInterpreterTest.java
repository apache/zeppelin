/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*  http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.zeppelin.python;

import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

public class PythonCondaInterpreterTest {
  private PythonCondaInterpreter conda;
  private PythonInterpreter python;

  @Before
  public void setUp() {
    conda = spy(new PythonCondaInterpreter(new Properties()));
    python = mock(PythonInterpreter.class);

    InterpreterGroup group = new InterpreterGroup();
    group.put("note", Arrays.asList(python, conda));
    python.setInterpreterGroup(group);
    conda.setInterpreterGroup(group);

    doReturn(python).when(conda).getPythonInterpreter();
  }

  private void setCondaEnvs() throws IOException, InterruptedException {
    StringBuilder sb = new StringBuilder();
    sb.append("#comment\n\nenv1   *  /path1\nenv2\t/path2\n");

    doReturn(sb).when(conda).createStringBuilder();
    doReturn(0).when(conda)
      .runCommand(any(StringBuilder.class), anyString(), anyString(), anyString());
  }

  @Test
  public void testListEnv() throws IOException, InterruptedException {
    setCondaEnvs();

    // list available env
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = conda.interpret("", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    context.out.flush();
    String out = new String(context.out.toByteArray());
    assertTrue(out.contains(">env1<"));
    assertTrue(out.contains(">/path1<"));
    assertTrue(out.contains(">env2<"));
    assertTrue(out.contains(">/path2<"));
  }

  @Test
  public void testActivateEnv() throws IOException, InterruptedException {
    setCondaEnvs();
    InterpreterContext context = getInterpreterContext();
    conda.interpret("activate env1", context);
    verify(python, times(1)).open();
    verify(python, times(1)).close();
    verify(python).setPythonCommand("/path1/bin/python");
  }

  @Test
  public void testDeactivate() {
    InterpreterContext context = getInterpreterContext();
    conda.interpret("deactivate", context);
    verify(python, times(1)).open();
    verify(python, times(1)).close();
    verify(python).setPythonCommand("python");
  }

  private InterpreterContext getInterpreterContext() {
    return new InterpreterContext(
        "noteId",
        "paragraphId",
        null,
        "paragraphTitle",
        "paragraphText",
        new AuthenticationInfo(),
        new HashMap<String, Object>(),
        new GUI(),
        null,
        null,
        null,
        new InterpreterOutput(null));
  }
}
