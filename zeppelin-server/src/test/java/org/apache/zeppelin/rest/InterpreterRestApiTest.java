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
 * Zeppelin interpreter rest api tests
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class InterpreterRestApiTest extends AbstractTestRestApi {
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
    String jsonRequest = "{\"name\":\"md2\",\"group\":\"md\",\"properties\":{\"propname\":\"propvalue\"}," +
        "\"interpreterGroup\":[{\"class\":\"org.apache.zeppelin.markdown.Markdown\",\"name\":\"md\"}]," +
        "\"dependencies\":[]," +
        "\"option\": { \"remote\": true, \"perNoteSession\": false }}";
    PostMethod post = httpPost("/interpreter/setting/", jsonRequest);
    LOG.info("testSettingCRUD create response\n" + post.getResponseBodyAsString());
    assertThat("test create method:", post, isCreated());

    Map<String, Object> resp = gson.fromJson(post.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());
    Map<String, Object> body = (Map<String, Object>) resp.get("body");
    //extract id from body string {id=2AWMQDNX7, name=md2, group=md,
    String newSettingId = body.toString().split(",")[0].split("=")[1];
    post.releaseConnection();

    // Call Update Setting REST API
    jsonRequest = "{\"name\":\"md2\",\"group\":\"md\",\"properties\":{\"propname\":\"Otherpropvalue\"}," +
        "\"interpreterGroup\":[{\"class\":\"org.apache.zeppelin.markdown.Markdown\",\"name\":\"md\"}]," +
        "\"dependencies\":[]," +
        "\"option\": { \"remote\": true, \"perNoteSession\": false }}";
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
    GetMethod get = httpGet("/notebook/interpreter/bind/" + note.id());
    assertThat(get, isAllowed());
    get.addRequestHeader("Origin", "http://localhost");
    Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(), new TypeToken<Map<String, Object>>() {
    }.getType());
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
  public void testListRepository() throws IOException {
    GetMethod get = httpGet("/interpreter/repository");
    assertThat(get, isAllowed());
    get.releaseConnection();
  }

  @Test
  public void testAddDeleteRepository() throws IOException {
    // Call create repository REST API
    String repoId = "securecentral";
    String jsonRequest = "{\"id\":\"" + repoId +
        "\",\"url\":\"https://repo1.maven.org/maven2\",\"snapshot\":\"false\"}";

    PostMethod post = httpPost("/interpreter/repository/", jsonRequest);
    assertThat("Test create method:", post, isCreated());
    post.releaseConnection();

    // Call delete repository REST API
    DeleteMethod delete = httpDelete("/interpreter/repository/" + repoId);
    assertThat("Test delete method:", delete, isAllowed());
    delete.releaseConnection();
  }
}
