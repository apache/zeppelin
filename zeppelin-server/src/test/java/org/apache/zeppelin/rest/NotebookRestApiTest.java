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
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NotebookAuthorization;
import org.apache.zeppelin.notebook.NotebookAuthorizationInfoSaving;
import org.apache.zeppelin.server.ZeppelinServer;
import org.junit.AfterClass;
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

  @BeforeClass
  public static void init() throws Exception {
    AbstractTestRestApi.startUp();
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  @Test
  public void testPermissions() throws IOException {
    Note note1 = ZeppelinServer.notebook.createNote();
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


    Note note2 = ZeppelinServer.notebook.createNote();
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
    ZeppelinServer.notebook.removeNote(note1.getId());
    ZeppelinServer.notebook.removeNote(note2.getId());

  }
}


