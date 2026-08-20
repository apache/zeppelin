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

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionConfInterpreterTest {

  @Test
  void testUserSessionConfInterpreter() throws InterpreterException {

    InterpreterSetting mockInterpreterSetting = mock(InterpreterSetting.class);
    ManagedInterpreterGroup mockInterpreterGroup = mock(ManagedInterpreterGroup.class);
    when(mockInterpreterSetting.getInterpreterGroup("group_1")).thenReturn(mockInterpreterGroup);
    when(mockInterpreterSetting.getOption()).thenReturn(new InterpreterOption());

    Properties properties = new Properties();
    properties.setProperty("property_1", "value_1");
    properties.setProperty("property_2", "value_2");
    SessionConfInterpreter confInterpreter = new SessionConfInterpreter(
        properties, "session_1", "group_1", mockInterpreterSetting);

    RemoteInterpreter remoteInterpreter =
        new RemoteInterpreter(properties, "session_1", "clasName", "user1",
            ZeppelinConfiguration.load());
    List<Interpreter> interpreters = new ArrayList<>();
    interpreters.add(confInterpreter);
    interpreters.add(remoteInterpreter);
    when(mockInterpreterGroup.get("session_1")).thenReturn(interpreters);

    InterpreterResult result =
        confInterpreter.interpret(
            "property_1\tupdated_value_1\nproperty_3\tvalue_3\nENV_1\tVALUE_1",
            mock(InterpreterContext.class));
    assertEquals(InterpreterResult.Code.SUCCESS, result.code);
    assertEquals(4, remoteInterpreter.getProperties().size());
    assertEquals("updated_value_1", remoteInterpreter.getProperty("property_1"));
    assertEquals("value_2", remoteInterpreter.getProperty("property_2"));
    assertEquals("value_3", remoteInterpreter.getProperty("property_3"));
    assertEquals("VALUE_1", remoteInterpreter.getProperty("ENV_1"));

    remoteInterpreter.setOpened(true);
    result =
        confInterpreter.interpret(
            "property_1\tupdated_value_1\nproperty_3\tvalue_3\nENV_1\tVALUE_1",
            mock(InterpreterContext.class));
    assertEquals(InterpreterResult.Code.ERROR, result.code);
  }

  @Test
  void testRejectEnvironmentVariableOverrideWithImpersonation() throws InterpreterException {
    InterpreterSetting mockInterpreterSetting = mock(InterpreterSetting.class);
    ManagedInterpreterGroup mockInterpreterGroup = mock(ManagedInterpreterGroup.class);
    InterpreterOption option = new InterpreterOption();
    option.setUserImpersonate(true);
    when(mockInterpreterSetting.getOption()).thenReturn(option);
    when(mockInterpreterSetting.getInterpreterGroup("group_1")).thenReturn(mockInterpreterGroup);

    Properties properties = new Properties();
    properties.setProperty("property_1", "value_1");
    properties.setProperty("JAVA_HOME", "/operator/java");
    SessionConfInterpreter confInterpreter = new SessionConfInterpreter(
        properties, "session_1", "group_1", mockInterpreterSetting);
    RemoteInterpreter remoteInterpreter =
        new RemoteInterpreter(properties, "session_1", "className", "user1",
            ZeppelinConfiguration.load());
    List<Interpreter> interpreters = new ArrayList<>();
    interpreters.add(confInterpreter);
    interpreters.add(remoteInterpreter);
    when(mockInterpreterGroup.get("session_1")).thenReturn(interpreters);

    InterpreterResult result = confInterpreter.interpret(
        "property_1\tupdated_value\nBASH_ENV\t/tmp/user-controlled.sh",
        mock(InterpreterContext.class));

    assertEquals(InterpreterResult.Code.ERROR, result.code);
    assertTrue(result.toString().contains("BASH_ENV"), result.toString());
    assertEquals("value_1", remoteInterpreter.getProperty("property_1"));
    assertEquals("/operator/java", remoteInterpreter.getProperty("JAVA_HOME"));
    assertNull(remoteInterpreter.getProperty("BASH_ENV"));

    result = confInterpreter.interpret(
        "property_1\tupdated_value", mock(InterpreterContext.class));
    assertEquals(InterpreterResult.Code.SUCCESS, result.code);
    assertEquals("updated_value", remoteInterpreter.getProperty("property_1"));
    assertEquals("/operator/java", remoteInterpreter.getProperty("JAVA_HOME"));
  }
}
