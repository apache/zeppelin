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
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.server.ZeppelinServer;
import org.junit.AfterClass;
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

  @BeforeClass
  public static void init() throws Exception {
    AbstractTestRestApi.startUp();
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
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
  public void testGetNotebookInfo() throws IOException {
    LOG.info("testGetNotebookInfo");
    // Create note to get info
    Note note = ZeppelinServer.notebook.createNote(null);
    assertNotNull("can't create new note", note);
    note.setName("note");
    Paragraph paragraph = note.addParagraph();
    Map config = paragraph.getConfig();
    config.put("enabled", true);
    paragraph.setConfig(config);
    String paragraphText = "%md This is my new paragraph in my new note";
    paragraph.setText(paragraphText);
    note.persist(null);

    String sourceNoteID = note.getId();
    GetMethod get = httpGet("/notebook/" + sourceNoteID);
    LOG.info("testGetNotebookInfo \n" + get.getResponseBodyAsString());
    assertThat("test notebook get method:", get, isAllowed());

    Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());

    assertNotNull(resp);
    assertEquals("OK", resp.get("status"));

    Map<String, Object> body = (Map<String, Object>) resp.get("body");
    List<Map<String, Object>> paragraphs = (List<Map<String, Object>>) body.get("paragraphs");

    assertTrue(paragraphs.size() > 0);
    assertEquals(paragraphText, paragraphs.get(0).get("text"));
  }

  @Test
  public void testNotebookCreateWithName() throws IOException {
    String noteName = "Test note name";
    testNotebookCreate(noteName);
  }

  @Test
  public void testNotebookCreateNoName() throws IOException {
    testNotebookCreate("");
  }

  @Test
  public void testNotebookCreateWithParagraphs() throws IOException {
    // Call Create Notebook REST API
    String noteName = "test";
    String jsonRequest = "{\"name\":\"" + noteName + "\", \"paragraphs\": [" +
        "{\"title\": \"title1\", \"text\": \"text1\"}," +
        "{\"title\": \"title2\", \"text\": \"text2\"}" +
        "]}";
    PostMethod post = httpPost("/notebook/", jsonRequest);
    LOG.info("testNotebookCreate \n" + post.getResponseBodyAsString());
    assertThat("test notebook create method:", post, isCreated());

    Map<String, Object> resp = gson.fromJson(post.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());

    String newNotebookId =  (String) resp.get("body");
    LOG.info("newNotebookId:=" + newNotebookId);
    Note newNote = ZeppelinServer.notebook.getNote(newNotebookId);
    assertNotNull("Can not find new note by id", newNote);
    // This is partial test as newNote is in memory but is not persistent
    String newNoteName = newNote.getName();
    LOG.info("new note name is: " + newNoteName);
    String expectedNoteName = noteName;
    if (noteName.isEmpty()) {
      expectedNoteName = "Note " + newNotebookId;
    }
    assertEquals("compare note name", expectedNoteName, newNoteName);
    assertEquals("initial paragraph check failed", 3, newNote.getParagraphs().size());
    for (Paragraph p : newNote.getParagraphs()) {
      if (StringUtils.isEmpty(p.getText()) ||
              p.getText().trim().equals(newNote.getLastInterpreterName())) {
        continue;
      }
      assertTrue("paragraph title check failed", p.getTitle().startsWith("title"));
      assertTrue("paragraph text check failed", p.getText().startsWith("text"));
    }
    // cleanup
    ZeppelinServer.notebook.removeNote(newNotebookId, null);
    post.releaseConnection();
  }

  private void testNotebookCreate(String noteName) throws IOException {
    // Call Create Notebook REST API
    String jsonRequest = "{\"name\":\"" + noteName + "\"}";
    PostMethod post = httpPost("/notebook/", jsonRequest);
    LOG.info("testNotebookCreate \n" + post.getResponseBodyAsString());
    assertThat("test notebook create method:", post, isCreated());

    Map<String, Object> resp = gson.fromJson(post.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());

    String newNotebookId =  (String) resp.get("body");
    LOG.info("newNotebookId:=" + newNotebookId);
    Note newNote = ZeppelinServer.notebook.getNote(newNotebookId);
    assertNotNull("Can not find new note by id", newNote);
    // This is partial test as newNote is in memory but is not persistent
    String newNoteName = newNote.getName();
    LOG.info("new note name is: " + newNoteName);
    String expectedNoteName = noteName;
    if (noteName.isEmpty()) {
      expectedNoteName = "Note " + newNotebookId;
    }
    assertEquals("compare note name", expectedNoteName, newNoteName);
    // cleanup
    ZeppelinServer.notebook.removeNote(newNotebookId, null);
    post.releaseConnection();

  }

  @Test
  public void testDeleteNote() throws IOException {
    LOG.info("testDeleteNote");
    //Create note and get ID
    Note note = ZeppelinServer.notebook.createNote(null);
    String noteId = note.getId();
    testDeleteNotebook(noteId);
  }

  @Test
  public void testDeleteNoteBadId() throws IOException {
    LOG.info("testDeleteNoteBadId");
    testDeleteNotebook("2AZFXEX97");
    testDeleteNotebook("bad_ID");
  }


  @Test
  public void testExportNotebook() throws IOException {
    LOG.info("testExportNotebook");
    Note note = ZeppelinServer.notebook.createNote(null);
    assertNotNull("can't create new note", note);
    note.setName("source note for export");
    Paragraph paragraph = note.addParagraph();
    Map config = paragraph.getConfig();
    config.put("enabled", true);
    paragraph.setConfig(config);
    paragraph.setText("%md This is my new paragraph in my new note");
    note.persist(null);
    String sourceNoteID = note.getId();
    // Call export Notebook REST API
    GetMethod get = httpGet("/notebook/export/" + sourceNoteID);
    LOG.info("testNotebookExport \n" + get.getResponseBodyAsString());
    assertThat("test notebook export method:", get, isAllowed());

    Map<String, Object> resp =
        gson.fromJson(get.getResponseBodyAsString(),
            new TypeToken<Map<String, Object>>() {}.getType());

    String exportJSON = (String) resp.get("body");
    assertNotNull("Can not find new notejson", exportJSON);
    LOG.info("export JSON:=" + exportJSON);
    ZeppelinServer.notebook.removeNote(sourceNoteID, null);
    get.releaseConnection();

  }

  @Test
  public void testImportNotebook() throws IOException {
    Map<String, Object> resp;
    String noteName = "source note for import";
    LOG.info("testImortNotebook");
    // create test notebook
    Note note = ZeppelinServer.notebook.createNote(null);
    assertNotNull("can't create new note", note);
    note.setName(noteName);
    Paragraph paragraph = note.addParagraph();
    Map config = paragraph.getConfig();
    config.put("enabled", true);
    paragraph.setConfig(config);
    paragraph.setText("%md This is my new paragraph in my new note");
    note.persist(null);
    String sourceNoteID = note.getId();
    // get note content as JSON
    String oldJson = getNoteContent(sourceNoteID);
    // call notebook post
    PostMethod importPost = httpPost("/notebook/import/", oldJson);
    assertThat(importPost, isCreated());
    resp =
        gson.fromJson(importPost.getResponseBodyAsString(),
            new TypeToken<Map<String, Object>>() {}.getType());
    String importId = (String) resp.get("body");

    assertNotNull("Did not get back a notebook id in body", importId);
    Note newNote = ZeppelinServer.notebook.getNote(importId);
    assertEquals("Compare note names", noteName, newNote.getName());
    assertEquals("Compare paragraphs count", note.getParagraphs().size(), newNote.getParagraphs()
        .size());
    // cleanup
    ZeppelinServer.notebook.removeNote(note.getId(), null);
    ZeppelinServer.notebook.removeNote(newNote.getId(), null);
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

  private void testDeleteNotebook(String notebookId) throws IOException {

    DeleteMethod delete = httpDelete(("/notebook/" + notebookId));
    LOG.info("testDeleteNotebook delete response\n" + delete.getResponseBodyAsString());
    assertThat("Test delete method:", delete, isAllowed());
    delete.releaseConnection();
    // make sure note is deleted
    if (!notebookId.isEmpty()) {
      Note deletedNote = ZeppelinServer.notebook.getNote(notebookId);
      assertNull("Deleted note should be null", deletedNote);
    }
  }

  @Test
  public void testCloneNotebook() throws IOException, CloneNotSupportedException, IllegalArgumentException {
    LOG.info("testCloneNotebook");
    // Create note to clone
    Note note = ZeppelinServer.notebook.createNote(null);
    assertNotNull("can't create new note", note);
    note.setName("source note for clone");
    Paragraph paragraph = note.addParagraph();
    Map config = paragraph.getConfig();
    config.put("enabled", true);
    paragraph.setConfig(config);
    paragraph.setText("%md This is my new paragraph in my new note");
    note.persist(null);
    String sourceNoteID = note.getId();

    String noteName = "clone Note Name";
    // Call Clone Notebook REST API
    String jsonRequest = "{\"name\":\"" + noteName + "\"}";
    PostMethod post = httpPost("/notebook/" + sourceNoteID, jsonRequest);
    LOG.info("testNotebookClone \n" + post.getResponseBodyAsString());
    assertThat("test notebook clone method:", post, isCreated());

    Map<String, Object> resp = gson.fromJson(post.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());

    String newNotebookId =  (String) resp.get("body");
    LOG.info("newNotebookId:=" + newNotebookId);
    Note newNote = ZeppelinServer.notebook.getNote(newNotebookId);
    assertNotNull("Can not find new note by id", newNote);
    assertEquals("Compare note names", noteName, newNote.getName());
    assertEquals("Compare paragraphs count", note.getParagraphs().size(), newNote.getParagraphs().size());
    //cleanup
    ZeppelinServer.notebook.removeNote(note.getId(), null);
    ZeppelinServer.notebook.removeNote(newNote.getId(), null);
    post.releaseConnection();
  }

  @Test
  public void testListNotebooks() throws IOException {
    LOG.info("testListNotebooks");
    GetMethod get = httpGet("/notebook/ ");
    assertThat("List notebooks method", get, isAllowed());
    Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());
    List<Map<String, String>> body = (List<Map<String, String>>) resp.get("body");
    assertEquals("List notebooks are equal", ZeppelinServer.notebook.getAllNotes().size(), body.size());
    get.releaseConnection();
  }

  @Test
  public void testNoteJobs() throws IOException, InterruptedException {
    LOG.info("testNoteJobs");
    // Create note to run test.
    Note note = ZeppelinServer.notebook.createNote(null);
    assertNotNull("can't create new note", note);
    note.setName("note for run test");
    Paragraph paragraph = note.addParagraph();
    
    Map config = paragraph.getConfig();
    config.put("enabled", true);
    paragraph.setConfig(config);
    
    paragraph.setText("%md This is test paragraph.");
    note.persist(null);
    String noteID = note.getId();

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
    
    // Call Run Notebook Jobs REST API
    PostMethod postNoteJobs = httpPost("/notebook/job/" + noteID, "");
    assertThat("test notebook jobs run:", postNoteJobs, isAllowed());
    postNoteJobs.releaseConnection();

    // Call Stop Notebook Jobs REST API
    DeleteMethod deleteNoteJobs = httpDelete("/notebook/job/" + noteID);
    assertThat("test notebook stop:", deleteNoteJobs, isAllowed());
    deleteNoteJobs.releaseConnection();    
    Thread.sleep(1000);
    
    // Call Run paragraph REST API
    PostMethod postParagraph = httpPost("/notebook/job/" + noteID + "/" + paragraph.getId(), "");
    assertThat("test paragraph run:", postParagraph, isAllowed());
    postParagraph.releaseConnection();    
    Thread.sleep(1000);
    
    // Call Stop paragraph REST API
    DeleteMethod deleteParagraph = httpDelete("/notebook/job/" + noteID + "/" + paragraph.getId());
    assertThat("test paragraph stop:", deleteParagraph, isAllowed());
    deleteParagraph.releaseConnection();    
    Thread.sleep(1000);
    
    //cleanup
    ZeppelinServer.notebook.removeNote(note.getId(), null);
  }

  @Test
  public void testGetNotebookJob() throws IOException, InterruptedException {
    LOG.info("testGetNotebookJob");
    // Create note to run test.
    Note note = ZeppelinServer.notebook.createNote(null);
    assertNotNull("can't create new note", note);
    note.setName("note for run test");
    Paragraph paragraph = note.addParagraph();

    Map config = paragraph.getConfig();
    config.put("enabled", true);
    paragraph.setConfig(config);

    paragraph.setText("%sh sleep 1");
    note.persist(null);
    String noteID = note.getId();

    note.runAll();

    // wait until paragraph gets started
    while (!paragraph.getStatus().isRunning()) {
      Thread.sleep(100);
    }

    // assume that status of the paragraph is running
    GetMethod get = httpGet("/notebook/job/" + noteID);
    assertThat("test get notebook job: ", get, isAllowed());
    String responseBody = get.getResponseBodyAsString();
    get.releaseConnection();

    LOG.info("test get notebook job: \n" + responseBody);
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
        LOG.info("testGetNotebookJob timeout job.");
        break;
      }
    }

    ZeppelinServer.notebook.removeNote(note.getId(), null);
  }

  @Test
  public void testRunParagraphWithParams() throws IOException, InterruptedException {
    LOG.info("testRunParagraphWithParams");
    // Create note to run test.
    Note note = ZeppelinServer.notebook.createNote(null);
    assertNotNull("can't create new note", note);
    note.setName("note for run test");
    Paragraph paragraph = note.addParagraph();

    Map config = paragraph.getConfig();
    config.put("enabled", true);
    paragraph.setConfig(config);

    paragraph.setText("%spark\nval param = z.input(\"param\").toString\nprintln(param)");
    note.persist(null);
    String noteID = note.getId();

    note.runAll();
    // wait until job is finished or timeout.
    int timeout = 1;
    while (!paragraph.isTerminated()) {
      Thread.sleep(1000);
      if (timeout++ > 120) {
        LOG.info("testRunParagraphWithParams timeout job.");
        break;
      }
    }

    // Call Run paragraph REST API
    PostMethod postParagraph = httpPost("/notebook/job/" + noteID + "/" + paragraph.getId(),
        "{\"params\": {\"param\": \"hello\", \"param2\": \"world\"}}");
    assertThat("test paragraph run:", postParagraph, isAllowed());
    postParagraph.releaseConnection();
    Thread.sleep(1000);

    Note retrNote = ZeppelinServer.notebook.getNote(noteID);
    Paragraph retrParagraph = retrNote.getParagraph(paragraph.getId());
    Map<String, Object> params = retrParagraph.settings.getParams();
    assertEquals("hello", params.get("param"));
    assertEquals("world", params.get("param2"));

    //cleanup
    ZeppelinServer.notebook.removeNote(note.getId(), null);
  }

  @Test
  public void testCronJobs() throws InterruptedException, IOException{
    // create a note and a paragraph
    Note note = ZeppelinServer.notebook.createNote(null);

    note.setName("note for run test");
    Paragraph paragraph = note.addParagraph();
    paragraph.setText("%md This is test paragraph.");
    
    Map config = paragraph.getConfig();
    config.put("enabled", true);
    paragraph.setConfig(config);

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
    ZeppelinServer.notebook.removeNote(note.getId(), null);
  }

  @Test
  public void testRegressionZEPPELIN_527() throws IOException {
    Note note = ZeppelinServer.notebook.createNote(null);

    note.setName("note for run test");
    Paragraph paragraph = note.addParagraph();
    paragraph.setText("%spark\nval param = z.input(\"param\").toString\nprintln(param)");

    note.persist(null);

    GetMethod getNoteJobs = httpGet("/notebook/job/" + note.getId());
    assertThat("test notebook jobs run:", getNoteJobs, isAllowed());
    Map<String, Object> resp = gson.fromJson(getNoteJobs.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());
    List<Map<String, String>> body = (List<Map<String, String>>) resp.get("body");
    assertFalse(body.get(0).containsKey("started"));
    assertFalse(body.get(0).containsKey("finished"));
    getNoteJobs.releaseConnection();

    ZeppelinServer.notebook.removeNote(note.getId(), null);
  }

  @Test
  public void testInsertParagraph() throws IOException {
    Note note = ZeppelinServer.notebook.createNote(null);

    String jsonRequest = "{\"title\": \"title1\", \"text\": \"text1\"}";
    PostMethod post = httpPost("/notebook/" + note.getId() + "/paragraph", jsonRequest);
    LOG.info("testInsertParagraph response\n" + post.getResponseBodyAsString());
    assertThat("Test insert method:", post, isCreated());
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
    assertThat("Test insert method:", post2, isCreated());
    post2.releaseConnection();

    Paragraph paragraphAtIdx0 = note.getParagraphs().get(0);
    assertEquals("title2", paragraphAtIdx0.getTitle());
    assertEquals("text2", paragraphAtIdx0.getText());

    ZeppelinServer.notebook.removeNote(note.getId(), null);
  }

  @Test
  public void testGetParagraph() throws IOException {
    Note note = ZeppelinServer.notebook.createNote(null);

    Paragraph p = note.addParagraph();
    p.setTitle("hello");
    p.setText("world");
    note.persist(null);

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

    ZeppelinServer.notebook.removeNote(note.getId(), null);
  }

  @Test
  public void testMoveParagraph() throws IOException {
    Note note = ZeppelinServer.notebook.createNote(null);

    Paragraph p = note.addParagraph();
    p.setTitle("title1");
    p.setText("text1");

    Paragraph p2 = note.addParagraph();
    p2.setTitle("title2");
    p2.setText("text2");

    note.persist(null);

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

    ZeppelinServer.notebook.removeNote(note.getId(), null);
  }

  @Test
  public void testDeleteParagraph() throws IOException {
    Note note = ZeppelinServer.notebook.createNote(null);

    Paragraph p = note.addParagraph();
    p.setTitle("title1");
    p.setText("text1");

    note.persist(null);

    DeleteMethod delete = httpDelete("/notebook/" + note.getId() + "/paragraph/" + p.getId());
    assertThat("Test delete method: ", delete, isAllowed());
    delete.releaseConnection();

    Note retrNote = ZeppelinServer.notebook.getNote(note.getId());
    Paragraph retrParagrah = retrNote.getParagraph(p.getId());
    assertNull("paragraph should be deleted", retrParagrah);

    ZeppelinServer.notebook.removeNote(note.getId(), null);
  }

  @Test
  public void testSearch() throws IOException {
    Map<String, String> body;

    GetMethod getSecurityTicket = httpGet("/security/ticket");
    getSecurityTicket.addRequestHeader("Origin", "http://localhost");
    Map<String, Object> respSecurityTicket = gson.fromJson(getSecurityTicket.getResponseBodyAsString(),
        new TypeToken<Map<String, Object>>() {
        }.getType());
    body = (Map<String, String>) respSecurityTicket.get("body");
    String username = body.get("principal");
    getSecurityTicket.releaseConnection();

    Note note1 = ZeppelinServer.notebook.createNote(null);
    String jsonRequest = "{\"title\": \"title1\", \"text\": \"ThisIsToTestSearchMethodWithPermissions 1\"}";
    PostMethod postNotebookText = httpPost("/notebook/" + note1.getId() + "/paragraph", jsonRequest);
    postNotebookText.releaseConnection();

    Note note2 = ZeppelinServer.notebook.createNote(null);
    jsonRequest = "{\"title\": \"title1\", \"text\": \"ThisIsToTestSearchMethodWithPermissions 2\"}";
    postNotebookText = httpPost("/notebook/" + note2.getId() + "/paragraph", jsonRequest);
    postNotebookText.releaseConnection();

    String jsonPermissions = "{\"owners\":[\"" + username + "\"],\"readers\":[\"" + username + "\"],\"writers\":[\"" + username + "\"]}";
    PutMethod putPermission = httpPut("/notebook/" + note1.getId() + "/permissions", jsonPermissions);
    putPermission.releaseConnection();

    jsonPermissions = "{\"owners\":[\"admin\"],\"readers\":[\"admin\"],\"writers\":[\"admin\"]}";
    putPermission = httpPut("/notebook/" + note2.getId() + "/permissions", jsonPermissions);
    putPermission.releaseConnection();

    GetMethod searchNotebook = httpGet("/notebook/search?q='ThisIsToTestSearchMethodWithPermissions'");
    searchNotebook.addRequestHeader("Origin", "http://localhost");
    Map<String, Object> respSearchResult = gson.fromJson(searchNotebook.getResponseBodyAsString(),
        new TypeToken<Map<String, Object>>() {
        }.getType());
    ArrayList searchBody = (ArrayList) respSearchResult.get("body");

    assertEquals("At-least one search results is there", true, searchBody.size() >= 1);

    for (int i = 0; i < searchBody.size(); i++) {
      Map<String, String> searchResult = (Map<String, String>) searchBody.get(i);
      String userId = searchResult.get("id").split("/", 2)[0];
      GetMethod getPermission = httpGet("/notebook/" + userId + "/permissions");
      getPermission.addRequestHeader("Origin", "http://localhost");
      Map<String, Object> resp = gson.fromJson(getPermission.getResponseBodyAsString(),
          new TypeToken<Map<String, Object>>() {
          }.getType());
      Map<String, ArrayList> permissions = (Map<String, ArrayList>) resp.get("body");
      ArrayList owners = permissions.get("owners");
      ArrayList readers = permissions.get("readers");
      ArrayList writers = permissions.get("writers");

      if (owners.size() != 0 && readers.size() != 0 && writers.size() != 0) {
        assertEquals("User has permissions  ", true, (owners.contains(username) || readers.contains(username) ||
            writers.contains(username)));
      }
      getPermission.releaseConnection();
    }
    searchNotebook.releaseConnection();
    ZeppelinServer.notebook.removeNote(note1.getId(), null);
    ZeppelinServer.notebook.removeNote(note2.getId(), null);
  }

  @Test
  public void testTitleSearch() throws IOException {
    Note note = ZeppelinServer.notebook.createNote(null);
    String jsonRequest = "{\"title\": \"testTitleSearchOfParagraph\", \"text\": \"ThisIsToTestSearchMethodWithTitle \"}";
    PostMethod postNotebookText = httpPost("/notebook/" + note.getId() + "/paragraph", jsonRequest);
    postNotebookText.releaseConnection();

    GetMethod searchNotebook = httpGet("/notebook/search?q='testTitleSearchOfParagraph'");
    searchNotebook.addRequestHeader("Origin", "http://localhost");
    Map<String, Object> respSearchResult = gson.fromJson(searchNotebook.getResponseBodyAsString(),
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
    searchNotebook.releaseConnection();
    ZeppelinServer.notebook.removeNote(note.getId(), null);
  }

}

