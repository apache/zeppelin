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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.contrib.nio.testing.LocalStorageHelper;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

//TODO(zjffdu) This test fails due to some changes in google, need to fix
@Disabled
class GCSNotebookRepoTest {
  private static final AuthenticationInfo AUTH_INFO = AuthenticationInfo.ANONYMOUS;

  private GCSNotebookRepo notebookRepo;
  private Storage storage;

  private static Stream<Arguments> buckets() {
    return Stream.of(
      Arguments.of("bucketname", Optional.empty(), "gs://bucketname"),
      Arguments.of("bucketname-with-slash", Optional.empty(), "gs://bucketname-with-slash/"),
      Arguments.of("bucketname", Optional.of("path/to/dir"), "gs://bucketname/path/to/dir"),
      Arguments.of("bucketname", Optional.of("trailing/slash"), "gs://bucketname/trailing/slash/"));
  }


  private Note runningNote;

  @BeforeEach
  void setUp() throws Exception {
    this.runningNote = makeRunningNote();
    this.storage = LocalStorageHelper.getOptions().getService();
  }

  private static Note makeRunningNote() {
    Note note = new Note();
    note.setPath("/test_note");
    note.setConfig(ImmutableMap.<String, Object>of("key", "value"));

    Paragraph p = new Paragraph(note, null);
    p.setText("text");
    p.setStatus(Status.RUNNING);
    p.setAuthenticationInfo(new AuthenticationInfo("anonymous", (String)null, "anonymous"));
    note.addParagraph(p);
    return note;
  }

  @ParameterizedTest
  @MethodSource("buckets")
  void testList_nonexistent(String bucketName, Optional<String> basePath, String uriPath) throws Exception {
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_GCS_STORAGE_DIR.getVarName(), uriPath);
    this.notebookRepo = new GCSNotebookRepo(ZeppelinConfiguration.create(), storage);
    assertThat(notebookRepo.list(AUTH_INFO)).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("buckets")
  void testList(String bucketName, Optional<String> basePath, String uriPath) throws Exception {
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_GCS_STORAGE_DIR.getVarName(), uriPath);
    this.notebookRepo = new GCSNotebookRepo(ZeppelinConfiguration.create(), storage);
    createAt(runningNote, "note.zpln", bucketName, basePath);
    createAt(runningNote, "/note.zpln", bucketName, basePath);
    createAt(runningNote, "validid/my_12.zpln", bucketName, basePath);
    createAt(runningNote, "validid-2/my_123.zpln", bucketName, basePath);
    createAt(runningNote, "cannot-be-dir/note.json/foo", bucketName, basePath);
    createAt(runningNote, "cannot/be/nested/note.json", bucketName, basePath);

    Map<String, NoteInfo> infos = notebookRepo.list(AUTH_INFO);
    List<String> noteIds = new ArrayList<>();
    for (NoteInfo info : infos.values()) {
      noteIds.add(info.getId());
    }
    // Only valid paths are gs://bucketname/path/<noteid>/note.json
    assertThat(noteIds).containsExactlyElementsIn(Arrays.asList("12", "123"));
  }

  @Test
  void testGet_nonexistent() throws Exception {
    try {
      notebookRepo.get("id", "", AUTH_INFO);
      fail();
    } catch (IOException e) {}
  }

  @ParameterizedTest
  @MethodSource("buckets")
  void testGet(String bucketName, Optional<String> basePath, String uriPath) throws Exception {
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_GCS_STORAGE_DIR.getVarName(), uriPath);
    this.notebookRepo = new GCSNotebookRepo(ZeppelinConfiguration.create(), storage);
    create(runningNote, bucketName, basePath);

    // Status of saved running note is removed in get()
    Note got = notebookRepo.get(runningNote.getId(), runningNote.getPath(),  AUTH_INFO);
    assertThat(got.getLastParagraph().getStatus()).isEqualTo(Status.ABORT);

    // But otherwise equal
    got.getLastParagraph().setStatus(Status.RUNNING);
    assertThat(got).isEqualTo(runningNote);
  }

  @ParameterizedTest
  @MethodSource("buckets")
  void testGet_malformed(String bucketName, Optional<String> basePath, String uriPath) throws Exception {
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_GCS_STORAGE_DIR.getVarName(), uriPath);
    this.notebookRepo = new GCSNotebookRepo(ZeppelinConfiguration.create(), storage);
    createMalformed("id", "/name", bucketName, basePath);
    try {
      notebookRepo.get("id", "/name", AUTH_INFO);
      fail();
    } catch (IOException e) {}
  }

  @ParameterizedTest
  @MethodSource("buckets")
  void testSave_create(String bucketName, Optional<String> basePath, String uriPath) throws Exception {
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_GCS_STORAGE_DIR.getVarName(), uriPath);
    this.notebookRepo = new GCSNotebookRepo(ZeppelinConfiguration.create(), storage);
    notebookRepo.save(runningNote, AUTH_INFO);
    // Output is saved
    assertThat(storage.readAllBytes(makeBlobId(runningNote.getId(), runningNote.getPath(), bucketName, basePath)))
        .isEqualTo(runningNote.toJson().getBytes("UTF-8"));
  }

  @ParameterizedTest
  @MethodSource("buckets")
  void testSave_update(String bucketName, Optional<String> basePath, String uriPath) throws Exception {
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_GCS_STORAGE_DIR.getVarName(), uriPath);
    this.notebookRepo = new GCSNotebookRepo(ZeppelinConfiguration.create(), storage);
    notebookRepo.save(runningNote, AUTH_INFO);
    // Change name of runningNote
    runningNote.setPath("/new-name");
    notebookRepo.save(runningNote, AUTH_INFO);
    assertThat(storage.readAllBytes(makeBlobId(runningNote.getId(), runningNote.getPath(), bucketName, basePath)))
        .isEqualTo(runningNote.toJson().getBytes("UTF-8"));
  }

  @Test
  void testRemove_nonexistent() throws Exception {
    try {
      notebookRepo.remove("id", "/name", AUTH_INFO);
      fail();
    } catch (IOException e) {}
  }

  @ParameterizedTest
  @MethodSource("buckets")
  void testRemove(String bucketName, Optional<String> basePath, String uriPath) throws Exception {
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_GCS_STORAGE_DIR.getVarName(), uriPath);
    this.notebookRepo = new GCSNotebookRepo(ZeppelinConfiguration.create(), storage);
    create(runningNote, bucketName, basePath);
    notebookRepo.remove(runningNote.getId(), runningNote.getPath(), AUTH_INFO);
    assertThat(storage.get(makeBlobId(runningNote.getId(), runningNote.getPath(), bucketName, basePath))).isNull();
  }

  @Test
  void testRemoveFolder_nonexistent() throws Exception {
    assertThrows(IOException.class, () -> {
      notebookRepo.remove("id", "/name", AUTH_INFO);
      fail();
    });

  }

  @ParameterizedTest
  @MethodSource("buckets")
  void testRemoveFolder(String bucketName, Optional<String> basePath, String uriPath) throws Exception {
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_GCS_STORAGE_DIR.getVarName(), uriPath);
    this.notebookRepo = new GCSNotebookRepo(ZeppelinConfiguration.create(), storage);
    Note firstNote = makeRunningNote();
    firstNote.setPath("/folder/test_note");
    create(firstNote, bucketName, basePath);
    Note secondNote = makeRunningNote();
    secondNote.setPath("/folder/sub_folder/test_note_second");
    create(secondNote, bucketName, basePath);
    notebookRepo.remove("/folder", AUTH_INFO);
    assertThat(storage.get(makeBlobId(firstNote.getId(), firstNote.getPath(), bucketName, basePath))).isNull();
    assertThat(storage.get(makeBlobId(secondNote.getId(), secondNote.getPath(), bucketName, basePath))).isNull();
  }


  @Test
  void testMove_nonexistent() {
    try {
      notebookRepo.move("id", "/name", "/name_new", AUTH_INFO);
      fail();
    } catch (IOException e) {}
  }

  @ParameterizedTest
  @MethodSource("buckets")
  void testMove(String bucketName, Optional<String> basePath, String uriPath) throws Exception {
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_GCS_STORAGE_DIR.getVarName(), uriPath);
    this.notebookRepo = new GCSNotebookRepo(ZeppelinConfiguration.create(), storage);
    create(runningNote, bucketName, basePath);
    notebookRepo.move(runningNote.getId(), runningNote.getPath(), runningNote.getPath() + "_new", AUTH_INFO);
    assertThat(storage.get(makeBlobId(runningNote.getId(), runningNote.getPath(), bucketName, basePath))).isNull();
  }

  @Test
  void testMoveFolder_nonexistent() throws Exception {
    assertThrows(IOException.class, () -> {
      notebookRepo.move("/name", "/name_new", AUTH_INFO);
      fail();
    });
  }

  @ParameterizedTest
  @MethodSource("buckets")
  void testMoveFolder(String bucketName, Optional<String> basePath, String uriPath) throws Exception {
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_GCS_STORAGE_DIR.getVarName(), uriPath);
    this.notebookRepo = new GCSNotebookRepo(ZeppelinConfiguration.create(), storage);
    Note firstNote = makeRunningNote();
    firstNote.setPath("/folder/test_note");
    create(firstNote, bucketName, basePath);
    Note secondNote = makeRunningNote();
    secondNote.setPath("/folder/sub_folder/test_note_second");
    create(secondNote, bucketName, basePath);
    notebookRepo.move("/folder", "/folder_new", AUTH_INFO);
    assertThat(storage.get(makeBlobId(firstNote.getId(), firstNote.getPath(), bucketName, basePath))).isNull();
    assertThat(storage.get(makeBlobId(firstNote.getId(), "/folder_new/test_note", bucketName, basePath))).isNotNull();
    assertThat(storage.get(makeBlobId(secondNote.getId(), secondNote.getPath(), bucketName, basePath))).isNull();
    assertThat(storage.get(makeBlobId(secondNote.getId(), "/folder_new/sub_folder/test_note_second", bucketName, basePath))).isNotNull();
  }

  private String makeName(String relativePath, Optional<String> basePath) {
    if (basePath.isPresent()) {
      return basePath.get() + "/" + relativePath;
    } else {
      return relativePath;
    }
  }

  private BlobId makeBlobId(String noteId, String notePath, String bucketName, Optional<String> basePath) {
    if (basePath.isPresent()) {
      return BlobId.of(bucketName, basePath.get() + notePath + "_" + noteId +".zpln");
    } else {
      return BlobId.of(bucketName, notePath.substring(1) + "_" + noteId +".zpln");
    }
  }

  private void createAt(Note note, String relativePath, String bucketName, Optional<String> basePath) throws IOException {
    BlobId id = BlobId.of(bucketName, makeName(relativePath, basePath));
    BlobInfo info = BlobInfo.newBuilder(id).setContentType("application/json").build();
    storage.create(info, note.toJson().getBytes("UTF-8"));
  }

  private void create(Note note, String bucketName, Optional<String> basePath) throws IOException {
    BlobInfo info = BlobInfo.newBuilder(makeBlobId(note.getId(), note.getPath(), bucketName, basePath))
        .setContentType("application/json")
        .build();
    storage.create(info, note.toJson().getBytes("UTF-8"));
  }

  private void createMalformed(String noteId, String notePath, String bucketName, Optional<String> basePath) throws IOException {
    BlobInfo info = BlobInfo.newBuilder(makeBlobId(noteId, notePath, bucketName, basePath))
        .setContentType("application/json")
        .build();
    storage.create(info, "{ invalid-json }".getBytes("UTF-8"));
  }

  /* These tests test path parsing for illegal paths, and do not use the parameterized vars */

  @Test
  void testInitialization_pathNotSet() throws Exception {
    try {
      System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_GCS_STORAGE_DIR.getVarName(), "");
      new GCSNotebookRepo(ZeppelinConfiguration.create(), storage);
      fail();
    } catch (IOException e) {}
  }

  @Test
  void testInitialization_malformedPath() throws Exception {
    try {
      System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_GCS_STORAGE_DIR.getVarName(), "foo");
      new GCSNotebookRepo(ZeppelinConfiguration.create(), storage);
      fail();
    } catch (IOException e) {}
  }
}
