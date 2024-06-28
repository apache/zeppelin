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

package org.apache.zeppelin.notebook.repo;

import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.GsonNoteParser;
import org.apache.zeppelin.notebook.NoteParser;
import org.apache.zeppelin.notebook.repo.mock.VFSNotebookRepoMock;
import org.apache.zeppelin.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotebookRepoSyncInitializationTest {

  private String validFirstStorageClass = "org.apache.zeppelin.notebook.repo.VFSNotebookRepo";
  private String validSecondStorageClass = "org.apache.zeppelin.notebook.repo.mock.VFSNotebookRepoMock";
  private String invalidStorageClass = "org.apache.zeppelin.notebook.repo.DummyNotebookRepo";
  private String validOneStorageConf = validFirstStorageClass;
  private String validTwoStorageConf = validFirstStorageClass + "," + validSecondStorageClass;
  private String invalidTwoStorageConf = validFirstStorageClass + "," + invalidStorageClass;
  private String unsupportedStorageConf = validFirstStorageClass + "," + validSecondStorageClass + "," + validSecondStorageClass;
  private String emptyStorageConf = "";

  private ZeppelinConfiguration zConf;
  private NoteParser noteParser;
  private PluginManager pluginManager;

  @BeforeEach
  public void setUp(){
    zConf = ZeppelinConfiguration.load();
    noteParser = new GsonNoteParser(zConf);
    System.setProperty("zeppelin.isTest", "true");
    zConf.setProperty(ConfVars.ZEPPELIN_PLUGINS_DIR.getVarName(),
        new File("../../../plugins").getAbsolutePath());
    pluginManager = new PluginManager(zConf);
  }

  @Test
  void validInitOneStorageTest() throws IOException {
    // no need to initialize folder due to one storage
    // set confs
    zConf.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE.getVarName(), validOneStorageConf);
    // create repo
    try (NotebookRepoSync notebookRepoSync = new NotebookRepoSync(pluginManager)) {
      notebookRepoSync.init(zConf, noteParser);

      // check proper initialization of one storage
      assertEquals(1, notebookRepoSync.getRepoCount());
      assertTrue(notebookRepoSync.getRepo(0) instanceof VFSNotebookRepo);
    }
  }

  @Test
  void validInitTwoStorageTest() throws IOException {

    File mainZepDir = Files.createTempDirectory(this.getClass().getSimpleName()).toFile();
    File conf = new File(mainZepDir, "conf");
    conf.mkdirs();
    File mainNotebookDir = new File(mainZepDir, "notebook");
    File secNotebookDir = new File(mainZepDir, "notebook_secondary");
    mainNotebookDir.mkdirs();
    secNotebookDir.mkdirs();

    // set confs
    zConf.setProperty(ConfVars.ZEPPELIN_HOME.getVarName(), mainZepDir.getAbsolutePath());
    zConf.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(),
        mainNotebookDir.getAbsolutePath());
    zConf.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE.getVarName(), validTwoStorageConf);
    // create repo
    try (NotebookRepoSync notebookRepoSync = new NotebookRepoSync(pluginManager)) {
      notebookRepoSync.init(zConf, noteParser);
      // check that both initialized
      assertEquals(2, notebookRepoSync.getRepoCount());
      assertTrue(notebookRepoSync.getRepo(0) instanceof VFSNotebookRepo);
      assertTrue(notebookRepoSync.getRepo(1) instanceof VFSNotebookRepoMock);
    }
    FileUtils.deleteDirectory(mainZepDir);

  }

  @Test
  void invalidInitTwoStorageTest() throws IOException {
    // set confs
    zConf.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE.getVarName(), invalidTwoStorageConf);
    // create repo
    IOException exception = assertThrows(IOException.class, () -> {
      try (NotebookRepoSync notebookRepoSync = new NotebookRepoSync(pluginManager)) {
        notebookRepoSync.init(zConf, noteParser);
      }
    });
    assertTrue(exception.getMessage()
        .contains("Fail to instantiate notebookrepo from classpath directly"));
  }

  @Test
  void initUnsupportedNumberStoragesTest() throws IOException {
    // initialize folders for each storage, currently for 2 only
    File mainZepDir = Files.createTempDirectory(this.getClass().getSimpleName()).toFile();
    mainZepDir.mkdirs();
    new File(mainZepDir, "conf").mkdirs();
    File mainNotebookDir = new File(mainZepDir, "notebook");
    File secNotebookDir = new File(mainZepDir, "notebook_secondary");
    mainNotebookDir.mkdirs();
    secNotebookDir.mkdirs();

    // set confs
    zConf.setProperty(ConfVars.ZEPPELIN_HOME.getVarName(), mainZepDir.getAbsolutePath());
    zConf.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(),
        mainNotebookDir.getAbsolutePath());
    zConf.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE.getVarName(), unsupportedStorageConf);
    // create repo
    try (NotebookRepoSync notebookRepoSync = new NotebookRepoSync(pluginManager)) {
      notebookRepoSync.init(zConf, noteParser);
      // check that first two storages initialized instead of three
      assertEquals(2, notebookRepoSync.getRepoCount());
      assertTrue(notebookRepoSync.getRepo(0) instanceof VFSNotebookRepo);
      assertTrue(notebookRepoSync.getRepo(1) instanceof VFSNotebookRepoMock);
    }
    FileUtils.deleteDirectory(mainZepDir);
  }

  @Test
  void initEmptyStorageTest() throws IOException {
    // set confs
    zConf.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE.getVarName(), emptyStorageConf);
    // create repo
    try (NotebookRepoSync notebookRepoSync = new NotebookRepoSync(pluginManager)) {
      notebookRepoSync.init(zConf, noteParser);
      // check initialization of one default storage
      assertEquals(1, notebookRepoSync.getRepoCount());
      assertTrue(notebookRepoSync.getRepo(0) instanceof NotebookRepoWithVersionControl);
    }
  }

  @Test
  void initOneDummyStorageTest() {
    zConf.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE.getVarName(), invalidStorageClass);
    // create repo
    IOException e = assertThrows(IOException.class, () -> {
      try (NotebookRepoSync notebookRepoSync = new NotebookRepoSync(pluginManager)) {
        notebookRepoSync.init(zConf, noteParser);
      }
    });
    assertTrue(e.getMessage().contains("Fail to instantiate notebookrepo from classpath directly"));
  }
}