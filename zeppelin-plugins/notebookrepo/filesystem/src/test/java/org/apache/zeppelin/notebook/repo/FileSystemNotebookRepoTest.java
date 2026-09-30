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
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.GsonNoteParser;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.NoteParser;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSystemNotebookRepoTest {

  private ZeppelinConfiguration zConf;
  private Configuration hadoopConf;
  private FileSystem fs;
  private FileSystemNotebookRepo hdfsNotebookRepo;
  private String notebookDir;
  private AuthenticationInfo authInfo = AuthenticationInfo.ANONYMOUS;
  private NoteParser noteParser;

  @BeforeEach
  void setUp() throws IOException {
    notebookDir = Files.createTempDirectory("FileSystemNotebookRepoTest").toFile().getAbsolutePath();
    zConf = ZeppelinConfiguration.load();
    noteParser = new GsonNoteParser(zConf);
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(),
        notebookDir);
    hadoopConf = new Configuration();
    fs = FileSystem.get(hadoopConf);
    hdfsNotebookRepo = new FileSystemNotebookRepo();
    hdfsNotebookRepo.init(zConf, noteParser);
  }

  @AfterEach
  void tearDown() throws IOException {
    FileUtils.deleteDirectory(new File(notebookDir));
  }

  @Test
  void testBasics() throws IOException {
    assertEquals(0, hdfsNotebookRepo.list(authInfo).size());

    // create a new note
    Note note = new Note();
    note.setZeppelinConfiguration(zConf);
    note.setNoteParser(noteParser);
    note.setPath("/title_1");

    Map<String, Object> config = new HashMap<>();
    config.put("config_1", "value_1");
    config.put("isZeppelinNotebookCronEnable", false);
    note.setConfig(config);
    hdfsNotebookRepo.save(note, authInfo);
    assertEquals(1, hdfsNotebookRepo.list(authInfo).size());

    // read this note from hdfs
    Note note_copy = hdfsNotebookRepo.get(note.getId(), note.getPath(), authInfo);
    assertEquals(note.getName(), note_copy.getName());
    assertEquals(note.getConfig(), note_copy.getConfig());

    // update this note
    note.setPersonalizedMode(true);
    hdfsNotebookRepo.save(note, authInfo);
    assertEquals(1, hdfsNotebookRepo.list(authInfo).size());
    note_copy = hdfsNotebookRepo.get(note.getId(), note.getPath(), authInfo);
    assertEquals(note.getName(), note_copy.getName());
    assertEquals(note.getConfig(), note_copy.getConfig());

    // move this note
    String newPath = "/new_folder/title_1";
    hdfsNotebookRepo.move(note.getId(), note.getPath(), newPath, authInfo);
    assertEquals(1, hdfsNotebookRepo.list(authInfo).size());
    assertEquals("title_1", hdfsNotebookRepo.get(note.getId(), newPath, authInfo).getName());

    // delete this note
    hdfsNotebookRepo.remove(note.getId(), newPath, authInfo);
    assertEquals(0, hdfsNotebookRepo.list(authInfo).size());

    // create another new note under folder
    note = new Note();
    note.setZeppelinConfiguration(zConf);
    note.setNoteParser(noteParser);
    note.setPath("/folder1/title_1");
    note.setConfig(config);
    hdfsNotebookRepo.save(note, authInfo);
    assertEquals(1, hdfsNotebookRepo.list(authInfo).size());

    hdfsNotebookRepo.move("/folder1", "/folder2/folder3", authInfo);
    Map<String, NoteInfo> notesInfo = hdfsNotebookRepo.list(authInfo);
    assertEquals(1, notesInfo.size());

    assertEquals("/folder2/folder3/title_1", notesInfo.get(note.getId()).getPath());

    // delete folder
    hdfsNotebookRepo.remove("/folder2", authInfo);
    assertEquals(0, hdfsNotebookRepo.list(authInfo).size());
  }

  @Test
  void testComplicatedScenarios() throws IOException {
    // scenario_1: notebook_dir is not clean. There're some unrecognized dir and file under notebook_dir
    fs.mkdirs(new Path(notebookDir, "1/2"));
    OutputStream out = fs.create(new Path(notebookDir, "1/a.json"));
    out.close();

    assertEquals(0, hdfsNotebookRepo.list(authInfo).size());

    // scenario_2: note_folder is existed.
    // create a new note
    Note note = new Note();
    note.setZeppelinConfiguration(zConf);
    note.setNoteParser(noteParser);
    note.setPath("/title_1");
    Map<String, Object> config = new HashMap<>();
    config.put("config_1", "value_1");
    note.setConfig(config);

    hdfsNotebookRepo.save(note, authInfo);
    assertEquals(1, hdfsNotebookRepo.list(authInfo).size());
  }

  @Test
  void testSaveLeavesNoTempOrBackupFile() throws IOException {
    Note note = createNote("/title_1", "value_1");
    hdfsNotebookRepo.save(note, authInfo);
    note.getConfig().put("config_1", "value_2");
    hdfsNotebookRepo.save(note, authInfo);

    assertEquals("value_2", getConfigValue(note));
    try (Stream<java.nio.file.Path> files = Files.list(Paths.get(notebookDir))) {
      assertTrue(files.allMatch(f -> f.getFileName().toString().endsWith(".zpln")));
    }
  }

  @Test
  void testRecoverFromTempFileWhenSaveStoppedBeforeLastRename() throws IOException {
    Note note = createNote("/title_1", "value_1");
    hdfsNotebookRepo.save(note, authInfo);
    // The original was moved to .bak and the new content fully written to .tmp
    note.getConfig().put("config_1", "value_2");
    Files.move(noteFile(note, ""), noteFile(note, ".bak"));
    writeString(noteFile(note, ".tmp"), note.toJson());

    restartRepo();

    assertEquals(1, hdfsNotebookRepo.list(authInfo).size());
    assertEquals("value_2", getConfigValue(note));
    assertFalse(Files.exists(noteFile(note, ".bak")));
  }

  @Test
  void testRecoverFromBackupFileWhenTempFileIsIncomplete() throws IOException {
    Note note = createNote("/title_1", "value_1");
    hdfsNotebookRepo.save(note, authInfo);
    Files.move(noteFile(note, ""), noteFile(note, ".bak"));
    String json = note.toJson();
    writeString(noteFile(note, ".tmp"), json.substring(0, json.length() / 2));

    restartRepo();

    assertEquals(1, hdfsNotebookRepo.list(authInfo).size());
    assertEquals("value_1", getConfigValue(note));
  }

  @Test
  void testDoNotRecoverFromTempFileOnly() throws IOException {
    // A lone .tmp cannot be told apart from a leftover of a deleted note
    Note note = createNote("/title_1", "value_1");
    writeString(noteFile(note, ".tmp"), note.toJson());

    restartRepo();

    assertEquals(0, hdfsNotebookRepo.list(authInfo).size());
    assertTrue(Files.exists(noteFile(note, ".tmp")));
  }

  @Test
  void testRemovedNoteIsNotRestoredFromLeftoverTmp() throws IOException {
    Note note = createNote("/title_1", "value_1");
    hdfsNotebookRepo.save(note, authInfo);
    writeString(noteFile(note, ".tmp"), note.toJson());
    hdfsNotebookRepo.remove(note.getId(), note.getPath(), authInfo);

    restartRepo();

    assertEquals(0, hdfsNotebookRepo.list(authInfo).size());
  }

  @Test
  void testMovedNoteIsNotDuplicatedFromLeftoverTmp() throws IOException {
    Note note = createNote("/title_1", "value_1");
    hdfsNotebookRepo.save(note, authInfo);
    writeString(noteFile(note, ".tmp"), note.toJson());
    hdfsNotebookRepo.move(note.getId(), "/title_1", "/dir/title_2", authInfo);

    restartRepo();

    long zplnCount;
    try (Stream<java.nio.file.Path> files = Files.walk(Paths.get(notebookDir))) {
      zplnCount = files.filter(f -> f.toString().endsWith(".zpln")).count();
    }
    assertEquals(1, zplnCount);
  }

  @Test
  void testKeepExistingNoteWhenTempFileIsLeftOver() throws IOException {
    Note note = createNote("/title_1", "value_1");
    hdfsNotebookRepo.save(note, authInfo);
    writeString(noteFile(note, ".tmp"), "{");

    restartRepo();

    assertEquals(1, hdfsNotebookRepo.list(authInfo).size());
    assertEquals("value_1", getConfigValue(note));
    assertTrue(Files.exists(noteFile(note, ".tmp")));
    assertFalse(Files.exists(noteFile(note, ".bak")));
  }

  private Note createNote(String path, String configValue) {
    Note note = new Note();
    note.setZeppelinConfiguration(zConf);
    note.setNoteParser(noteParser);
    note.setPath(path);
    Map<String, Object> config = new HashMap<>();
    config.put("config_1", configValue);
    note.setConfig(config);
    return note;
  }

  private java.nio.file.Path noteFile(Note note, String suffix) throws IOException {
    return Paths.get(notebookDir,
        hdfsNotebookRepo.buildNoteFileName(note.getId(), note.getPath()) + suffix);
  }

  private void writeString(java.nio.file.Path file, String content) throws IOException {
    Files.write(file, content.getBytes(StandardCharsets.UTF_8));
  }

  private Object getConfigValue(Note note) throws IOException {
    return hdfsNotebookRepo.get(note.getId(), note.getPath(), authInfo)
        .getConfig().get("config_1");
  }

  private void restartRepo() throws IOException {
    hdfsNotebookRepo = new FileSystemNotebookRepo();
    hdfsNotebookRepo.init(zConf, noteParser);
  }
}
