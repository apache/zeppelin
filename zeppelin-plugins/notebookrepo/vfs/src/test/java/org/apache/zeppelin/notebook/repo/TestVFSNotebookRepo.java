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

import com.google.common.collect.ImmutableMap;
import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class TestVFSNotebookRepo {

  private ZeppelinConfiguration zConf;
  private VFSNotebookRepo notebookRepo;
  private String notebookDir = "/tmp/zeppelin/vfs_notebookrepo/";

  @Before
  public void setUp() throws IOException {
    notebookRepo = new VFSNotebookRepo();
    FileUtils.forceMkdir(new File(notebookDir));
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(), notebookDir);
    zConf = new ZeppelinConfiguration();
    notebookRepo.init(zConf);
  }

  @After
  public void tearDown() throws IOException {
    FileUtils.deleteDirectory(new File(notebookDir));
  }

  @Test
  public void testBasics() throws IOException {
    assertEquals(0, notebookRepo.list(AuthenticationInfo.ANONYMOUS).size());

    Note note1 = new Note();
    Paragraph p1 = note1.insertNewParagraph(0, AuthenticationInfo.ANONYMOUS);
    p1.setText("%md hello world");
    p1.setTitle("my title");
    notebookRepo.save(note1, AuthenticationInfo.ANONYMOUS);

    assertEquals(1, notebookRepo.list(AuthenticationInfo.ANONYMOUS).size());
    Note note2 = notebookRepo.get(note1.getId(), AuthenticationInfo.ANONYMOUS);
    assertEquals(note1.getParagraphCount(), note2.getParagraphCount());

    Paragraph p2 = note2.getParagraph(p1.getId());
    assertEquals(p1.getText(), p2.getText());
    assertEquals(p1.getTitle(), p2.getTitle());

    notebookRepo.remove(note1.getId(), AuthenticationInfo.ANONYMOUS);
    assertEquals(0, notebookRepo.list(AuthenticationInfo.ANONYMOUS).size());
  }

  @Test
  public void testInvalidJson() throws IOException {
    assertEquals(0, notebookRepo.list(AuthenticationInfo.ANONYMOUS).size());

    // invalid note will be ignored
    createNewNote("invalid_content", "id_1");
    assertEquals(0, notebookRepo.list(AuthenticationInfo.ANONYMOUS).size());

    // only valid note will be fetched
    createNewNote("{}", "id_2");
    assertEquals(1, notebookRepo.list(AuthenticationInfo.ANONYMOUS).size());
  }

  @Test
  public void testUpdateSettings() throws IOException {
    List<NotebookRepoSettingsInfo> repoSettings = notebookRepo.getSettings(AuthenticationInfo.ANONYMOUS);
    assertEquals(1, repoSettings.size());
    NotebookRepoSettingsInfo settingInfo = repoSettings.get(0);
    assertEquals("Notebook Path", settingInfo.name);
    assertEquals(notebookDir, settingInfo.selected);

    createNewNote("{}", "id_2");
    assertEquals(1, notebookRepo.list(AuthenticationInfo.ANONYMOUS).size());

    String newNotebookDir = "/tmp/zeppelin/vfs_notebookrepo2";
    FileUtils.forceMkdir(new File(newNotebookDir));
    Map<String, String> newSettings = ImmutableMap.of("Notebook Path", newNotebookDir);
    notebookRepo.updateSettings(newSettings, AuthenticationInfo.ANONYMOUS);
    assertEquals(0, notebookRepo.list(AuthenticationInfo.ANONYMOUS).size());
  }

  private void createNewNote(String content, String noteId) throws IOException {
    FileUtils.writeStringToFile(new File(notebookDir + "/" + noteId, "note.json"), content);
  }
}
