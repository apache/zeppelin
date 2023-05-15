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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.rest.message.NoteJobStatus;
import org.apache.zeppelin.utils.TestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.user.AuthenticationInfo;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * BASIC Zeppelin rest api tests.
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
class ZeppelinRestApiTest extends AbstractTestRestApi {
  Gson gson = new Gson();
  AuthenticationInfo anonymous;

  @BeforeAll
  static void init() throws Exception {
    AbstractTestRestApi.startUp(ZeppelinRestApiTest.class.getSimpleName());
  }

  @AfterAll
  static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  @BeforeEach
  void setUp() {
    anonymous = new AuthenticationInfo("anonymous");
  }

  /**
   * ROOT API TEST.
   **/
  @Test
  void getApiRoot() throws IOException {
    // when
    CloseableHttpResponse httpGetRoot = httpGet("/");
    // then
    assertThat(httpGetRoot, isAllowed());
    httpGetRoot.close();
  }

  @Test
  void testGetNoteInfo() throws IOException {
    LOG.info("testGetNoteInfo");
    String noteId = null;
    try {
      // Create note to get info
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      assertNotNull("can't create new note", noteId);
      // use write lock because name is overwritten
      String paragraphText = TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          note.setName("note");
          Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          Map<String, Object> config = paragraph.getConfig();
          config.put("enabled", true);
          paragraph.setConfig(config);
          String paragraphTextTmp = "%md This is my new paragraph in my new note";
          paragraph.setText(paragraphTextTmp);
          TestUtils.getInstance(Notebook.class).saveNote(note, anonymous);
          return paragraphTextTmp;
        });


      CloseableHttpResponse get = httpGet("/notebook/" + noteId);
      String getResponse = EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8);
      LOG.info("testGetNoteInfo \n" + getResponse);
      assertThat("test note get method:", get, isAllowed());

      Map<String, Object> resp = gson.fromJson(getResponse,
              new TypeToken<Map<String, Object>>() {}.getType());

      assertNotNull(resp);
      assertEquals("OK", resp.get("status"));

      Map<String, Object> body = (Map<String, Object>) resp.get("body");
      List<Map<String, Object>> paragraphs = (List<Map<String, Object>>) body.get("paragraphs");

      assertTrue(paragraphs.size() > 0);
      assertEquals(paragraphText, paragraphs.get(0).get("text"));
      get.close();
    } finally {
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  void testNoteCreateWithName() throws IOException {
    String noteName = "Test note name";
    testNoteCreate(noteName);
  }

  @Test
  void testNoteCreateNoName() throws IOException {
    testNoteCreate("");
  }

  @Test
  void testNoteCreateWithParagraphs() throws IOException {
    // Call Create Note REST API
    String noteName = "test";
    String jsonRequest = "{\"name\":\"" + noteName + "\", \"paragraphs\": [" +
        "{\"title\": \"title1\", \"text\": \"text1\"}," +
        "{\"title\": \"title2\", \"text\": \"text2\"}," +
        "{\"title\": \"titleConfig\", \"text\": \"text3\", " +
        "\"config\": {\"colWidth\": 9.0, \"title\": true, " +
        "\"results\": [{\"graph\": {\"mode\": \"pieChart\"}}] " +
        "}}]} ";
    CloseableHttpResponse post = httpPost("/notebook/", jsonRequest);
    String postResponse = EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8);
    LOG.info("testNoteCreate \n" + postResponse);
    assertThat("test note create method:", post, isAllowed());

    Map<String, Object> resp = gson.fromJson(postResponse,
            new TypeToken<Map<String, Object>>() {}.getType());

    String newNoteId =  (String) resp.get("body");
    LOG.info("newNoteId:=" + newNoteId);
    TestUtils.getInstance(Notebook.class).processNote(newNoteId,
      newNote -> {
        assertNotNull(newNote, "Can not find new note by id");
        // This is partial test as newNote is in memory but is not persistent
        String newNoteName = newNote.getName();
        LOG.info("new note name is: " + newNoteName);
        String expectedNoteName = noteName;
        if (noteName.isEmpty()) {
          expectedNoteName = "Note " + newNoteId;
        }
        assertEquals(expectedNoteName, newNoteName, "compare note name");
        assertEquals(3, newNote.getParagraphs().size(), "initial paragraph check failed");
        for (Paragraph p : newNote.getParagraphs()) {
          if (StringUtils.isEmpty(p.getText())) {
            continue;
          }
          assertTrue(p.getTitle().startsWith("title"), "paragraph title check failed");
          assertTrue(p.getText().startsWith("text"), "paragraph text check failed");
          if (p.getTitle().equals("titleConfig")) {
            assertEquals(9.0, p.getConfig().get("colWidth"), "paragraph col width check failed");
            assertTrue(((boolean) p.getConfig().get("title")), "paragraph show title check failed");
            Map graph = ((List<Map>) p.getConfig().get("results")).get(0);
            String mode = ((Map) graph.get("graph")).get("mode").toString();
            assertEquals("pieChart", mode, "paragraph graph mode check failed");
          }
        }
        return null;
      });

    // cleanup
    TestUtils.getInstance(Notebook.class).removeNote(newNoteId, anonymous);
    post.close();
  }

  private void testNoteCreate(String noteName) throws IOException {
    // Call Create Note REST API
    String jsonRequest = "{\"name\":\"" + noteName + "\"}";
    CloseableHttpResponse post = httpPost("/notebook/", jsonRequest);
    String postResponse = EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8);
    LOG.info("testNoteCreate \n" + postResponse);
    assertThat("test note create method:", post, isAllowed());

    Map<String, Object> resp = gson.fromJson(postResponse,
            new TypeToken<Map<String, Object>>() {}.getType());

    String newNoteId =  (String) resp.get("body");
    LOG.info("newNoteId:=" + newNoteId);
    TestUtils.getInstance(Notebook.class).processNote(newNoteId,
      newNote -> {
        assertNotNull(newNote, "Can not find new note by id");
        // This is partial test as newNote is in memory but is not persistent
        String newNoteName = newNote.getName();
        LOG.info("new note name is: " + newNoteName);
        String noteNameTmp = noteName;
        if (StringUtils.isBlank(noteNameTmp)) {
          noteNameTmp = "Untitled Note";
        }
        assertEquals(noteNameTmp, newNoteName, "compare note name");
        return null;
      });
    // cleanup
    TestUtils.getInstance(Notebook.class).removeNote(newNoteId, anonymous);
    post.close();
  }

  @Test
  void testDeleteNote() throws IOException {
    LOG.info("testDeleteNote");
    String noteId = null;
    try {
      //Create note and get ID
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1_testDeletedNote", anonymous);
      testDeleteNote(noteId);
    } finally {
      if (noteId != null) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  void testDeleteNoteBadId() throws IOException {
    LOG.info("testDeleteNoteBadId");
    testDeleteNotExistNote("bad_ID");
  }

  @Test
  void testExportNote() throws IOException {
    LOG.info("testExportNote");

    String noteId = null;
    try {
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1_testExportNote", anonymous);
      // use write lock because name is overwritten
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          assertNotNull(note, "can't create new note");
          note.setName("source note for export");
          Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          Map<String, Object> config = paragraph.getConfig();
          config.put("enabled", true);
          paragraph.setConfig(config);
          paragraph.setText("%md This is my new paragraph in my new note");
          TestUtils.getInstance(Notebook.class).saveNote(note, anonymous);
          return null;
        });

      // Call export Note REST API
      CloseableHttpResponse get = httpGet("/notebook/export/" + noteId);
      String getResponse = EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8);
      LOG.info("testNoteExport \n" + getResponse);
      assertThat("test note export method:", get, isAllowed());

      Map<String, Object> resp =
          gson.fromJson(getResponse,
              new TypeToken<Map<String, Object>>() {}.getType());

      String exportJSON = (String) resp.get("body");
      assertNotNull("Can not find new notejson", exportJSON);
      LOG.info("export JSON:=" + exportJSON);
      get.close();
    } finally {
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  void testImportNotebook() throws IOException {
    String noteId = null;
    Map<String, Object> resp;
    String oldJson;
    String noteName;
    String importId = null;
    try {
      noteName = "source note for import";
      LOG.info("testImportNote");
      // create test note
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1_testImportNotebook", anonymous);
      // use write lock because name is overwritten
      int paragraphSize = TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          assertNotNull(note, "can't create new note");
          note.setName(noteName);
          Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          Map<String, Object> config = paragraph.getConfig();
          config.put("enabled", true);
          paragraph.setConfig(config);
          paragraph.setText("%md This is my new paragraph in my new note");
          TestUtils.getInstance(Notebook.class).saveNote(note, anonymous);
          return note.getParagraphs().size();
        });

      // get note content as JSON
      oldJson = getNoteContent(noteId);
      // delete it first then import it
      TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);

      // call note post
      CloseableHttpResponse importPost = httpPost("/notebook/import/", oldJson);
      assertThat(importPost, isAllowed());
      resp =
          gson.fromJson(EntityUtils.toString(importPost.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {}.getType());
      importId = (String) resp.get("body");

      assertNotNull("Did not get back a note id in body", importId);
      TestUtils.getInstance(Notebook.class).processNote(importId,
        newNote -> {
          assertEquals(noteName, newNote.getName(), "Compare note names");
          assertEquals(paragraphSize, newNote.getParagraphs().size(), "Compare paragraphs count");
          return null;
      });
      importPost.close();
    } finally {
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
      TestUtils.getInstance(Notebook.class).removeNote(importId, anonymous);
    }
  }

  private String getNoteContent(String id) throws IOException {
    CloseableHttpResponse get = httpGet("/notebook/export/" + id);
    assertThat(get, isAllowed());
    Map<String, Object> resp =
        gson.fromJson(EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8),
            new TypeToken<Map<String, Object>>() {}.getType());
    assertEquals(200, get.getStatusLine().getStatusCode());
    String body = resp.get("body").toString();
    // System.out.println("Body is " + body);
    get.close();
    return body;
  }

  private void testDeleteNote(String noteId) throws IOException {
    CloseableHttpResponse delete = httpDelete(("/notebook/" + noteId));
    LOG.info("testDeleteNote delete response\n" + EntityUtils.toString(delete.getEntity(), StandardCharsets.UTF_8));
    assertThat("Test delete method:", delete, isAllowed());
    delete.close();
    // make sure note is deleted
    if (!noteId.isEmpty()) {
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        deletedNote -> {
          assertNull(deletedNote, "Deleted note should be null");
          return null;
        });
    }
  }

  private void testDeleteNotExistNote(String noteId) throws IOException {
    CloseableHttpResponse delete = httpDelete(("/notebook/" + noteId));
    LOG.info("testDeleteNote delete response\n" + EntityUtils.toString(delete.getEntity(), StandardCharsets.UTF_8));
    assertThat("Test delete method:", delete, isNotFound());
    delete.close();
  }

  @Test
  void testCloneNote() throws IOException, IllegalArgumentException {
    LOG.info("testCloneNote");
    String noteId = null;
    String newNoteId = null;
    try {
      // Create note to clone
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1_testCloneNote", anonymous);
      int paragraphSize = TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          assertNotNull(note, "can't create new note");
          note.setName("source note for clone");
          Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          Map<String, Object> config = paragraph.getConfig();
          config.put("enabled", true);
          paragraph.setConfig(config);
          paragraph.setText("%md This is my new paragraph in my new note");
          TestUtils.getInstance(Notebook.class).saveNote(note, anonymous);
          return note.getParagraphs().size();
        });

      String noteName = "clone Note Name";
      // Call Clone Note REST API
      String jsonRequest = "{\"name\":\"" + noteName + "\"}";
      CloseableHttpResponse post = httpPost("/notebook/" + noteId, jsonRequest);
      String postResponse = EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8);
      LOG.info("testNoteClone \n" + postResponse);
      assertThat("test note clone method:", post, isAllowed());

      Map<String, Object> resp = gson.fromJson(postResponse,
              new TypeToken<Map<String, Object>>() {}.getType());

      newNoteId =  (String) resp.get("body");
      LOG.info("newNoteId:=" + newNoteId);
      TestUtils.getInstance(Notebook.class).processNote(newNoteId,
        newNote -> {
          assertNotNull(newNote, "Can not find new note by id");
          assertEquals(noteName, newNote.getName(), "Compare note names");
          assertEquals(paragraphSize, newNote.getParagraphs().size(), "Compare paragraphs count");
          return null;
        });

      post.close();
    } finally {
      //cleanup
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
      TestUtils.getInstance(Notebook.class).removeNote(newNoteId, anonymous);
    }
  }

  @Test
  void testListNotes() throws IOException {
    LOG.info("testListNotes");
    CloseableHttpResponse get = httpGet("/notebook/");
    assertThat("List notes method", get, isAllowed());
    Map<String, Object> resp = gson.fromJson(EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8),
            new TypeToken<Map<String, Object>>() {}.getType());
    List<Map<String, String>> body = (List<Map<String, String>>) resp.get("body");
    //TODO(khalid): anonymous or specific user notes?
    HashSet<String> anonymous = new HashSet<>(Arrays.asList("anonymous"));
    AuthorizationService authorizationService = TestUtils.getInstance(AuthorizationService.class);
    assertEquals(TestUtils.getInstance(Notebook.class)
      .getNotesInfo(noteId -> authorizationService.isReader(noteId, anonymous))
      .size(), body.size(), "List notes are equal");
    get.close();
  }

  @Test
  void testNoteJobs() throws Exception {
    LOG.info("testNoteJobs");

    String noteId = null;
    try {
      // Create note to run test.
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1_testNoteJobs", anonymous);
      // use write lock because name is overwritten
      String paragraphId = TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          assertNotNull(note, "can't create new note");
          note.setName("note for run test");
          Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);

          Map<String, Object> config = paragraph.getConfig();
          config.put("enabled", true);
          paragraph.setConfig(config);

          paragraph.setText("%md This is test paragraph.");
          TestUtils.getInstance(Notebook.class).saveNote(note, anonymous);
          return paragraph.getId();
        });

      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          try {
            note.runAll(anonymous, true, false, new HashMap<>());
          } catch (Exception e) {
            fail(e);
          }
          return null;
        });

      // wait until job is finished or timeout.
      int timeout = 1;
      boolean terminated = TestUtils.getInstance(Notebook.class).processNote(noteId, note -> note.getParagraph(0).isTerminated());
      while (!terminated) {
        Thread.sleep(1000);
        terminated = TestUtils.getInstance(Notebook.class).processNote(noteId, note -> note.getParagraph(0).isTerminated());
        if (timeout++ > 10) {
          LOG.info("testNoteJobs timeout job.");
          break;
        }
      }

      // Call Run note jobs REST API
      CloseableHttpResponse postNoteJobs = httpPost("/notebook/job/" + noteId + "?blocking=true", "");
      assertThat("test note jobs run:", postNoteJobs, isAllowed());
      postNoteJobs.close();

      // Call Stop note jobs REST API
      CloseableHttpResponse deleteNoteJobs = httpDelete("/notebook/job/" + noteId);
      assertThat("test note stop:", deleteNoteJobs, isAllowed());
      deleteNoteJobs.close();
      Thread.sleep(1000);

      // Call Run paragraph REST API
      CloseableHttpResponse postParagraph = httpPost("/notebook/job/" + noteId + "/" + paragraphId, "");
      assertThat("test paragraph run:", postParagraph, isAllowed());
      postParagraph.close();
      Thread.sleep(1000);

      // Call Stop paragraph REST API
      CloseableHttpResponse deleteParagraph = httpDelete("/notebook/job/" + noteId + "/" + paragraphId);
      assertThat("test paragraph stop:", deleteParagraph, isAllowed());
      deleteParagraph.close();
      Thread.sleep(1000);
    } finally {
      //cleanup
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  void testGetNoteJob() throws Exception {
    LOG.info("testGetNoteJob");

    String noteId = null;
    try {
      // Create note to run test.
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1_testGetNoteJob", anonymous);
      // use write lock because name is overwritten
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          assertNotNull(note, "can't create new note");
          note.setName("note for run test");
          Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);

          Map<String, Object> config = paragraph.getConfig();
          config.put("enabled", true);
          paragraph.setConfig(config);

          paragraph.setText("%sh sleep 1");
          paragraph.setAuthenticationInfo(anonymous);
          TestUtils.getInstance(Notebook.class).saveNote(note, anonymous);
          return null;
        });

      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          try {
            note.runAll(anonymous, true, false, new HashMap<>());
          } catch (Exception e) {
            fail();
          }
          return null;
        });
      // assume that status of the paragraph is running
      CloseableHttpResponse get = httpGet("/notebook/job/" + noteId);
      assertThat("test get note job: ", get, isAllowed());
      String responseBody = EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8);
      get.close();

      LOG.info("test get note job: \n" + responseBody);
      Map<String, Object> resp = gson.fromJson(responseBody,
              new TypeToken<Map<String, Object>>() {}.getType());

      NoteJobStatus noteJobStatus = gson.fromJson(gson.toJson(resp.get("body")), NoteJobStatus.class);
      assertEquals(1, noteJobStatus.getParagraphJobStatusList().size());
      int progress = Integer.parseInt(noteJobStatus.getParagraphJobStatusList().get(0).getProgress());
      assertTrue(progress >= 0 && progress <= 100);

      // wait until job is finished or timeout.
      int timeout = 1;
      boolean terminated = TestUtils.getInstance(Notebook.class).processNote(noteId, note -> note.getParagraph(0).isTerminated());
      while (!terminated) {
        Thread.sleep(100);
        terminated = TestUtils.getInstance(Notebook.class).processNote(noteId, note -> note.getParagraph(0).isTerminated());
        if (timeout++ > 10) {
          LOG.info("testGetNoteJob timeout job.");
          break;
        }
      }
    } finally {
      //cleanup
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  void testRunParagraphWithParams() throws Exception {
    LOG.info("testRunParagraphWithParams");

    String noteId = null;
    try {
      // Create note to run test.
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1_testRunParagraphWithParams", anonymous);
      // use write lock because name is overwritten
      String paragraphId = TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          assertNotNull(note, "can't create new note");
          note.setName("note for run test");
          Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);

          Map<String, Object> config = paragraph.getConfig();
          config.put("enabled", true);
          paragraph.setConfig(config);

          paragraph.setText("%spark\nval param = z.input(\"param\").toString\nprintln(param)");
          TestUtils.getInstance(Notebook.class).saveNote(note, anonymous);
          return paragraph.getId();
        });


      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          try {
            note.runAll(anonymous, true, false, new HashMap<>());
          } catch (Exception e) {
            fail();
          }
          return null;
        });

      // Call Run paragraph REST API
      CloseableHttpResponse postParagraph = httpPost("/notebook/job/" + noteId + "/" + paragraphId,
          "{\"params\": {\"param\": \"hello\", \"param2\": \"world\"}}");
      assertThat("test paragraph run:", postParagraph, isAllowed());
      postParagraph.close();
      Thread.sleep(1000);

      TestUtils.getInstance(Notebook.class).processNote(noteId,
        retrNote -> {
          Paragraph retrParagraph = retrNote.getParagraph(paragraphId);
          Map<String, Object> params = retrParagraph.settings.getParams();
          assertEquals("hello", params.get("param"));
          assertEquals("world", params.get("param2"));
          return null;
        });
    } finally {
      //cleanup
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  void testJobs() throws Exception {
    // create a note and a paragraph
    String noteId = null;
    try {
      System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_CRON_ENABLE.getVarName(), "true");
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1_testJobs", anonymous);
      // Use write lock, because name is overwritten
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          note.setName("note for run test");
          Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          paragraph.setText("%md This is test paragraph.");

          Map<String, Object> config = paragraph.getConfig();
          config.put("enabled", true);
          paragraph.setConfig(config);
          return null;
        });

      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          try {
            note.runAll(AuthenticationInfo.ANONYMOUS, false, false, new HashMap<>());
          } catch (Exception e) {
            fail();
          }
          return null;
        });

      String jsonRequest = "{\"cron\":\"* * * * * ?\" }";
      // right cron expression but not exist note.
      CloseableHttpResponse postCron = httpPost("/notebook/cron/notexistnote", jsonRequest);
      assertThat("", postCron, isNotFound());
      postCron.close();

      // right cron expression.
      postCron = httpPost("/notebook/cron/" + noteId, jsonRequest);
      assertThat("", postCron, isAllowed());
      postCron.close();
      Thread.sleep(1000);

      // wrong cron expression.
      jsonRequest = "{\"cron\":\"a * * * * ?\" }";
      postCron = httpPost("/notebook/cron/" + noteId, jsonRequest);
      assertThat("", postCron, isBadRequest());
      postCron.close();
      Thread.sleep(1000);

      // remove cron job.
      CloseableHttpResponse deleteCron = httpDelete("/notebook/cron/" + noteId);
      assertThat("", deleteCron, isAllowed());
      deleteCron.close();
    } finally {
      //cleanup
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
      System.clearProperty(ConfVars.ZEPPELIN_NOTEBOOK_CRON_ENABLE.getVarName());
    }
  }

  @Test
  void testCronDisable() throws Exception {
    String noteId = null;
    try {
      // create a note and a paragraph
      System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_CRON_ENABLE.getVarName(), "false");
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1_testCronDisable", anonymous);
      // use write lock because Name is overwritten
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          note.setName("note for run test");
          Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          paragraph.setText("%md This is test paragraph.");

          Map<String, Object> config = paragraph.getConfig();
          config.put("enabled", true);
          paragraph.setConfig(config);
          return null;
        });

      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          try {
            note.runAll(AuthenticationInfo.ANONYMOUS, true, true, new HashMap<>());
          } catch (Exception e) {
            fail();
          }
          return null;
        });


      String jsonRequest = "{\"cron\":\"* * * * * ?\" }";
      // right cron expression.
      CloseableHttpResponse postCron = httpPost("/notebook/cron/" + noteId, jsonRequest);
      assertThat("", postCron, isForbidden());
      postCron.close();

      System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_CRON_ENABLE.getVarName(), "true");
      System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_CRON_FOLDERS.getVarName(), "/System");

      // use write lock, because Name is overwritten
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          note.setName("System/test2");
          return null;
        });
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          try {
            note.runAll(AuthenticationInfo.ANONYMOUS, true, true, new HashMap<>());
          } catch (Exception e) {
            fail();
          }
          return null;
        });
      postCron = httpPost("/notebook/cron/" + noteId, jsonRequest);
      assertThat("", postCron, isAllowed());
      postCron.close();
      Thread.sleep(1000);

      // remove cron job.
      CloseableHttpResponse deleteCron = httpDelete("/notebook/cron/" + noteId);
      assertThat("", deleteCron, isAllowed());
      deleteCron.close();
      Thread.sleep(1000);

      System.clearProperty(ConfVars.ZEPPELIN_NOTEBOOK_CRON_FOLDERS.getVarName());
    } finally {
      //cleanup
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
      System.clearProperty(ConfVars.ZEPPELIN_NOTEBOOK_CRON_ENABLE.getVarName());
    }
  }

  @Test
  void testRegressionZEPPELIN_527() throws Exception {
    String noteId = null;
    try {
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1_testRegressionZEPPELIN_527", anonymous);
      // use write lock because name is overwritten
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          note.setName("note for run test");
          Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          paragraph.setText("%spark\nval param = z.input(\"param\").toString\nprintln(param)");
          return null;
        });

      TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          try {
            note.runAll(AuthenticationInfo.ANONYMOUS, true, false, new HashMap<>());
            TestUtils.getInstance(Notebook.class).saveNote(note, anonymous);
          } catch (Exception e) {
            fail();
          }
          return null;
        });



      CloseableHttpResponse getNoteJobs = httpGet("/notebook/job/" + noteId);
      assertThat("test note jobs run:", getNoteJobs, isAllowed());
      Map<String, Object> resp = gson.fromJson(EntityUtils.toString(getNoteJobs.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {}.getType());
      NoteJobStatus noteJobStatus = gson.fromJson(gson.toJson(resp.get("body")), NoteJobStatus.class);
      assertNotNull(noteJobStatus.getParagraphJobStatusList().get(0).getStarted());
      assertNotNull(noteJobStatus.getParagraphJobStatusList().get(0).getFinished());
      getNoteJobs.close();
    } finally {
      //cleanup
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  void testInsertParagraph() throws IOException {
    String noteId = null;
    try {
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1_testInsertParagraph", anonymous);

      String jsonRequest = "{\"title\": \"title1\", \"text\": \"text1\"}";
      CloseableHttpResponse post = httpPost("/notebook/" + noteId + "/paragraph", jsonRequest);
      String postResponse = EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8);
      LOG.info("testInsertParagraph response\n" + postResponse);
      assertThat("Test insert method:", post, isAllowed());
      post.close();

      Map<String, Object> resp = gson.fromJson(postResponse,
              new TypeToken<Map<String, Object>>() {}.getType());

      String newParagraphId = (String) resp.get("body");
      LOG.info("newParagraphId:=" + newParagraphId);

      Paragraph lastParagraph = TestUtils.getInstance(Notebook.class).processNote(noteId, Note::getLastParagraph);
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        retrNote -> {
          Paragraph newParagraph = retrNote.getParagraph(newParagraphId);
          assertNotNull(newParagraph, "Can not find new paragraph by id");
          assertEquals("title1", newParagraph.getTitle());
          assertEquals("text1", newParagraph.getText());
          assertEquals(newParagraph.getId(), lastParagraph.getId());
          return null;
        });

      // insert to index 0
      String jsonRequest2 = "{\"index\": 0, \"title\": \"title2\", \"text\": \"text2\"}";
      CloseableHttpResponse post2 = httpPost("/notebook/" + noteId + "/paragraph", jsonRequest2);
      LOG.info("testInsertParagraph response2\n" + EntityUtils.toString(post2.getEntity(), StandardCharsets.UTF_8));
      assertThat("Test insert method:", post2, isAllowed());
      post2.close();

      Paragraph paragraphAtIdx0 =TestUtils.getInstance(Notebook.class).processNote(noteId, note -> note.getParagraphs().get(0));
      assertEquals("title2", paragraphAtIdx0.getTitle());
      assertEquals("text2", paragraphAtIdx0.getText());

      //append paragraph providing graph
      String jsonRequest3 = "{\"title\": \"title3\", \"text\": \"text3\", " +
                            "\"config\": {\"colWidth\": 9.0, \"title\": true, " +
                            "\"results\": [{\"graph\": {\"mode\": \"pieChart\"}}]}}";
      CloseableHttpResponse post3 = httpPost("/notebook/" + noteId + "/paragraph", jsonRequest3);
      LOG.info("testInsertParagraph response4\n" + EntityUtils.toString(post3.getEntity(), StandardCharsets.UTF_8));
      assertThat("Test insert method:", post3, isAllowed());
      post3.close();

      Paragraph p = TestUtils.getInstance(Notebook.class).processNote(noteId, Note::getLastParagraph);
      assertEquals("title3", p.getTitle());
      assertEquals("text3", p.getText());
      Map result = ((List<Map>) p.getConfig().get("results")).get(0);
      String mode = ((Map) result.get("graph")).get("mode").toString();
      assertEquals("pieChart", mode);
      assertEquals(9.0, p.getConfig().get("colWidth"));
      assertTrue(((boolean) p.getConfig().get("title")));
    } finally {
      //cleanup
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  void testUpdateParagraph() throws IOException {
    String noteId = null;
    try {
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1_testUpdateParagraph", anonymous);

      String jsonRequest = "{\"title\": \"title1\", \"text\": \"text1\"}";
      CloseableHttpResponse post = httpPost("/notebook/" + noteId + "/paragraph", jsonRequest);
      Map<String, Object> resp = gson.fromJson(EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {}.getType());
      post.close();

      String newParagraphId = (String) resp.get("body");
      TestUtils.getInstance(Notebook.class).processNote(noteId,
        noteP -> {
          Paragraph newParagraph = noteP.getParagraph(newParagraphId);
          assertEquals("title1", newParagraph.getTitle());
          assertEquals("text1", newParagraph.getText());
          return null;
        });

      String updateRequest = "{\"text\": \"updated text\"}";
      CloseableHttpResponse put = httpPut("/notebook/" + noteId + "/paragraph/" + newParagraphId,
              updateRequest);
      assertThat("Test update method:", put, isAllowed());
      put.close();

      TestUtils.getInstance(Notebook.class).processNote(noteId,
        noteP -> {
          Paragraph updatedParagraph = noteP.getParagraph(newParagraphId);
          assertEquals("title1", updatedParagraph.getTitle());
          assertEquals("updated text", updatedParagraph.getText());
          return null;
        });

      String updateBothRequest = "{\"title\": \"updated title\", \"text\" : \"updated text 2\" }";
      CloseableHttpResponse updatePut = httpPut("/notebook/" + noteId + "/paragraph/" + newParagraphId,
              updateBothRequest);
      updatePut.close();

      TestUtils.getInstance(Notebook.class).processNote(noteId,
        noteP -> {
          Paragraph updatedBothParagraph = noteP.getParagraph(newParagraphId);
          assertEquals("updated title", updatedBothParagraph.getTitle());
          assertEquals("updated text 2", updatedBothParagraph.getText());
          return null;
        });
    } finally {
      //cleanup
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  void testGetParagraph() throws IOException {
    String noteId = null;
    try {
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1_testGetParagraph", anonymous);
      String pId = TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          p.setTitle("hello");
          p.setText("world");
          TestUtils.getInstance(Notebook.class).saveNote(note, anonymous);
          return p.getId();
        });

      CloseableHttpResponse get = httpGet("/notebook/" + noteId + "/paragraph/" + pId);
      String getResponse = EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8);
      LOG.info("testGetParagraph response\n" + getResponse);
      assertThat("Test get method: ", get, isAllowed());
      get.close();

      Map<String, Object> resp = gson.fromJson(getResponse,
              new TypeToken<Map<String, Object>>() {}.getType());

      assertNotNull(resp);
      assertEquals("OK", resp.get("status"));

      Map<String, Object> body = (Map<String, Object>) resp.get("body");

      assertEquals(pId, body.get("id"));
      assertEquals("hello", body.get("title"));
      assertEquals("world", body.get("text"));
    } finally {
      //cleanup
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  void testMoveParagraph() throws IOException {
    String noteId = null;
    try {
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1_testMoveParagraph", anonymous);
      Paragraph p2 = TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          p.setTitle("title1");
          p.setText("text1");
          Paragraph p2tmp = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          p2tmp.setTitle("title2");
          p2tmp.setText("text2");
          TestUtils.getInstance(Notebook.class).saveNote(note, anonymous);
          return p2tmp;
        });

      CloseableHttpResponse post = httpPost("/notebook/" + noteId + "/paragraph/" + p2.getId() +
              "/move/" + 0, "");
      assertThat("Test post method: ", post, isAllowed());
      post.close();

      TestUtils.getInstance(Notebook.class).processNote(noteId,
        retrNote -> {
          Paragraph paragraphAtIdx0 = retrNote.getParagraphs().get(0);

          assertEquals(p2.getId(), paragraphAtIdx0.getId());
          assertEquals(p2.getTitle(), paragraphAtIdx0.getTitle());
          assertEquals(p2.getText(), paragraphAtIdx0.getText());
          return null;
        });


      CloseableHttpResponse post2 = httpPost("/notebook/" + noteId + "/paragraph/" + p2.getId() +
              "/move/" + 10, "");
      assertThat("Test post method: ", post2, isBadRequest());
      post.close();
    } finally {
      //cleanup
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  void testDeleteParagraph() throws IOException {
    String noteId = null;
    try {
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1_testDeleteParagraph", anonymous);
      Paragraph p = TestUtils.getInstance(Notebook.class).processNote(noteId,
        note -> {
          Paragraph ptmp = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          ptmp.setTitle("title1");
          ptmp.setText("text1");

          TestUtils.getInstance(Notebook.class).saveNote(note, anonymous);
          return ptmp;
        });


      CloseableHttpResponse delete = httpDelete("/notebook/" + noteId + "/paragraph/" + p.getId());
      assertThat("Test delete method: ", delete, isAllowed());
      delete.close();

      TestUtils.getInstance(Notebook.class).processNote(noteId,
        retrNote -> {
          Paragraph retrParagrah = retrNote.getParagraph(p.getId());
          assertNull(retrParagrah, "paragraph should be deleted");
          return null;
        });
    } finally {
      //cleanup
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  void testTitleSearch() throws IOException, InterruptedException {
    String noteId = null;
    try {
      noteId = TestUtils.getInstance(Notebook.class).createNote("note1_testTitleSearch", anonymous);
      String jsonRequest = "{\"title\": \"testTitleSearchOfParagraph\", " +
              "\"text\": \"ThisIsToTestSearchMethodWithTitle \"}";
      CloseableHttpResponse postNoteText = httpPost("/notebook/" + noteId + "/paragraph", jsonRequest);
      postNoteText.close();
      Thread.sleep(3000);

      CloseableHttpResponse searchNote = httpGet("/notebook/search?q='testTitleSearchOfParagraph'");
      Map<String, Object> respSearchResult = gson.fromJson(EntityUtils.toString(searchNote.getEntity(), StandardCharsets.UTF_8),
          new TypeToken<Map<String, Object>>() {
          }.getType());
      ArrayList searchBody = (ArrayList) respSearchResult.get("body");

      int numberOfTitleHits = 0;
      for (int i = 0; i < searchBody.size(); i++) {
        Map<String, String> searchResult = (Map<String, String>) searchBody.get(i);
        if (searchResult.get("header").contains("testTitleSearchOfParagraph")) {
          numberOfTitleHits++;
        }
      }
      assertTrue(numberOfTitleHits >= 1, "Paragraph title hits must be at-least one");
      searchNote.close();
    } finally {
      //cleanup
      if (null != noteId) {
        TestUtils.getInstance(Notebook.class).removeNote(noteId, anonymous);
      }
    }
  }
}
