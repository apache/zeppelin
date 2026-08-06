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

package org.apache.zeppelin.interpreter.recovery;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.launcher.InterpreterClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileSystemRecoveryStorageTest {

  @TempDir
  Path recoveryDir;

  @Test
  void persistsRecoveryDataThroughHadoopFileSystem() throws Exception {
    ZeppelinConfiguration zConf = ZeppelinConfiguration.load();
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_RECOVERY_DIR.getVarName(),
        recoveryDir.toString());

    InterpreterSetting setting = mock(InterpreterSetting.class);
    when(setting.getAllInterpreterGroups()).thenReturn(Collections.emptyList());
    when(setting.getJavaProperties()).thenReturn(new Properties());

    InterpreterSettingManager settingManager = mock(InterpreterSettingManager.class);
    when(settingManager.getInterpreterSettingByName("test")).thenReturn(setting);
    when(settingManager.getByName("test")).thenReturn(setting);

    InterpreterClient client = mock(InterpreterClient.class);
    when(client.getInterpreterSettingName()).thenReturn("test");

    FileSystemRecoveryStorage storage = new FileSystemRecoveryStorage(zConf, settingManager);
    storage.onInterpreterClientStart(client);

    Path recoveryFile = recoveryDir.resolve("test.recovery");
    assertTrue(Files.exists(recoveryFile));
    assertEquals("", Files.readString(recoveryFile));
    assertTrue(storage.restore().isEmpty());
  }
}
