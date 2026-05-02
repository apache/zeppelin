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

package org.apache.zeppelin.storage;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConfigStorageTest {

  @TempDir
  Path tempDir;

  @Test
  void createConfigStorageLoadsPluginWhenClassIsNotOnMainClasspath() throws Exception {
    String className = "org.apache.zeppelin.storage.plugin.TestPluginConfigStorage";
    Path pluginsDir = tempDir.resolve("plugins");
    Path pluginDir = pluginsDir.resolve("ConfigStorage").resolve("TestPluginConfigStorage");
    Path pluginClassesDir = pluginDir.resolve("classes");
    Files.createDirectories(pluginClassesDir);

    Path sourceFile = tempDir.resolve("source")
        .resolve("org").resolve("apache").resolve("zeppelin")
        .resolve("storage").resolve("plugin").resolve("TestPluginConfigStorage.java");
    Files.createDirectories(sourceFile.getParent());
    Files.writeString(sourceFile, String.join(System.lineSeparator(),
        "package org.apache.zeppelin.storage.plugin;",
        "public class TestPluginConfigStorage extends org.apache.zeppelin.storage.ConfigStorage {",
        "  public TestPluginConfigStorage(org.apache.zeppelin.conf.ZeppelinConfiguration zConf) {",
        "    super(zConf);",
        "  }",
        "  public void save(org.apache.zeppelin.interpreter.InterpreterInfoSaving settingInfos) { }",
        "  public org.apache.zeppelin.interpreter.InterpreterInfoSaving loadInterpreterSettings() { return null; }",
        "  public void save(org.apache.zeppelin.notebook.NotebookAuthorizationInfoSaving authorizationInfoSaving) { }",
        "  public org.apache.zeppelin.notebook.NotebookAuthorizationInfoSaving loadNotebookAuthorization() {",
        "    return null;",
        "  }",
        "  public String loadCredentials() { return null; }",
        "  public void saveCredentials(String credentials) { }",
        "}"));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler);
    int compileResult = compiler.run(null, null, null,
        "-classpath", System.getProperty("java.class.path"),
        "-d", pluginClassesDir.toString(),
        sourceFile.toString());
    assertEquals(0, compileResult);

    ZeppelinConfiguration zConf = ZeppelinConfiguration.load();
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_PLUGINS_DIR.getVarName(),
        pluginsDir.toString());
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_CONFIG_STORAGE_CLASS.getVarName(),
        className);

    ConfigStorage configStorage = ConfigStorage.createConfigStorage(zConf);
    assertEquals(className, configStorage.getClass().getName());
  }
}
