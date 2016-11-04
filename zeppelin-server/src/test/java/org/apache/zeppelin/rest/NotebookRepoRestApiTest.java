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
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * NotebookRepo rest api test.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class NotebookRepoRestApiTest extends AbstractTestRestApi {

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
  
  private List<Map<String, Object>> getListOfReposotiry() throws IOException {
    GetMethod get = httpGet("/notebook-repositories");
    Map<String, Object> responce = gson.fromJson(get.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {}.getType());
    get.releaseConnection();
    return (List<Map<String, Object>>) responce.get("body");
  }
  
  private void updateNotebookRepoWithNewSetting(String payload) throws IOException {
    PutMethod put = httpPut("/notebook-repositories", payload);
    int status = put.getStatusCode();
    put.releaseConnection();
    assertThat(status, is(200));
  }
  
  @Test public void ThatCanGetNotebookRepositoiesSettings() throws IOException {
    List<Map<String, Object>> listOfRepositories = getListOfReposotiry();
    assertThat(listOfRepositories.size(), is(not(0)));
  }
  
  @Test public void setNewDirectoryForLocalDirectory() throws IOException {
    List<Map<String, Object>> listOfRepositories = getListOfReposotiry();
    String localVfs = StringUtils.EMPTY;
    String className = StringUtils.EMPTY;

    for (int i = 0; i < listOfRepositories.size(); i++) {
      if (listOfRepositories.get(i).get("name").equals("VFSNotebookRepo")) {
        localVfs = (String) ((List<Map<String, Object>>)listOfRepositories.get(i).get("settings")).get(0).get("selected");
        className = (String) listOfRepositories.get(i).get("className");
        break;
      }
    }

    if (StringUtils.isBlank(localVfs)) {
      // no loval VFS set...
      return;
    }

    String payload = "{ \"name\": \"" + className + "\", \"settings\" : { \"Notebook Path\" : \"/tmp/newDir\" } }";
    updateNotebookRepoWithNewSetting(payload);
    
    // Verify
    listOfRepositories = getListOfReposotiry();
    String updatedPath = StringUtils.EMPTY;
    for (int i = 0; i < listOfRepositories.size(); i++) {
      if (listOfRepositories.get(i).get("name").equals("VFSNotebookRepo")) {
        updatedPath = (String) ((List<Map<String, Object>>)listOfRepositories.get(i).get("settings")).get(0).get("selected");
        break;
      }
    }
    assertThat(updatedPath, is("/tmp/newDir"));
    
    // go back to normal
    payload = "{ \"name\": \"" + className + "\", \"settings\" : { \"Notebook Path\" : \"" + localVfs + "\" } }";
    updateNotebookRepoWithNewSetting(payload);
  }
}
