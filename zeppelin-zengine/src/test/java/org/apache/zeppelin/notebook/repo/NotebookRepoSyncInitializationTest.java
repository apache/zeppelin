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

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.repo.mock.VFSNotebookRepoMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

//TODO(zjffdu) move it to zeppelin-zengine
public class NotebookRepoSyncInitializationTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(NotebookRepoSyncInitializationTest.class);
  private String validFirstStorageClass = "org.apache.zeppelin.notebook.repo.VFSNotebookRepo";
  private String validSecondStorageClass = "org.apache.zeppelin.notebook.repo.mock.VFSNotebookRepoMock";
  private String invalidStorageClass = "org.apache.zeppelin.notebook.repo.DummyNotebookRepo";
  private String validOneStorageConf = validFirstStorageClass;
  private String validTwoStorageConf = validFirstStorageClass + "," + validSecondStorageClass;
  private String invalidTwoStorageConf = validFirstStorageClass + "," + invalidStorageClass;
  private String unsupportedStorageConf = validFirstStorageClass + "," + validSecondStorageClass + "," + validSecondStorageClass;
  private String emptyStorageConf = "";

  @Before
  public void setUp(){
    System.setProperty(ConfVars.ZEPPELIN_PLUGINS_DIR.getVarName(), new File("../../../plugins").getAbsolutePath());
    System.setProperty("zeppelin.isTest", "true");
  }

  @After
  public void tearDown() {
    System.clearProperty("zeppelin.isTest");
  }

  @Test
  public void validInitOneStorageTest() throws IOException {
    // no need to initialize folder due to one storage
    // set confs
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE.getVarName(), validOneStorageConf);
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    // create repo
    NotebookRepoSync notebookRepoSync = new NotebookRepoSync(conf);
    // check proper initialization of one storage
    assertEquals(notebookRepoSync.getRepoCount(), 1);
    assertTrue(notebookRepoSync.getRepo(0) instanceof VFSNotebookRepo);
  }

  @Test
  public void validInitTwoStorageTest() throws IOException {
    // initialize folders for each storage
    String zpath = System.getProperty("java.io.tmpdir") + "/ZeppelinLTest_" + System.currentTimeMillis();
    File mainZepDir = new File(zpath);
    mainZepDir.mkdirs();
    new File(mainZepDir, "conf").mkdirs();
    String mainNotePath = zpath+"/notebook";
    String secNotePath = mainNotePath + "_secondary";
    File mainNotebookDir = new File(mainNotePath);
    File secNotebookDir = new File(secNotePath);
    mainNotebookDir.mkdirs();
    secNotebookDir.mkdirs();

    // set confs
    System.setProperty(ConfVars.ZEPPELIN_HOME.getVarName(), mainZepDir.getAbsolutePath());
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(), mainNotebookDir.getAbsolutePath());
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE.getVarName(), validTwoStorageConf);
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    // create repo
    NotebookRepoSync notebookRepoSync = new NotebookRepoSync(conf);
    // check that both initialized
    assertEquals(notebookRepoSync.getRepoCount(), 2);
    assertTrue(notebookRepoSync.getRepo(0) instanceof VFSNotebookRepo);
    assertTrue(notebookRepoSync.getRepo(1) instanceof VFSNotebookRepoMock);
  }

  @Test
  public void invalidInitTwoStorageTest() throws IOException {
    // set confs
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE.getVarName(), invalidTwoStorageConf);
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    // create repo
    try {
      NotebookRepoSync notebookRepoSync = new NotebookRepoSync(conf);
      fail("Should throw exception due to invalid NotebookRepo");
    } catch (IOException e) {
      LOGGER.error(e.getMessage());
      assertTrue(e.getMessage().contains("Fail to instantiate notebookrepo from classpath directly"));
    }
  }

  @Test
  public void initUnsupportedNumberStoragesTest() throws IOException {
    // initialize folders for each storage, currently for 2 only
    String zpath = System.getProperty("java.io.tmpdir") + "/ZeppelinLTest_" + System.currentTimeMillis();
    File mainZepDir = new File(zpath);
    mainZepDir.mkdirs();
    new File(mainZepDir, "conf").mkdirs();
    String mainNotePath = zpath+"/notebook";
    String secNotePath = mainNotePath + "_secondary";
    File mainNotebookDir = new File(mainNotePath);
    File secNotebookDir = new File(secNotePath);
    mainNotebookDir.mkdirs();
    secNotebookDir.mkdirs();

    // set confs
    System.setProperty(ConfVars.ZEPPELIN_HOME.getVarName(), mainZepDir.getAbsolutePath());
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(), mainNotebookDir.getAbsolutePath());
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE.getVarName(), unsupportedStorageConf);
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    // create repo
    NotebookRepoSync notebookRepoSync = new NotebookRepoSync(conf);
    // check that first two storages initialized instead of three
    assertEquals(notebookRepoSync.getRepoCount(), 2);
    assertTrue(notebookRepoSync.getRepo(0) instanceof VFSNotebookRepo);
    assertTrue(notebookRepoSync.getRepo(1) instanceof VFSNotebookRepoMock);
  }

  @Test
  public void initEmptyStorageTest() throws IOException {
    // set confs
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE.getVarName(), emptyStorageConf);
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    // create repo
    NotebookRepoSync notebookRepoSync = new NotebookRepoSync(conf);
    // check initialization of one default storage
    assertEquals(notebookRepoSync.getRepoCount(), 1);
    assertTrue(notebookRepoSync.getRepo(0) instanceof NotebookRepoWithVersionControl);
  }

  @Test
  public void initOneDummyStorageTest() {
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE.getVarName(), invalidStorageClass);
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    // create repo
    try {
      NotebookRepoSync notebookRepoSync = new NotebookRepoSync(conf);
      fail("Should throw exception due to invalid NotebookRepo");
    } catch (IOException e) {
      LOGGER.error(e.getMessage());
      assertTrue(e.getMessage().contains("Fail to instantiate notebookrepo from classpath directly"));
    }
  }
}