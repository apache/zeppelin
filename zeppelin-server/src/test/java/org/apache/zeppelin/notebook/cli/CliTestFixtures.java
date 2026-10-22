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

import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * Shared test fixture setup for the headless {@code notebook.cli} package tests. Mirrors
 * {@code AbstractInterpreterTest}'s directory/config bootstrap so each Phase test gets an
 * isolated {@code interpreter}/{@code conf}/{@code notebook} directory triad copied from
 * {@code src/test/resources}.
 */
final class CliTestFixtures {

  private CliTestFixtures() {
  }

  static final class TestDirs {
    final File zeppelinHome;
    final File interpreterDir;
    final File confDir;
    final File notebookDir;
    final ZeppelinConfiguration zConf;

    private TestDirs(File zeppelinHome, File interpreterDir, File confDir, File notebookDir,
        ZeppelinConfiguration zConf) {
      this.zeppelinHome = zeppelinHome;
      this.interpreterDir = interpreterDir;
      this.confDir = confDir;
      this.notebookDir = notebookDir;
      this.zConf = zConf;
    }
  }

  static TestDirs setUp(Class<?> testClass) throws IOException {
    File zeppelinHome = new File("..");
    File interpreterDir = new File(zeppelinHome, "interpreter_" + testClass.getSimpleName());
    File confDir = new File(zeppelinHome, "conf_" + testClass.getSimpleName());
    File notebookDir = new File(zeppelinHome, "notebook_" + testClass.getSimpleName());
    FileUtils.deleteDirectory(notebookDir);

    interpreterDir.mkdirs();
    confDir.mkdirs();
    notebookDir.mkdirs();

    FileUtils.copyDirectory(new File("src/test/resources/interpreter"), interpreterDir);
    FileUtils.copyDirectory(new File("src/test/resources/conf"), confDir);

    ZeppelinConfiguration zConf = ZeppelinConfiguration.load();
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME.getVarName(),
        zeppelinHome.getAbsolutePath());
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_CONF_DIR.getVarName(),
        confDir.getAbsolutePath());
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_DIR.getVarName(),
        interpreterDir.getAbsolutePath());
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(),
        notebookDir.getAbsolutePath());
    zConf.setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_GROUP_DEFAULT.getVarName(), "test");

    return new TestDirs(zeppelinHome, interpreterDir, confDir, notebookDir, zConf);
  }

  static void tearDown(TestDirs dirs) throws IOException {
    if (dirs == null) {
      return;
    }
    FileUtils.deleteDirectory(dirs.interpreterDir);
    FileUtils.deleteDirectory(dirs.confDir);
    FileUtils.deleteDirectory(dirs.notebookDir);
  }
}
