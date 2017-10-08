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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.server.ZeppelinServer;
import org.hamcrest.Matcher;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class NotebookSecurityRestApiTest extends AbstractTestRestApi {

  Gson gson = new Gson();

  @BeforeClass
  public static void init() throws Exception {
    AbstractTestRestApi.startUpWithAuthenticationEnable(NotebookSecurityRestApiTest.class.getSimpleName());
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  @Before
  public void setUp() {}

  
  @Test
  public void testThatUserCanCreateAndRemoveNote() throws IOException {
    String noteId = createNoteForUser("test", "admin", "password1");
    assertNotNull(noteId);
    String id = getNoteIdForUser(noteId, "admin", "password1");
    assertThat(id, is(noteId));
    deleteNoteForUser(noteId, "admin", "password1");
  }
  
  @Test
  public void testThatOtherUserCanAccessNoteIfPermissionNotSet() throws IOException {
    String noteId = createNoteForUser("test", "admin", "password1");
    
    userTryGetNote(noteId, "user1", "password2", isAllowed());
    
    deleteNoteForUser(noteId, "admin", "password1");
  }
  
  @Test
  public void testThatOtherUserCannotAccessNoteIfPermissionSet() throws IOException {
    String noteId = createNoteForUser("test", "admin", "password1");
    
    //set permission
    String payload = "{ \"owners\": [\"admin\"], \"readers\": [\"user2\"], \"runners\": [\"user2\"], \"writers\": [\"user2\"] }";
    PutMethod put = httpPut("/notebook/" + noteId + "/permissions", payload , "admin", "password1");
    assertThat("test set note permission method:", put, isAllowed());
    put.releaseConnection();
    
    userTryGetNote(noteId, "user1", "password2", isForbidden());
    
    userTryGetNote(noteId, "user2", "password3", isAllowed());
    
    deleteNoteForUser(noteId, "admin", "password1");
  }
  
  @Test
  public void testThatWriterCannotRemoveNote() throws IOException {
    String noteId = createNoteForUser("test", "admin", "password1");
    
    //set permission
    String payload = "{ \"owners\": [\"admin\", \"user1\"], \"readers\": [\"user2\"], \"runners\": [\"user2\"], \"writers\": [\"user2\"] }";
    PutMethod put = httpPut("/notebook/" + noteId + "/permissions", payload , "admin", "password1");
    assertThat("test set note permission method:", put, isAllowed());
    put.releaseConnection();
    
    userTryRemoveNote(noteId, "user2", "password3", isForbidden());
    userTryRemoveNote(noteId, "user1", "password2", isAllowed());
    
    Note deletedNote = ZeppelinServer.notebook.getNote(noteId);
    assertNull("Deleted note should be null", deletedNote);
  }

  @Test
  public void testThatUserCanSearchNote() throws IOException {
    String noteId1 = createNoteForUser("test1", "admin", "password1");
    createParagraphForUser(noteId1, "admin", "password1", "title1", "ThisIsToTestSearchMethodWithPermissions 1");

    String noteId2 = createNoteForUser("test2", "user1", "password2");
    createParagraphForUser(noteId1, "admin", "password1", "title2", "ThisIsToTestSearchMethodWithPermissions 2");

    //set permission for each note
    setPermissionForNote(noteId1, "admin", "password1");
    setPermissionForNote(noteId1, "user1", "password2");

    searchNoteBasedOnPermission("ThisIsToTestSearchMethodWithPermissions", "admin", "password1");

    deleteNoteForUser(noteId1, "admin", "password1");
    deleteNoteForUser(noteId2, "user1", "password2");
  }

  private void userTryRemoveNote(String noteId, String user, String pwd, Matcher<? super HttpMethodBase> m) throws IOException {
    DeleteMethod delete = httpDelete(("/notebook/" + noteId), user, pwd);
    assertThat(delete, m);
    delete.releaseConnection();
  }
  
  private void userTryGetNote(String noteId, String user, String pwd, Matcher<? super HttpMethodBase> m) throws IOException {
    GetMethod get = httpGet("/notebook/" + noteId, user, pwd);
    assertThat(get, m);
    get.releaseConnection();
  }

  private String getNoteIdForUser(String noteId, String user, String pwd) throws IOException {
    GetMethod get = httpGet("/notebook/" + noteId, user, pwd);
    assertThat("test note create method:", get, isAllowed());
    Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());
    get.releaseConnection();
    return (String) ((Map<String, Object>)resp.get("body")).get("id");
  }
  
  private String createNoteForUser(String noteName, String user, String pwd) throws IOException {
    String jsonRequest = "{\"name\":\"" + noteName + "\"}";
    PostMethod post = httpPost("/notebook/", jsonRequest, user, pwd);
    assertThat("test note create method:", post, isAllowed());
    Map<String, Object> resp = gson.fromJson(post.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());
    post.releaseConnection();
    String newNoteId =  (String) resp.get("body");
    Note newNote = ZeppelinServer.notebook.getNote(newNoteId);
    assertNotNull("Can not find new note by id", newNote);
    return newNoteId;
  }

  private void deleteNoteForUser(String noteId, String user, String pwd) throws IOException {
    DeleteMethod delete = httpDelete(("/notebook/" + noteId), user, pwd);
    assertThat("Test delete method:", delete, isAllowed());
    delete.releaseConnection();
    // make sure note is deleted
    if (!noteId.isEmpty()) {
      Note deletedNote = ZeppelinServer.notebook.getNote(noteId);
      assertNull("Deleted note should be null", deletedNote);
    }
  }

  private void createParagraphForUser(String noteId, String user, String pwd, String title, String text) throws IOException {
    String payload = "{\"title\": \"" + title + "\",\"text\": \"" + text + "\"}";
    PostMethod post = httpPost(("/notebook/" + noteId + "/paragraph"), payload, user, pwd);
    post.releaseConnection();
  }

  private void setPermissionForNote(String noteId, String user, String pwd) throws IOException {
    String payload = "{\"owners\":[\"" + user + "\"],\"readers\":[\"" + user + "\"],\"runners\":[\"" + user + "\"],\"writers\":[\"" + user + "\"]}";
    PutMethod put = httpPut(("/notebook/" + noteId + "/permissions"), payload, user, pwd);
    put.releaseConnection();
  }


  private void searchNoteBasedOnPermission(String searchText, String user, String pwd) throws IOException{
    GetMethod searchNote = httpGet(("/notebook/search?q=" + searchText), user, pwd);
    Map<String, Object> respSearchResult = gson.fromJson(searchNote.getResponseBodyAsString(),
      new TypeToken<Map<String, Object>>() {
      }.getType());
    ArrayList searchBody = (ArrayList) respSearchResult.get("body");
    assertEquals("At-least one search results is there", true, searchBody.size() >= 1);

    for (int i = 0; i < searchBody.size(); i++) {
      Map<String, String> searchResult = (Map<String, String>) searchBody.get(i);
      String userId = searchResult.get("id").split("/", 2)[0];

      GetMethod getPermission = httpGet(("/notebook/" + userId + "/permissions"), user, pwd);
      Map<String, Object> resp = gson.fromJson(getPermission.getResponseBodyAsString(),
        new TypeToken<Map<String, Object>>() {
        }.getType());
      Map<String, ArrayList> permissions = (Map<String, ArrayList>) resp.get("body");
      ArrayList owners = permissions.get("owners");
      ArrayList readers = permissions.get("readers");
      ArrayList writers = permissions.get("writers");
      ArrayList runners = permissions.get("runners");

      if (owners.size() != 0 && readers.size() != 0 && writers.size() != 0 && runners.size() != 0) {
        assertEquals("User has permissions  ", true, (owners.contains(user) || readers.contains(user) ||
          writers.contains(user) || runners.contains(user)));
      }
      getPermission.releaseConnection();
    }
    searchNote.releaseConnection();
  }
}
