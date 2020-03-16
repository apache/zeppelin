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
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.google.common.truth.Truth;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.repo.NotebookRepoWithVersionControl.Revision;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;

public class GitNotebookRepoTest {
  private static final Logger LOG = LoggerFactory.getLogger(GitNotebookRepoTest.class);

  private static final String TEST_NOTE_ID = "2A94M5J1Z";
  private static final String TEST_NOTE_ID2 = "2A94M5J2Z";
  private static final String TEST_NOTE_PATH = "/my_project/my_note1";
  private static final String TEST_NOTE_PATH2 = "/my_project/my_note2";

  private File zeppelinDir;
  private String notebooksDir;
  private ZeppelinConfiguration conf;
  private GitNotebookRepo notebookRepo;

  @Before
  public void setUp() throws Exception {
    String zpath = System.getProperty("java.io.tmpdir") + "/ZeppelinTest_" + System.currentTimeMillis();
    zeppelinDir = new File(zpath);
    zeppelinDir.mkdirs();
    new File(zeppelinDir, "conf").mkdirs();

    notebooksDir = Joiner.on(File.separator).join(zpath, "notebook");
    File notebookDir = new File(notebooksDir);
    notebookDir.mkdirs();

    FileUtils.copyDirectory(
            new File(GitNotebookRepoTest.class.getResource("/notebook").getFile()),
            new File(notebooksDir));

    System.setProperty(ConfVars.ZEPPELIN_HOME.getVarName(), zeppelinDir.getAbsolutePath());
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(), notebookDir.getAbsolutePath());
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE.getVarName(), "org.apache.zeppelin.notebook.repo.GitNotebookRepo");

    conf = ZeppelinConfiguration.create();
  }

  @After
  public void tearDown() throws Exception {
    if (!FileUtils.deleteQuietly(zeppelinDir)) {
      LOG.error("Failed to delete {} ", zeppelinDir.getName());
    }
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
    Truth.assertThat(git).isNotNull();

    assertThat(dotGit.exists()).isEqualTo(true);
    assertThat(notebookRepo.list(null)).isNotEmpty();

    List<DiffEntry> diff = git.diff().call();
    // no commit, diff isn't empty
    Truth.assertThat(diff).isNotEmpty();
  }

  @Test
  public void showNotebookHistoryEmptyTest() throws GitAPIException, IOException {
    //given
    notebookRepo = new GitNotebookRepo(conf);
    assertThat(notebookRepo.list(null)).isNotEmpty();

    //when
    List<Revision> testNotebookHistory = notebookRepo.revisionHistory(TEST_NOTE_ID, TEST_NOTE_PATH, null);

    //then
    //no initial commit, empty history
    assertThat(testNotebookHistory).isEmpty();
  }

  @Test
  public void showNotebookHistoryMultipleNotesTest() throws IOException {
    //initial checks
    notebookRepo = new GitNotebookRepo(conf);
    assertThat(notebookRepo.list(null)).isNotEmpty();
    assertThat(containsNote(notebookRepo.list(null), TEST_NOTE_ID)).isTrue();
    assertThat(containsNote(notebookRepo.list(null), TEST_NOTE_ID2)).isTrue();
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, TEST_NOTE_PATH, null)).isEmpty();
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID2, TEST_NOTE_PATH2, null)).isEmpty();

    //add commit to both notes
    notebookRepo.checkpoint(TEST_NOTE_ID, TEST_NOTE_PATH, "first commit, note1", null);
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, TEST_NOTE_PATH, null).size()).isEqualTo(1);
    notebookRepo.checkpoint(TEST_NOTE_ID2, TEST_NOTE_PATH2, "first commit, note2", null);
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID2, TEST_NOTE_PATH2, null).size()).isEqualTo(1);

    //modify, save and checkpoint first note
    Note note = notebookRepo.get(TEST_NOTE_ID, TEST_NOTE_PATH, null);
    note.setInterpreterFactory(mock(InterpreterFactory.class));
    Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map<String, Object> config = p.getConfig();
    config.put("enabled", true);
    p.setConfig(config);
    p.setText("%md note1 test text");
    notebookRepo.save(note, null);
    assertThat(notebookRepo.checkpoint(TEST_NOTE_ID, TEST_NOTE_PATH, "second commit, note1", null)).isNotNull();
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, TEST_NOTE_PATH, null).size()).isEqualTo(2);
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID2, TEST_NOTE_PATH2, null).size()).isEqualTo(1);
    assertThat(notebookRepo.checkpoint(TEST_NOTE_ID2, TEST_NOTE_PATH2, "first commit, note2", null))
      .isEqualTo(Revision.EMPTY);
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID2, TEST_NOTE_PATH2, null).size()).isEqualTo(1);

    //modify, save and checkpoint second note
    note = notebookRepo.get(TEST_NOTE_ID2, TEST_NOTE_PATH2, null);
    note.setInterpreterFactory(mock(InterpreterFactory.class));
    p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    config = p.getConfig();
    config.put("enabled", false);
    p.setConfig(config);
    p.setText("%md note2 test text");
    notebookRepo.save(note, null);
    assertThat(notebookRepo.checkpoint(TEST_NOTE_ID2, TEST_NOTE_PATH2, "second commit, note2", null)).isNotNull();
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, TEST_NOTE_PATH, null).size()).isEqualTo(2);
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID2, TEST_NOTE_PATH2, null).size()).isEqualTo(2);
  }

  @Test
  public void addCheckpointTest() throws IOException, GitAPIException {
    // initial checks
    notebookRepo = new GitNotebookRepo(conf);
    assertThat(notebookRepo.list(null)).isNotEmpty();
    assertThat(containsNote(notebookRepo.list(null), TEST_NOTE_ID)).isTrue();
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, TEST_NOTE_PATH, null)).isEmpty();

    notebookRepo.checkpoint(TEST_NOTE_ID, TEST_NOTE_PATH, "first commit", null);
    List<Revision> notebookHistoryBefore = notebookRepo.revisionHistory(TEST_NOTE_ID, TEST_NOTE_PATH, null);
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, TEST_NOTE_PATH, null)).isNotEmpty();
    int initialCount = notebookHistoryBefore.size();

    // add changes to note
    Note note = notebookRepo.get(TEST_NOTE_ID, TEST_NOTE_PATH, null);
    note.setInterpreterFactory(mock(InterpreterFactory.class));
    Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map<String, Object> config = p.getConfig();
    config.put("enabled", true);
    p.setConfig(config);
    p.setText("%md checkpoint test text");

    // save and checkpoint note
    notebookRepo.save(note, null);
    notebookRepo.checkpoint(TEST_NOTE_ID, TEST_NOTE_PATH, "second commit", null);

    // see if commit is added
    List<Revision> notebookHistoryAfter = notebookRepo.revisionHistory(TEST_NOTE_ID, TEST_NOTE_PATH, null);
    assertThat(notebookHistoryAfter.size()).isEqualTo(initialCount + 1);

    int revCountBefore = 0;
    Iterable<RevCommit> revCommits = notebookRepo.getGit().log().call();
    for (RevCommit revCommit : revCommits) {
      revCountBefore++;
    }

    // add changes to note2
    Note note2 = notebookRepo.get(TEST_NOTE_ID2, TEST_NOTE_PATH2, null);
    note2.setInterpreterFactory(mock(InterpreterFactory.class));
    Paragraph p2 = note2.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map<String, Object> config2 = p2.getConfig();
    config2.put("enabled", true);
    p2.setConfig(config);
    p2.setText("%md checkpoint test text");

    // save note2 and checkpoint this note without changes
    notebookRepo.save(note2, null);
    notebookRepo.checkpoint(TEST_NOTE_ID, TEST_NOTE_PATH, "third commit", null);

    // should not add more commit
    int revCountAfter = 0;
    revCommits = notebookRepo.getGit().log().call();
    for (RevCommit revCommit : revCommits) {
      revCountAfter++;
    }
    assertThat(revCountAfter).isEqualTo(revCountBefore);
  }

  private boolean containsNote(Map<String, NoteInfo> notes, String noteId) {
    for (NoteInfo note: notes.values()) {
      if (note.getId().equals(noteId)) {
        return true;
      }
    }
    return false;
  }

  @Test
  public void getRevisionTest() throws IOException {
    // initial checks
    notebookRepo = new GitNotebookRepo(conf);
    assertThat(notebookRepo.list(null)).isNotEmpty();
    assertThat(containsNote(notebookRepo.list(null), TEST_NOTE_ID)).isTrue();
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, TEST_NOTE_PATH, null)).isEmpty();

    // add first checkpoint
    Revision revision_1 = notebookRepo.checkpoint(TEST_NOTE_ID, TEST_NOTE_PATH, "first commit", null);
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, TEST_NOTE_PATH, null).size()).isEqualTo(1);
    int paragraphCount_1 = notebookRepo.get(TEST_NOTE_ID, TEST_NOTE_PATH, null).getParagraphs().size();

    // add paragraph and save
    Note note = notebookRepo.get(TEST_NOTE_ID, TEST_NOTE_PATH, null);
    note.setInterpreterFactory(mock(InterpreterFactory.class));
    Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map<String, Object> config = p1.getConfig();
    config.put("enabled", true);
    p1.setConfig(config);
    p1.setText("checkpoint test text");
    notebookRepo.save(note, null);

    // second checkpoint
    notebookRepo.checkpoint(TEST_NOTE_ID, TEST_NOTE_PATH, "second commit", null);
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, TEST_NOTE_PATH, null).size()).isEqualTo(2);
    int paragraphCount_2 = notebookRepo.get(TEST_NOTE_ID, TEST_NOTE_PATH, null).getParagraphs().size();
    assertThat(paragraphCount_2).isEqualTo(paragraphCount_1 + 1);

    // get note from revision 1
    Note noteRevision_1 = notebookRepo.get(TEST_NOTE_ID, TEST_NOTE_PATH, revision_1.id, null);
    assertThat(noteRevision_1.getParagraphs().size()).isEqualTo(paragraphCount_1);

    // get current note
    note = notebookRepo.get(TEST_NOTE_ID, TEST_NOTE_PATH, null);
    note.setInterpreterFactory(mock(InterpreterFactory.class));
    assertThat(note.getParagraphs().size()).isEqualTo(paragraphCount_2);

    // add one more paragraph and save
    Paragraph p2 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    config.put("enabled", false);
    p2.setConfig(config);
    p2.setText("get revision when modified note test text");
    notebookRepo.save(note, null);
    note = notebookRepo.get(TEST_NOTE_ID, TEST_NOTE_PATH, null);
    note.setInterpreterFactory(mock(InterpreterFactory.class));
    int paragraphCount_3 = note.getParagraphs().size();
    assertThat(paragraphCount_3).isEqualTo(paragraphCount_2 + 1);

    // get revision 1 again
    noteRevision_1 = notebookRepo.get(TEST_NOTE_ID, TEST_NOTE_PATH, revision_1.id, null);
    assertThat(noteRevision_1.getParagraphs().size()).isEqualTo(paragraphCount_1);

    // check that note is unchanged
    note = notebookRepo.get(TEST_NOTE_ID, TEST_NOTE_PATH, null);
    assertThat(note.getParagraphs().size()).isEqualTo(paragraphCount_3);
  }

  @Test
  public void getRevisionFailTest() throws IOException {
    // initial checks
    notebookRepo = new GitNotebookRepo(conf);
    assertThat(notebookRepo.list(null)).isNotEmpty();
    assertThat(containsNote(notebookRepo.list(null), TEST_NOTE_ID)).isTrue();
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, TEST_NOTE_PATH, null)).isEmpty();

    // add first checkpoint
    Revision revision_1 = notebookRepo.checkpoint(TEST_NOTE_ID, TEST_NOTE_PATH, "first commit", null);
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, TEST_NOTE_PATH, null).size()).isEqualTo(1);
    int paragraphCount_1 = notebookRepo.get(TEST_NOTE_ID, TEST_NOTE_PATH, null).getParagraphs().size();

    // get current note
    Note note = notebookRepo.get(TEST_NOTE_ID, TEST_NOTE_PATH, null);
    note.setInterpreterFactory(mock(InterpreterFactory.class));
    assertThat(note.getParagraphs().size()).isEqualTo(paragraphCount_1);

    // add one more paragraph and save
    Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map<String, Object> config = p1.getConfig();
    config.put("enabled", true);
    p1.setConfig(config);
    p1.setText("get revision when modified note test text");
    notebookRepo.save(note, null);
    int paragraphCount_2 = note.getParagraphs().size();

    // get note from revision 1
    Note noteRevision_1 = notebookRepo.get(TEST_NOTE_ID, TEST_NOTE_PATH, revision_1.id, null);
    assertThat(noteRevision_1.getParagraphs().size()).isEqualTo(paragraphCount_1);

    // get current note
    note = notebookRepo.get(TEST_NOTE_ID, TEST_NOTE_PATH, null);
    note.setInterpreterFactory(mock(InterpreterFactory.class));
    assertThat(note.getParagraphs().size()).isEqualTo(paragraphCount_2);

    // test for absent revision
    Revision absentRevision = new Revision("absentId", StringUtils.EMPTY, 0);
    note = notebookRepo.get(TEST_NOTE_ID, TEST_NOTE_PATH, absentRevision.id, null);
    assertThat(note).isNull();
  }

  @Test
  public void setRevisionTest() throws IOException {
    //create repo and check that note doesn't contain revisions
    notebookRepo = new GitNotebookRepo(conf);
    assertThat(notebookRepo.list(null)).isNotEmpty();
    assertThat(containsNote(notebookRepo.list(null), TEST_NOTE_ID)).isTrue();
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, TEST_NOTE_PATH, null)).isEmpty();

    // get current note
    Note note = notebookRepo.get(TEST_NOTE_ID, TEST_NOTE_PATH, null);
    note.setInterpreterFactory(mock(InterpreterFactory.class));
    int paragraphCount_1 = note.getParagraphs().size();
    LOG.info("initial paragraph count: {}", paragraphCount_1);

    // checkpoint revision1
    Revision revision1 = notebookRepo.checkpoint(TEST_NOTE_ID, TEST_NOTE_PATH, "set revision: first commit", null);
    //TODO(khalid): change to EMPTY after rebase
    assertThat(revision1).isNotNull();
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, TEST_NOTE_PATH, null).size()).isEqualTo(1);

    // add one more paragraph and save
    Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map<String, Object> config = p1.getConfig();
    config.put("enabled", true);
    p1.setConfig(config);
    p1.setText("set revision sample text");
    notebookRepo.save(note, null);
    int paragraphCount_2 = note.getParagraphs().size();
    assertThat(paragraphCount_2).isEqualTo(paragraphCount_1 + 1);
    LOG.info("paragraph count after modification: {}", paragraphCount_2);

    // checkpoint revision2
    Revision revision2 = notebookRepo.checkpoint(TEST_NOTE_ID, TEST_NOTE_PATH, "set revision: second commit", null);
    //TODO(khalid): change to EMPTY after rebase
    assertThat(revision2).isNotNull();
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, TEST_NOTE_PATH, null).size()).isEqualTo(2);

    // set note to revision1
    Note returnedNote = notebookRepo.setNoteRevision(note.getId(), note.getPath(), revision1.id, null);
    assertThat(returnedNote).isNotNull();
    assertThat(returnedNote.getParagraphs().size()).isEqualTo(paragraphCount_1);

    // check note from repo
    Note updatedNote = notebookRepo.get(note.getId(), note.getPath(), null);
    assertThat(updatedNote).isNotNull();
    assertThat(updatedNote.getParagraphs().size()).isEqualTo(paragraphCount_1);

    // set back to revision2
    returnedNote = notebookRepo.setNoteRevision(note.getId(), note.getPath(), revision2.id, null);
    assertThat(returnedNote).isNotNull();
    assertThat(returnedNote.getParagraphs().size()).isEqualTo(paragraphCount_2);

    // check note from repo
    updatedNote = notebookRepo.get(note.getId(), note.getPath(), null);
    assertThat(updatedNote).isNotNull();
    assertThat(updatedNote.getParagraphs().size()).isEqualTo(paragraphCount_2);

    // try failure case - set to invalid revision
    returnedNote = notebookRepo.setNoteRevision(note.getId(), note.getPath(), "nonexistent_id", null);
    assertThat(returnedNote).isNull();
  }
}
