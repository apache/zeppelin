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

public class GitHubNotebookRepoTest {
  private static final Logger LOG = LoggerFactory.getLogger(GitNotebookRepoTest.class);

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

    String remoteRepositoryPath = System.getProperty("java.io.tmpdir") + "/ZeppelinTestRemote_";
    String localRepositoryPath = System.getProperty("java.io.tmpdir") + "/ZeppelinTest_";

    String currentPath = new File(".").getCanonicalFile().getPath();

    remoteZeppelinDir = new File(remoteRepositoryPath);
    remoteZeppelinDir.mkdirs();
    new File(remoteZeppelinDir, "conf").mkdirs();

    localZeppelinDir = new File(localRepositoryPath);
    localZeppelinDir.mkdirs();
    new File(localZeppelinDir, "conf").mkdirs();

    localNotebooksDir = Joiner.on(File.separator).join(localRepositoryPath, "notebook");
    remoteNotebooksDir = Joiner.on(File.separator).join(remoteRepositoryPath, "notebook");

    File notebookDir = new File(localNotebooksDir);
    notebookDir.mkdirs();

    String remoteTestNoreDir = Joiner.on(File.separator).join(remoteNotebooksDir, TEST_NOTE_ID);
    FileUtils.copyDirectory(
            new File(
                    Joiner.on(File.separator).join(
                            currentPath,"zeppelin-server", "src", "test", "resources", TEST_NOTE_ID
                    )
            ), new File(remoteTestNoreDir)
    );

    Repository remoteRepository = new FileRepository(Joiner.on(File.separator).join(remoteNotebooksDir, ".git"));
    remoteRepository.create(); // Create another repository

    remoteGit = new Git(remoteRepository);
    remoteGit.add().addFilepattern(".").call();
    firstCommitRevision = remoteGit.commit().setMessage("First commit from remote repository").call();

    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME.getVarName(), remoteZeppelinDir.getAbsolutePath());
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(), notebookDir.getAbsolutePath());
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_STORAGE.getVarName(), "org.apache.zeppelin.notebook.repo.GitHubNotebookRepo");

    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_GIT_REMOTE_URL.getVarName(), remoteNotebooksDir + File.separator + ".git");
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_GIT_REMOTE_USERNAME.getVarName(), "token");
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_GIT_REMOTE_ACCESS_TOKEN.getVarName(), "access-token");

    gitHubNotebookRepo = new GitHubNotebookRepo(conf);
  }

  @After
  public void tearDown() throws Exception {
    if (!FileUtils.deleteQuietly(remoteZeppelinDir)) {
      LOG.error("Failed to delete {} ", remoteZeppelinDir.getName());
    }

    if (!FileUtils.deleteQuietly(localZeppelinDir)) {
      LOG.error("Failed to delete {} ", localZeppelinDir.getName());
    }
  }

  @Test
  public void pullChangesFromRemoteRepositoryOnLoadingNotebook() throws IOException, GitAPIException {
    NotebookRepo.Revision firstHistoryRevision = gitHubNotebookRepo.revisionHistory(TEST_NOTE_ID, null).get(0);

    assert(this.firstCommitRevision.getName().equals(firstHistoryRevision.id));
  }

  @Test
  public void pullChangesFromRemoteRepositoryOnCheckpointing() throws GitAPIException, IOException {
    // Create a new commit in the remote repository
    RevCommit secondCommitRevision = remoteGit.commit().setMessage("Second commit from remote repository").call();

    // Add a new paragraph to the local repository
    Note note = gitHubNotebookRepo.get(TEST_NOTE_ID, null);
    note.setInterpreterFactory(mock(InterpreterFactory.class));
    Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    paragraph.setText("%md text");
    gitHubNotebookRepo.save(note, null);

    // Commit and push the changes to remote repository
    NotebookRepo.Revision thirdCommitRevision = gitHubNotebookRepo.checkpoint(
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
  public void pushLocalChangesToRemoteRepositoryOnCheckpointing() throws IOException, GitAPIException {
    // Add a new paragraph to the local repository
    Note note = gitHubNotebookRepo.get(TEST_NOTE_ID, null);
    note.setInterpreterFactory(mock(InterpreterFactory.class));
    Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    paragraph.setText("%md text");
    gitHubNotebookRepo.save(note, null);

    // Commit and push the changes to remote repository
    NotebookRepo.Revision secondCommitRevision = gitHubNotebookRepo.checkpoint(
            TEST_NOTE_ID, "Second commit from local repository", null);

    // Check all the commits as seen from the local repository. The commits are ordered chronologically. The last
    // commit is the first in the commit logs.
    Iterator<RevCommit> revisions = remoteGit.log().all().call().iterator();

    assert(secondCommitRevision.id.equals(revisions.next().getName())); // The local commit after adding the paragraph

    // The first commit done on the remote repository
    assert(firstCommitRevision.getName().equals(revisions.next().getName()));
  }
}
