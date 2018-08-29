
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


import com.google.common.base.Joiner;
import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import static org.mockito.Mockito.mock;

/**
 * This tests the remote Git tracking for notebooks. The tests uses two local Git repositories created locally
 * to handle the tracking of Git actions (pushes and pulls). The repositories are:
 * 1. The first repository is considered as a remote that mimics a remote GitHub directory
 * 2. The second repository is considered as the local notebook repository
 */
public class GitHubNotebookRepoTest {
  private static final Logger LOG = LoggerFactory.getLogger(GitHubNotebookRepoTest.class);

  private static final String TEST_NOTE_ID = "2A94M5J1Z";

  private File remoteZeppelinDir;
  private File localZeppelinDir;
  private String localNotebooksDir;
  private String remoteNotebooksDir;
  private ZeppelinConfiguration conf;
  private GitHubNotebookRepo gitHubNotebookRepo;
  private RevCommit firstCommitRevision;
  private Git remoteGit;

  @Before
  public void setUp() throws Exception {
    conf = ZeppelinConfiguration.create();

    String remoteRepositoryPath = System.getProperty("java.io.tmpdir") + "/ZeppelinTestRemote_" +
            System.currentTimeMillis();
    String localRepositoryPath = System.getProperty("java.io.tmpdir") + "/ZeppelinTest_" +
            System.currentTimeMillis();

    // Create a fake remote notebook Git repository locally in another directory
    remoteZeppelinDir = new File(remoteRepositoryPath);
    remoteZeppelinDir.mkdirs();

    // Create a local repository for notebooks
    localZeppelinDir = new File(localRepositoryPath);
    localZeppelinDir.mkdirs();

    // Notebooks directory (for both the remote and local directories)
    localNotebooksDir = Joiner.on(File.separator).join(localRepositoryPath, "notebook");
    remoteNotebooksDir = Joiner.on(File.separator).join(remoteRepositoryPath, "notebook");

    File notebookDir = new File(localNotebooksDir);
    notebookDir.mkdirs();

    // Copy the test notebook directory from the test/resources/2A94M5J1Z folder to the fake remote Git directory
    String remoteTestNoteDir = Joiner.on(File.separator).join(remoteNotebooksDir, TEST_NOTE_ID);
    FileUtils.copyDirectory(
            new File(
              GitHubNotebookRepoTest.class.getResource(
                Joiner.on(File.separator).join("", TEST_NOTE_ID)
              ).getFile()
            ), new File(remoteTestNoteDir)
    );

    // Create the fake remote Git repository
    Repository remoteRepository = new FileRepository(Joiner.on(File.separator).join(remoteNotebooksDir, ".git"));
    remoteRepository.create();

    remoteGit = new Git(remoteRepository);
    remoteGit.add().addFilepattern(".").call();
    firstCommitRevision = remoteGit.commit().setMessage("First commit from remote repository").call();

    // Set the Git and Git configurations
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME.getVarName(), remoteZeppelinDir.getAbsolutePath());
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(), notebookDir.getAbsolutePath());

    // Set the GitHub configurations
    System.setProperty(
            ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_STORAGE.getVarName(),
            "org.apache.zeppelin.notebook.repo.GitHubNotebookRepo");
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_GIT_REMOTE_URL.getVarName(),
            remoteNotebooksDir + File.separator + ".git");
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_GIT_REMOTE_USERNAME.getVarName(), "token");
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_GIT_REMOTE_ACCESS_TOKEN.getVarName(),
            "access-token");

    // Create the Notebook repository (configured for the local repository)
    gitHubNotebookRepo = new GitHubNotebookRepo();
    gitHubNotebookRepo.init(conf);
  }

  @After
  public void tearDown() throws Exception {
    // Cleanup the temporary folders uses as Git repositories
    File[] temporaryFolders = { remoteZeppelinDir, localZeppelinDir };

    for(File temporaryFolder : temporaryFolders) {
      if (!FileUtils.deleteQuietly(temporaryFolder))
        LOG.error("Failed to delete {} ", temporaryFolder.getName());
    }
  }

  @Test
  /**
   * Test the case when the Notebook repository is created, it pulls the latest changes from the remote repository
   */
  public void pullChangesFromRemoteRepositoryOnLoadingNotebook() throws IOException, GitAPIException {
    NotebookRepoWithVersionControl.Revision firstHistoryRevision = gitHubNotebookRepo.revisionHistory(TEST_NOTE_ID, null).get(0);

    assert(this.firstCommitRevision.getName().equals(firstHistoryRevision.id));
  }

  @Test
  /**
   * Test the case when the check-pointing (add new files and commit) it also pulls the latest changes from the
   * remote repository
   */
  public void pullChangesFromRemoteRepositoryOnCheckpointing() throws GitAPIException, IOException {
    // Create a new commit in the remote repository
    RevCommit secondCommitRevision = remoteGit.commit().setMessage("Second commit from remote repository").call();

    // Add a new paragraph to the local repository
    addParagraphToNotebook(TEST_NOTE_ID);

    // Commit and push the changes to remote repository
    NotebookRepoWithVersionControl.Revision thirdCommitRevision = gitHubNotebookRepo.checkpoint(
            TEST_NOTE_ID, "Third commit from local repository", null);

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
  public void pushLocalChangesToRemoteRepositoryOnCheckpointing() throws IOException, GitAPIException {
    // Add a new paragraph to the local repository
    addParagraphToNotebook(TEST_NOTE_ID);

    // Commit and push the changes to remote repository
    NotebookRepoWithVersionControl.Revision secondCommitRevision = gitHubNotebookRepo.checkpoint(
            TEST_NOTE_ID, "Second commit from local repository", null);

    // Check all the commits as seen from the remote repository. The commits are ordered chronologically. The last
    // commit is the first in the commit logs.
    Iterator<RevCommit> revisions = remoteGit.log().all().call().iterator();

    assert(secondCommitRevision.id.equals(revisions.next().getName())); // The local commit after adding the paragraph

    // The first commit done on the remote repository
    assert(firstCommitRevision.getName().equals(revisions.next().getName()));
  }

  private void addParagraphToNotebook(String noteId) throws IOException {
    Note note = gitHubNotebookRepo.get(TEST_NOTE_ID, null);
    note.setInterpreterFactory(mock(InterpreterFactory.class));
    Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    paragraph.setText("%md text");
    gitHubNotebookRepo.save(note, null);
  }
}
