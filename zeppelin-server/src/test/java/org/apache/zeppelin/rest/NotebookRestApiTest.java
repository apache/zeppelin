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
package org.apache.zeppelin.rest;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.repo.NotebookRepoWithVersionControl;
import org.apache.zeppelin.rest.message.ParametersRequest;
import org.apache.zeppelin.socket.NotebookServer;
import org.apache.zeppelin.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.user.AuthenticationInfo;

/**
 * Zeppelin notebook rest api tests.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class NotebookRestApiTest extends AbstractTestRestApi {
  Gson gson = new Gson();
  AuthenticationInfo anonymous;

  @BeforeClass
  public static void init() throws Exception {
    startUp(NotebookRestApiTest.class.getSimpleName());
    TestUtils.getInstance(Notebook.class).setParagraphJobListener(NotebookServer.getInstance());
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  @Before
  public void setUp() {
    anonymous = new AuthenticationInfo("anonymous");
  }

  @Test
  public void testGetReloadNote() throws IOException {
    LOG.info("Running testGetNote");
    String note1Id = null;
    try {
      note1Id = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          TestUtils.getInstance(Notebook.class).saveNote(note1, anonymous);
          return null;
        });

      CloseableHttpResponse get = httpGet("/notebook/" + note1Id);
      assertThat(get, isAllowed());
      Map<String, Object> resp = gson.fromJson(EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {}.getType());
      Map<String, Object> noteObject = (Map<String, Object>) resp.get("body");
      assertEquals(1, ((List)noteObject.get("paragraphs")).size());

      // add one new paragraph, but don't save it and reload it again
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          return null;
        });

      get = httpGet("/notebook/" + note1Id + "?reload=true");
      assertThat(get, isAllowed());
      resp = gson.fromJson(EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {}.getType());
      noteObject = (Map<String, Object>) resp.get("body");
      assertEquals(1, ((List)noteObject.get("paragraphs")).size());
      get.close();
    } finally {
      // cleanup
      if (null != note1Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note1Id, anonymous);
      }
    }
  }

  @Test
  public void testGetNoteByPath() throws IOException {
    LOG.info("Running testGetNoteByPath");
    String note1Id = null;
    try {
        String notePath = "dir1/note1";
        note1Id = TestUtils.getInstance(Notebook.class).createNote(notePath, anonymous);
        TestUtils.getInstance(Notebook.class).processNote(note1Id,
                note1 -> {
                    note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
                    TestUtils.getInstance(Notebook.class).saveNote(note1, anonymous);
                    return null;
                });

        CloseableHttpResponse post = httpPost("/notebook/getByPath" , "{\"notePath\":\""+ notePath + "\"}" );

        assertThat(post, isAllowed());
        Map<String, Object> resp = gson.fromJson(EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8),
                new TypeToken<Map<String, Object>>() {}.getType());
        Map<String, Object> noteObject = (Map<String, Object>) resp.get("body");
        assertEquals(notePath, ((String)noteObject.get("path")).substring(1));
        post.close();
    } finally {
        // cleanup
        if (null != note1Id) {
            TestUtils.getInstance(Notebook.class).removeNote(note1Id, anonymous);
        }
    }
  }

  @Test
  public void testGetNoteByPathWithPathNotExist() throws IOException {
    LOG.info("Running testGetNoteByPathWithPathNotExist");
    String notePath = "A note that doesn't exist";
    CloseableHttpResponse post = httpPost("/notebook/getByPath" , "{\"notePath\":\""+ notePath + "\"}" );
    assertThat(post, isNotFound());
    Map<String, Object> resp = gson.fromJson(EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8),
            new TypeToken<Map<String, Object>>() {}.getType());
    String status = (String) resp.get("status");
    assertEquals(status, "NOT_FOUND");
    post.close();
  }

  @Test
  public void testGetNoteRevisionHistory() throws IOException {
    LOG.info("Running testGetNoteRevisionHistory");
    String note1Id = null;
    Notebook notebook = TestUtils.getInstance(Notebook.class);
    try {
      String notePath = "note1";
      note1Id = notebook.createNote(notePath, anonymous);

      //Add a paragraph and commit
      NotebookRepoWithVersionControl.Revision first_commit =
              notebook.processNote(note1Id, note -> {
                Paragraph p1 = note.addNewParagraph(anonymous);
                p1.setText("text1");
                notebook.saveNote(note, AuthenticationInfo.ANONYMOUS);
                return notebook.checkpointNote(note.getId(), note.getPath(), "first commit", anonymous);
              });

      //Add a paragraph again
      notebook.processNote(note1Id, note -> {
        Paragraph p2 = note.addNewParagraph(anonymous);
        p2.setText("text2");
        notebook.saveNote(note, AuthenticationInfo.ANONYMOUS);
        return null;
      });

      // Verify
      CloseableHttpResponse get1 = httpGet("/notebook/" + note1Id + "/revision");

      assertThat(get1, isAllowed());
      Map<String, Object> resp = gson.fromJson(EntityUtils.toString(get1.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {
              }.getType());
      List<Map<String, Object>> body = (List<Map<String, Object>>) resp.get("body");
      assertEquals(1, body.size());
      assertEquals(first_commit.id, body.get(0).get("id"));
      get1.close();

      // Second commit
      NotebookRepoWithVersionControl.Revision second_commit = notebook.processNote(note1Id, note -> notebook.checkpointNote(note.getId(), note.getPath(), "Second commit", anonymous));

      // Verify
      CloseableHttpResponse get2 = httpGet("/notebook/" + note1Id + "/revision");

      assertThat(get2, isAllowed());
      resp = gson.fromJson(EntityUtils.toString(get2.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {
              }.getType());
      body = (List<Map<String, Object>>) resp.get("body");
      assertEquals(2, body.size());
      assertEquals(second_commit.id, body.get(0).get("id"));
      get2.close();

    } finally {
      // cleanup
      if (null != note1Id) {
        notebook.removeNote(note1Id, anonymous);
      }
    }
  }

  @Test
  public void testGetNoteByRevision() throws IOException {
    LOG.info("Running testGetNoteByRevision");
    String note1Id = null;
    Notebook notebook = TestUtils.getInstance(Notebook.class);
    try {
      String notePath = "note1";
      note1Id = notebook.createNote(notePath, anonymous);

      //Add a paragraph and commit
      NotebookRepoWithVersionControl.Revision first_commit =
              notebook.processNote(note1Id, note -> {
                Paragraph p1 = note.addNewParagraph(anonymous);
                p1.setText("text1");
                notebook.saveNote(note, AuthenticationInfo.ANONYMOUS);
                return notebook.checkpointNote(note.getId(), note.getPath(), "first commit", anonymous);
              });

      //Add a paragraph again
      notebook.processNote(note1Id, note -> {
        Paragraph p2 = note.addNewParagraph(anonymous);
        p2.setText("text2");
        notebook.saveNote(note, AuthenticationInfo.ANONYMOUS);
        return null;
      });

      // Verify
      CloseableHttpResponse get = httpGet("/notebook/" + note1Id + "/revision/" + first_commit.id);

      assertThat(get, isAllowed());
      Map<String, Object> resp = gson.fromJson(EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {
              }.getType());
      Map<String, Object> noteObject = (Map<String, Object>) resp.get("body");
      assertEquals(1, ((List) noteObject.get("paragraphs")).size());
      assertEquals("text1", ((List<Map<String, String>>) noteObject.get("paragraphs")).get(0).get("text"));
      get.close();
    } finally {
      // cleanup
      if (null != note1Id) {
        notebook.removeNote(note1Id, anonymous);
      }
    }
  }

  @Test
  public void testGetNoteParagraphJobStatus() throws IOException {
    LOG.info("Running testGetNoteParagraphJobStatus");
    String note1Id = null;
    try {
      note1Id = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      String paragraphId = TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          return note1.addNewParagraph(AuthenticationInfo.ANONYMOUS).getId();
        });

      CloseableHttpResponse get = httpGet("/notebook/job/" + note1Id + "/" + paragraphId);
      assertThat(get, isAllowed());
      Map<String, Object> resp = gson.fromJson(EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {}.getType());
      Map<String, Set<String>> paragraphStatus = (Map<String, Set<String>>) resp.get("body");

      // Check id and status have proper value
      assertEquals(paragraphStatus.get("id"), paragraphId);
      assertEquals(paragraphStatus.get("status"), "READY");
      get.close();
    } finally {
      // cleanup
      if (null != note1Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note1Id, anonymous);
      }
    }
  }

  @Test
  public void testCheckpointNote() throws IOException {
    LOG.info("Running testCheckpointNote");
    String note1Id = null;
    Notebook notebook = TestUtils.getInstance(Notebook.class);
    try {
      String notePath = "note1";
      note1Id = notebook.createNote(notePath, anonymous);

      //Add a paragraph
      notebook.processNote(note1Id, note -> {
        Paragraph p1 = note.addNewParagraph(anonymous);
        p1.setText("text1");
        notebook.saveNote(note, AuthenticationInfo.ANONYMOUS);
        return null;
      });

      // Call restful api to save a revision and verify
      String commitMessage = "first commit";
      CloseableHttpResponse post = httpPost("/notebook/" + note1Id + "/revision", "{\"commitMessage\" : \"" + commitMessage + "\"}");

      assertThat(post, isAllowed());
      Map<String, Object> resp = gson.fromJson(EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {
              }.getType());
      assertEquals("OK", resp.get("status"));
      String revisionId = (String) resp.get("body");
      notebook.processNote(note1Id, note -> {
        Note revisionOfNote = notebook.getNoteByRevision(note.getId(), note.getPath(), revisionId, anonymous);
        assertEquals(1, notebook.listRevisionHistory(note.getId(), note.getPath(), anonymous).size());
        assertEquals(1, revisionOfNote.getParagraphs().size());
        assertEquals("text1", revisionOfNote.getParagraph(0).getText());
        return null;
      });
      post.close();
    } finally {
      // cleanup
      if (null != note1Id) {
        notebook.removeNote(note1Id, anonymous);
      }
    }
  }


  @Test
  public void testSetNoteRevision() throws IOException {
    LOG.info("Running testSetNoteRevision");
    String note1Id = null;
    Notebook notebook = TestUtils.getInstance(Notebook.class);
    try {
      String notePath = "note1";
      note1Id = notebook.createNote(notePath, anonymous);

      //Add a paragraph and commit
      NotebookRepoWithVersionControl.Revision first_commit =
              notebook.processNote(note1Id, note -> {
                Paragraph p1 = note.addNewParagraph(anonymous);
                p1.setText("text1");
                notebook.saveNote(note, AuthenticationInfo.ANONYMOUS);
                return notebook.checkpointNote(note.getId(), note.getPath(), "first commit", anonymous);
              });

      //Add a paragraph again
      notebook.processNote(note1Id, note -> {
        Paragraph p2 = note.addNewParagraph(anonymous);
        p2.setText("text2");
        notebook.saveNote(note, AuthenticationInfo.ANONYMOUS);
        return null;
      });

      // Call restful api to revert note to first revision and verify
      CloseableHttpResponse put = httpPut("/notebook/" + note1Id + "/revision/" + first_commit.id, "");

      assertThat(put, isAllowed());
      Map<String, Object> resp = gson.fromJson(EntityUtils.toString(put.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {
              }.getType());
      assertEquals("OK", resp.get("status"));
      notebook.processNote(note1Id, note -> {
        assertEquals(1, note.getParagraphs().size());
        assertEquals("text1", note.getParagraph(0).getText());
        return null;
      });
      put.close();
    } finally {
      // cleanup
      if (null != note1Id) {
        notebook.removeNote(note1Id, anonymous);
      }
    }
  }


  @Test
  public void testRunParagraphJob() throws Exception {
    LOG.info("Running testRunParagraphJob");
    String note1Id = null;
    try {
      note1Id = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      Paragraph p = TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          return note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        });


      // run blank paragraph
      CloseableHttpResponse post = httpPost("/notebook/job/" + note1Id + "/" + p.getId(), "");
      assertThat(post, isAllowed());
      Map<String, Object> resp = gson.fromJson(EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {}.getType());
      assertEquals("OK", resp.get("status"));
      post.close();
      p.waitUntilFinished();
      assertEquals(Job.Status.FINISHED, p.getStatus());

      // run non-blank paragraph
      p.setText("test");
      post = httpPost("/notebook/job/" + note1Id + "/" + p.getId(), "");
      assertThat(post, isAllowed());
      resp = gson.fromJson(EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {}.getType());
      assertEquals("OK", resp.get("status"));
      post.close();
      p.waitUntilFinished();
      assertNotEquals(Job.Status.FINISHED, p.getStatus());
    } finally {
      // cleanup
      if (null != note1Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note1Id, anonymous);
      }
    }
  }

  @Test
  public void testCancelNoteJob() throws Exception {
    LOG.info("Running testCancelNoteJob");
    String note1Id = null;
    Notebook notebook = TestUtils.getInstance(Notebook.class);
    try {
      note1Id = notebook.createNote("note1", anonymous);
      // Add 3 paragraphs for the note.
      List<Paragraph> paragraphs = notebook.processNote(note1Id,
              note1 -> {
                List<Paragraph> paragraphsList = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                  Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
                  p1.setText("%python\nimport time\ntime.sleep(10)\nprint('done')");
                  note1.run(p1.getId());
                  paragraphsList.add(p1);
                }
                return paragraphsList;
              });
      //The first paragraph is running, and the other two is pending.
      paragraphs.get(0).waitUntilRunning();


      // cancel running note
      CloseableHttpResponse delete = httpDelete("/notebook/job/" + note1Id);
      assertThat(delete, isAllowed());
      Map<String, Object> resp = gson.fromJson(EntityUtils.toString(delete.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {
              }.getType());
      assertEquals("OK", resp.get("status"));
      delete.close();
      for (Paragraph p : paragraphs) {
        p.waitUntilFinished();
        assertEquals(Job.Status.ABORT, p.getStatus());
      }

    } finally {
      // cleanup
      if (null != note1Id) {
        notebook.removeNote(note1Id, anonymous);
      }
    }
  }

  @Test
  public void testRunParagraphSynchronously() throws IOException {
    LOG.info("Running testRunParagraphSynchronously");
    String note1Id = null;
    try {
      note1Id = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      Paragraph p = TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          return note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        });

      // run non-blank paragraph
      String title = "title";
      String text = "%sh\n sleep 1";
      p.setTitle(title);
      p.setText(text);

      CloseableHttpResponse post = httpPost("/notebook/run/" + note1Id + "/" + p.getId(), "");
      assertThat(post, isAllowed());
      Map<String, Object> resp = gson.fromJson(EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8),
          new TypeToken<Map<String, Object>>() {}.getType());
      assertEquals("OK", resp.get("status"));
      post.close();
      assertNotEquals(Job.Status.READY, p.getStatus());

      // Check if the paragraph is emptied
      assertEquals(title, p.getTitle());
      assertEquals(text, p.getText());

      // run invalid code
      text = "%sh\n invalid_cmd";
      p.setTitle(title);
      p.setText(text);

      post = httpPost("/notebook/run/" + note1Id + "/" + p.getId(), "");
      assertEquals(200, post.getStatusLine().getStatusCode());
      resp = gson.fromJson(EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {}.getType());
      assertEquals("OK", resp.get("status"));
      Map stringMap = (Map) resp.get("body");
      assertEquals("ERROR", stringMap.get("code"));
      List<Map> interpreterResults = (List<Map>) stringMap.get("msg");
      assertTrue(interpreterResults.get(0).toString(),
              interpreterResults.get(0).get("data").toString().contains("invalid_cmd: command not found"));
      post.close();
      assertNotEquals(Job.Status.READY, p.getStatus());

      // Check if the paragraph is emptied
      assertEquals(title, p.getTitle());
      assertEquals(text, p.getText());
    } finally {
      // cleanup
      if (null != note1Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note1Id, anonymous);
      }
    }
  }

  @Test
  public void testCreateNote() throws Exception {
    LOG.info("Running testCreateNote");
    String message1 = "{\n\t\"name\" : \"test1\",\n\t\"addingEmptyParagraph\" : true\n}";
    CloseableHttpResponse post1 = httpPost("/notebook/", message1);
    assertThat(post1, isAllowed());

    Map<String, Object> resp1 = gson.fromJson(EntityUtils.toString(post1.getEntity(), StandardCharsets.UTF_8),
            new TypeToken<Map<String, Object>>() {}.getType());
    assertEquals("OK", resp1.get("status"));

    String note1Id = (String) resp1.get("body");
    TestUtils.getInstance(Notebook.class).processNote(note1Id,
      note1 -> {
        assertEquals("test1", note1.getName());
        assertEquals(1, note1.getParagraphCount());
        assertNull(note1.getParagraph(0).getText());
        assertNull(note1.getParagraph(0).getTitle());
        return null;
      });


    String message2 = "{\n\t\"name\" : \"test2\"\n}";
    CloseableHttpResponse post2 = httpPost("/notebook/", message2);
    assertThat(post2, isAllowed());

    Map<String, Object> resp2 = gson.fromJson(EntityUtils.toString(post2.getEntity(), StandardCharsets.UTF_8),
            new TypeToken<Map<String, Object>>() {}.getType());
    assertEquals("OK", resp2.get("status"));

    String noteId2 = (String) resp2.get("body");
    Note note2 = TestUtils.getInstance(Notebook.class).processNote(noteId2,
      note -> {
        return note;
      });
    assertEquals("test2", note2.getName());
    assertEquals(0, note2.getParagraphCount());
  }

  @Test
  public void testRunNoteBlocking() throws IOException {
    LOG.info("Running testRunNoteBlocking");
    String note1Id = null;
    try {
      note1Id = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      // 2 paragraphs
      // P1:
      //    %python
      //    from __future__ import print_function
      //    import time
      //    time.sleep(1)
      //    user='abc'
      // P2:
      //    %python
      //    print(user)
      //
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          Paragraph p2 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          p1.setText("%python from __future__ import print_function\nimport time\ntime.sleep(1)\nuser='abc'");
          p2.setText("%python print(user)");
          return null;
        });

      CloseableHttpResponse post = httpPost("/notebook/job/" + note1Id + "?blocking=true", "");
      assertThat(post, isAllowed());
      Map<String, Object> resp = gson.fromJson(EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {}.getType());
      assertEquals("OK", resp.get("status"));
      post.close();

      TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.getParagraph(0);
          Paragraph p2 = note1.getParagraph(1);
          assertEquals(Job.Status.FINISHED, p1.getStatus());
          assertEquals(Job.Status.FINISHED, p2.getStatus());
          assertEquals("abc\n", p2.getReturn().message().get(0).getData());
          return null;
        });
    } finally {
      // cleanup
      if (null != note1Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note1Id, anonymous);
      }
    }
  }

  @Test
  public void testRunNoteNonBlocking() throws Exception {
    LOG.info("Running testRunNoteNonBlocking");
    String note1Id = null;
    try {
      note1Id = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      // 2 paragraphs
      // P1:
      //    %python
      //    import time
      //    time.sleep(5)
      //    name='hello'
      //    z.put('name', name)
      // P2:
      //    %%sh(interpolate=true)
      //    echo '{name}'
      //
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          Paragraph p2 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          p1.setText("%python import time\ntime.sleep(5)\nname='hello'\nz.put('name', name)");
          p2.setText("%sh(interpolate=true) echo '{name}'");
          return null;
        });

      CloseableHttpResponse post = httpPost("/notebook/job/" + note1Id + "?blocking=true", "");
      assertThat(post, isAllowed());
      Map<String, Object> resp = gson.fromJson(EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {}.getType());
      assertEquals("OK", resp.get("status"));
      post.close();

      TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.getParagraph(0);
          Paragraph p2 = note1.getParagraph(1);
          try {
            p1.waitUntilFinished();
            p2.waitUntilFinished();
          } catch (InterruptedException e) {
            fail();
          }
          assertEquals(Job.Status.FINISHED, p1.getStatus());
          assertEquals(Job.Status.FINISHED, p2.getStatus());
          assertEquals("hello\n", p2.getReturn().message().get(0).getData());
          return null;
        });
    } finally {
      // cleanup
      if (null != note1Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note1Id, anonymous);
      }
    }
  }

  @Test
  public void testRunNoteBlocking_Isolated() throws IOException {
    LOG.info("Running testRunNoteBlocking_Isolated");
    String note1Id = null;
    try {
      InterpreterSettingManager interpreterSettingManager =
              TestUtils.getInstance(InterpreterSettingManager.class);
      InterpreterSetting interpreterSetting = interpreterSettingManager.getInterpreterSettingByName("python");
      int pythonProcessNum = interpreterSetting.getAllInterpreterGroups().size();

      note1Id = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      // 2 paragraphs
      // P1:
      //    %python
      //    from __future__ import print_function
      //    import time
      //    time.sleep(1)
      //    user='abc'
      // P2:
      //    %python
      //    print(user)
      //
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          Paragraph p2 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          p1.setText("%python from __future__ import print_function\nimport time\ntime.sleep(1)\nuser='abc'");
          p2.setText("%python print(user)");
          return null;
        });

      CloseableHttpResponse post = httpPost("/notebook/job/" + note1Id + "?blocking=true&isolated=true", "");
      assertThat(post, isAllowed());
      Map<String, Object> resp = gson.fromJson(EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {}.getType());
      assertEquals("OK", resp.get("status"));
      post.close();

      TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.getParagraph(0);
          Paragraph p2 = note1.getParagraph(1);
          assertEquals(Job.Status.FINISHED, p1.getStatus());
          assertEquals(Job.Status.FINISHED, p2.getStatus());
          assertEquals("abc\n", p2.getReturn().message().get(0).getData());
          return null;
        });
      // no new python process is created because it is isolated mode.
      assertEquals(pythonProcessNum, interpreterSetting.getAllInterpreterGroups().size());
    } finally {
      // cleanup
      if (null != note1Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note1Id, anonymous);
      }
    }
  }

  @Test
  public void testRunNoteNonBlocking_Isolated() throws IOException, InterruptedException {
    LOG.info("Running testRunNoteNonBlocking_Isolated");
    String note1Id = null;
    try {
      InterpreterSettingManager interpreterSettingManager =
              TestUtils.getInstance(InterpreterSettingManager.class);
      InterpreterSetting interpreterSetting = interpreterSettingManager.getInterpreterSettingByName("python");
      int pythonProcessNum = interpreterSetting.getAllInterpreterGroups().size();

      note1Id = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      // 2 paragraphs
      // P1:
      //    %python
      //    from __future__ import print_function
      //    import time
      //    time.sleep(1)
      //    user='abc'
      // P2:
      //    %python
      //    print(user)
      //
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          Paragraph p2 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          p1.setText("%python from __future__ import print_function\nimport time\ntime.sleep(1)\nuser='abc'");
          p2.setText("%python print(user)");
          return null;
        });


      CloseableHttpResponse post = httpPost("/notebook/job/" + note1Id + "?blocking=false&isolated=true", "");
      assertThat(post, isAllowed());
      Map<String, Object> resp = gson.fromJson(EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {}.getType());
      assertEquals("OK", resp.get("status"));
      post.close();

      // wait for all the paragraphs are done
      boolean isRunning = TestUtils.getInstance(Notebook.class).processNote(note1Id, Note::isRunning);
      while(isRunning) {
        Thread.sleep(1000);
        isRunning = TestUtils.getInstance(Notebook.class).processNote(note1Id, Note::isRunning);
      }
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.getParagraph(0);
          Paragraph p2 = note1.getParagraph(1);
          assertEquals(Job.Status.FINISHED, p1.getStatus());
          assertEquals(Job.Status.FINISHED, p2.getStatus());
          assertEquals("abc\n", p2.getReturn().message().get(0).getData());
          return null;
        });

      // no new python process is created because it is isolated mode.
      assertEquals(pythonProcessNum, interpreterSetting.getAllInterpreterGroups().size());
    } finally {
      // cleanup
      if (null != note1Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note1Id, anonymous);
      }
    }
  }

  @Test
  public void testRunNoteWithParams() throws IOException, InterruptedException {
    String note1Id = null;
    try {
      note1Id = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      // 2 paragraphs
      // P1:
      //    %python
      //    name = z.input('name', 'world')
      //    print(name)
      // P2:
      //    %sh
      //    echo ${name|world}
      //
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          Paragraph p2 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          p1.setText("%python name = z.input('name', 'world')\nprint(name)");
          p2.setText("%sh(form=simple) echo '${name=world}'");
          return null;
        });

      Map<String, Object> paramsMap = new HashMap<>();
      paramsMap.put("name", "zeppelin");
      ParametersRequest parametersRequest = new ParametersRequest(paramsMap);
      CloseableHttpResponse post = httpPost("/notebook/job/" + note1Id + "?blocking=false&isolated=true&",
              parametersRequest.toJson());
      assertThat(post, isAllowed());
      Map<String, Object> resp = gson.fromJson(EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {}.getType());
      assertEquals("OK",resp.get("status"));
      post.close();

      // wait for all the paragraphs are done
      boolean isRunning = TestUtils.getInstance(Notebook.class).processNote(note1Id, Note::isRunning);
      while(isRunning) {
        Thread.sleep(1000);
        isRunning = TestUtils.getInstance(Notebook.class).processNote(note1Id, Note::isRunning);
      }
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.getParagraph(0);
          Paragraph p2 = note1.getParagraph(1);
          assertEquals(Job.Status.FINISHED, p1.getStatus());
          assertEquals(Job.Status.FINISHED, p2.getStatus());
          assertEquals("zeppelin\n", p1.getReturn().message().get(0).getData());
          assertEquals("zeppelin\n", p2.getReturn().message().get(0).getData());
          return null;
        });

      // another attempt rest api call without params
      post = httpPost("/notebook/job/" + note1Id + "?blocking=false&isolated=true", "");
      assertThat(post, isAllowed());
      resp = gson.fromJson(EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {}.getType());
      assertEquals("OK", resp.get("status"));
      post.close();

      // wait for all the paragraphs are done
      isRunning = TestUtils.getInstance(Notebook.class).processNote(note1Id, Note::isRunning);
      while(isRunning) {
        Thread.sleep(1000);
        isRunning = TestUtils.getInstance(Notebook.class).processNote(note1Id, Note::isRunning);
      }
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.getParagraph(0);
          Paragraph p2 = note1.getParagraph(1);
          assertEquals(Job.Status.FINISHED, p1.getStatus());
          assertEquals(Job.Status.FINISHED, p2.getStatus());
          assertEquals("world\n", p1.getReturn().message().get(0).getData());
          assertEquals("world\n", p2.getReturn().message().get(0).getData());
          return null;
        });
    } finally {
      // cleanup
      if (null != note1Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note1Id, anonymous);
      }
    }
  }

  @Test
  public void testRunAllParagraph_FirstFailed() throws IOException {
    LOG.info("Running testRunAllParagraph_FirstFailed");
    String note1Id = null;
    try {
      note1Id = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      // 2 paragraphs
      // P1:
      //    %python
      //    from __future__ import print_function
      //    import time
      //    time.sleep(1)
      //    print(user2)
      //
      // P2:
      //    %python
      //    user2='abc'
      //    print(user2)
      //
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          Paragraph p2 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          p1.setText("%python from __future__ import print_function\nimport time\ntime.sleep(1)\nprint(user2)");
          p2.setText("%python user2='abc'\nprint(user2)");
          return null;
        });


      CloseableHttpResponse post = httpPost("/notebook/job/" + note1Id + "?blocking=true", "");
      assertThat(post, isAllowed());
      post.close();

      TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.getParagraph(0);
          Paragraph p2 = note1.getParagraph(1);
          assertEquals(Job.Status.ERROR, p1.getStatus());
          // p2 will be skipped because p1 is failed.
          assertEquals(Job.Status.READY, p2.getStatus());
          return null;
        });
    } finally {
      // cleanup
      if (null != note1Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note1Id, anonymous);
      }
    }
  }

  @Test
  public void testCloneNote() throws IOException {
    LOG.info("Running testCloneNote");
    String note1Id = null;
    List<String> clonedNoteIds = new ArrayList<>();
    String text1 = "%text clone note";
    String text2 = "%text clone revision of note";
    try {
      Notebook notebook = TestUtils.getInstance(Notebook.class);
      note1Id = notebook.createNote("note1", anonymous);

      // add text and commit note
      NotebookRepoWithVersionControl.Revision first_commit =
              notebook.processNote(note1Id, note -> {
                Paragraph p1 = note.addNewParagraph(anonymous);
                p1.setText(text2);
                notebook.saveNote(note, AuthenticationInfo.ANONYMOUS);
                return notebook.checkpointNote(note.getId(), note.getPath(), "first commit", anonymous);
              });

      // change the text of note
      notebook.processNote(note1Id, note -> {
        note.getParagraph(0).setText(text1);
        return null;
      });

      // Clone a note
      CloseableHttpResponse post1 = httpPost("/notebook/" + note1Id, "");
      // Clone a revision of note
      CloseableHttpResponse post2 =
              httpPost("/notebook/" + note1Id, "{ revisionId: " + first_commit.id + "}");

      // Verify the responses
      for (int i = 0; i < 2; i++) {
        CloseableHttpResponse post = Arrays.asList(post1, post2).get(i);
        String text = Arrays.asList(text1, text2).get(i);

        String postResponse = EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8);
        LOG.info("testCloneNote response: {}", postResponse);
        assertThat(post, isAllowed());
        Map<String, Object> resp = gson.fromJson(postResponse,
                new TypeToken<Map<String, Object>>() {
                }.getType());
        clonedNoteIds.add((String) resp.get("body"));
        post.close();

        CloseableHttpResponse get = httpGet("/notebook/" + clonedNoteIds.get(clonedNoteIds.size() - 1));
        assertThat(get, isAllowed());
        Map<String, Object> resp2 = gson.fromJson(EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8),
                new TypeToken<Map<String, Object>>() {
                }.getType());
        Map<String, Object> resp2Body = (Map<String, Object>) resp2.get("body");
        List<Map<String, String>> paragraphs = (List<Map<String, String>>) resp2Body.get("paragraphs");
        // Verify that the original and copied text are consistent
        assertEquals(text, paragraphs.get(0).get("text"));
        //    assertEquals(resp2Body.get("name"), "Note " + clonedNoteId);
        get.close();
      }
    } finally {
      // cleanup
      if (null != note1Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note1Id, anonymous);
      }
      if (null != clonedNoteIds) {
        for (String clonedNoteId : clonedNoteIds) {
          TestUtils.getInstance(Notebook.class).removeNote(clonedNoteId, anonymous);
        }
      }
    }
  }

  @Test
  public void testRenameNote() throws IOException {
    LOG.info("Running testRenameNote");
    String noteId = null;
    try {
      String oldName = "old_name";
      noteId = TestUtils.getInstance(Notebook.class).createNote(oldName, anonymous);
      assertEquals(oldName, TestUtils.getInstance(Notebook.class).processNote(noteId, Note::getName));

      final String newName = "testName";
      String jsonRequest = "{\"name\": " + newName + "}";

      CloseableHttpResponse put = httpPut("/notebook/" + noteId + "/rename/", jsonRequest);
      assertThat("test testRenameNote:", put, isAllowed());
      put.close();

      assertEquals(newName, TestUtils.getInstance(Notebook.class).processNote(noteId, Note::getName));
    } finally {
      // cleanup
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  public void testUpdateParagraphConfig() throws IOException {
    LOG.info("Running testUpdateParagraphConfig");
    String noteId = null;
    try {
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      String paragraphId = TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          assertNull(p.getConfig().get("colWidth"));
          return p.getId();
        });

      String jsonRequest = "{\"colWidth\": 6.0}";

      CloseableHttpResponse put = httpPut("/notebook/" + noteId + "/paragraph/" + paragraphId + "/config",
              jsonRequest);
      assertThat("test testUpdateParagraphConfig:", put, isAllowed());

      Map<String, Object> resp = gson.fromJson(EntityUtils.toString(put.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {}.getType());
      Map<String, Object> respBody = (Map<String, Object>) resp.get("body");
      Map<String, Object> config = (Map<String, Object>) respBody.get("config");
      put.close();

      assertEquals(config.get("colWidth"), 6.0);
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          assertEquals(note.getParagraph(paragraphId).getConfig().get("colWidth"), 6.0);
          return null;
        });
    } finally {
      // cleanup
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  public void testClearAllParagraphOutput() throws IOException {
    LOG.info("Running testClearAllParagraphOutput");
    String noteId = null;
    try {
      // Create note and set result explicitly
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      String p1Id = TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          InterpreterResult result = new InterpreterResult(InterpreterResult.Code.SUCCESS,
                  InterpreterResult.Type.TEXT, "result");
          p1.setResult(result);
          return p1.getId();
        });

      String p2Id = TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          Paragraph p2 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          InterpreterResult result = new InterpreterResult(InterpreterResult.Code.SUCCESS,
                  InterpreterResult.Type.TEXT, "result");
          p2.setReturn(result, new Throwable());
          return p2.getId();
        });

      // clear paragraph result
      CloseableHttpResponse put = httpPut("/notebook/" + noteId + "/clear", "");
      LOG.info("test clear paragraph output response\n" + EntityUtils.toString(put.getEntity(), StandardCharsets.UTF_8));
      assertThat(put, isAllowed());
      put.close();

      // check if paragraph results are cleared
      CloseableHttpResponse get = httpGet("/notebook/" + noteId + "/paragraph/" + p1Id);
      assertThat(get, isAllowed());
      Map<String, Object> resp1 = gson.fromJson(EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {}.getType());
      Map<String, Object> resp1Body = (Map<String, Object>) resp1.get("body");
      assertNull(resp1Body.get("result"));
      get.close();

      get = httpGet("/notebook/" + noteId + "/paragraph/" + p2Id);
      assertThat(get, isAllowed());
      Map<String, Object> resp2 = gson.fromJson(EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {}.getType());
      Map<String, Object> resp2Body = (Map<String, Object>) resp2.get("body");
      assertNull(resp2Body.get("result"));
      get.close();
    } finally {
      // cleanup
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  public void testRunWithServerRestart() throws Exception {
    LOG.info("Running testRunWithServerRestart");
    String note1Id = null;
    try {
      note1Id = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      // 2 paragraphs
      // P1:
      //    %python
      //    from __future__ import print_function
      //    import time
      //    time.sleep(1)
      //    user='abc'
      // P2:
      //    %python
      //    print(user)
      //
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          Paragraph p2 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          p1.setText("%python from __future__ import print_function\nimport time\ntime.sleep(1)\nuser='abc'");
          p2.setText("%python print(user)");
          return null;
        });


      CloseableHttpResponse post1 = httpPost("/notebook/job/" + note1Id + "?blocking=true", "");
      assertThat(post1, isAllowed());
      post1.close();
      CloseableHttpResponse put = httpPut("/notebook/" + note1Id + "/clear", "");
      LOG.info("test clear paragraph output response\n" + EntityUtils.toString(put.getEntity(), StandardCharsets.UTF_8));
      assertThat(put, isAllowed());
      put.close();

      // restart server (while keeping interpreter configuration)
      AbstractTestRestApi.shutDown(false);
      startUp(NotebookRestApiTest.class.getSimpleName(), false);

      CloseableHttpResponse post2 = httpPost("/notebook/job/" + note1Id + "?blocking=true", "");
      assertThat(post2, isAllowed());
      Map<String, Object> resp = gson.fromJson(EntityUtils.toString(post2.getEntity(), StandardCharsets.UTF_8),
          new TypeToken<Map<String, Object>>() {}.getType());
      assertEquals("OK", resp.get("status"));
      post2.close();
      TestUtils.getInstance(Notebook.class).processNote(note1Id,
        note1 -> {
          Paragraph p1 = note1.getParagraph(0);
          Paragraph p2 = note1.getParagraph(1);
          assertEquals(Job.Status.FINISHED, p1.getStatus());
          assertEquals(p2.getReturn().toString(), Job.Status.FINISHED, p2.getStatus());
          assertNotNull(p2.getReturn());
          assertEquals("abc\n", p2.getReturn().message().get(0).getData());
          return null;
        });
    } finally {
      // cleanup
      if (null != note1Id) {
        TestUtils.getInstance(Notebook.class).removeNote(note1Id, anonymous);
      }
    }
  }
}
