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
  public void getAvailableInterpreters() throws IOException {
    // when
    GetMethod get = httpGet("/interpreter");

    // then
    assertThat(get, isAllowed());
    Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());
    Map<String, Object> body = (Map<String, Object>) resp.get("body");
    assertEquals(ZeppelinServer.notebook.getInterpreterFactory().getRegisteredInterpreterList().size(), body.size());
    get.releaseConnection();
  }

  @Test
  public void getSettings() throws IOException {
    // when
    GetMethod get = httpGet("/interpreter/setting");

    // then
    Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());
    assertThat(get, isAllowed());
    get.releaseConnection();
  }

  @Test
  public void testSettingsCRUD() throws IOException {
    // Call Create Setting REST API
    String jsonRequest = "{\"name\":\"md2\",\"group\":\"md\",\"properties\":{\"propname\":\"propvalue\"},\"" +
        "interpreterGroup\":[{\"class\":\"org.apache.zeppelin.markdown.Markdown\",\"name\":\"md\"}]}";
    PostMethod post = httpPost("/interpreter/setting/", jsonRequest);
    LOG.info("testSettingCRUD create response\n" + post.getResponseBodyAsString());
    assertThat("test create method:", post, isCreated());

    Map<String, Object> resp = gson.fromJson(post.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());
    Map<String, Object> body = (Map<String, Object>) resp.get("body");
    //extract id from body string {id=2AWMQDNX7, name=md2, group=md,
    String newSettingId =  body.toString().split(",")[0].split("=")[1];
    post.releaseConnection();

    // Call Update Setting REST API
    jsonRequest = "{\"name\":\"md2\",\"group\":\"md\",\"properties\":{\"propname\":\"Otherpropvalue\"},\"" +
        "interpreterGroup\":[{\"class\":\"org.apache.zeppelin.markdown.Markdown\",\"name\":\"md\"}]}";
    PutMethod put = httpPut("/interpreter/setting/" + newSettingId, jsonRequest);
    LOG.info("testSettingCRUD update response\n" + put.getResponseBodyAsString());
    assertThat("test update method:", put, isAllowed());
    put.releaseConnection();

    // Call Delete Setting REST API
    DeleteMethod delete = httpDelete("/interpreter/setting/" + newSettingId);
    LOG.info("testSettingCRUD delete response\n" + delete.getResponseBodyAsString());
    assertThat("Test delete method:", delete, isAllowed());
    delete.releaseConnection();
  }
  @Test
  public void testInterpreterAutoBinding() throws IOException {
    // create note
    Note note = ZeppelinServer.notebook.createNote();

    // check interpreter is binded
    GetMethod get = httpGet("/notebook/interpreter/bind/"+note.id());
    assertThat(get, isAllowed());
    get.addRequestHeader("Origin", "http://localhost");
    Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(), new TypeToken<Map<String, Object>>(){}.getType());
    List<Map<String, String>> body = (List<Map<String, String>>) resp.get("body");
    assertTrue(0 < body.size());

    get.releaseConnection();
    //cleanup
    ZeppelinServer.notebook.removeNote(note.getId());
  }

  @Test
  public void testInterpreterRestart() throws IOException, InterruptedException {
    // create new note
    Note note = ZeppelinServer.notebook.createNote();
    note.addParagraph();
    Paragraph p = note.getLastParagraph();
    Map config = p.getConfig();
    config.put("enabled", true);

    // run markdown paragraph
    p.setConfig(config);
    p.setText("%md markdown");
    note.run(p.getId());
    while (p.getStatus() != Status.FINISHED) {
      Thread.sleep(100);
    }
    assertEquals("<p>markdown</p>\n", p.getResult().message());

    
    // restart interpreter
    for (InterpreterSetting setting : note.getNoteReplLoader().getInterpreterSettings()) {
      if (setting.getName().equals("md")) {
        // Call Restart Interpreter REST API
        PutMethod put = httpPut("/interpreter/setting/restart/" + setting.id(), "");
        assertThat("test interpreter restart:", put, isAllowed());
        put.releaseConnection();
        break;
      }
    }

    // run markdown paragraph, again
    p = note.addParagraph();
    p.setConfig(config);
    p.setText("%md markdown restarted");
    note.run(p.getId());
    while (p.getStatus() != Status.FINISHED) {
      Thread.sleep(100);
    }
    assertEquals("<p>markdown restarted</p>\n", p.getResult().message());
    //cleanup
    ZeppelinServer.notebook.removeNote(note.getId());
  }

  @Test
  public void testGetNotebookInfo() throws IOException {
    LOG.info("testGetNotebookInfo");
    // Create note to get info
    Note note = ZeppelinServer.notebook.createNote();
    assertNotNull("can't create new note", note);
    note.setName("note");
    Paragraph paragraph = note.addParagraph();
    Map config = paragraph.getConfig();
    config.put("enabled", true);
    paragraph.setConfig(config);
    String paragraphText = "%md This is my new paragraph in my new note";
    paragraph.setText(paragraphText);
    note.persist();

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
      if (StringUtils.isEmpty(p.getText())) {
        continue;
      }
      assertTrue("paragraph title check failed", p.getTitle().startsWith("title"));
      assertTrue("paragraph text check failed", p.getText().startsWith("text"));
    }
    // cleanup
    ZeppelinServer.notebook.removeNote(newNotebookId);
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
    ZeppelinServer.notebook.removeNote(newNotebookId);
    post.releaseConnection();

  }

  @Test
  public void  testDeleteNote() throws IOException {
    LOG.info("testDeleteNote");
    //Create note and get ID
    Note note = ZeppelinServer.notebook.createNote();
    String noteId = note.getId();
    testDeleteNotebook(noteId);
  }

  @Test
  public void testDeleteNoteBadId() throws IOException {
    LOG.info("testDeleteNoteBadId");
    testDeleteNotebook("2AZFXEX97");
    testDeleteNotebook("bad_ID");
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
    Note note = ZeppelinServer.notebook.createNote();
    assertNotNull("can't create new note", note);
    note.setName("source note for clone");
    Paragraph paragraph = note.addParagraph();
    Map config = paragraph.getConfig();
    config.put("enabled", true);
    paragraph.setConfig(config);
    paragraph.setText("%md This is my new paragraph in my new note");
    note.persist();
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
    ZeppelinServer.notebook.removeNote(note.getId());
    ZeppelinServer.notebook.removeNote(newNote.getId());
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
    Note note = ZeppelinServer.notebook.createNote();
    assertNotNull("can't create new note", note);
    note.setName("note for run test");
    Paragraph paragraph = note.addParagraph();
    
    Map config = paragraph.getConfig();
    config.put("enabled", true);
    paragraph.setConfig(config);
    
    paragraph.setText("%md This is test paragraph.");
    note.persist();
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
    ZeppelinServer.notebook.removeNote(note.getId());
  }

  @Test
  public void testGetNotebookJob() throws IOException, InterruptedException {
    LOG.info("testGetNotebookJob");
    // Create note to run test.
    Note note = ZeppelinServer.notebook.createNote();
    assertNotNull("can't create new note", note);
    note.setName("note for run test");
    Paragraph paragraph = note.addParagraph();

    Map config = paragraph.getConfig();
    config.put("enabled", true);
    paragraph.setConfig(config);

    paragraph.setText("%sh sleep 1");
    note.persist();
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

    ZeppelinServer.notebook.removeNote(note.getId());
  }

  @Test
  public void testRunParagraphWithParams() throws IOException, InterruptedException {
    LOG.info("testRunParagraphWithParams");
    // Create note to run test.
    Note note = ZeppelinServer.notebook.createNote();
    assertNotNull("can't create new note", note);
    note.setName("note for run test");
    Paragraph paragraph = note.addParagraph();

    Map config = paragraph.getConfig();
    config.put("enabled", true);
    paragraph.setConfig(config);

    paragraph.setText("%spark\nval param = z.input(\"param\").toString\nprintln(param)");
    note.persist();
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
    ZeppelinServer.notebook.removeNote(note.getId());
  }

  @Test
  public void testCronJobs() throws InterruptedException, IOException{
    // create a note and a paragraph
    Note note = ZeppelinServer.notebook.createNote();

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
    ZeppelinServer.notebook.removeNote(note.getId());
  }

  @Test
  public void testRegressionZEPPELIN_527() throws IOException {
    Note note = ZeppelinServer.notebook.createNote();

    note.setName("note for run test");
    Paragraph paragraph = note.addParagraph();
    paragraph.setText("%spark\nval param = z.input(\"param\").toString\nprintln(param)");

    note.persist();

    GetMethod getNoteJobs = httpGet("/notebook/job/" + note.getId());
    assertThat("test notebook jobs run:", getNoteJobs, isAllowed());
    Map<String, Object> resp = gson.fromJson(getNoteJobs.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());
    List<Map<String, String>> body = (List<Map<String, String>>) resp.get("body");
    assertFalse(body.get(0).containsKey("started"));
    assertFalse(body.get(0).containsKey("finished"));
    getNoteJobs.releaseConnection();

    ZeppelinServer.notebook.removeNote(note.getId());
  }

  @Test
  public void testInsertParagraph() throws IOException {
    Note note = ZeppelinServer.notebook.createNote();

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

    ZeppelinServer.notebook.removeNote(note.getId());
  }

  @Test
  public void testGetParagraph() throws IOException {
    Note note = ZeppelinServer.notebook.createNote();

    Paragraph p = note.addParagraph();
    p.setTitle("hello");
    p.setText("world");
    note.persist();

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

    ZeppelinServer.notebook.removeNote(note.getId());
  }

  @Test
  public void testMoveParagraph() throws IOException {
    Note note = ZeppelinServer.notebook.createNote();

    Paragraph p = note.addParagraph();
    p.setTitle("title1");
    p.setText("text1");

    Paragraph p2 = note.addParagraph();
    p2.setTitle("title2");
    p2.setText("text2");

    note.persist();

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

    ZeppelinServer.notebook.removeNote(note.getId());
  }

  @Test
  public void testDeleteParagraph() throws IOException {
    Note note = ZeppelinServer.notebook.createNote();

    Paragraph p = note.addParagraph();
    p.setTitle("title1");
    p.setText("text1");

    note.persist();

    DeleteMethod delete = httpDelete("/notebook/" + note.getId() + "/paragraph/" + p.getId());
    assertThat("Test delete method: ", delete, isAllowed());
    delete.releaseConnection();

    Note retrNote = ZeppelinServer.notebook.getNote(note.getId());
    Paragraph retrParagrah = retrNote.getParagraph(p.getId());
    assertNull("paragraph should be deleted", retrParagrah);

    ZeppelinServer.notebook.removeNote(note.getId());
  }
}

