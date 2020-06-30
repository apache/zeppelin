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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.rest.message.NoteJobStatus;
import org.apache.zeppelin.service.AuthenticationService;
import org.apache.zeppelin.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.server.ZeppelinServer;
import org.apache.zeppelin.user.AuthenticationInfo;

/**
 * BASIC Zeppelin rest api tests.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ZeppelinRestApiTest extends AbstractTestRestApi {
  Gson gson = new Gson();
  AuthenticationInfo anonymous;

  @BeforeClass
  public static void init() throws Exception {
    AbstractTestRestApi.startUp(ZeppelinRestApiTest.class.getSimpleName());
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  @Before
  public void setUp() {
    anonymous = new AuthenticationInfo("anonymous");
  }

  /**
   * ROOT API TEST.
   **/
  @Test
  public void getApiRoot() throws IOException {
    // when
    GetMethod httpGetRoot = httpGet("/");
    // then
    assertThat(httpGetRoot, isAllowed());
    httpGetRoot.releaseConnection();
  }

  @Test
  public void testGetNoteInfo() throws IOException {
    LOG.info("testGetNoteInfo");
    Note note = null;
    try {
      // Create note to get info
      note = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      assertNotNull("can't create new note", note);
      note.setName("note");
      Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      Map config = paragraph.getConfig();
      config.put("enabled", true);
      paragraph.setConfig(config);
      String paragraphText = "%md This is my new paragraph in my new note";
      paragraph.setText(paragraphText);
      TestUtils.getInstance(Notebook.class).saveNote(note, anonymous);

      String sourceNoteId = note.getId();
      GetMethod get = httpGet("/notebook/" + sourceNoteId);
      LOG.info("testGetNoteInfo \n" + get.getResponseBodyAsString());
      assertThat("test note get method:", get, isAllowed());

      Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(),
              new TypeToken<Map<String, Object>>() {}.getType());

      assertNotNull(resp);
      assertEquals("OK", resp.get("status"));

      Map<String, Object> body = (Map<String, Object>) resp.get("body");
      List<Map<String, Object>> paragraphs = (List<Map<String, Object>>) body.get("paragraphs");

      assertTrue(paragraphs.size() > 0);
      assertEquals(paragraphText, paragraphs.get(0).get("text"));
    } finally {
      if (null != note) {
        TestUtils.getInstance(Notebook.class).removeNote(note, anonymous);
      }
    }
  }

  @Test
  public void testNoteCreateWithName() throws IOException {
    String noteName = "Test note name";
    testNoteCreate(noteName);
  }

  @Test
  public void testNoteCreateNoName() throws IOException {
    testNoteCreate("");
  }

  @Test
  public void testNoteCreateWithParagraphs() throws IOException {
    // Call Create Note REST API
    String noteName = "test";
    String jsonRequest = "{\"name\":\"" + noteName + "\", \"paragraphs\": [" +
        "{\"title\": \"title1\", \"text\": \"text1\"}," +
        "{\"title\": \"title2\", \"text\": \"text2\"}," +
        "{\"title\": \"titleConfig\", \"text\": \"text3\", " +
        "\"config\": {\"colWidth\": 9.0, \"title\": true, " +
        "\"results\": [{\"graph\": {\"mode\": \"pieChart\"}}] " +
        "}}]} ";
    PostMethod post = httpPost("/notebook/", jsonRequest);
    LOG.info("testNoteCreate \n" + post.getResponseBodyAsString());
    assertThat("test note create method:", post, isAllowed());

    Map<String, Object> resp = gson.fromJson(post.getResponseBodyAsString(),
            new TypeToken<Map<String, Object>>() {}.getType());

    String newNoteId =  (String) resp.get("body");
    LOG.info("newNoteId:=" + newNoteId);
    Note newNote = TestUtils.getInstance(Notebook.class).getNote(newNoteId);
    assertNotNull("Can not find new note by id", newNote);
    // This is partial test as newNote is in memory but is not persistent
    String newNoteName = newNote.getName();
    LOG.info("new note name is: " + newNoteName);
    String expectedNoteName = noteName;
    if (noteName.isEmpty()) {
      expectedNoteName = "Note " + newNoteId;
    }
    assertEquals("compare note name", expectedNoteName, newNoteName);
    assertEquals("initial paragraph check failed", 4, newNote.getParagraphs().size());
    for (Paragraph p : newNote.getParagraphs()) {
      if (StringUtils.isEmpty(p.getText())) {
        continue;
      }
      assertTrue("paragraph title check failed", p.getTitle().startsWith("title"));
      assertTrue("paragraph text check failed", p.getText().startsWith("text"));
      if (p.getTitle().equals("titleConfig")) {
        assertEquals("paragraph col width check failed", 9.0, p.getConfig().get("colWidth"));
        assertTrue("paragraph show title check failed", ((boolean) p.getConfig().get("title")));
        Map graph = ((List<Map>) p.getConfig().get("results")).get(0);
        String mode = ((Map) graph.get("graph")).get("mode").toString();
        assertEquals("paragraph graph mode check failed", "pieChart", mode);
      }
    }
    // cleanup
    TestUtils.getInstance(Notebook.class).removeNote(newNote, anonymous);
    post.releaseConnection();
  }

  private void testNoteCreate(String noteName) throws IOException {
    // Call Create Note REST API
    String jsonRequest = "{\"name\":\"" + noteName + "\"}";
    PostMethod post = httpPost("/notebook/", jsonRequest);
    LOG.info("testNoteCreate \n" + post.getResponseBodyAsString());
    assertThat("test note create method:", post, isAllowed());

    Map<String, Object> resp = gson.fromJson(post.getResponseBodyAsString(),
            new TypeToken<Map<String, Object>>() {}.getType());

    String newNoteId =  (String) resp.get("body");
    LOG.info("newNoteId:=" + newNoteId);
    Note newNote = TestUtils.getInstance(Notebook.class).getNote(newNoteId);
    assertNotNull("Can not find new note by id", newNote);
    // This is partial test as newNote is in memory but is not persistent
    String newNoteName = newNote.getName();
    LOG.info("new note name is: " + newNoteName);
    if (StringUtils.isBlank(noteName)) {
      noteName = "Untitled Note";
    }
    assertEquals("compare note name", noteName, newNoteName);
    // cleanup
    TestUtils.getInstance(Notebook.class).removeNote(newNote, anonymous);
    post.releaseConnection();
  }

  @Test
  public void testDeleteNote() throws IOException {
    LOG.info("testDeleteNote");
    Note note = null;
    try {
      //Create note and get ID
      note = TestUtils.getInstance(Notebook.class).createNote("note1_testDeletedNote", anonymous);
      String noteId = note.getId();
      testDeleteNote(noteId);
    } finally {
      if (null != note) {
        if (TestUtils.getInstance(Notebook.class).getNote(note.getId()) != null) {
          TestUtils.getInstance(Notebook.class).removeNote(note, anonymous);
        }
      }
    }
  }

  @Test
  public void testDeleteNoteBadId() throws IOException {
    LOG.info("testDeleteNoteBadId");
    testDeleteNotExistNote("bad_ID");
  }

  @Test
  public void testExportNote() throws IOException {
    LOG.info("testExportNote");

    Note note = null;
    try {
      note = TestUtils.getInstance(Notebook.class).createNote("note1_testExportNote", anonymous);
      assertNotNull("can't create new note", note);
      note.setName("source note for export");
      Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      Map config = paragraph.getConfig();
      config.put("enabled", true);
      paragraph.setConfig(config);
      paragraph.setText("%md This is my new paragraph in my new note");
      TestUtils.getInstance(Notebook.class).saveNote(note, anonymous);
      String sourceNoteId = note.getId();
      // Call export Note REST API
      GetMethod get = httpGet("/notebook/export/" + sourceNoteId);
      LOG.info("testNoteExport \n" + get.getResponseBodyAsString());
      assertThat("test note export method:", get, isAllowed());

      Map<String, Object> resp =
          gson.fromJson(get.getResponseBodyAsString(),
              new TypeToken<Map<String, Object>>() {}.getType());

      String exportJSON = (String) resp.get("body");
      assertNotNull("Can not find new notejson", exportJSON);
      LOG.info("export JSON:=" + exportJSON);
      get.releaseConnection();
    } finally {
      if (null != note) {
        TestUtils.getInstance(Notebook.class).removeNote(note, anonymous);
      }
    }
  }

  @Test
  public void testImportNotebook() throws IOException {
    Note note = null;
    Note newNote = null;
    Map<String, Object> resp;
    String oldJson;
    String noteName;
    try {
      noteName = "source note for import";
      LOG.info("testImportNote");
      // create test note
      note = TestUtils.getInstance(Notebook.class).createNote("note1_testImportNotebook", anonymous);
      assertNotNull("can't create new note", note);
      note.setName(noteName);
      Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      Map config = paragraph.getConfig();
      config.put("enabled", true);
      paragraph.setConfig(config);
      paragraph.setText("%md This is my new paragraph in my new note");
      TestUtils.getInstance(Notebook.class).saveNote(note, anonymous);
      String sourceNoteId = note.getId();
      // get note content as JSON
      oldJson = getNoteContent(sourceNoteId);
      // delete it first then import it
      TestUtils.getInstance(Notebook.class).removeNote(note, anonymous);

      // call note post
      PostMethod importPost = httpPost("/notebook/import/", oldJson);
      assertThat(importPost, isAllowed());
      resp =
          gson.fromJson(importPost.getResponseBodyAsString(),
              new TypeToken<Map<String, Object>>() {}.getType());
      String importId = (String) resp.get("body");

      assertNotNull("Did not get back a note id in body", importId);
      newNote = TestUtils.getInstance(Notebook.class).getNote(importId);
      assertEquals("Compare note names", noteName, newNote.getName());
      assertEquals("Compare paragraphs count", note.getParagraphs().size(), newNote.getParagraphs()
          .size());
      importPost.releaseConnection();
    } finally {
      if (null != note) {
        if (TestUtils.getInstance(Notebook.class).getNote(note.getId()) != null) {
          TestUtils.getInstance(Notebook.class).removeNote(note, anonymous);
        }
      }
      if (null != newNote) {
        if (TestUtils.getInstance(Notebook.class).getNote(newNote.getId()) != null) {
          TestUtils.getInstance(Notebook.class).removeNote(newNote, anonymous);
        }
      }
    }
  }

  private String getNoteContent(String id) throws IOException {
    GetMethod get = httpGet("/notebook/export/" + id);
    assertThat(get, isAllowed());
    get.addRequestHeader("Origin", "http://localhost");
    Map<String, Object> resp =
        gson.fromJson(get.getResponseBodyAsString(),
            new TypeToken<Map<String, Object>>() {}.getType());
    assertEquals(200, get.getStatusCode());
    String body = resp.get("body").toString();
    // System.out.println("Body is " + body);
    get.releaseConnection();
    return body;
  }

  private void testDeleteNote(String noteId) throws IOException {
    DeleteMethod delete = httpDelete(("/notebook/" + noteId));
    LOG.info("testDeleteNote delete response\n" + delete.getResponseBodyAsString());
    assertThat("Test delete method:", delete, isAllowed());
    delete.releaseConnection();
    // make sure note is deleted
    if (!noteId.isEmpty()) {
      Note deletedNote = TestUtils.getInstance(Notebook.class).getNote(noteId);
      assertNull("Deleted note should be null", deletedNote);
    }
  }

  private void testDeleteNotExistNote(String noteId) throws IOException {
    DeleteMethod delete = httpDelete(("/notebook/" + noteId));
    LOG.info("testDeleteNote delete response\n" + delete.getResponseBodyAsString());
    assertThat("Test delete method:", delete, isNotFound());
    delete.releaseConnection();
  }

  @Test
  public void testCloneNote() throws IOException, IllegalArgumentException {
    LOG.info("testCloneNote");
    Note note = null, newNote = null;
    try {
      // Create note to clone
      note = TestUtils.getInstance(Notebook.class).createNote("note1_testCloneNote", anonymous);
      assertNotNull("can't create new note", note);
      note.setName("source note for clone");
      Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      Map config = paragraph.getConfig();
      config.put("enabled", true);
      paragraph.setConfig(config);
      paragraph.setText("%md This is my new paragraph in my new note");
      TestUtils.getInstance(Notebook.class).saveNote(note, anonymous);
      String sourceNoteId = note.getId();

      String noteName = "clone Note Name";
      // Call Clone Note REST API
      String jsonRequest = "{\"name\":\"" + noteName + "\"}";
      PostMethod post = httpPost("/notebook/" + sourceNoteId, jsonRequest);
      LOG.info("testNoteClone \n" + post.getResponseBodyAsString());
      assertThat("test note clone method:", post, isAllowed());

      Map<String, Object> resp = gson.fromJson(post.getResponseBodyAsString(),
              new TypeToken<Map<String, Object>>() {}.getType());

      String newNoteId =  (String) resp.get("body");
      LOG.info("newNoteId:=" + newNoteId);
      newNote = TestUtils.getInstance(Notebook.class).getNote(newNoteId);
      assertNotNull("Can not find new note by id", newNote);
      assertEquals("Compare note names", noteName, newNote.getName());
      assertEquals("Compare paragraphs count", note.getParagraphs().size(),
              newNote.getParagraphs().size());
      post.releaseConnection();
    } finally {
      //cleanup
      if (null != note) {
        TestUtils.getInstance(Notebook.class).removeNote(note, anonymous);
      }
      if (null != newNote) {
        TestUtils.getInstance(Notebook.class).removeNote(newNote, anonymous);
      }
    }
  }

  @Test
  public void testListNotes() throws IOException {
    LOG.info("testListNotes");
    GetMethod get = httpGet("/notebook/ ");
    assertThat("List notes method", get, isAllowed());
    Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(),
            new TypeToken<Map<String, Object>>() {}.getType());
    List<Map<String, String>> body = (List<Map<String, String>>) resp.get("body");
    //TODO(khalid): anonymous or specific user notes?
    HashSet<String> anonymous = Sets.newHashSet("anonymous");
    AuthorizationService authorizationService = TestUtils.getInstance(AuthorizationService.class);
    assertEquals("List notes are equal", TestUtils.getInstance(Notebook.class)
            .getAllNotes(note -> authorizationService.isReader(note.getId(), anonymous))
            .size(), body.size());
    get.releaseConnection();
  }

  @Test
  public void testNoteJobs() throws Exception {
    LOG.info("testNoteJobs");

    Note note = null;
    try {
      // Create note to run test.
      note = TestUtils.getInstance(Notebook.class).createNote("note1_testNoteJobs", anonymous);
      assertNotNull("can't create new note", note);
      note.setName("note for run test");
      Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);

      Map config = paragraph.getConfig();
      config.put("enabled", true);
      paragraph.setConfig(config);

      paragraph.setText("%md This is test paragraph.");
      TestUtils.getInstance(Notebook.class).saveNote(note, anonymous);
      String noteId = note.getId();

      note.runAll(anonymous, true, false, new HashMap<>());
      // wait until job is finished or timeout.
      int timeout = 1;
      while (!paragraph.isTerminated()) {
        Thread.sleep(1000);
        if (timeout++ > 10) {
          LOG.info("testNoteJobs timeout job.");
          break;
        }
      }

      // Call Run note jobs REST API
      PostMethod postNoteJobs = httpPost("/notebook/job/" + noteId + "?blocking=true", "");
      assertThat("test note jobs run:", postNoteJobs, isAllowed());
      postNoteJobs.releaseConnection();

      // Call Stop note jobs REST API
      DeleteMethod deleteNoteJobs = httpDelete("/notebook/job/" + noteId);
      assertThat("test note stop:", deleteNoteJobs, isAllowed());
      deleteNoteJobs.releaseConnection();
      Thread.sleep(1000);

      // Call Run paragraph REST API
      PostMethod postParagraph = httpPost("/notebook/job/" + noteId + "/" + paragraph.getId(), "");
      assertThat("test paragraph run:", postParagraph, isAllowed());
      postParagraph.releaseConnection();
      Thread.sleep(1000);

      // Call Stop paragraph REST API
      DeleteMethod deleteParagraph = httpDelete("/notebook/job/" + noteId + "/" + paragraph.getId());
      assertThat("test paragraph stop:", deleteParagraph, isAllowed());
      deleteParagraph.releaseConnection();
      Thread.sleep(1000);
    } finally {
      //cleanup
      if (null != note) {
        TestUtils.getInstance(Notebook.class).removeNote(note, anonymous);
      }
    }
  }

  @Test
  public void testGetNoteJob() throws Exception {
    LOG.info("testGetNoteJob");

    Note note = null;
    try {
      // Create note to run test.
      note = TestUtils.getInstance(Notebook.class).createNote("note1_testGetNoteJob", anonymous);
      assertNotNull("can't create new note", note);
      note.setName("note for run test");
      Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);

      Map config = paragraph.getConfig();
      config.put("enabled", true);
      paragraph.setConfig(config);

      paragraph.setText("%sh sleep 1");
      paragraph.setAuthenticationInfo(anonymous);
      TestUtils.getInstance(Notebook.class).saveNote(note, anonymous);
      String noteId = note.getId();

      note.runAll(anonymous, true, false, new HashMap<>());
      // assume that status of the paragraph is running
      GetMethod get = httpGet("/notebook/job/" + noteId);
      assertThat("test get note job: ", get, isAllowed());
      String responseBody = get.getResponseBodyAsString();
      get.releaseConnection();

      LOG.info("test get note job: \n" + responseBody);
      Map<String, Object> resp = gson.fromJson(responseBody,
              new TypeToken<Map<String, Object>>() {}.getType());

      NoteJobStatus noteJobStatus = NoteJobStatus.fromJson(gson.toJson(resp.get("body")));
      assertEquals(1, noteJobStatus.getParagraphJobStatusList().size());
      int progress = Integer.parseInt(noteJobStatus.getParagraphJobStatusList().get(0).getProgress());
      assertTrue(progress >= 0 && progress <= 100);

      // wait until job is finished or timeout.
      int timeout = 1;
      while (!paragraph.isTerminated()) {
        Thread.sleep(100);
        if (timeout++ > 10) {
          LOG.info("testGetNoteJob timeout job.");
          break;
        }
      }
    } finally {
      //cleanup
      if (null != note) {
        TestUtils.getInstance(Notebook.class).removeNote(note, anonymous);
      }
    }
  }

  @Test
  public void testRunParagraphWithParams() throws Exception {
    LOG.info("testRunParagraphWithParams");

    Note note = null;
    try {
      // Create note to run test.
      note = TestUtils.getInstance(Notebook.class).createNote("note1_testRunParagraphWithParams", anonymous);
      assertNotNull("can't create new note", note);
      note.setName("note for run test");
      Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);

      Map config = paragraph.getConfig();
      config.put("enabled", true);
      paragraph.setConfig(config);

      paragraph.setText("%spark\nval param = z.input(\"param\").toString\nprintln(param)");
      TestUtils.getInstance(Notebook.class).saveNote(note, anonymous);
      String noteId = note.getId();

      note.runAll(anonymous, true, false, new HashMap<>());

      // Call Run paragraph REST API
      PostMethod postParagraph = httpPost("/notebook/job/" + noteId + "/" + paragraph.getId(),
          "{\"params\": {\"param\": \"hello\", \"param2\": \"world\"}}");
      assertThat("test paragraph run:", postParagraph, isAllowed());
      postParagraph.releaseConnection();
      Thread.sleep(1000);

      Note retrNote = TestUtils.getInstance(Notebook.class).getNote(noteId);
      Paragraph retrParagraph = retrNote.getParagraph(paragraph.getId());
      Map<String, Object> params = retrParagraph.settings.getParams();
      assertEquals("hello", params.get("param"));
      assertEquals("world", params.get("param2"));
    } finally {
      //cleanup
      if (null != note) {
        TestUtils.getInstance(Notebook.class).removeNote(note, anonymous);
      }
    }
  }

  @Test
  public void testJobs() throws Exception {
    // create a note and a paragraph
    Note note = null;
    try {
      System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_CRON_ENABLE.getVarName(), "true");
      note = TestUtils.getInstance(Notebook.class).createNote("note1_testJobs", anonymous);

      note.setName("note for run test");
      Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      paragraph.setText("%md This is test paragraph.");

      Map config = paragraph.getConfig();
      config.put("enabled", true);
      paragraph.setConfig(config);

      note.runAll(AuthenticationInfo.ANONYMOUS, false, false, new HashMap<>());

      String jsonRequest = "{\"cron\":\"* * * * * ?\" }";
      // right cron expression but not exist note.
      PostMethod postCron = httpPost("/notebook/cron/notexistnote", jsonRequest);
      assertThat("", postCron, isNotFound());
      postCron.releaseConnection();

      // right cron expression.
      postCron = httpPost("/notebook/cron/" + note.getId(), jsonRequest);
      assertThat("", postCron, isAllowed());
      postCron.releaseConnection();
      Thread.sleep(1000);

      // wrong cron expression.
      jsonRequest = "{\"cron\":\"a * * * * ?\" }";
      postCron = httpPost("/notebook/cron/" + note.getId(), jsonRequest);
      assertThat("", postCron, isBadRequest());
      postCron.releaseConnection();
      Thread.sleep(1000);

      // remove cron job.
      DeleteMethod deleteCron = httpDelete("/notebook/cron/" + note.getId());
      assertThat("", deleteCron, isAllowed());
      deleteCron.releaseConnection();
    } finally {
      //cleanup
      if (null != note) {
        TestUtils.getInstance(Notebook.class).removeNote(note, anonymous);
      }
      System.clearProperty(ConfVars.ZEPPELIN_NOTEBOOK_CRON_ENABLE.getVarName());
    }
  }

  @Test
  public void testCronDisable() throws Exception {
    Note note = null;
    try {
      // create a note and a paragraph
      System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_CRON_ENABLE.getVarName(), "false");
      note = TestUtils.getInstance(Notebook.class).createNote("note1_testCronDisable", anonymous);

      note.setName("note for run test");
      Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      paragraph.setText("%md This is test paragraph.");

      Map config = paragraph.getConfig();
      config.put("enabled", true);
      paragraph.setConfig(config);

      note.runAll(AuthenticationInfo.ANONYMOUS, true, true, new HashMap<>());

      String jsonRequest = "{\"cron\":\"* * * * * ?\" }";
      // right cron expression.
      PostMethod postCron = httpPost("/notebook/cron/" + note.getId(), jsonRequest);
      assertThat("", postCron, isForbidden());
      postCron.releaseConnection();

      System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_CRON_ENABLE.getVarName(), "true");
      System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_CRON_FOLDERS.getVarName(), "/System");

      note.setName("System/test2");
      note.runAll(AuthenticationInfo.ANONYMOUS, true, true, new HashMap<>());
      postCron = httpPost("/notebook/cron/" + note.getId(), jsonRequest);
      assertThat("", postCron, isAllowed());
      postCron.releaseConnection();
      Thread.sleep(1000);

      // remove cron job.
      DeleteMethod deleteCron = httpDelete("/notebook/cron/" + note.getId());
      assertThat("", deleteCron, isAllowed());
      deleteCron.releaseConnection();
      Thread.sleep(1000);

      System.clearProperty(ConfVars.ZEPPELIN_NOTEBOOK_CRON_FOLDERS.getVarName());
    } finally {
      //cleanup
      if (null != note) {
        TestUtils.getInstance(Notebook.class).removeNote(note, anonymous);
      }
      System.clearProperty(ConfVars.ZEPPELIN_NOTEBOOK_CRON_ENABLE.getVarName());
    }
  }

  @Test
  public void testRegressionZEPPELIN_527() throws Exception {
    Note note = null;
    try {
      note = TestUtils.getInstance(Notebook.class).createNote("note1_testRegressionZEPPELIN_527", anonymous);

      note.setName("note for run test");
      Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      paragraph.setText("%spark\nval param = z.input(\"param\").toString\nprintln(param)");
      note.runAll(AuthenticationInfo.ANONYMOUS, true, false, new HashMap<>());
      TestUtils.getInstance(Notebook.class).saveNote(note, anonymous);

      GetMethod getNoteJobs = httpGet("/notebook/job/" + note.getId());
      assertThat("test note jobs run:", getNoteJobs, isAllowed());
      Map<String, Object> resp = gson.fromJson(getNoteJobs.getResponseBodyAsString(),
              new TypeToken<Map<String, Object>>() {}.getType());
      NoteJobStatus noteJobStatus = NoteJobStatus.fromJson(gson.toJson(resp.get("body")));
      assertNotNull(noteJobStatus.getParagraphJobStatusList().get(0).getStarted());
      assertNotNull(noteJobStatus.getParagraphJobStatusList().get(0).getFinished());
      getNoteJobs.releaseConnection();
    } finally {
      //cleanup
      if (null != note) {
        TestUtils.getInstance(Notebook.class).removeNote(note, anonymous);
      }
    }
  }

  @Test
  public void testInsertParagraph() throws IOException {
    Note note = null;
    try {
      note = TestUtils.getInstance(Notebook.class).createNote("note1_testInsertParagraph", anonymous);

      String jsonRequest = "{\"title\": \"title1\", \"text\": \"text1\"}";
      PostMethod post = httpPost("/notebook/" + note.getId() + "/paragraph", jsonRequest);
      LOG.info("testInsertParagraph response\n" + post.getResponseBodyAsString());
      assertThat("Test insert method:", post, isAllowed());
      post.releaseConnection();

      Map<String, Object> resp = gson.fromJson(post.getResponseBodyAsString(),
              new TypeToken<Map<String, Object>>() {}.getType());

      String newParagraphId = (String) resp.get("body");
      LOG.info("newParagraphId:=" + newParagraphId);

      Note retrNote = TestUtils.getInstance(Notebook.class).getNote(note.getId());
      Paragraph newParagraph = retrNote.getParagraph(newParagraphId);
      assertNotNull("Can not find new paragraph by id", newParagraph);

      assertEquals("title1", newParagraph.getTitle());
      assertEquals("text1", newParagraph.getText());

      Paragraph lastParagraph = note.getLastParagraph();
      assertEquals(newParagraph.getId(), lastParagraph.getId());

      // insert to index 0
      String jsonRequest2 = "{\"index\": 0, \"title\": \"title2\", \"text\": \"text2\"}";
      PostMethod post2 = httpPost("/notebook/" + note.getId() + "/paragraph", jsonRequest2);
      LOG.info("testInsertParagraph response2\n" + post2.getResponseBodyAsString());
      assertThat("Test insert method:", post2, isAllowed());
      post2.releaseConnection();

      Paragraph paragraphAtIdx0 = note.getParagraphs().get(0);
      assertEquals("title2", paragraphAtIdx0.getTitle());
      assertEquals("text2", paragraphAtIdx0.getText());

      //append paragraph providing graph
      String jsonRequest3 = "{\"title\": \"title3\", \"text\": \"text3\", " +
                            "\"config\": {\"colWidth\": 9.0, \"title\": true, " +
                            "\"results\": [{\"graph\": {\"mode\": \"pieChart\"}}]}}";
      PostMethod post3 = httpPost("/notebook/" + note.getId() + "/paragraph", jsonRequest3);
      LOG.info("testInsertParagraph response4\n" + post3.getResponseBodyAsString());
      assertThat("Test insert method:", post3, isAllowed());
      post3.releaseConnection();

      Paragraph p = note.getLastParagraph();
      assertEquals("title3", p.getTitle());
      assertEquals("text3", p.getText());
      Map result = ((List<Map>) p.getConfig().get("results")).get(0);
      String mode = ((Map) result.get("graph")).get("mode").toString();
      assertEquals("pieChart", mode);
      assertEquals(9.0, p.getConfig().get("colWidth"));
      assertTrue(((boolean) p.getConfig().get("title")));
    } finally {
      //cleanup
      if (null != note) {
        TestUtils.getInstance(Notebook.class).removeNote(note, anonymous);
      }
    }
  }

  @Test
  public void testUpdateParagraph() throws IOException {
    Note note = null;
    try {
      note = TestUtils.getInstance(Notebook.class).createNote("note1_testUpdateParagraph", anonymous);

      String jsonRequest = "{\"title\": \"title1\", \"text\": \"text1\"}";
      PostMethod post = httpPost("/notebook/" + note.getId() + "/paragraph", jsonRequest);
      Map<String, Object> resp = gson.fromJson(post.getResponseBodyAsString(),
              new TypeToken<Map<String, Object>>() {}.getType());
      post.releaseConnection();

      String newParagraphId = (String) resp.get("body");
      Paragraph newParagraph = TestUtils.getInstance(Notebook.class).getNote(note.getId())
              .getParagraph(newParagraphId);

      assertEquals("title1", newParagraph.getTitle());
      assertEquals("text1", newParagraph.getText());

      String updateRequest = "{\"text\": \"updated text\"}";
      PutMethod put = httpPut("/notebook/" + note.getId() + "/paragraph/" + newParagraphId,
              updateRequest);
      assertThat("Test update method:", put, isAllowed());
      put.releaseConnection();

      Paragraph updatedParagraph = TestUtils.getInstance(Notebook.class).getNote(note.getId())
              .getParagraph(newParagraphId);

      assertEquals("title1", updatedParagraph.getTitle());
      assertEquals("updated text", updatedParagraph.getText());

      String updateBothRequest = "{\"title\": \"updated title\", \"text\" : \"updated text 2\" }";
      PutMethod updatePut = httpPut("/notebook/" + note.getId() + "/paragraph/" + newParagraphId,
              updateBothRequest);
      updatePut.releaseConnection();

      Paragraph updatedBothParagraph = TestUtils.getInstance(Notebook.class).getNote(note.getId())
              .getParagraph(newParagraphId);

      assertEquals("updated title", updatedBothParagraph.getTitle());
      assertEquals("updated text 2", updatedBothParagraph.getText());
    } finally {
      //cleanup
      if (null != note) {
        TestUtils.getInstance(Notebook.class).removeNote(note, anonymous);
      }
    }
  }

  @Test
  public void testGetParagraph() throws IOException {
    Note note = null;
    try {
      note = TestUtils.getInstance(Notebook.class).createNote("note1_testGetParagraph", anonymous);

      Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      p.setTitle("hello");
      p.setText("world");
      TestUtils.getInstance(Notebook.class).saveNote(note, anonymous);

      GetMethod get = httpGet("/notebook/" + note.getId() + "/paragraph/" + p.getId());
      LOG.info("testGetParagraph response\n" + get.getResponseBodyAsString());
      assertThat("Test get method: ", get, isAllowed());
      get.releaseConnection();

      Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(),
              new TypeToken<Map<String, Object>>() {}.getType());

      assertNotNull(resp);
      assertEquals("OK", resp.get("status"));

      Map<String, Object> body = (Map<String, Object>) resp.get("body");

      assertEquals(p.getId(), body.get("id"));
      assertEquals("hello", body.get("title"));
      assertEquals("world", body.get("text"));
    } finally {
      //cleanup
      if (null != note) {
        TestUtils.getInstance(Notebook.class).removeNote(note, anonymous);
      }
    }
  }

  @Test
  public void testMoveParagraph() throws IOException {
    Note note = null;
    try {
      note = TestUtils.getInstance(Notebook.class).createNote("note1_testMoveParagraph", anonymous);

      Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      p.setTitle("title1");
      p.setText("text1");

      Paragraph p2 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      p2.setTitle("title2");
      p2.setText("text2");

      TestUtils.getInstance(Notebook.class).saveNote(note, anonymous);

      PostMethod post = httpPost("/notebook/" + note.getId() + "/paragraph/" + p2.getId() +
              "/move/" + 0, "");
      assertThat("Test post method: ", post, isAllowed());
      post.releaseConnection();

      Note retrNote = TestUtils.getInstance(Notebook.class).getNote(note.getId());
      Paragraph paragraphAtIdx0 = retrNote.getParagraphs().get(0);

      assertEquals(p2.getId(), paragraphAtIdx0.getId());
      assertEquals(p2.getTitle(), paragraphAtIdx0.getTitle());
      assertEquals(p2.getText(), paragraphAtIdx0.getText());

      PostMethod post2 = httpPost("/notebook/" + note.getId() + "/paragraph/" + p2.getId() +
              "/move/" + 10, "");
      assertThat("Test post method: ", post2, isBadRequest());
      post.releaseConnection();
    } finally {
      //cleanup
      if (null != note) {
        TestUtils.getInstance(Notebook.class).removeNote(note, anonymous);
      }
    }
  }

  @Test
  public void testDeleteParagraph() throws IOException {
    Note note = null;
    try {
      note = TestUtils.getInstance(Notebook.class).createNote("note1_testDeleteParagraph", anonymous);

      Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      p.setTitle("title1");
      p.setText("text1");

      TestUtils.getInstance(Notebook.class).saveNote(note, anonymous);

      DeleteMethod delete = httpDelete("/notebook/" + note.getId() + "/paragraph/" + p.getId());
      assertThat("Test delete method: ", delete, isAllowed());
      delete.releaseConnection();

      Note retrNote = TestUtils.getInstance(Notebook.class).getNote(note.getId());
      Paragraph retrParagrah = retrNote.getParagraph(p.getId());
      assertNull("paragraph should be deleted", retrParagrah);
    } finally {
      //cleanup
      if (null != note) {
        TestUtils.getInstance(Notebook.class).removeNote(note, anonymous);
      }
    }
  }

  // TODO(zjffdu) disable it as it fails, need to investigate why.
  //@Test
  public void testTitleSearch() throws IOException, InterruptedException {
    Note note = null;
    try {
      note = TestUtils.getInstance(Notebook.class).createNote("note1_testTitleSearch", anonymous);
      String jsonRequest = "{\"title\": \"testTitleSearchOfParagraph\", " +
              "\"text\": \"ThisIsToTestSearchMethodWithTitle \"}";
      PostMethod postNoteText = httpPost("/notebook/" + note.getId() + "/paragraph", jsonRequest);
      postNoteText.releaseConnection();
      Thread.sleep(1000);

      GetMethod searchNote = httpGet("/notebook/search?q='testTitleSearchOfParagraph'");
      searchNote.addRequestHeader("Origin", "http://localhost");
      Map<String, Object> respSearchResult = gson.fromJson(searchNote.getResponseBodyAsString(),
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
      assertEquals("Paragraph title hits must be at-least one", true, numberOfTitleHits >= 1);
      searchNote.releaseConnection();
    } finally {
      //cleanup
      if (null != note) {
        TestUtils.getInstance(Notebook.class).removeNote(note, anonymous);
      }
    }
  }
}
