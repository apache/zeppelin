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
import static junit.framework.TestCase.fail;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.contrib.nio.testing.LocalStorageHelper;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GCSNotebookRepoTest {
  private static final AuthenticationInfo AUTH_INFO = AuthenticationInfo.ANONYMOUS;

  private GCSNotebookRepo notebookRepo;
  private Storage storage;

  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
        { "bucketname", Optional.absent(), "gs://bucketname" },
        { "bucketname-with-slash", Optional.absent(), "gs://bucketname-with-slash/" },
        { "bucketname", Optional.of("path/to/dir"), "gs://bucketname/path/to/dir" },
        { "bucketname", Optional.of("trailing/slash"), "gs://bucketname/trailing/slash/" }
    });
  }

  @Parameter(0)
  public String bucketName;

  @Parameter(1)
  public Optional<String> basePath;

  @Parameter(2)
  public String uriPath;

  private Note runningNote;

  @Before
  public void setUp() throws Exception {
    this.runningNote = makeRunningNote();

    this.storage = LocalStorageHelper.getOptions().getService();

    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_GCS_STORAGE_DIR.getVarName(), uriPath);
    this.notebookRepo = new GCSNotebookRepo(new ZeppelinConfiguration(), storage);
  }

  private static Note makeRunningNote() {
    Note note = new Note();
    note.setConfig(ImmutableMap.<String, Object>of("key", "value"));

    Paragraph p = new Paragraph(note, null, null);
    p.setText("text");
    p.setStatus(Status.RUNNING);
    note.addParagraph(p);
    return note;
  }

  @Test
  public void testList_nonexistent() throws Exception {
    assertThat(notebookRepo.list(AUTH_INFO)).isEmpty();
  }

  @Test
  public void testList() throws Exception {
    createAt(runningNote, "note.json");
    createAt(runningNote, "/note.json");
    createAt(runningNote, "validid/note.json");
    createAt(runningNote, "validid-2/note.json");
    createAt(runningNote, "cannot-be-dir/note.json/foo");
    createAt(runningNote, "cannot/be/nested/note.json");

    List<NoteInfo> infos = notebookRepo.list(AUTH_INFO);
    List<String> noteIds = new ArrayList<>();
    for (NoteInfo info : infos) {
      noteIds.add(info.getId());
    }
    // Only valid paths are gs://bucketname/path/<noteid>/note.json
    assertThat(noteIds).containsExactlyElementsIn(ImmutableList.of("validid", "validid-2"));
  }

  @Test
  public void testGet_nonexistent() throws Exception {
    try {
      notebookRepo.get("id", AUTH_INFO);
      fail();
    } catch (IOException e) {}
  }

  @Test
  public void testGet() throws Exception {
    create(runningNote);

    // Status of saved running note is removed in get()
    Note got = notebookRepo.get(runningNote.getId(), AUTH_INFO);
    assertThat(got.getLastParagraph().getStatus()).isEqualTo(Status.ABORT);

    // But otherwise equal
    got.getLastParagraph().setStatus(Status.RUNNING);
    assertThat(got).isEqualTo(runningNote);
  }

  @Test
  public void testGet_malformed() throws Exception {
    createMalformed("id");
    try {
      notebookRepo.get("id", AUTH_INFO);
      fail();
    } catch (IOException e) {}
  }

  @Test
  public void testSave_create() throws Exception {
    notebookRepo.save(runningNote, AUTH_INFO);
    // Output is saved
    assertThat(storage.readAllBytes(makeBlobId(runningNote.getId())))
        .isEqualTo(runningNote.toJson().getBytes("UTF-8"));
  }

  @Test
  public void testSave_update() throws Exception {
    notebookRepo.save(runningNote, AUTH_INFO);
    // Change name of runningNote
    runningNote.setName("new-name");
    notebookRepo.save(runningNote, AUTH_INFO);
    assertThat(storage.readAllBytes(makeBlobId(runningNote.getId())))
        .isEqualTo(runningNote.toJson().getBytes("UTF-8"));
  }

  @Test
  public void testRemove_nonexistent() throws Exception {
    try {
      notebookRepo.remove("id", AUTH_INFO);
      fail();
    } catch (IOException e) {}
  }

  @Test
  public void testRemove() throws Exception {
    create(runningNote);
    notebookRepo.remove(runningNote.getId(), AUTH_INFO);
    assertThat(storage.get(makeBlobId(runningNote.getId()))).isNull();
  }

  private String makeName(String relativePath) {
    if (basePath.isPresent()) {
      return basePath.get() + "/" + relativePath;
    } else {
      return relativePath;
    }
  }

  private BlobId makeBlobId(String noteId) {
    return BlobId.of(bucketName, makeName(noteId + "/note.json"));
  }

  private void createAt(Note note, String relativePath) throws IOException {
    BlobId id = BlobId.of(bucketName, makeName(relativePath));
    BlobInfo info = BlobInfo.newBuilder(id).setContentType("application/json").build();
    storage.create(info, note.toJson().getBytes("UTF-8"));
  }

  private void create(Note note) throws IOException {
    BlobInfo info = BlobInfo.newBuilder(makeBlobId(note.getId()))
        .setContentType("application/json")
        .build();
    storage.create(info, note.toJson().getBytes("UTF-8"));
  }

  private void createMalformed(String noteId) throws IOException {
    BlobInfo info = BlobInfo.newBuilder(makeBlobId(noteId))
        .setContentType("application/json")
        .build();
    storage.create(info, "{ invalid-json }".getBytes("UTF-8"));
  }

  /* These tests test path parsing for illegal paths, and do not use the parameterized vars */

  @Test
  public void testInitialization_pathNotSet() throws Exception {
    try {
      System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_GCS_STORAGE_DIR.getVarName(), "");
      new GCSNotebookRepo(new ZeppelinConfiguration(), storage);
      fail();
    } catch (IOException e) {}
  }

  @Test
  public void testInitialization_malformedPath() throws Exception {
    try {
      System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_GCS_STORAGE_DIR.getVarName(), "foo");
      new GCSNotebookRepo(new ZeppelinConfiguration(), storage);
      fail();
    } catch (IOException e) {}
  }
}
