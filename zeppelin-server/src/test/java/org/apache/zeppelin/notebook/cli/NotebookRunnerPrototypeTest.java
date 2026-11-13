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
package org.apache.zeppelin.notebook.cli;

import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.interpreter.ExecutionContext;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.apache.zeppelin.plugin.PluginManager;
import org.apache.zeppelin.storage.ConfigStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Phase 0 gate: proves that a headless bootstrap built the exact way
 * {@code StopInterpreter} assembles {@link InterpreterSettingManager} (no Jetty/HK2 involved)
 * can actually launch an interpreter process and receive its Thrift registration callback,
 * by observing a synchronous {@link InterpreterResult.Code#SUCCESS} from
 * {@link Interpreter#interpret}.
 *
 * <p>If this test times out or hangs, the headless approach itself is broken (port binding or
 * process callback failure) and Phase 1 onward must not proceed.
 */
class NotebookRunnerPrototypeTest {

  private CliTestFixtures.TestDirs dirs;
  private InterpreterSettingManager interpreterSettingManager;

  @BeforeEach
  void setUp() throws Exception {
    dirs = CliTestFixtures.setUp(NotebookRunnerPrototypeTest.class);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (interpreterSettingManager != null) {
      interpreterSettingManager.close();
    }
    CliTestFixtures.tearDown(dirs);
  }

  @Test
  @Timeout(30)
  void interpretReturnsSuccessSynchronouslyThroughRealInterpreterProcess() throws Exception {
    ConfigStorage storage = ConfigStorage.createConfigStorage(dirs.zConf);
    PluginManager pluginManager = new PluginManager(dirs.zConf);
    interpreterSettingManager = new InterpreterSettingManager(dirs.zConf,
        mock(AngularObjectRegistryListener.class),
        mock(RemoteInterpreterProcessListener.class),
        mock(ApplicationEventListener.class),
        storage, pluginManager);
    InterpreterFactory interpreterFactory = new InterpreterFactory(interpreterSettingManager);

    Interpreter interpreter = interpreterFactory.getInterpreter("",
        new ExecutionContext("user1", "noteId", "test"));
    InterpreterContext context = InterpreterContext.builder()
        .setNoteId("noteId")
        .setParagraphId("paragraphId")
        .build();

    InterpreterResult result = interpreter.interpret("echo hello", context);

    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
  }
}
