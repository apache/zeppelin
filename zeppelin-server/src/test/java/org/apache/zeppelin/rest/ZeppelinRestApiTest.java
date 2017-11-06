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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Sets;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.server.ZeppelinServer;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import static org.junit.Assert.*;

/**
 * BASIC Zeppelin rest api tests
 *
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

  /***
   * ROOT API TEST
   ***/
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
    // Create note to get info
    Note note = ZeppelinServer.notebook.createNote(anonymous);
    assertNotNull("can't create new note", note);
    note.setName("note");
    Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map config = paragraph.getConfig();
    config.put("enabled", true);
    paragraph.setConfig(config);
    String paragraphText = "%md This is my new paragraph in my new note";
    paragraph.setText(paragraphText);
    note.persist(anonymous);

    String sourceNoteId = note.getId();
    GetMethod get = httpGet("/notebook/" + sourceNoteId);
    LOG.info("testGetNoteInfo \n" + get.getResponseBodyAsString());
    assertThat("test note get method:", get, isAllowed());

    Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());

    assertNotNull(resp);
    assertEquals("OK", resp.get("status"));

    Map<String, Object> body = (Map<String, Object>) resp.get("body");
    List<Map<String, Object>> paragraphs = (List<Map<String, Object>>) body.get("paragraphs");

    assertTrue(paragraphs.size() > 0);
    assertEquals(paragraphText, paragraphs.get(0).get("text"));
    //
    ZeppelinServer.notebook.removeNote(sourceNoteId, anonymous);
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
        "\"config\": {\"colWidth\": 9.0, \"title\": true, "+
        "\"results\": [{\"graph\": {\"mode\": \"pieChart\"}}] "+
        "}}]} ";
    PostMethod post = httpPost("/notebook/", jsonRequest);
    LOG.info("testNoteCreate \n" + post.getResponseBodyAsString());
    assertThat("test note create method:", post, isAllowed());

    Map<String, Object> resp = gson.fromJson(post.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());

    String newNoteId =  (String) resp.get("body");
    LOG.info("newNoteId:=" + newNoteId);
    Note newNote = ZeppelinServer.notebook.getNote(newNoteId);
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
      if ( p.getTitle().equals("titleConfig")){
        assertEquals("paragraph col width check failed", 9.0, p.getConfig().get("colWidth"));
        assertTrue("paragraph show title check failed", ((boolean) p.getConfig().get("title")));
        Map graph = ((List<Map>)p.getConfig().get("results")).get(0);
        String mode = ((Map)graph.get("graph")).get("mode").toString();
        assertEquals("paragraph graph mode check failed", "pieChart", mode);
      }
    }
    // cleanup
    ZeppelinServer.notebook.removeNote(newNoteId, anonymous);
    post.releaseConnection();
  }

  private void testNoteCreate(String noteName) throws IOException {
    // Call Create Note REST API
    String jsonRequest = "{\"name\":\"" + noteName + "\"}";
    PostMethod post = httpPost("/notebook/", jsonRequest);
    LOG.info("testNoteCreate \n" + post.getResponseBodyAsString());
    assertThat("test note create method:", post, isAllowed());

    Map<String, Object> resp = gson.fromJson(post.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());

    String newNoteId =  (String) resp.get("body");
    LOG.info("newNoteId:=" + newNoteId);
    Note newNote = ZeppelinServer.notebook.getNote(newNoteId);
    assertNotNull("Can not find new note by id", newNote);
    // This is partial test as newNote is in memory but is not persistent
    String newNoteName = newNote.getName();
    LOG.info("new note name is: " + newNoteName);
    String expectedNoteName = noteName;
    if (noteName.isEmpty()) {
      expectedNoteName = "Note " + newNoteId;
    }
    assertEquals("compare note name", expectedNoteName, newNoteName);
    // cleanup
    ZeppelinServer.notebook.removeNote(newNoteId, anonymous);
    post.releaseConnection();

  }

  @Test
  public void testDeleteNote() throws IOException {
    LOG.info("testDeleteNote");
    //Create note and get ID
    Note note = ZeppelinServer.notebook.createNote(anonymous);
    String noteId = note.getId();
    testDeleteNote(noteId);
  }

  @Test
  public void testDeleteNoteBadId() throws IOException {
    LOG.info("testDeleteNoteBadId");
    testDeleteNote("2AZFXEX97");
    testDeleteNote("bad_ID");
  }


  @Test
  public void testExportNote() throws IOException {
    LOG.info("testExportNote");
    Note note = ZeppelinServer.notebook.createNote(anonymous);
    assertNotNull("can't create new note", note);
    note.setName("source note for export");
    Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map config = paragraph.getConfig();
    config.put("enabled", true);
    paragraph.setConfig(config);
    paragraph.setText("%md This is my new paragraph in my new note");
    note.persist(anonymous);
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
    ZeppelinServer.notebook.removeNote(sourceNoteId, anonymous);
    get.releaseConnection();

  }

  @Test
  public void testImportNotebook() throws IOException {
    Map<String, Object> resp;
    String noteName = "source note for import";
    LOG.info("testImportNote");
    // create test note
    Note note = ZeppelinServer.notebook.createNote(anonymous);
    assertNotNull("can't create new note", note);
    note.setName(noteName);
    Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map config = paragraph.getConfig();
    config.put("enabled", true);
    paragraph.setConfig(config);
    paragraph.setText("%md This is my new paragraph in my new note");
    note.persist(anonymous);
    String sourceNoteId = note.getId();
    // get note content as JSON
    String oldJson = getNoteContent(sourceNoteId);
    // call note post
    PostMethod importPost = httpPost("/notebook/import/", oldJson);
    assertThat(importPost, isAllowed());
    resp =
        gson.fromJson(importPost.getResponseBodyAsString(),
            new TypeToken<Map<String, Object>>() {}.getType());
    String importId = (String) resp.get("body");

    assertNotNull("Did not get back a note id in body", importId);
    Note newNote = ZeppelinServer.notebook.getNote(importId);
    assertEquals("Compare note names", noteName, newNote.getName());
    assertEquals("Compare paragraphs count", note.getParagraphs().size(), newNote.getParagraphs()
        .size());
    // cleanup
    ZeppelinServer.notebook.removeNote(note.getId(), anonymous);
    ZeppelinServer.notebook.removeNote(newNote.getId(), anonymous);
    importPost.releaseConnection();
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
      Note deletedNote = ZeppelinServer.notebook.getNote(noteId);
      assertNull("Deleted note should be null", deletedNote);
    }
  }

  @Test
  public void testCloneNote() throws IOException, CloneNotSupportedException, IllegalArgumentException {
    LOG.info("testCloneNote");
    // Create note to clone
    Note note = ZeppelinServer.notebook.createNote(anonymous);
    assertNotNull("can't create new note", note);
    note.setName("source note for clone");
    Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map config = paragraph.getConfig();
    config.put("enabled", true);
    paragraph.setConfig(config);
    paragraph.setText("%md This is my new paragraph in my new note");
    note.persist(anonymous);
    String sourceNoteId = note.getId();

    String noteName = "clone Note Name";
    // Call Clone Note REST API
    String jsonRequest = "{\"name\":\"" + noteName + "\"}";
    PostMethod post = httpPost("/notebook/" + sourceNoteId, jsonRequest);
    LOG.info("testNoteClone \n" + post.getResponseBodyAsString());
    assertThat("test note clone method:", post, isAllowed());

    Map<String, Object> resp = gson.fromJson(post.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());

    String newNoteId =  (String) resp.get("body");
    LOG.info("newNoteId:=" + newNoteId);
    Note newNote = ZeppelinServer.notebook.getNote(newNoteId);
    assertNotNull("Can not find new note by id", newNote);
    assertEquals("Compare note names", noteName, newNote.getName());
    assertEquals("Compare paragraphs count", note.getParagraphs().size(), newNote.getParagraphs().size());
    //cleanup
    ZeppelinServer.notebook.removeNote(note.getId(), anonymous);
    ZeppelinServer.notebook.removeNote(newNote.getId(), anonymous);
    post.releaseConnection();
  }

  @Test
  public void testListNotes() throws IOException {
    LOG.info("testListNotes");
    GetMethod get = httpGet("/notebook/ ");
    assertThat("List notes method", get, isAllowed());
    Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());
    List<Map<String, String>> body = (List<Map<String, String>>) resp.get("body");
    //TODO(khalid): anonymous or specific user notes?
    HashSet<String> anonymous = Sets.newHashSet("anonymous");
    assertEquals("List notes are equal", ZeppelinServer.notebook.getAllNotes(anonymous).size(), body.size());
    get.releaseConnection();
  }

  @Test
  public void testNoteJobs() throws IOException, InterruptedException {
    LOG.info("testNoteJobs");
    // Create note to run test.
    Note note = ZeppelinServer.notebook.createNote(anonymous);
    assertNotNull("can't create new note", note);
    note.setName("note for run test");
    Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    
    Map config = paragraph.getConfig();
    config.put("enabled", true);
    paragraph.setConfig(config);
    
    paragraph.setText("%md This is test paragraph.");
    note.persist(anonymous);
    String noteId = note.getId();

    note.runAll();
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
    PostMethod postNoteJobs = httpPost("/notebook/job/" + noteId, "");
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
    
    //cleanup
    ZeppelinServer.notebook.removeNote(note.getId(), anonymous);
  }

  @Test
  public void testGetNoteJob() throws IOException, InterruptedException {
    LOG.info("testGetNoteJob");
    // Create note to run test.
    Note note = ZeppelinServer.notebook.createNote(anonymous);
    assertNotNull("can't create new note", note);
    note.setName("note for run test");
    Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);

    Map config = paragraph.getConfig();
    config.put("enabled", true);
    paragraph.setConfig(config);

    paragraph.setText("%sh sleep 1");
    paragraph.setAuthenticationInfo(anonymous);
    note.persist(anonymous);
    String noteId = note.getId();

    note.runAll();
    // assume that status of the paragraph is running
    GetMethod get = httpGet("/notebook/job/" + noteId);
    assertThat("test get note job: ", get, isAllowed());
    String responseBody = get.getResponseBodyAsString();
    get.releaseConnection();

    LOG.info("test get note job: \n" + responseBody);
    Map<String, Object> resp = gson.fromJson(responseBody, new TypeToken<Map<String, Object>>() {
    }.getType());

    List<Map<String, Object>> paragraphs = (List<Map<String, Object>>) resp.get("body");
    assertEquals(1, paragraphs.size());
    assertTrue(paragraphs.get(0).containsKey("progress"));
    int progress = Integer.parseInt((String) paragraphs.get(0).get("progress"));
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

    ZeppelinServer.notebook.removeNote(note.getId(), anonymous);
  }

  @Test
  public void testRunParagraphWithParams() throws IOException, InterruptedException {
    LOG.info("testRunParagraphWithParams");
    // Create note to run test.
    Note note = ZeppelinServer.notebook.createNote(anonymous);
    assertNotNull("can't create new note", note);
    note.setName("note for run test");
    Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);

    Map config = paragraph.getConfig();
    config.put("enabled", true);
    paragraph.setConfig(config);

    paragraph.setText("%spark\nval param = z.input(\"param\").toString\nprintln(param)");
    note.persist(anonymous);
    String noteId = note.getId();

    note.runAll();

    // Call Run paragraph REST API
    PostMethod postParagraph = httpPost("/notebook/job/" + noteId + "/" + paragraph.getId(),
        "{\"params\": {\"param\": \"hello\", \"param2\": \"world\"}}");
    assertThat("test paragraph run:", postParagraph, isAllowed());
    postParagraph.releaseConnection();
    Thread.sleep(1000);

    Note retrNote = ZeppelinServer.notebook.getNote(noteId);
    Paragraph retrParagraph = retrNote.getParagraph(paragraph.getId());
    Map<String, Object> params = retrParagraph.settings.getParams();
    assertEquals("hello", params.get("param"));
    assertEquals("world", params.get("param2"));

    //cleanup
    ZeppelinServer.notebook.removeNote(note.getId(), anonymous);
  }

  @Test
  public void testJobs() throws InterruptedException, IOException{
    // create a note and a paragraph
    Note note = ZeppelinServer.notebook.createNote(anonymous);

    note.setName("note for run test");
    Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    paragraph.setText("%md This is test paragraph.");
    
    Map config = paragraph.getConfig();
    config.put("enabled", true);
    paragraph.setConfig(config);

    note.runAll(AuthenticationInfo.ANONYMOUS, false);

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
    ZeppelinServer.notebook.removeNote(note.getId(), anonymous);
  }

  @Test
  public void testRegressionZEPPELIN_527() throws IOException {
    Note note = ZeppelinServer.notebook.createNote(anonymous);

    note.setName("note for run test");
    Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    paragraph.setText("%spark\nval param = z.input(\"param\").toString\nprintln(param)");

    note.persist(anonymous);

    GetMethod getNoteJobs = httpGet("/notebook/job/" + note.getId());
    assertThat("test note jobs run:", getNoteJobs, isAllowed());
    Map<String, Object> resp = gson.fromJson(getNoteJobs.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());
    List<Map<String, String>> body = (List<Map<String, String>>) resp.get("body");
    assertFalse(body.get(0).containsKey("started"));
    assertFalse(body.get(0).containsKey("finished"));
    getNoteJobs.releaseConnection();

    ZeppelinServer.notebook.removeNote(note.getId(), anonymous);
  }

  @Test
  public void testInsertParagraph() throws IOException {
    Note note = ZeppelinServer.notebook.createNote(anonymous);

    String jsonRequest = "{\"title\": \"title1\", \"text\": \"text1\"}";
    PostMethod post = httpPost("/notebook/" + note.getId() + "/paragraph", jsonRequest);
    LOG.info("testInsertParagraph response\n" + post.getResponseBodyAsString());
    assertThat("Test insert method:", post, isAllowed());
    post.releaseConnection();

    Map<String, Object> resp = gson.fromJson(post.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());

    String newParagraphId = (String) resp.get("body");
    LOG.info("newParagraphId:=" + newParagraphId);

    Note retrNote = ZeppelinServer.notebook.getNote(note.getId());
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
    String jsonRequest3 = "{\"title\": \"title3\", \"text\": \"text3\", "+
                          "\"config\": {\"colWidth\": 9.0, \"title\": true, "+
                          "\"results\": [{\"graph\": {\"mode\": \"pieChart\"}}]}}";
    PostMethod post3 = httpPost("/notebook/" + note.getId() + "/paragraph", jsonRequest3);
    LOG.info("testInsertParagraph response4\n" + post3.getResponseBodyAsString());
    assertThat("Test insert method:", post3, isAllowed());
    post3.releaseConnection();

    Paragraph p = note.getLastParagraph();
    assertEquals("title3", p.getTitle());
    assertEquals("text3", p.getText());
    Map result = ((List<Map>)p.getConfig().get("results")).get(0);
    String mode = ((Map)result.get("graph")).get("mode").toString();
    assertEquals("pieChart", mode);
    assertEquals(9.0, p.getConfig().get("colWidth"));
    assertTrue(((boolean) p.getConfig().get("title")));


    ZeppelinServer.notebook.removeNote(note.getId(), anonymous);
  }

  @Test
  public void testUpdateParagraph() throws IOException {
    Note note = ZeppelinServer.notebook.createNote(anonymous);

    String jsonRequest = "{\"title\": \"title1\", \"text\": \"text1\"}";
    PostMethod post = httpPost("/notebook/" + note.getId() + "/paragraph", jsonRequest);
    Map<String, Object> resp = gson.fromJson(post.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {}.getType());
    post.releaseConnection();

    String newParagraphId = (String) resp.get("body");
    Paragraph newParagraph = ZeppelinServer.notebook.getNote(note.getId()).getParagraph(newParagraphId);

    assertEquals("title1", newParagraph.getTitle());
    assertEquals("text1", newParagraph.getText());

    String updateRequest = "{\"text\": \"updated text\"}";
    PutMethod put = httpPut("/notebook/" + note.getId() + "/paragraph/" + newParagraphId, updateRequest);
    assertThat("Test update method:", put, isAllowed());
    put.releaseConnection();

    Paragraph updatedParagraph = ZeppelinServer.notebook.getNote(note.getId()).getParagraph(newParagraphId);

    assertEquals("title1", updatedParagraph.getTitle());
    assertEquals("updated text", updatedParagraph.getText());

    String updateBothRequest = "{\"title\": \"updated title\", \"text\" : \"updated text 2\" }";
    PutMethod updatePut = httpPut("/notebook/" + note.getId() + "/paragraph/" + newParagraphId, updateBothRequest);
    updatePut.releaseConnection();

    Paragraph updatedBothParagraph = ZeppelinServer.notebook.getNote(note.getId()).getParagraph(newParagraphId);

    assertEquals("updated title", updatedBothParagraph.getTitle());
    assertEquals("updated text 2", updatedBothParagraph.getText());

    ZeppelinServer.notebook.removeNote(note.getId(), anonymous);
  }

  @Test
  public void testGetParagraph() throws IOException {
    Note note = ZeppelinServer.notebook.createNote(anonymous);

    Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p.setTitle("hello");
    p.setText("world");
    note.persist(anonymous);

    GetMethod get = httpGet("/notebook/" + note.getId() + "/paragraph/" + p.getId());
    LOG.info("testGetParagraph response\n" + get.getResponseBodyAsString());
    assertThat("Test get method: ", get, isAllowed());
    get.releaseConnection();

    Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());

    assertNotNull(resp);
    assertEquals("OK", resp.get("status"));

    Map<String, Object> body = (Map<String, Object>) resp.get("body");

    assertEquals(p.getId(), body.get("id"));
    assertEquals("hello", body.get("title"));
    assertEquals("world", body.get("text"));

    ZeppelinServer.notebook.removeNote(note.getId(), anonymous);
  }

  @Test
  public void testMoveParagraph() throws IOException {
    Note note = ZeppelinServer.notebook.createNote(anonymous);

    Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p.setTitle("title1");
    p.setText("text1");

    Paragraph p2 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p2.setTitle("title2");
    p2.setText("text2");

    note.persist(anonymous);

    PostMethod post = httpPost("/notebook/" + note.getId() + "/paragraph/" + p2.getId() + "/move/" + 0, "");
    assertThat("Test post method: ", post, isAllowed());
    post.releaseConnection();

    Note retrNote = ZeppelinServer.notebook.getNote(note.getId());
    Paragraph paragraphAtIdx0 = retrNote.getParagraphs().get(0);

    assertEquals(p2.getId(), paragraphAtIdx0.getId());
    assertEquals(p2.getTitle(), paragraphAtIdx0.getTitle());
    assertEquals(p2.getText(), paragraphAtIdx0.getText());

    PostMethod post2 = httpPost("/notebook/" + note.getId() + "/paragraph/" + p2.getId() + "/move/" + 10, "");
    assertThat("Test post method: ", post2, isBadRequest());
    post.releaseConnection();

    ZeppelinServer.notebook.removeNote(note.getId(), anonymous);
  }

  @Test
  public void testDeleteParagraph() throws IOException {
    Note note = ZeppelinServer.notebook.createNote(anonymous);

    Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p.setTitle("title1");
    p.setText("text1");

    note.persist(anonymous);

    DeleteMethod delete = httpDelete("/notebook/" + note.getId() + "/paragraph/" + p.getId());
    assertThat("Test delete method: ", delete, isAllowed());
    delete.releaseConnection();

    Note retrNote = ZeppelinServer.notebook.getNote(note.getId());
    Paragraph retrParagrah = retrNote.getParagraph(p.getId());
    assertNull("paragraph should be deleted", retrParagrah);

    ZeppelinServer.notebook.removeNote(note.getId(), anonymous);
  }

  @Test
  public void testTitleSearch() throws IOException {
    Note note = ZeppelinServer.notebook.createNote(anonymous);
    String jsonRequest = "{\"title\": \"testTitleSearchOfParagraph\", \"text\": \"ThisIsToTestSearchMethodWithTitle \"}";
    PostMethod postNoteText = httpPost("/notebook/" + note.getId() + "/paragraph", jsonRequest);
    postNoteText.releaseConnection();

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
    ZeppelinServer.notebook.removeNote(note.getId(), anonymous);
  }

}
