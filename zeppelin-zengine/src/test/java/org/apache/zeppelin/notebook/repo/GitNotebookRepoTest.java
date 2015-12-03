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

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.interpreter.mock.MockInterpreter1;
import org.apache.zeppelin.interpreter.mock.MockInterpreter2;
import org.apache.zeppelin.notebook.repo.NotebookRepoVersioned.Rev;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Joiner;

public class GitNotebookRepoTest {

  private static final String TEST_NOTE_ID = "2A94M5J1Z";

  private File zeppelinDir;
  private String notebooksDir;
  private ZeppelinConfiguration conf;
  private GitNotebookRepo notebookRepo;

  @Before
  public void setUp() throws Exception {
    String zpath = System.getProperty("java.io.tmpdir")+"/ZeppelinTest_"+System.currentTimeMillis();
    zeppelinDir = new File(zpath);
    zeppelinDir.mkdirs();
    new File(zeppelinDir, "conf").mkdirs();

    notebooksDir = Joiner.on(File.separator).join(zpath, "notebook");
    File notebookDir = new File(notebooksDir);
    notebookDir.mkdirs();

    String testNoteDir = Joiner.on(File.separator).join(notebooksDir, TEST_NOTE_ID);
    FileUtils.copyDirectory(new File(Joiner.on(File.separator).join("src", "test", "resources", TEST_NOTE_ID)),
        new File(testNoteDir)
    );

    System.setProperty(ConfVars.ZEPPELIN_HOME.getVarName(), zeppelinDir.getAbsolutePath());
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(), notebookDir.getAbsolutePath());
    System.setProperty(ConfVars.ZEPPELIN_INTERPRETERS.getVarName(), "org.apache.zeppelin.interpreter.mock.MockInterpreter1,org.apache.zeppelin.interpreter.mock.MockInterpreter2");
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE.getVarName(), "org.apache.zeppelin.notebook.repo.GitNotebookRepo");

    MockInterpreter1.register("mock1", "org.apache.zeppelin.interpreter.mock.MockInterpreter1");
    MockInterpreter2.register("mock2", "org.apache.zeppelin.interpreter.mock.MockInterpreter2");

    conf = ZeppelinConfiguration.create();
  }

  @After
  public void tearDown() throws Exception {
    NotebookRepoSyncTest.delete(zeppelinDir);
  }

  @Test
  public void initNonemptyNotebookDir() throws IOException, GitAPIException {
    //given - .git does not exit
    File dotGit = new File(Joiner.on(File.separator).join(notebooksDir, ".git"));
    assertThat(dotGit.exists()).isEqualTo(false);

    //when
    notebookRepo = new GitNotebookRepo(conf);

    //then
    Git git = notebookRepo.getGit();
    assertThat(git).isNotNull();

    assertThat(dotGit.exists()).isEqualTo(true);
    assertThat(notebookRepo.list()).isNotEmpty();

    List<DiffEntry> diff = git.diff().call();
    assertThat(diff).isEmpty();
  }

  @Test
  public void showNotebookHistory() throws GitAPIException, IOException {
    //given
    notebookRepo = new GitNotebookRepo(conf);
    assertThat(notebookRepo.list()).isNotEmpty();

    //when
    List<Rev> testNotebookHistory = notebookRepo.history(TEST_NOTE_ID);

    //then
    assertThat(testNotebookHistory).isNotEmpty();
  }

}
