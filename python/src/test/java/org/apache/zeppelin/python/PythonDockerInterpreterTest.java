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

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.util.Arrays;
import java.util.Properties;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class PythonDockerInterpreterTest {
  private PythonDockerInterpreter docker;
  private PythonInterpreter python;

  @Before
  public void setUp() throws InterpreterException {
    docker = spy(new PythonDockerInterpreter(new Properties()));
    python = mock(PythonInterpreter.class);

    InterpreterGroup group = new InterpreterGroup();
    group.put("note", Arrays.asList(python, docker));
    python.setInterpreterGroup(group);
    docker.setInterpreterGroup(group);

    doReturn(true).when(docker).pull(any(InterpreterOutput.class), anyString());
    doReturn(new File("/scriptpath")).when(python).getPythonWorkDir();
    doReturn(PythonDockerInterpreter.class.getName()).when(docker).getClassName();
    doReturn(PythonInterpreter.class.getName()).when(python).getClassName();
    docker.open();
  }

  @Test
  public void testActivateEnv() throws InterpreterException {
    InterpreterContext context = getInterpreterContext();
    docker.interpret("activate env", context);
    verify(python, times(1)).open();
    verify(python, times(1)).close();
    verify(docker, times(1)).pull(any(InterpreterOutput.class), anyString());
    verify(python).setPythonExec(Mockito.matches("docker run -i --rm -v.*"));
  }

  @Test
  public void testDeactivate() throws InterpreterException {
    InterpreterContext context = getInterpreterContext();
    docker.interpret("deactivate", context);
    verify(python, times(1)).open();
    verify(python, times(1)).close();
    verify(python).setPythonExec(null);
  }

  private InterpreterContext getInterpreterContext() {
    return InterpreterContext.builder()
        .setInterpreterOut(new InterpreterOutput())
        .build();
  }
}
