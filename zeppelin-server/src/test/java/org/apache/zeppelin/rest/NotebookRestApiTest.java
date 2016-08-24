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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NotebookAuthorization;
import org.apache.zeppelin.notebook.NotebookAuthorizationInfoSaving;
import org.apache.zeppelin.server.ZeppelinServer;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * Zeppelin notebook rest api tests
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class NotebookRestApiTest extends AbstractTestRestApi {
  Gson gson = new Gson();
  AuthenticationInfo anonymous;

  @BeforeClass
  public static void init() throws Exception {
    AbstractTestRestApi.startUp();
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
  public void testPermissions() throws IOException {
    Note note1 = ZeppelinServer.notebook.createNote(anonymous);
    // Set only readers
    String jsonRequest = "{\"readers\":[\"admin-team\"],\"owners\":[]," +
            "\"writers\":[]}";
    PutMethod put = httpPut("/notebook/" + note1.getId() + "/permissions/", jsonRequest);
    LOG.info("testPermissions response\n" + put.getResponseBodyAsString());
    assertThat("test update method:", put, isAllowed());
    put.releaseConnection();


    GetMethod get = httpGet("/notebook/" + note1.getId() + "/permissions/");
    assertThat(get, isAllowed());
    Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());
    Map<String, Set<String>> authInfo = (Map<String, Set<String>>) resp.get("body");

    // Check that both owners and writers is set to the princpal if empty
    assertEquals(authInfo.get("readers"), Lists.newArrayList("admin-team"));
    assertEquals(authInfo.get("owners"), Lists.newArrayList("anonymous"));
    assertEquals(authInfo.get("writers"), Lists.newArrayList("anonymous"));
    get.releaseConnection();


    Note note2 = ZeppelinServer.notebook.createNote(anonymous);
    // Set only writers
    jsonRequest = "{\"readers\":[],\"owners\":[]," +
            "\"writers\":[\"admin-team\"]}";
    put = httpPut("/notebook/" + note2.getId() + "/permissions/", jsonRequest);
    assertThat("test update method:", put, isAllowed());
    put.releaseConnection();

    get = httpGet("/notebook/" + note2.getId() + "/permissions/");
    assertThat(get, isAllowed());
    resp = gson.fromJson(get.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());
    authInfo = (Map<String, Set<String>>) resp.get("body");
    // Check that owners is set to the princpal if empty
    assertEquals(authInfo.get("owners"), Lists.newArrayList("anonymous"));
    assertEquals(authInfo.get("writers"), Lists.newArrayList("admin-team"));
    get.releaseConnection();


    // Test clear permissions
    jsonRequest = "{\"readers\":[],\"owners\":[],\"writers\":[]}";
    put = httpPut("/notebook/" + note2.getId() + "/permissions/", jsonRequest);
    put.releaseConnection();
    get = httpGet("/notebook/" + note2.getId() + "/permissions/");
    assertThat(get, isAllowed());
    resp = gson.fromJson(get.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());
    authInfo = (Map<String, Set<String>>) resp.get("body");

    assertEquals(authInfo.get("readers"), Lists.newArrayList());
    assertEquals(authInfo.get("writers"), Lists.newArrayList());
    assertEquals(authInfo.get("owners"), Lists.newArrayList());
    get.releaseConnection();
    //cleanup
    ZeppelinServer.notebook.removeNote(note1.getId(), anonymous);
    ZeppelinServer.notebook.removeNote(note2.getId(), anonymous);

  }

  @Test
  public void testGetNoteParagraphJobStatus() throws IOException {
    Note note1 = ZeppelinServer.notebook.createNote(anonymous);
    note1.addParagraph();

    String paragraphId = note1.getLastParagraph().getId();

    GetMethod get = httpGet("/notebook/job/" + note1.getId() + "/" + paragraphId);
    assertThat(get, isAllowed());
    Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());
    Map<String, Set<String>> paragraphStatus = (Map<String, Set<String>>) resp.get("body");

    // Check id and status have proper value
    assertEquals(paragraphStatus.get("id"), paragraphId);
    assertEquals(paragraphStatus.get("status"), "READY");

    //cleanup
    ZeppelinServer.notebook.removeNote(note1.getId(), anonymous);

  }

  @Test
  public void testCloneNotebook() throws IOException {
    Note note1 = ZeppelinServer.notebook.createNote(null);
    PostMethod post = httpPost("/notebook/" + note1.getId(), "");
    LOG.info("testCloneNotebook response\n" + post.getResponseBodyAsString());
    assertThat(post, isCreated());
    Map<String, Object> resp = gson.fromJson(post.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());
    String clonedNotebookId = (String) resp.get("body");
    post.releaseConnection();

    GetMethod get = httpGet("/notebook/" + clonedNotebookId);
    assertThat(get, isAllowed());
    Map<String, Object> resp2 = gson.fromJson(get.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());
    Map<String, Object> resp2Body = (Map<String, Object>) resp2.get("body");

    assertEquals((String)resp2Body.get("name"), "Note " + clonedNotebookId);
    get.releaseConnection();

    //cleanup
    ZeppelinServer.notebook.removeNote(note1.getId(), null);
    ZeppelinServer.notebook.removeNote(clonedNotebookId, null);

  }
}


