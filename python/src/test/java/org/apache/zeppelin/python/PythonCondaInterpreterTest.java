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

package org.apache.zeppelin.python;


import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class PythonCondaInterpreterTest {
  private PythonCondaInterpreter conda;
  private PythonInterpreter python;

  @Before
  public void setUp() throws InterpreterException {
    conda = spy(new PythonCondaInterpreter(new Properties()));
    python = mock(PythonInterpreter.class);

    InterpreterGroup group = new InterpreterGroup();
    group.put("note", Arrays.asList(python, conda));
    python.setInterpreterGroup(group);
    conda.setInterpreterGroup(group);

    doReturn(python).when(conda).getPythonInterpreter();
  }

  private void setMockCondaEnvList() throws IOException, InterruptedException {
    Map<String, String> envList = new LinkedHashMap<String, String>();
    envList.put("env1", "/path1");
    envList.put("env2", "/path2");
    doReturn(envList).when(conda).getCondaEnvs();
  }

  @Test
  public void testListEnv() throws IOException, InterruptedException, InterpreterException {
    setMockCondaEnvList();

    // list available env
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = conda.interpret("env list", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    assertTrue(result.toString().contains(">env1<"));
    assertTrue(result.toString().contains("/path1<"));
    assertTrue(result.toString().contains(">env2<"));
    assertTrue(result.toString().contains("/path2<"));
  }

  @Test
  public void testActivateEnv() throws IOException, InterruptedException, InterpreterException {
    setMockCondaEnvList();
    String envname = "env1";
    InterpreterContext context = getInterpreterContext();
    conda.interpret("activate " + envname, context);
    verify(python, times(1)).open();
    verify(python, times(1)).close();
    verify(python).setPythonCommand("/path1/bin/python");
    assertTrue(envname.equals(conda.getCurrentCondaEnvName()));
  }

  @Test
  public void testDeactivate() throws InterpreterException {
    InterpreterContext context = getInterpreterContext();
    conda.interpret("deactivate", context);
    verify(python, times(1)).open();
    verify(python, times(1)).close();
    verify(python).setPythonCommand("python");
    assertTrue(conda.getCurrentCondaEnvName().isEmpty());
  }

  @Test
  public void testParseCondaCommonStdout()
      throws IOException, InterruptedException {

    StringBuilder sb = new StringBuilder()
        .append("# comment1\n")
        .append("# comment2\n")
        .append("env1     /location1\n")
        .append("env2     /location2\n");

    Map<String, String> locationPerEnv =
        PythonCondaInterpreter.parseCondaCommonStdout(sb.toString());

    assertEquals("/location1", locationPerEnv.get("env1"));
    assertEquals("/location2", locationPerEnv.get("env2"));
  }

  @Test
  public void testGetRestArgsFromMatcher() {
    Matcher m =
        PythonCondaInterpreter.PATTERN_COMMAND_ENV.matcher("env remove --name test --yes");
    m.matches();

    List<String> restArgs = PythonCondaInterpreter.getRestArgsFromMatcher(m);
    List<String> expected = Arrays.asList(new String[]{"remove", "--name", "test", "--yes"});
    assertEquals(expected, restArgs);
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
        new GUI(),
        null,
        null,
        null,
        new InterpreterOutput(null));
  }


}
