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
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.interpreter.mock.MockInterpreter1;
import org.apache.zeppelin.interpreter.mock.MockInterpreter2;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.repo.NotebookRepo.Revision;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Joiner;

public class GitNotebookRepoTest {

  private static final String TEST_NOTE_ID = "2A94M5J1Z";
  private static final String TEST_NOTE_ID2 = "2A94M5J2Z";

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

    String testNoteDir = Joiner.on(File.separator).join(notebooksDir, TEST_NOTE_ID);
    String testNoteDir2 = Joiner.on(File.separator).join(notebooksDir, TEST_NOTE_ID2);
    FileUtils.copyDirectory(new File(Joiner.on(File.separator).join("src", "test", "resources", TEST_NOTE_ID)),
        new File(testNoteDir));
    FileUtils.copyDirectory(new File(Joiner.on(File.separator).join("src", "test", "resources", TEST_NOTE_ID2)),
        new File(testNoteDir2)
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
    //NotebookRepoSyncTest.delete(zeppelinDir);
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
    assertThat(notebookRepo.list(null)).isNotEmpty();

    List<DiffEntry> diff = git.diff().call();
    // no commit, diff isn't empty
    assertThat(diff).isNotEmpty();
  }

  @Test
  public void showNotebookHistoryEmptyTest() throws GitAPIException, IOException {
    //given
    notebookRepo = new GitNotebookRepo(conf);
    assertThat(notebookRepo.list(null)).isNotEmpty();

    //when
    List<Revision> testNotebookHistory = notebookRepo.revisionHistory(TEST_NOTE_ID, null);

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
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, null)).isEmpty();
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID2, null)).isEmpty();

    //add commit to both notes
    notebookRepo.checkpoint(TEST_NOTE_ID, "first commit, note1", null);
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, null).size()).isEqualTo(1);
    notebookRepo.checkpoint(TEST_NOTE_ID2, "first commit, note2", null);
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID2, null).size()).isEqualTo(1);

    //modify, save and checkpoint first note
    Note note = notebookRepo.get(TEST_NOTE_ID, null);
    Paragraph p = note.addParagraph();
    Map<String, Object> config = p.getConfig();
    config.put("enabled", true);
    p.setConfig(config);
    p.setText("%md note1 test text");
    notebookRepo.save(note, null);
    assertThat(notebookRepo.checkpoint(TEST_NOTE_ID, "second commit, note1", null)).isNotNull();
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, null).size()).isEqualTo(2);
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID2, null).size()).isEqualTo(1);
    assertThat(notebookRepo.checkpoint(TEST_NOTE_ID2, "first commit, note2", null)).isNull();
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID2, null).size()).isEqualTo(1);

    //modify, save and checkpoint second note
    note = notebookRepo.get(TEST_NOTE_ID2, null);
    p = note.addParagraph();
    config = p.getConfig();
    config.put("enabled", false);
    p.setConfig(config);
    p.setText("%md note2 test text");
    notebookRepo.save(note, null);
    assertThat(notebookRepo.checkpoint(TEST_NOTE_ID2, "second commit, note2", null)).isNotNull();
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, null).size()).isEqualTo(2);
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID2, null).size()).isEqualTo(2);
  }

  @Test
  public void addCheckpointTest() throws IOException {
    // initial checks
    notebookRepo = new GitNotebookRepo(conf);
    assertThat(notebookRepo.list(null)).isNotEmpty();
    assertThat(containsNote(notebookRepo.list(null), TEST_NOTE_ID)).isTrue();
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, null)).isEmpty();

    notebookRepo.checkpoint(TEST_NOTE_ID, "first commit", null);
    List<Revision> notebookHistoryBefore = notebookRepo.revisionHistory(TEST_NOTE_ID, null);
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, null)).isNotEmpty();
    int initialCount = notebookHistoryBefore.size();
    
    // add changes to note
    Note note = notebookRepo.get(TEST_NOTE_ID, null);
    Paragraph p = note.addParagraph();
    Map<String, Object> config = p.getConfig();
    config.put("enabled", true);
    p.setConfig(config);
    p.setText("%md checkpoint test text");
    
    // save and checkpoint note
    notebookRepo.save(note, null);
    notebookRepo.checkpoint(TEST_NOTE_ID, "second commit", null);
    
    // see if commit is added
    List<Revision> notebookHistoryAfter = notebookRepo.revisionHistory(TEST_NOTE_ID, null);
    assertThat(notebookHistoryAfter.size()).isEqualTo(initialCount + 1);
  }
  
  private boolean containsNote(List<NoteInfo> notes, String noteId) {
    for (NoteInfo note: notes) {
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
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, null)).isEmpty();

    // add first checkpoint
    Revision revision_1 = notebookRepo.checkpoint(TEST_NOTE_ID, "first commit", null);
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, null).size()).isEqualTo(1);
    int paragraphCount_1 = notebookRepo.get(TEST_NOTE_ID, null).getParagraphs().size();

    // add paragraph and save
    Note note = notebookRepo.get(TEST_NOTE_ID, null);
    Paragraph p1 = note.addParagraph();
    Map<String, Object> config = p1.getConfig();
    config.put("enabled", true);
    p1.setConfig(config);
    p1.setText("%md checkpoint test text");
    notebookRepo.save(note, null);

    // second checkpoint
    notebookRepo.checkpoint(TEST_NOTE_ID, "second commit", null);
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, null).size()).isEqualTo(2);
    int paragraphCount_2 = notebookRepo.get(TEST_NOTE_ID, null).getParagraphs().size();
    assertThat(paragraphCount_2).isEqualTo(paragraphCount_1 + 1);

    // get note from revision 1
    Note noteRevision_1 = notebookRepo.get(TEST_NOTE_ID, revision_1, null);
    assertThat(noteRevision_1.getParagraphs().size()).isEqualTo(paragraphCount_1);

    // get current note
    note = notebookRepo.get(TEST_NOTE_ID, null);
    assertThat(note.getParagraphs().size()).isEqualTo(paragraphCount_2);

    // add one more paragraph and save
    Paragraph p2 = note.addParagraph();
    config.put("enabled", false);
    p2.setConfig(config);
    p2.setText("%md get revision when modified note test text");
    notebookRepo.save(note, null);
    note = notebookRepo.get(TEST_NOTE_ID, null);
    int paragraphCount_3 = note.getParagraphs().size();
    assertThat(paragraphCount_3).isEqualTo(paragraphCount_2 + 1);

    // get revision 1 again
    noteRevision_1 = notebookRepo.get(TEST_NOTE_ID, revision_1, null);
    assertThat(noteRevision_1.getParagraphs().size()).isEqualTo(paragraphCount_1);

    // check that note is unchanged
    note = notebookRepo.get(TEST_NOTE_ID, null);
    assertThat(note.getParagraphs().size()).isEqualTo(paragraphCount_3);
  }

  @Test
  public void getRevisionFailTest() throws IOException {
    // initial checks
    notebookRepo = new GitNotebookRepo(conf);
    assertThat(notebookRepo.list(null)).isNotEmpty();
    assertThat(containsNote(notebookRepo.list(null), TEST_NOTE_ID)).isTrue();
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, null)).isEmpty();

    // add first checkpoint
    Revision revision_1 = notebookRepo.checkpoint(TEST_NOTE_ID, "first commit", null);
    assertThat(notebookRepo.revisionHistory(TEST_NOTE_ID, null).size()).isEqualTo(1);
    int paragraphCount_1 = notebookRepo.get(TEST_NOTE_ID, null).getParagraphs().size();

    // get current note
    Note note = notebookRepo.get(TEST_NOTE_ID, null);
    assertThat(note.getParagraphs().size()).isEqualTo(paragraphCount_1);

    // add one more paragraph and save
    Paragraph p1 = note.addParagraph();
    Map<String, Object> config = p1.getConfig();
    config.put("enabled", true);
    p1.setConfig(config);
    p1.setText("%md get revision when modified note test text");
    notebookRepo.save(note, null);
    int paragraphCount_2 = note.getParagraphs().size();

    // get note from revision 1
    Note noteRevision_1 = notebookRepo.get(TEST_NOTE_ID, revision_1, null);
    assertThat(noteRevision_1.getParagraphs().size()).isEqualTo(paragraphCount_1);

    // get current note
    note = notebookRepo.get(TEST_NOTE_ID, null);
    assertThat(note.getParagraphs().size()).isEqualTo(paragraphCount_2);

    // test for absent revision
    Revision absentRevision = new Revision("absentId", StringUtils.EMPTY, 0);
    note = notebookRepo.get(TEST_NOTE_ID, absentRevision, null);
    assertThat(note).isNull();
  }

}
