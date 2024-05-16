package org.apache.zeppelin.interpreter.install;

import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
class InstallInterpreterTest {
  private File tmpDir;
  private InstallInterpreter installer;
  private File interpreterBaseDir;
  private ZeppelinConfiguration zConf;

  @BeforeEach
  public void setUp() throws IOException {
    tmpDir = Files.createTempDirectory("InstallInterpreterTest").toFile();
    zConf = ZeppelinConfiguration.load();
    interpreterBaseDir = new File(tmpDir, "interpreter");
    File localRepoDir = new File(tmpDir, "local-repo");
    interpreterBaseDir.mkdir();
    localRepoDir.mkdir();

    File interpreterListFile = new File(tmpDir, "conf/interpreter-list");


    // create interpreter list file
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME.getVarName(), tmpDir.getAbsolutePath());

    String interpreterList = "";
    interpreterList += "intp1   org.apache.commons:commons-csv:1.1   test interpreter 1\n";
    interpreterList += "intp2   org.apache.commons:commons-math3:3.6.1 test interpreter 2\n";

    FileUtils.writeStringToFile(new File(tmpDir, "conf/interpreter-list"), interpreterList, StandardCharsets.UTF_8);

    installer = new InstallInterpreter(interpreterListFile, interpreterBaseDir,
        localRepoDir.getAbsolutePath(), zConf);
  }

  @AfterEach
  public void tearDown() throws IOException {
    FileUtils.deleteDirectory(tmpDir);
  }


  @Test
  void testList() {
    assertEquals(2, installer.list().size());
  }

  @Test
  void install() {
    assertEquals(0, interpreterBaseDir.listFiles().length);

    installer.install("intp1");
    assertTrue(new File(interpreterBaseDir, "intp1").isDirectory());
  }

  @Test
  void installAll() {
    installer.installAll();
    assertTrue(new File(interpreterBaseDir, "intp1").isDirectory());
    assertTrue(new File(interpreterBaseDir, "intp2").isDirectory());
  }
}
