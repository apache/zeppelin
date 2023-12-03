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

package org.apache.zeppelin.integration;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.ExecutionContext;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class K8sMinikubeTestBasic {
    private static MiniZeppelin zeppelin;
    private static InterpreterFactory interpreterFactory;
    private static InterpreterSettingManager interpreterSettingManager;

    @BeforeClass
    public static void setUp() throws IOException {
        ZeppelinConfiguration zconf = ZeppelinConfiguration.create();
        zconf.setRunMode(ZeppelinConfiguration.RUN_MODE.K8S);
        zconf.setProperty("zeppelin.k8s.portforward", "true");
        zconf.setProperty("zeppelin.interpreter.connect.timeout", "600000");

        zeppelin = new MiniZeppelin();
        zeppelin.start(K8sMinikubeTestBasic.class, zconf);
        interpreterFactory = zeppelin.getInterpreterFactory();
        interpreterSettingManager = zeppelin.getInterpreterSettingManager();
    }

    @AfterClass
    public static void tearDown() throws IOException {
        if (zeppelin != null) {
            zeppelin.stop();
        }
    }

    @Test
    public void testK8sStartShellSuccessful() throws InterpreterException {
        // given
        InterpreterSetting interpreterSetting = interpreterSettingManager.getInterpreterSettingByName("sh");
        interpreterSetting.setProperty("zeppelin.k8s.interpreter.container.image", "local/zeppelin");
        interpreterSetting.setProperty("ZEPPELIN_CONF_DIR", "/opt/zeppelin/conf");
        interpreterSetting.setProperty("ZEPPELIN_HOME", "/opt/zeppelin");
        interpreterSetting.setProperty("zeppelin.k8s.interpreter.container.imagePullPolicy", "Never");

        // test shell interpreter
        Interpreter interpreter = interpreterFactory.getInterpreter("sh", new ExecutionContext("user1", "note1", "test"));

        InterpreterContext context = new InterpreterContext.Builder().setNoteId("note1").setParagraphId("paragraph_1").build();
        InterpreterResult interpreterResult = interpreter.interpret("pwd", context);
        assertEquals(interpreterResult.toString(), InterpreterResult.Code.SUCCESS, interpreterResult.code());
    }

    @Test
    public void testK8sStartPythonSuccessful() throws InterpreterException {
        // given
        InterpreterSetting interpreterSetting = interpreterSettingManager.getInterpreterSettingByName("python");
        interpreterSetting.setProperty("zeppelin.k8s.interpreter.container.image", "local/zeppelin");
        interpreterSetting.setProperty("ZEPPELIN_CONF_DIR", "/opt/zeppelin/conf");
        interpreterSetting.setProperty("ZEPPELIN_HOME", "/opt/zeppelin");
        interpreterSetting.setProperty("zeppelin.k8s.interpreter.container.imagePullPolicy", "Never");
        interpreterSetting.setProperty("zeppelin.python", "python3");

        // test shell interpreter
        Interpreter interpreter = interpreterFactory.getInterpreter("python", new ExecutionContext("user1", "note1", "test"));

        InterpreterContext context = new InterpreterContext.Builder().setNoteId("note1").setParagraphId("paragraph_1").build();
        InterpreterResult interpreterResult = interpreter.interpret("foo = True\nprint(foo)", context);
        assertEquals(interpreterResult.toString(), InterpreterResult.Code.SUCCESS, interpreterResult.code());
        assertTrue(interpreterResult.toString(), interpreterResult.message().get(0).getData().contains("True"));
    }
}
