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

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.repo.storage.RemoteStorageOperator;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class OSSNotebookRepoTest {

  private AuthenticationInfo anonymous = AuthenticationInfo.ANONYMOUS;
  private OSSNotebookRepo notebookRepo;
  private RemoteStorageOperator ossOperator;
  private String bucket;
  private static int OSS_VERSION_MAX = 30;



  @Before
  public void setUp() throws IOException {
    bucket = "zeppelin-test-bucket";
    String endpoint = "yourEndpoint";
    String accessKeyId = "yourAccessKeyId";
    String accessKeySecret = "yourAccessKeySecret";
    ossOperator = new MockStorageOperator();
    ossOperator.createBucket(bucket);
    notebookRepo = new OSSNotebookRepo();
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_OSS_ENDPOINT.getVarName(),
            endpoint);
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_OSS_BUCKET.getVarName(),
            bucket);
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_OSS_ACCESSKEYID.getVarName(),
            accessKeyId);
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_OSS_ACCESSKEYSECRET.getVarName(),
            accessKeySecret);
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_OSS_VERSION_MAX.getVarName(),
            OSS_VERSION_MAX + "");
    notebookRepo.init(conf);
    notebookRepo.setOssOperator(ossOperator);
  }

  @After
  public void tearDown() throws InterruptedException, IOException {
    if (notebookRepo != null) {
      notebookRepo.close();
    }
    ossOperator.deleteDir(bucket, "");
    ossOperator.deleteBucket(bucket);
    // The delete operations on OSS Service above has a delay.
    // And it would affect setup of next test case if we do not wait for them to end.
    Thread.sleep(1000);
  }

  @Test
  public void testNotebookRepo() throws IOException {
    Map<String, NoteInfo> notesInfo = notebookRepo.list(anonymous);
    assertEquals(0, notesInfo.size());

    // create Note note1
    Note note1 = new Note();
    note1.setPath("/spark/note_1");
    notebookRepo.save(note1, anonymous);

    //
    for (int i = 1; i <= OSS_VERSION_MAX + 3; i++) {
      Paragraph p = new Paragraph(note1, null);
      p.setText("text" + i);
      p.setStatus(Job.Status.RUNNING);
      p.setAuthenticationInfo(new AuthenticationInfo("anonymous", (String) null, "anonymous"));
      note1.addParagraph(p);
      notebookRepo.save(note1, anonymous);
      notebookRepo.checkpoint(note1.getId(), note1.getPath(), "commit " + i, anonymous);
    }

    notesInfo = notebookRepo.list(anonymous);
    assertEquals(1, notesInfo.size());
    assertEquals("/spark/note_1", notesInfo.get(note1.getId()).getPath());

    // Get note1
    Note noteFromRepo = notebookRepo.get(note1.getId(), note1.getPath(), anonymous);
    assertEquals(note1.getName(), noteFromRepo.getName());

    // Get non-existed note
    try {
      notebookRepo.get("invalid_id", "/invalid_path", anonymous);
      fail("Should fail to get non-existed note1");
    } catch (IOException e) {
      assertEquals(e.getMessage(), "Note or its revision not found");
    }

    // create another Note note2
    Note note2 = new Note();
    note2.setPath("/spark/note_2");
    notebookRepo.save(note2, anonymous);

    notesInfo = notebookRepo.list(anonymous);
    assertEquals(2, notesInfo.size());
    assertEquals("/spark/note_1", notesInfo.get(note1.getId()).getPath());
    assertEquals("/spark/note_2", notesInfo.get(note2.getId()).getPath());

    // move note1
    notebookRepo.move(note1.getId(), note1.getPath(), "/spark2/note_1", anonymous);

    notesInfo = notebookRepo.list(anonymous);
    assertEquals(2, notesInfo.size());
    assertEquals("/spark2/note_1", notesInfo.get(note1.getId()).getPath());
    assertEquals("/spark/note_2", notesInfo.get(note2.getId()).getPath());

    // move folder
    notebookRepo.move("/spark2", "/spark3", anonymous);

    notesInfo = notebookRepo.list(anonymous);
    assertEquals(2, notesInfo.size());
    assertEquals("/spark3/note_1", notesInfo.get(note1.getId()).getPath());
    assertEquals("/spark/note_2", notesInfo.get(note2.getId()).getPath());

    // delete note
    notebookRepo.remove(note1.getId(), notesInfo.get(note1.getId()).getPath(), anonymous);
    notesInfo = notebookRepo.list(anonymous);
    assertEquals(1, notesInfo.size());
    assertEquals("/spark/note_2", notesInfo.get(note2.getId()).getPath());

    // delete folder
    notebookRepo.remove("/spark", anonymous);
    notesInfo = notebookRepo.list(anonymous);
    assertEquals(0, notesInfo.size());
  }


  @Test
  public void testNotebookRepoWithVersionControl() throws IOException {
    Map<String, NoteInfo> notesInfo = notebookRepo.list(anonymous);
    assertEquals(0, notesInfo.size());

    // create Note note1
    Note note1 = new Note();
    note1.setPath("/version_control/note_1");

    List<NotebookRepoWithVersionControl.Revision> revisionList = new ArrayList<>();

    for (int i = 1; i <= OSS_VERSION_MAX + 3; i++) {
      Paragraph p = new Paragraph(note1, null);
      p.setText("text" + i);
      p.setStatus(Job.Status.RUNNING);
      p.setAuthenticationInfo(new AuthenticationInfo("anonymous", (String) null, "anonymous"));
      note1.addParagraph(p);
      notebookRepo.save(note1, anonymous);

      // checkpoint
      NotebookRepoWithVersionControl.Revision revision = notebookRepo.checkpoint(note1.getId(), note1.getPath(), "commit " + i, anonymous);
      revisionList.add(revision);

      List<NotebookRepoWithVersionControl.Revision> revisionsHistory = notebookRepo.revisionHistory(note1.getId(), note1.getPath(), anonymous);
      // verify OSS_VERSION_MAX control
      if (i <= OSS_VERSION_MAX) {
        assertEquals(i, revisionsHistory.size());
      } else {
        assertEquals(OSS_VERSION_MAX, revisionsHistory.size());
      }
    }

    // get note by non-existed revisionId
    for (int i = 1; i <= 3; i++) {
      try {
        notebookRepo.get(note1.getId(), note1.getPath(), revisionList.get(i - 1).id, anonymous);
        fail("Should fail to get non-existed note1");
      } catch (IOException e) {
        assertEquals(e.getMessage(), "Note or its revision not found");
      }
    }

    // get note by existed revisionId
    for (int i = 4; i <= OSS_VERSION_MAX + 3; i++) {
      Note note = notebookRepo.get(note1.getId(), note1.getPath(), revisionList.get(i - 1).id, anonymous);
      assertEquals(i, note.getParagraphs().size());
    }

    // revisionsHistory
    List<NotebookRepoWithVersionControl.Revision> revisionsHistory = notebookRepo.revisionHistory(note1.getId(), note1.getPath(), anonymous);
    for (int i = 0; i < revisionsHistory.size(); i++) {
      assertEquals(revisionsHistory.get(i).id, revisionList.get(revisionList.size() - i - 1).id);
      assertEquals(revisionsHistory.get(i).message, revisionList.get(revisionList.size() - i - 1).message);
      assertEquals(revisionsHistory.get(i).time, revisionList.get(revisionList.size() - i - 1).time);
    }


    // Modify note to distinguish itself with last version
    Paragraph p = new Paragraph(note1, null);
    p.setText("text" + OSS_VERSION_MAX + 4);
    p.setStatus(Job.Status.RUNNING);
    p.setAuthenticationInfo(new AuthenticationInfo("anonymous", (String) null, "anonymous"));
    note1.addParagraph(p);
    notebookRepo.save(note1, anonymous);

    assertEquals(notebookRepo.get(note1.getId(), note1.getPath(), anonymous).getParagraphs().size(), OSS_VERSION_MAX + 4);

    // Assume OSS_VERSION_MAX = 30
    // revert note to revision 31 , then to revision 32, then to revision 33, finally to revision 31
    for (int i = OSS_VERSION_MAX + 1; i <= OSS_VERSION_MAX + 3; i++) {
      notebookRepo.setNoteRevision(note1.getId(), note1.getPath(), revisionList.get(i - 1).id, anonymous);
      assertEquals(notebookRepo.get(note1.getId(), note1.getPath(), anonymous).getParagraphs().size(), i);
    }

    // finally revert note to revision 31
    notebookRepo.setNoteRevision(note1.getId(), note1.getPath(), revisionList.get(OSS_VERSION_MAX).id, anonymous);
    assertEquals(notebookRepo.get(note1.getId(), note1.getPath(), anonymous).getParagraphs().size(), OSS_VERSION_MAX + 1);

    notebookRepo.remove("/version_control", anonymous);
  }
}