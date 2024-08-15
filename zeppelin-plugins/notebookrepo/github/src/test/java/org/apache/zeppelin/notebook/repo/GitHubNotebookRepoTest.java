
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
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.notebook.GsonNoteParser;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteParser;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * This tests the remote Git tracking for notebooks. The tests uses two local Git repositories created locally
 * to handle the tracking of Git actions (pushes and pulls). The repositories are:
 * 1. The first repository is considered as a remote that mimics a remote GitHub directory
 * 2. The second repository is considered as the local notebook repository
 */
class GitHubNotebookRepoTest {
  private static final String TEST_NOTE_ID = "2A94M5J1Z";
  private static final String TEST_NOTE_PATH = "/my_project/my_note1";

  private File remoteZeppelinDir;
  private File localZeppelinDir;
  private File localNotebooksDir;
  private File remoteNotebooksDir;
  private ZeppelinConfiguration zConf;
  private GitHubNotebookRepo gitHubNotebookRepo;
  private RevCommit firstCommitRevision;
  private Git remoteGit;
  private NoteParser noteParser;

  @BeforeEach
  void setUp() throws Exception {
    zConf = ZeppelinConfiguration.load();
    noteParser = new GsonNoteParser(zConf);

    // Create a fake remote notebook Git repository locally in another directory
    remoteZeppelinDir =
        Files.createTempDirectory(this.getClass().getSimpleName() + "remote").toFile();
    // Create a local repository for notebooks
    localZeppelinDir =
        Files.createTempDirectory(this.getClass().getSimpleName() + "local").toFile();

    // Notebooks directory (for both the remote and local directories)
    localNotebooksDir = new File(localZeppelinDir, "notebook");
    remoteNotebooksDir = new File(remoteZeppelinDir, "notebook");

    FileUtils.copyDirectory(
        new File(GitHubNotebookRepoTest.class.getResource("/notebook").getFile()),
        remoteNotebooksDir);

    // Create the fake remote Git repository
    Repository remoteRepository = new FileRepository(new File(remoteNotebooksDir, ".git"));
    remoteRepository.create();

    remoteGit = new Git(remoteRepository);
    remoteGit.add().addFilepattern(".").call();
    firstCommitRevision = remoteGit.commit().setMessage("First commit from remote repository").call();

    // Set the Git and Git configurations
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME.getVarName(),
        remoteZeppelinDir.getAbsolutePath());
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(),
        localNotebooksDir.getAbsolutePath());

    // Set the GitHub configurations
    zConf.setProperty(
            ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_STORAGE.getVarName(),
            "org.apache.zeppelin.notebook.repo.GitHubNotebookRepo");
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_GIT_REMOTE_URL.getVarName(),
            remoteNotebooksDir + File.separator + ".git");
    zConf.setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_GIT_REMOTE_USERNAME.getVarName(), "token");
    zConf.setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_GIT_REMOTE_ACCESS_TOKEN.getVarName(),
            "access-token");

    // Create the Notebook repository (configured for the local repository)
    gitHubNotebookRepo = new GitHubNotebookRepo();
    gitHubNotebookRepo.init(zConf, noteParser);
  }

  @AfterEach
  void tearDown() throws Exception {
    // Cleanup the temporary folders uses as Git repositories
    FileUtils.deleteDirectory(localZeppelinDir);
    FileUtils.deleteDirectory(remoteZeppelinDir);
  }

  @Test
  /**
   * Test the case when the Notebook repository is created, it pulls the latest changes from the remote repository
   */
  void pullChangesFromRemoteRepositoryOnLoadingNotebook() throws IOException, GitAPIException {
    NotebookRepoWithVersionControl.Revision firstHistoryRevision = gitHubNotebookRepo.revisionHistory(TEST_NOTE_ID, TEST_NOTE_PATH, null).get(0);

    assertEquals(this.firstCommitRevision.getName(), firstHistoryRevision.id);
  }

  @Test
  /**
   * Test the case when the check-pointing (add new files and commit) it also pulls the latest changes from the
   * remote repository
   */
  void pullChangesFromRemoteRepositoryOnCheckpointing() throws GitAPIException, IOException {
    // Create a new commit in the remote repository
    RevCommit secondCommitRevision = remoteGit.commit().setMessage("Second commit from remote repository").call();

    // Add a new paragraph to the local repository
    addParagraphToNotebook();

    // Commit and push the changes to remote repository
    NotebookRepoWithVersionControl.Revision thirdCommitRevision = gitHubNotebookRepo.checkpoint(
            TEST_NOTE_ID, TEST_NOTE_PATH, "Third commit from local repository", null);

    // Check all the commits as seen from the local repository. The commits are ordered chronologically. The last
    // commit is the first in the commit logs.
    Iterator<RevCommit> revisions = gitHubNotebookRepo.getGit().log().all().call().iterator();

    revisions.next(); // The Merge `master` commit after pushing to the remote repository

    assert(thirdCommitRevision.id.equals(revisions.next().getName())); // The local commit after adding the paragraph

    // The second commit done on the remote repository
    assert(secondCommitRevision.getName().equals(revisions.next().getName()));

    // The first commit done on the remote repository
    assert(firstCommitRevision.getName().equals(revisions.next().getName()));
  }

  @Test
  /**
   * Test the case when the check-pointing (add new files and commit) it pushes the local commits to the remote
   * repository
   */
  void pushLocalChangesToRemoteRepositoryOnCheckpointing() throws IOException, GitAPIException {
    // Add a new paragraph to the local repository
    addParagraphToNotebook();

    // Commit and push the changes to remote repository
    NotebookRepoWithVersionControl.Revision secondCommitRevision = gitHubNotebookRepo.checkpoint(
            TEST_NOTE_ID, TEST_NOTE_PATH, "Second commit from local repository", null);

    // Check all the commits as seen from the remote repository. The commits are ordered chronologically. The last
    // commit is the first in the commit logs.
    Iterator<RevCommit> revisions = remoteGit.log().all().call().iterator();

    assert(secondCommitRevision.id.equals(revisions.next().getName())); // The local commit after adding the paragraph

    // The first commit done on the remote repository
    assert(firstCommitRevision.getName().equals(revisions.next().getName()));
  }

  private void addParagraphToNotebook() throws IOException {
    Note note = gitHubNotebookRepo.get(TEST_NOTE_ID, TEST_NOTE_PATH, null);
    note.setInterpreterFactory(mock(InterpreterFactory.class));
    Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    paragraph.setText("%md text");
    gitHubNotebookRepo.save(note, null);
  }
}
